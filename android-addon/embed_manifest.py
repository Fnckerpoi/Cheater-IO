"""Add only the non-exported embedded workbench Activity to binary AXML."""
import struct
import background_manifest as ax
from tts_manifest import NS,U
ACTIVITY='com.turboio.addon.AaEmbeddedActivity'
def patch(data):
 chunks=ax._chunks(data)
 pi=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==1)
 mi=next(i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==0x180)
 strings=ax._decode_pool(chunks[pi])[0]
 chunks[pi],strings,idx=ax._append_pool_strings(chunks[pi],strings,['activity','name','exported',NS,ACTIVITY])
 resource=ax._attr_resource_map(chunks[mi]);resource.extend([0]*(len(strings)-len(resource)))
 for name,value in dict(name=0x01010003,exported=0x01010010).items():
  if resource[idx[name]] not in (0,value):raise ValueError('framework mapping')
  resource[idx[name]]=value
 chunks[mi]=struct.pack('<HHI',0x180,8,8+4*len(resource))+struct.pack('<'+'I'*len(resource),*resource)
 attrs=[ax._attr_record(idx[NS],idx['name'],idx[ACTIVITY],3,idx[ACTIVITY]),ax._attr_record(idx[NS],idx['exported'],U,0x12,0)]
 nodes=[ax._begin(idx['activity'],sorted(attrs,key=lambda a:resource[struct.unpack_from('<I',a,4)[0]])),ax._end(idx['activity'])]
 end=None
 for i,c in enumerate(chunks):
  kind=struct.unpack_from('<H',c)[0]
  if kind==0x102 and strings[ax._node_name(c)]=='activity':
   if any(strings[struct.unpack_from('<I',a,4)[0]]=='name' and ax._attribute_string(a,strings)==ACTIVITY for a in ax._attributes(c)[0]):
    if chunks[i:i+2]!=nodes:raise ValueError('embedded activity mismatch')
    return data
  if kind==0x103 and strings[struct.unpack_from('<I',c,20)[0]]=='application':end=i
 if end is None:raise ValueError('application missing')
 chunks[end:end]=nodes
 body=b''.join(chunks)
 return struct.pack('<HHI',3,8,len(body)+8)+body

"""Append only the TurboIO dedicated callback activity; preserve every host node."""
import struct
import background_manifest as ax
from tts_manifest import NS,U
ACTIVITY='com.turboio.addon.DshAuthCallbackActivity'
SCHEME='agents-anywhere'
HOST='oauth'
PATH='/callback'
def patch(data):
 chunks=ax._chunks(data)
 pools=[i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==1]
 maps=[i for i,c in enumerate(chunks) if struct.unpack_from('<H',c)[0]==0x180]
 if len(pools)!=1 or len(maps)!=1:raise ValueError('manifest pools')
 pi,mi=pools[0],maps[0];strings=ax._decode_pool(chunks[pi])[0]
 vocabulary=['activity','intent-filter','action','category','data','name','exported','scheme','host','path',NS,ACTIVITY,SCHEME,HOST,PATH,'android.intent.action.VIEW','android.intent.category.DEFAULT','android.intent.category.BROWSABLE']
 chunks[pi],strings,idx=ax._append_pool_strings(chunks[pi],strings,vocabulary)
 resource=ax._attr_resource_map(chunks[mi]);resource.extend([0]*(len(strings)-len(resource)))
 for name,value in dict(name=0x01010003,exported=0x01010010,scheme=0x01010027,host=0x01010028,path=0x0101002a).items():
  if resource[idx[name]] not in (0,value):raise ValueError('framework mapping')
  resource[idx[name]]=value
 chunks[mi]=struct.pack('<HHI',0x180,8,8+4*len(resource))+struct.pack('<'+'I'*len(resource),*resource)
 def attr(name,value):return ax._attr_record(idx[NS],idx[name],idx[value],3,idx[value])
 def begin(name,attrs):return ax._begin(idx[name],sorted(attrs,key=lambda a:resource[struct.unpack_from('<I',a,4)[0]]))
 nodes=[begin('activity',[attr('name',ACTIVITY),ax._attr_record(idx[NS],idx['exported'],U,0x12,0xffffffff)]),begin('intent-filter',[]),begin('action',[attr('name','android.intent.action.VIEW')]),ax._end(idx['action']),begin('category',[attr('name','android.intent.category.DEFAULT')]),ax._end(idx['category']),begin('category',[attr('name','android.intent.category.BROWSABLE')]),ax._end(idx['category']),begin('data',[attr('scheme',SCHEME),attr('host',HOST),attr('path',PATH)]),ax._end(idx['data']),ax._end(idx['intent-filter']),ax._end(idx['activity'])]
 stack=[];app_end=None;existing=[];capturing=None
 for i,c in enumerate(chunks):
  kind=struct.unpack_from('<H',c)[0]
  if kind==0x102:
   ni=ax._node_name(c);name=strings[ni];stack.append(name)
   if name=='activity':
    attrs=ax._attributes(c)[0]
    if any(strings[struct.unpack_from('<I',a,4)[0]]=='name' and ax._attribute_string(a,strings)==ACTIVITY for a in attrs):
     if capturing is not None:raise ValueError('duplicate callback')
     capturing=i
  elif kind==0x103:
   name=strings[struct.unpack_from('<I',c,20)[0]]
   if not stack or stack.pop()!=name:raise ValueError('nesting')
   if name=='activity' and capturing is not None:existing.append(chunks[capturing:i+1]);capturing=None
   if name=='application':
    if app_end is not None:raise ValueError('duplicate application')
    app_end=i
 if stack or app_end is None:raise ValueError('application missing')
 if existing:
  if len(existing)!=1 or existing[0]!=nodes:raise ValueError('callback declaration mismatch')
 else:chunks[app_end:app_end]=nodes
 body=b''.join(chunks)
 return struct.pack('<HHI',3,8,len(body)+8)+body

def shared_patch(data):return patch(ax.patch(data))

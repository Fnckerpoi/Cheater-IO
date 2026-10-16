#!/usr/bin/env python3
import hashlib,struct
from pathlib import Path
LOAD='@loader_path/../TurboIOStageBObserver.dylib'
INSTALL='@rpath/Flutter.framework/Flutter'
SHA='488ba057942fc02f45c39313401b144457e467637a149b3b46fd467f0dc103b0'
DEPCMDS={12,0x80000018,0x8000001f,0x80000023}
def inspect(data):
 if len(data)<32 or struct.unpack_from('<I',data)[0]!=0xfeedfacf:raise ValueError('Requires thin64 MachO')
 n,size=struct.unpack_from('<II',data,16);end=32+size;pos=32;first=len(data);commands=[];deps=[];identity=None;install=None
 if n>1024 or end>len(data):raise ValueError('Invalid header')
 for _ in range(n):
  if pos+8>end:raise ValueError('Truncated command')
  cmd,length=struct.unpack_from('<II',data,pos)
  if length<8 or length%8 or pos+length>end:raise ValueError('Malformed command')
  raw=data[pos:pos+length];commands.append((cmd,raw))
  if cmd==0x19:
   count=struct.unpack_from('<I',raw,64)[0]
   if 72+80*count>len(raw):raise ValueError('Malformed segment')
   for i in range(count):
    at=72+80*i;extent,offset=struct.unpack_from('<QI',raw,at+40);flags=struct.unpack_from('<I',raw,at+64)[0]&255
    if offset and extent and flags not in (1,12,18):first=min(first,offset)
  if cmd in DEPCMDS|{13}:
   offset=struct.unpack_from('<I',raw,8)[0]
   if offset<24 or offset>=length or b'\0' not in raw[offset:]:raise ValueError('Bad dependency')
   name=raw[offset:].split(b'\0')[0].decode()
   if cmd==13:install=name
   else:deps.append(name)
  if cmd==0x1b:identity=raw[8:24].hex()
  pos+=length
 if pos!=end or first<end:raise ValueError('Header overlaps payload')
 return dict(commands=commands,deps=deps,install=install,uuid=identity,end=end,first=first,padding=first-end)

def patch(data,*,fixture=False,bootstrap_deps=()):
 if not fixture and hashlib.sha256(data).hexdigest()!=SHA:raise ValueError('Wrong clean Flutter SHA')
 before=inspect(data)
 if before['install']!=INSTALL:raise ValueError('Wrong Flutter install identity')
 if LOAD in before['deps']:raise ValueError('Dependency already present')
 if any('Flutter' in name for name in bootstrap_deps):raise ValueError('Circular bootstrap dependency')
 if any(not name.startswith(('/usr/lib/','/System/Library/')) for name in bootstrap_deps):raise ValueError('Bootstrap must have system-only dependencies')
 name=LOAD.encode()+b'\0';size=(24+len(name)+7)//8*8
 command=struct.pack('<6I',12,size,24,0,0,0)+name+bytes(size-24-len(name))
 retained=[raw for cmd,raw in before['commands'] if cmd!=0x1d]
 if len(retained)<len(before['commands'])-1:raise ValueError('Multiple signatures')
 raw=b''.join(retained)+command;end=32+len(raw)
 if end>before['first'] or end>len(data) or any(data[before['end']:max(end,before['end'])]):raise ValueError('Insufficient zero header padding')
 result=bytearray(data);stop=max(end,before['end']);result[32:stop]=raw+bytes(stop-end)
 struct.pack_into('<II',result,16,len(retained)+1,len(raw));result=bytes(result);after=inspect(result)
 if [raw for _,raw in after['commands'][:-1]]!=retained or after['deps']!=before['deps']+[LOAD] or after['uuid']!=before['uuid']:raise ValueError('Original load graph changed')
 if len(data)!=len(result) or data[:16]!=result[:16] or data[24:32]!=result[24:32] or data[stop:]!=result[stop:]:raise ValueError('Non-header bytes changed')
 return result

def write_copy(source,target,*,fixture=False,bootstrap_deps=()):
 source,target=Path(source),Path(target)
 if target.exists() or target.is_symlink():raise ValueError('New output only')
 for p in [target,*target.parents]:
  if p.is_symlink():raise ValueError('Symlink target rejected')
 data=source.read_bytes();changed=patch(data,fixture=fixture,bootstrap_deps=bootstrap_deps)
 target.parent.mkdir(parents=True,exist_ok=True)
 with target.open('xb') as f:f.write(changed)
 target.chmod(source.stat().st_mode&0o777)
 if source.read_bytes()!=data:raise ValueError('Original changed')
 return changed

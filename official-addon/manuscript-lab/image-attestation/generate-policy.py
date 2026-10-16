#!/usr/bin/env python3
"""Only derive pinned identity policy from the exact approved public IPA."""
import hashlib, json, pathlib, struct, sys, zipfile
PIN_IPA="39d12aff8f2b312dc3b40ce784a444e52c536e61378434005255177548df966a"
PINS={"Flutter":"488ba057942fc02f45c39313401b144457e467637a149b3b46fd467f0dc103b0","isar_plus_flutter_libs":"728997f23a55e0515bb01676fea35c6965a1255ec08f099c2f12227890a98b88"}
def digest(b): return hashlib.sha256(b).hexdigest()
def cbytes(s): return "{"+",".join("0x"+s[i:i+2] for i in range(0,len(s),2))+"}"
def main():
 if len(sys.argv)!=3: raise SystemExit("usage: generate-policy.py exact-public.ipa new-output-directory")
 p=pathlib.Path(sys.argv[1]);dest=pathlib.Path(sys.argv[2])
 if dest.exists(): raise SystemExit("output must not already exist")
 h=hashlib.sha256()
 with p.open("rb") as f:
  for b in iter(lambda:f.read(1048576),b""): h.update(b)
 if h.hexdigest()!=PIN_IPA: raise SystemExit("input digest mismatch")
 facts=[];lines=["/* Derived from pinned public input, not runtime/signature proof. */"]
 with zipfile.ZipFile(p) as z:
  for name,pin in PINS.items():
   member=f"Payload/Runner.app/Frameworks/{name}.framework/{name}";b=z.read(member)
   if digest(b)!=pin: raise SystemExit("member digest mismatch")
   magic,cpu,sub,_,nc,sz,_,_=struct.unpack_from("<8I",b)
   if magic!=0xfeedfacf or cpu!=0x100000c or sub!=0 or 32+sz>len(b): raise SystemExit("unsupported member")
   o=32;uuid=None;text=None
   for _ in range(nc):
    c,s=struct.unpack_from("<II",b,o)
    if s<8 or o+s>32+sz: raise SystemExit("bad command")
    if c==0x1b:
     if uuid is not None or s!=24: raise SystemExit("bad UUID")
     uuid=b[o+8:o+24].hex()
    if c==0x19:
     nsects=struct.unpack_from("<I",b,o+64)[0]
     for j in range(nsects):
      q=o+72+j*80;section=b[q:q+16].split(b"\0")[0];segment=b[q+16:q+32].split(b"\0")[0]
      if section==b"__text" and segment==b"__TEXT":
       if text is not None: raise SystemExit("duplicate text")
       va,n=struct.unpack_from("<QQ",b,q+32);off=struct.unpack_from("<I",b,q+48)[0];text=(va,n,off)
    o+=s
   if o!=32+sz or uuid is None or text is None: raise SystemExit("incomplete metadata")
   va,n,off=text
   if off+n>len(b): raise SystemExit("text range")
   entry=0x1e530 if name!="Flutter" else 0
   ehash=digest(b[entry:entry+64]) if entry else "00"*32
   fact={"member":member,"member_sha256":pin,"uuid":uuid,"text_va":va,"text_size":n,"text_fileoff":off,"text_sha256":digest(b[off:off+n]),"entry_va":entry,"entry64_sha256":ehash}
   facts.append(fact);label="Flutter" if name=="Flutter" else "Isar"
   rel=f"Frameworks/{name}.framework/{name}"
   lines.append(f"static const Policy k{label}Policy = {{0x100000cU,{cbytes(uuid)},UINT64_C({va}),UINT64_C({n}),{cbytes(digest(b[off:off+n]))},UINT64_C({entry}),{cbytes(ehash)},\"{rel}\"}};")
 dest.mkdir();(dest/"ImagePolicy.inc").write_text("\n".join(lines)+"\n");(dest/"policy-provenance.json").write_text(json.dumps({"ipa_sha256":PIN_IPA,"facts":facts,"approval":"REQUIRES_INDEPENDENT_REVIEW_NOT_RUNTIME_AUTHORITY"},indent=2)+"\n")
 print("Pinned public policy generated; independent review required")
if __name__=="__main__": main()

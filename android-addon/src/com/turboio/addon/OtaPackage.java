package com.turboio.addon;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** One reviewed TAP1 candidate, not an arbitrary firmware loader. No extracted paths. */
final class OtaPackage {
 static final String VERSION="Strix OS 1.0.4.12", NAME="TAP1-TEST-01 · 菜单显示TEST";
 static final int ZIP_SIZE=9320672, AP_SIZE=9590856;
 static final String ZIP_SHA="5ef21d8c18501d03fad6edee4fe039649cb567352e30dbd234081ecf97140afa";
 static final String AP_SHA="65e0a5312361ec785eb9ab1659badf182765f4054d4d95fef4c61ceb49dfa4b4";
 // Non-AP pins independently match the archived original 1.0.4.12 OTA payloads.
 private static final String[] PINS={
  "OtaFileInfo.json 2515 6bad48f6b3ce289bbd104c8cdc9ee219d872ba6e0d331ea3f6843bf2aeaf53b5",
  "cb_fw_venus.bin 114592 8799f12071bdac082218e3601c24ab0989275fc2f7db74e37b8314a225c2cf7d",
  "fac_test_img.bin 1821552 18eb0901c3e0daeae48dd5f015252b6931ea7368f0160c2c985c4e74296d17eb",
  "images.bin 324283 192fefaa70545856884c89a9186e3f86d8f99a0738b201e116a11bc63011c158",
  "lotties.bin 651481 ad66d83a3394034f825a12769aad292fc03e59fa865fe06bc43536b918a0b32b",
  "nuttx_ap.bin 9590856 "+AP_SHA,
  "nuttx_apc1.bin 1420248 97d56ffc6dd575ad3d8bf7739d49e491ad7e4a959db8770b653f5302e171fb5c",
  "nuttx_audio.bin 1461920 549a72c032595e3829da7d63b7e1fd13cceda1a55dfefa7a7cad02db5a9b42ab",
  "nuttx_bth.bin 1160160 a8f4594868bffbd3bc08a38d1f3899571608e2d57869d147de3d06f3e4f7a12a",
  "ota_installer_progress.bin 5616 6fd65d296a667e1a7d94ec129adfd26458a4008ef403b331f855192c36eb3e3f",
  "pil_algo_up_demo_nand.dll 504388 e266726fe055ac6ec6f785a9440e8da7421402f2b4d3d7889039ad9b94e119c2",
  "pil_algo_vad_demo.dll 433432 7ac5b5191202adea20c62642987fa0049024dde9c386ba6cd08a1f29ddd5c92c",
  "pil_algo_wakeup_dll_nand.dll 287560 d1594cb8658b228e52c2606d02f1eab696d35a11ed42ddcb725e76a745f5550f",
  "rives.bin 689431 836bc019f7152890b94cafafe2edaa024f317526318349f7833319e41158b104",
  "smf.json 23510 2713dc3a302a65c101fa981500cd8b217766c526b6045bf02db5e9e6763d076d"};
 final OtaProfile profile;
 private final Map<String,byte[]> members;
 private OtaPackage(Map<String,byte[]> members,OtaProfile profile){this.members=members;this.profile=profile;}
 static String hash(String algorithm,byte[] bytes){try{byte[] h=MessageDigest.getInstance(algorithm).digest(bytes);StringBuilder out=new StringBuilder();for(byte v:h)out.append(String.format(Locale.ROOT,"%02x",v&255));return out.toString();}catch(Exception e){throw new IllegalStateException("digest unavailable");}}
 static OtaPackage verify(byte[] zip)throws IOException{return verify(zip,OtaProfile.TAP1);}
 static OtaPackage verify(byte[] zip,OtaProfile profile)throws IOException{
  String[] selected=profile==OtaProfile.TAP1?PINS:profile.pins();
  if(zip==null||zip.length!=profile.zipSize||!profile.sha.equals(hash("SHA-256",zip)))throw new IOException("仅接受已审核候选原样 ZIP，大小或 SHA-256 不符");
  Map<String,String[]> pins=new HashMap<>();for(String line:selected){String[] p=line.split(" ");pins.put(p[0],p);}
  Map<String,byte[]> files=new HashMap<>();int total=0;
  try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(zip))){ZipEntry e;while((e=in.getNextEntry())!=null){String[] p=pins.get(e.getName());if(p==null||e.isDirectory()||files.containsKey(e.getName()))throw new IOException("ZIP member");int size=Integer.parseInt(p[1]);byte[] data=new byte[size];int at=0,n;while(at<size&&(n=in.read(data,at,size-at))!=-1)at+=n;if(at!=size||in.read()!=-1||!p[2].equals(hash("SHA-256",data)))throw new IOException("member size/hash");total+=size;if(total>19000000)throw new IOException("expanded budget");files.put(e.getName(),data);in.closeEntry();}}
  if(files.size()!=selected.length)throw new IOException("missing member");
  try{List<Object> rows=BoundedJson.list(BoundedJson.read(files.get("OtaFileInfo.json")));if(rows.size()!=selected.length-1)throw new IllegalArgumentException();Set<String> found=new HashSet<>();
   for(Object value:rows){Map<String,Object> row=BoundedJson.map(value);BoundedJson.keys(row,"Name","Size","Md5","BurnMode","BurnAddr","BurnPath");String name=BoundedJson.text(row.get("Name"),64);byte[] bytes=files.get(name);if(bytes==null||name.equals("OtaFileInfo.json")||!found.add(name)||BoundedJson.number(row.get("Size"),1,profile.apSize)!=bytes.length||!hash("MD5",bytes).equals(row.get("Md5")))throw new IllegalArgumentException();if(profile==OtaProfile.TAP1&&name.equals("nuttx_ap.bin")){row.put("Size",9466560);row.put("Md5","9156b9419c3557e9bd72dc61692d0b4b");}}
   // Restoring just AP size/MD5 must reproduce the original manifest exactly,
   // including all original row order, burn paths, addresses and burn modes.
   if(profile==OtaProfile.TAP1&&!"1619382c8f0b69f68ba9b8d346017a2e42dcf7518b5d29fc348659e5eaebed2c".equals(hash("SHA-256",BoundedJson.canonical(rows))))throw new IllegalArgumentException();
  }catch(RuntimeException e){throw new IOException("manifest/AP-only mismatch");}
  return new OtaPackage(files,profile);
 }
 Object manifest(){return BoundedJson.read(members.get("OtaFileInfo.json"));}
 // Read-only preparation evidence, NOT a transfer-session or installation claim.
 static void verifyDirectory(java.nio.file.Path directory)throws IOException{
  freezeDirectory(directory);
 }
 // Snapshot checked bytes into private memory; outgoing official slices must
 // equal this snapshot even if the vendor's directory is replaced afterward.
 static OtaPackage freezeDirectory(java.nio.file.Path directory)throws IOException{return freezeDirectory(directory,OtaProfile.TAP1);}
 static OtaPackage freezePreparedDirectory(java.nio.file.Path appDataRoot,java.nio.file.Path relative,OtaProfile profile)throws IOException{
  if(relative.isAbsolute()||relative.normalize().startsWith("..")||(!relative.toString().replace('\\','/').equals("files/ota/"+profile.directory)&&!relative.toString().replace('\\','/').equals("app_flutter/ota/"+profile.directory)))throw new IOException("untrusted relative directory");
  java.nio.file.Path canonicalRoot=appDataRoot.toRealPath();
  java.nio.file.Path candidate=canonicalRoot.resolve(relative).normalize();
  if(!candidate.startsWith(canonicalRoot))throw new IOException("untrusted relative directory");
  return freezeDirectory(candidate,profile);
 }
 static final class DiagnosticFailure extends IOException {final String code;DiagnosticFailure(String stage,Throwable cause){super(stage,cause);code=stage+"/"+safeCategory(cause)+"/"+safeFailure(cause);}}
 static String safeCategory(Throwable error){
  return error instanceof IOException?"IO":error instanceof SecurityException?"SECURITY":error instanceof UnsupportedOperationException?"UNSUPPORTED":"VALIDATION";
 }
 static String safeFailure(Throwable error){
  if(error instanceof DiagnosticFailure)return ((DiagnosticFailure)error).code;
  if(error instanceof java.nio.file.NoSuchFileException)return "PATH_MISSING";
  if(error instanceof java.nio.file.AccessDeniedException||error instanceof SecurityException)return "ACCESS_DENIED";
  if(error instanceof UnsupportedOperationException)return "PROVIDER_UNSUPPORTED";
  String message=error.getMessage();
  if(message!=null){
   if(message.startsWith("directory identity unavailable"))return "DIRECTORY_LINK_OR_TYPE";
   if(message.equals("directory replaced/changed"))return "DIRECTORY_CHANGED";
   if(message.equals("unexpected member"))return "MEMBER_EXTRA";
   if(message.equals("missing member"))return "MEMBER_MISSING";
   if(message.equals("member type/size/identity"))return "MEMBER_TYPE_OR_SIZE";
   if(message.equals("member changed/hash"))return "MEMBER_CHANGED_OR_HASH";
   if(message.equals("not prepared"))return "PREPARED_NOT_FOUND";
   if(message.equals("ambiguous directory"))return "PREPARED_AMBIGUOUS";
   if(message.equals("untrusted relative directory"))return "UNTRUSTED_RELATIVE_PATH";
  }
  return error instanceof IOException?"IO_FAILURE":"VALIDATION_FAILURE";
 }
 interface SnapshotProbe {void checkpoint(String stage)throws IOException;}
 static OtaPackage freezeDirectory(java.nio.file.Path directory,OtaProfile profile)throws IOException{return freezeDirectory(directory,profile,stage->{});}
 static OtaPackage freezeDirectory(java.nio.file.Path directory,OtaProfile profile,SnapshotProbe probe)throws IOException{
  directory=directory.toAbsolutePath().normalize();
  String[] selected=profile==OtaProfile.TAP1?PINS:profile.pins();
  Map<java.nio.file.Path,java.nio.file.attribute.BasicFileAttributes> ancestry=new LinkedHashMap<>();
  for(java.nio.file.Path p=directory;p!=null;p=p.getParent())ancestry.put(p,directoryAttributes(p));
  checkAncestry(ancestry);
  Map<String,byte[]> frozen=new HashMap<>();
  try(java.nio.file.DirectoryStream<java.nio.file.Path> stream=java.nio.file.Files.newDirectoryStream(directory)){
   checkAncestry(ancestry);checkMembers(stream,selected);checkAncestry(ancestry);
   java.nio.file.SecureDirectoryStream<java.nio.file.Path> secure=stream instanceof java.nio.file.SecureDirectoryStream?(java.nio.file.SecureDirectoryStream<java.nio.file.Path>)stream:null;
   for(String line:selected){String[] pin=line.split(" ");java.nio.file.Path relative=directory.getFileSystem().getPath(pin[0]),p=directory.resolve(relative);
    probe.checkpoint("before-read:"+pin[0]);checkAncestry(ancestry);
    java.nio.file.attribute.BasicFileAttributes before=memberAttributes(p,relative,secure);
    int size=Integer.parseInt(pin[1]);if(!before.isRegularFile()||before.isSymbolicLink()||before.size()!=size)throw new IOException("member type/size/identity");
    byte[] bytes=new byte[size];java.nio.ByteBuffer target=java.nio.ByteBuffer.wrap(bytes);
    Set<java.nio.file.OpenOption> options=new HashSet<>();options.add(java.nio.file.StandardOpenOption.READ);options.add(java.nio.file.LinkOption.NOFOLLOW_LINKS);
    try(java.nio.channels.SeekableByteChannel in=secure==null?java.nio.file.Files.newByteChannel(p,options):secure.newByteChannel(relative,options)){
     checkAncestry(ancestry);
     while(target.hasRemaining())if(in.read(target)<0)throw new IOException("short file");
     if(in.read(java.nio.ByteBuffer.allocate(1))!=-1)throw new IOException("file grew");
    }
    checkAncestry(ancestry);
    java.nio.file.attribute.BasicFileAttributes after=memberAttributes(p,relative,secure);
    if(!after.isRegularFile()||after.isSymbolicLink()||!sameIdentity(before,after)||!before.lastModifiedTime().equals(after.lastModifiedTime())||after.size()!=size||!pin[2].equals(hash("SHA-256",bytes)))throw new IOException("member changed/hash");
    frozen.put(pin[0],bytes);
   }
  }
  probe.checkpoint("before-final");checkAncestry(ancestry);
  try(java.nio.file.DirectoryStream<java.nio.file.Path> finalList=java.nio.file.Files.newDirectoryStream(directory)){checkAncestry(ancestry);checkMembers(finalList,selected);checkAncestry(ancestry);}
  checkAncestry(ancestry);return new OtaPackage(frozen,profile);
 }
 private static java.nio.file.attribute.BasicFileAttributes directoryAttributes(java.nio.file.Path p)throws IOException{
  java.nio.file.attribute.BasicFileAttributes a=java.nio.file.Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class,java.nio.file.LinkOption.NOFOLLOW_LINKS);
  if(!a.isDirectory()||a.isSymbolicLink())throw new IOException("directory identity unavailable");return a;
 }
 // Providers without fileKey (including Windows JBR here) only expose creation time; this is not an openat identity guarantee.
 private static boolean sameIdentity(java.nio.file.attribute.BasicFileAttributes a,java.nio.file.attribute.BasicFileAttributes b){
  return a.fileKey()!=null?a.fileKey().equals(b.fileKey()):b.fileKey()==null&&a.creationTime().equals(b.creationTime());
 }
 private static void checkAncestry(Map<java.nio.file.Path,java.nio.file.attribute.BasicFileAttributes> ancestry)throws IOException{
  boolean root=true;for(Map.Entry<java.nio.file.Path,java.nio.file.attribute.BasicFileAttributes> e:ancestry.entrySet()){
   java.nio.file.attribute.BasicFileAttributes a=directoryAttributes(e.getKey()),before=e.getValue();
   if(!sameIdentity(before,a)||(root&&!before.lastModifiedTime().equals(a.lastModifiedTime())))throw new IOException("directory replaced/changed");root=false;
  }
 }
 private static void checkMembers(java.nio.file.DirectoryStream<java.nio.file.Path> list,String[] selected)throws IOException{
  Set<String> expected=new HashSet<>();for(String pin:selected)expected.add(pin.split(" ")[0]);
  for(java.nio.file.Path p:list)if(!expected.remove(p.getFileName().toString()))throw new IOException("unexpected member");
  if(!expected.isEmpty())throw new IOException("missing member");
 }
 private static java.nio.file.attribute.BasicFileAttributes memberAttributes(java.nio.file.Path path,java.nio.file.Path relative,java.nio.file.SecureDirectoryStream<java.nio.file.Path> secure)throws IOException{
  return secure==null?java.nio.file.Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class,java.nio.file.LinkOption.NOFOLLOW_LINKS):secure.getFileAttributeView(relative,java.nio.file.attribute.BasicFileAttributeView.class,java.nio.file.LinkOption.NOFOLLOW_LINKS).readAttributes();
 }
 int size(String name){byte[] b=members.get(name);if(b==null||name.equals("OtaFileInfo.json"))throw new IllegalArgumentException("payload not allowed");return b.length;}
 byte[] slice(String name,int start,int size){int length=size(name);if(start<0||size<1||size>51200||start>length-size)throw new IllegalArgumentException("slice bounds");return Arrays.copyOfRange(members.get(name),start,start+size);}
}

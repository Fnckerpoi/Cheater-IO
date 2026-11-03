package com.turboio.addon;
import java.nio.file.*;import java.io.*;import java.util.zip.*;
public final class Tmg3OtaTrustedRootTest {
 static int checks; interface Work {void run() throws Exception;}
 static void bad(Work w)throws Exception {try{w.run();throw new AssertionError("unsafe path accepted");}catch(IOException expected){checks++;}}
 static void clean(Path p)throws Exception {if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))try(DirectoryStream<Path> d=Files.newDirectoryStream(p)){for(Path c:d)clean(c);}Files.deleteIfExists(p);}
 public static void main(String[] args)throws Exception {
  OtaProfile profile=null; for(java.lang.reflect.Field f:OtaProfile.class.getDeclaredFields())if(f.getType()==OtaProfile.class){f.setAccessible(true);OtaProfile p=(OtaProfile)f.get(null);if(p.sha.equals("2e5eff12002191523c35d708f83dbb42422d724e5abd390f74a9416c3788cb93"))profile=p;}
  if(profile==null)throw new AssertionError("missing TMG3");final OtaProfile selected=profile;
  Path base=Files.createTempDirectory("tmg3-trusted-root-"),root=base.resolve("app"),alias=base.resolve("system-alias");
  try {
   for(String prefix:new String[]{"files","app_flutter"}){
    Path relative=Paths.get(prefix,"ota",profile.directory),dir=root.resolve(relative);Files.createDirectories(dir);
    try(ZipInputStream z=new ZipInputStream(Files.newInputStream(Paths.get(args[0])))){ZipEntry e;while((e=z.getNextEntry())!=null){if(!e.getName().matches("[A-Za-z0-9_.]+"))throw new IOException();Files.copy(z,dir.resolve(e.getName()));}}
    OtaPackage frozen=OtaPackage.freezePreparedDirectory(root,relative,profile);if(frozen.size("nuttx_ap.bin")!=7515400)throw new AssertionError();checks++;
    bad(()->OtaPackage.freezePreparedDirectory(root,Paths.get("../escape"),selected));
    if(args.length>1){
     Files.createSymbolicLink(alias,root);
     bad(()->OtaPackage.freezeDirectory(alias.resolve(relative),selected));
     OtaPackage.freezePreparedDirectory(alias,relative,profile);checks++;
     Path saved=dir.resolveSibling("saved");Files.move(dir,saved);Files.createSymbolicLink(dir,saved);bad(()->OtaPackage.freezePreparedDirectory(alias,relative,selected));Files.delete(dir);Files.move(saved,dir);
     Path member=dir.resolve("nuttx_ap.bin"),stored=base.resolve("stored");Files.move(member,stored);Files.createSymbolicLink(member,stored);bad(()->OtaPackage.freezePreparedDirectory(alias,relative,selected));Files.delete(member);Files.move(stored,member);
     Path ota=dir.getParent(),moved=base.resolve("ota-saved");Files.move(ota,moved);Files.createSymbolicLink(ota,moved);bad(()->OtaPackage.freezePreparedDirectory(alias,relative,selected));Files.delete(ota);Files.move(moved,ota);Files.delete(alias);
    }
    byte[] manifest=Files.readAllBytes(dir.resolve("OtaFileInfo.json"));Files.write(dir.resolve("OtaFileInfo.json"),new byte[]{1});bad(()->OtaPackage.freezePreparedDirectory(root,relative,selected));Files.write(dir.resolve("OtaFileInfo.json"),manifest);
    Files.write(dir.resolve("extra"),new byte[]{1});bad(()->OtaPackage.freezePreparedDirectory(root,relative,selected));Files.delete(dir.resolve("extra"));
   }
   System.out.println("TMG3 trusted root: "+checks+" checks passed; links="+(args.length>1));
  } finally {clean(base);}
 }
}

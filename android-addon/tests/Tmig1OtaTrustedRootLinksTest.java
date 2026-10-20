package com.turboio.addon;
import java.nio.file.*;import java.io.*;import java.util.zip.*;
public final class Tmig1OtaTrustedRootLinksTest {
 static int checks;interface Work{void run()throws Exception;}static void bad(Work w)throws Exception{try{w.run();throw new AssertionError("descendant link accepted");}catch(IOException expected){checks++;}}
 static void clean(Path p)throws Exception{if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)){try(DirectoryStream<Path> list=Files.newDirectoryStream(p)){for(Path c:list)clean(c);}}Files.deleteIfExists(p);}
 public static void main(String[] args)throws Exception{
  Path base=Files.createTempDirectory("tmig1-root-links-"),root=base.resolve("app"),alias=base.resolve("system-alias"),relative=Paths.get("files/ota/Strix_OS_1.0.5.5"),dir=root.resolve(relative);Files.createDirectories(dir);
  try{
   try(ZipInputStream z=new ZipInputStream(Files.newInputStream(Paths.get(args[0])))){ZipEntry e;while((e=z.getNextEntry())!=null){if(!e.getName().matches("[A-Za-z0-9_.]+"))throw new IOException();Files.copy(z,dir.resolve(e.getName()));}}
   Files.createSymbolicLink(alias,root);OtaPackage.freezePreparedDirectory(alias,relative,OtaProfile.TMIG1);checks++;
   Path saved=dir.resolveSibling("saved");Files.move(dir,saved);Files.createSymbolicLink(dir,saved);bad(()->OtaPackage.freezePreparedDirectory(alias,relative,OtaProfile.TMIG1));Files.delete(dir);Files.move(saved,dir);
   Path member=dir.resolve("bt_controller.bin"),stored=base.resolve("bt-saved");Files.move(member,stored);Files.createSymbolicLink(member,stored);bad(()->OtaPackage.freezePreparedDirectory(alias,relative,OtaProfile.TMIG1));
   System.out.println("TMIG1 trusted root alias allowed, candidate/member links rejected: "+checks+" checks");
  }finally{clean(base);}
 }
}

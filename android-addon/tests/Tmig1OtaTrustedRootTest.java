package com.turboio.addon;
import java.nio.file.*;import java.io.*;import java.util.zip.*;
public final class Tmig1OtaTrustedRootTest {
 static int checks;interface Work{void run()throws Exception;}
 static void bad(Work work,String code)throws Exception{try{work.run();throw new AssertionError("accepted");}catch(IOException expected){if(!OtaPackage.safeFailure(expected).equals(code))throw new AssertionError(OtaPackage.safeFailure(expected));checks++;}}
 static void clean(Path p)throws Exception{if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)){try(DirectoryStream<Path> list=Files.newDirectoryStream(p)){for(Path c:list)clean(c);}}Files.deleteIfExists(p);}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("tmig1-trusted-root-"),relative=Paths.get("files/ota/Strix_OS_1.0.5.5"),dir=root.resolve(relative);Files.createDirectories(dir);
  try{
   try(ZipInputStream z=new ZipInputStream(Files.newInputStream(Paths.get(args[0])))){ZipEntry e;while((e=z.getNextEntry())!=null){if(!e.getName().matches("[A-Za-z0-9_.]+"))throw new IOException();Files.copy(z,dir.resolve(e.getName()));}}
   OtaPackage.freezePreparedDirectory(root,relative,OtaProfile.TMIG1);checks++;
   bad(()->OtaPackage.freezePreparedDirectory(root,Paths.get("../files/ota/Strix_OS_1.0.5.5"),OtaProfile.TMIG1),"UNTRUSTED_RELATIVE_PATH");
   bad(()->OtaPackage.freezePreparedDirectory(root,dir,OtaProfile.TMIG1),"UNTRUSTED_RELATIVE_PATH");
   bad(()->OtaPackage.freezePreparedDirectory(root,Paths.get("other/ota/Strix_OS_1.0.5.5"),OtaProfile.TMIG1),"UNTRUSTED_RELATIVE_PATH");
   if(!OtaPackage.safeFailure(new IOException("private/path device-secret")).equals("IO_FAILURE"))throw new AssertionError("message leak");checks++;
   if(!OtaPackage.safeFailure(new NoSuchFileException("secret")).equals("PATH_MISSING"))throw new AssertionError();checks++;
   if(!OtaPackage.safeFailure(new OtaPackage.DiagnosticFailure("SNAPSHOT",new NoSuchFileException("private-secret"))).equals("SNAPSHOT/IO/PATH_MISSING"))throw new AssertionError("stage leak");checks++;
   System.out.println("TMIG1 trusted root and safe diagnostics: "+checks+" checks passed; symlink cases require separate privileged suite");
  }finally{clean(root);}
 }
}

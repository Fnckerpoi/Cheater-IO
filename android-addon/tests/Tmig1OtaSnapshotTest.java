package com.turboio.addon;
import java.nio.file.*;import java.io.*;import java.util.*;import java.util.zip.*;
public final class Tmig1OtaSnapshotTest {
 interface Work{void run()throws Exception;}static int checks;
 static void bad(Work w)throws Exception{try{w.run();throw new AssertionError("invalid snapshot accepted");}catch(IOException expected){checks++;}}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("tmig1-snapshot-");
  try{
   try(ZipInputStream z=new ZipInputStream(Files.newInputStream(Paths.get(args[0])))){ZipEntry e;while((e=z.getNextEntry())!=null){if(!e.getName().matches("[A-Za-z0-9_.]+"))throw new IOException();Files.copy(z,root.resolve(e.getName()));}}
   OtaPackage frozen=OtaPackage.freezeDirectory(root,OtaProfile.TMIG1);checks++;
   Path bt=root.resolve("bt_controller.bin");byte[] original=Files.readAllBytes(bt),changed=original.clone();changed[0]^=1;Files.write(bt,changed);bad(()->OtaPackage.freezeDirectory(root,OtaProfile.TMIG1));
   if(!Arrays.equals(frozen.slice("bt_controller.bin",0,100),Arrays.copyOf(original,100)))throw new AssertionError("snapshot changed");checks++;
   Files.write(bt,original);Files.delete(bt);bad(()->OtaPackage.freezeDirectory(root,OtaProfile.TMIG1));Files.write(bt,original);
   Path extra=root.resolve("extra");Files.write(extra,new byte[0]);bad(()->OtaPackage.freezeDirectory(root,OtaProfile.TMIG1));Files.delete(extra);
   Path info=root.resolve("OtaFileInfo.json");byte[] manifest=Files.readAllBytes(info);manifest[0]^=1;Files.write(info,manifest);bad(()->OtaPackage.freezeDirectory(root,OtaProfile.TMIG1));
   System.out.println("TMIG1 frozen directory contents: "+checks+" checks passed; symlink rejection covered by separate directory suite");
  }finally{try(DirectoryStream<Path> list=Files.newDirectoryStream(root)){for(Path file:list)Files.delete(file);}Files.delete(root);}
 }
}

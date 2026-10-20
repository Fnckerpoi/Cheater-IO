package com.turboio.addon;
import java.nio.file.*;import java.io.*;import java.util.zip.*;
public final class Tmig1OtaDirectoryRaceTest {
 static int checks;static void extract(Path zip,Path root)throws Exception{Files.createDirectories(root);try(ZipInputStream z=new ZipInputStream(Files.newInputStream(zip))){ZipEntry e;while((e=z.getNextEntry())!=null){if(!e.getName().matches("[A-Za-z0-9_.]+"))throw new IOException();Files.copy(z,root.resolve(e.getName()));}}}
 static void clean(Path p)throws Exception{if(Files.isDirectory(p)){try(DirectoryStream<Path> list=Files.newDirectoryStream(p)){for(Path c:list)clean(c);}}Files.deleteIfExists(p);}
 public static void main(String[] args)throws Exception{
  Path zip=Paths.get(args[0]),base=Files.createTempDirectory("tmig1-root-race-");
  try{
   for(String stage:new String[]{"before-read:OtaFileInfo.json","before-final"}){
    Path root=base.resolve("root"),old=base.resolve("old"),replacement=base.resolve("replacement");extract(zip,root);extract(zip,replacement);boolean[] changed={false};
    try{OtaPackage.freezeDirectory(root,OtaProfile.TMIG1,at->{if(at.equals(stage)){Files.move(root,old);Files.move(replacement,root);changed[0]=true;}});throw new AssertionError("root replacement accepted");}catch(IOException expected){if(!changed[0])throw new AssertionError("root swap did not execute",expected);checks++;}clean(root);clean(old);
   }
   Path root=base.resolve("root");extract(zip,root);boolean[] changed={false};
   try{OtaPackage.freezeDirectory(root,OtaProfile.TMIG1,at->{if(at.equals("before-final")){Files.write(root.resolve("late-extra"),new byte[0]);changed[0]=true;}});throw new AssertionError("late member accepted");}catch(IOException expected){if(!changed[0])throw new AssertionError("late mutation did not execute",expected);checks++;}clean(root);
   Path parent=base.resolve("parent"),oldParent=base.resolve("old-parent"),newParent=base.resolve("new-parent");extract(zip,parent.resolve("root"));extract(zip,newParent.resolve("root"));changed[0]=false;
   try{OtaPackage.freezeDirectory(parent.resolve("root"),OtaProfile.TMIG1,at->{if(at.equals("before-final")){Files.move(parent,oldParent);Files.move(newParent,parent);changed[0]=true;}});throw new AssertionError("ancestor replacement accepted");}catch(IOException expected){if(!changed[0])throw new AssertionError("ancestor swap did not execute",expected);checks++;}
   try(DirectoryStream<Path> stream=Files.newDirectoryStream(parent.resolve("root"))){System.out.println("SecureDirectoryStream available: "+(stream instanceof SecureDirectoryStream));}
   System.out.println("TMIG1 root/ancestor/final-set races: "+checks+" executed mutations rejected");
  }finally{clean(base);}
 }
}

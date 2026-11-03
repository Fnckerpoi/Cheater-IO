package com.turboio.addon;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
public final class Stock127OtaTest {
 static int checks;
 static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static OtaProfile stock()throws Exception{try{return (OtaProfile)OtaProfile.class.getDeclaredField("STOCK127").get(null);}catch(NoSuchFieldException e){throw new AssertionError("independent STOCK127 profile missing");}}
 static void rejected(byte[] b,OtaProfile p)throws Exception{try{OtaPackage.verify(b,p);throw new AssertionError("wrong ZIP accepted for "+p.token);}catch(IOException expected){checks++;}}
 static String request(int port,String path){return "GET "+path+" HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\n\r\n";}
 static Object data(OfficialOtaFeed.Reply r){ok(r.code==200,"feed status");return BoundedJson.map(BoundedJson.read(r.body)).get("data");}
 public static void main(String[] args)throws Exception{
  OtaProfile p=stock();byte[] original=Files.readAllBytes(Paths.get(args[0])),enhanced=Files.readAllBytes(Paths.get(args[1]));
  ok("b7bfd3cc73528e3e9995ad227dc877c8334d43dc8051436e2d2ccff2ca0b4133".equals(OtaPackage.hash("SHA-256",original)),"stock fixture identity");
  OtaPackage pack=OtaPackage.verify(original,p);ok(pack.size("nuttx_ap.bin")==7313036,"stock AP length");
  try(ZipFile z=new ZipFile(args[0])){
   ok(z.size()==16,"stock member count");
   byte[] manifest;try(InputStream in=z.getInputStream(z.getEntry("OtaFileInfo.json"))){manifest=read(in);}
   ok(Arrays.equals(BoundedJson.canonical(BoundedJson.read(manifest)),BoundedJson.canonical(pack.manifest())),"original manifest preserved");
   for(Object v:BoundedJson.list(pack.manifest())){Map<String,Object> row=BoundedJson.map(v);String name=(String)row.get("Name");try(InputStream in=z.getInputStream(z.getEntry(name))){byte[] bytes=read(in);ok(bytes.length==pack.size(name),"payload size "+name);for(int at=0;at<bytes.length;at+=51200){int size=Math.min(51200,bytes.length-at);ok(Arrays.equals(Arrays.copyOfRange(bytes,at,at+size),pack.slice(name,at,size)),"payload slice "+name);}}}
  }
  rejected(enhanced,p);byte[] changed=original.clone();changed[400]^=1;rejected(changed,p);
  for(OtaProfile other:new OtaProfile[]{OtaProfile.TAP1,OtaProfile.TMIG1,OtaProfile.TMG2,OtaProfile.TMG3}){rejected(original,other);ok(!p.matchesJournal(other.sha,other.token),"journal isolation");ok(!OfficialOtaAuthorization.tokenMatches(other.token,p),"token isolation");}
  ok(OfficialOtaAuthorization.tokenMatches("STOCK-127-CONTROL",p),"stock grant token");
  Map<String,String> q=OfficialOtaFeed.query("/g/test?packageType=firmware&packageName=strix+OS&glassesType=S3&deviceType=android&versionCode=01.00.05.0012");
  ok(OfficialOtaFeed.firmware(q,p),"same version stock query");ok(OfficialOtaFeed.firmware(q,OtaProfile.TMG3),"existing TMG3 query");
  for(OtaProfile other:new OtaProfile[]{OtaProfile.TAP1,OtaProfile.TMIG1,OtaProfile.TMG2})ok(!OfficialOtaFeed.firmware(q,other),"same version query isolation");
  for(String key:new String[]{"packageType","packageName","glassesType","deviceType","versionCode"}){Map<String,String> wrong=new HashMap<>(q);wrong.put(key,"unexpected");ok(!OfficialOtaFeed.firmware(wrong,p),"query field "+key);}
  ok(p.acceptsSource("Strix OS 1.0.5.12")&&!p.acceptsSource("Strix OS 1.0.4.12"),"source version policy");
  try(OfficialOtaFeed f=new OfficialOtaFeed(()->1000,()->true,true,p)){int port=f.start(0);f.arm(original);String path=OfficialOtaFeed.PATHS[0]+"?packageType=firmware&packageName=strix+OS&glassesType=S3&deviceType=android&versionCode=01.00.05.0012";Map<String,Object> meta=BoundedJson.map(data(f.reply(request(port,path))));
   ok("0100050012".equals(meta.get("versionName"))&&((Number)meta.get("versionCode")).intValue()==100050012,"stock feed version");
   ok(((Number)meta.get("apkSize")).intValue()==8769953,"stock ZIP size metadata");ok("efd5bb5a679f5fda659401add30434ca".equals(meta.get("apkMd5")),"stock ZIP MD5 metadata");
   String desc=(String)meta.get("updateDesc");ok(desc.contains("unmodified stock")&&!desc.contains("Modified AP"),"stock description");
   ok(((String)meta.get("taskName")).startsWith("STOCK-127-CONTROL"),"stock feed identity");String download=new URI((String)meta.get("downloadUrl")).getRawPath();ok(Arrays.equals(original,f.reply(request(port,download)).body),"download is exact stock ZIP");
   try{f.arm(enhanced);throw new AssertionError("enhanced feed accepted");}catch(IOException expected){checks++;}
   f.disarm();ok(f.reply(request(port,download)).code==404,"disarmed download");
  }
  System.out.println("Stock127 OTA control: "+checks+" checks passed; no device I/O");
 }
 static byte[] read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}
}

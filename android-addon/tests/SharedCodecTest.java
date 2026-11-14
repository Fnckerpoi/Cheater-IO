package com.turboio.addon;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
public final class SharedCodecTest {
 public static void main(String[] args) throws Exception {
  byte[] b={(byte)0x78,(byte)0x56,(byte)0x34,(byte)0xf2};
  if(SharedCodec.u32(b,0)!=0xf2345678L)throw new AssertionError("little endian u32");
  if(SharedCodec.crc("123456789".getBytes(StandardCharsets.UTF_8),9)!=0xcbf43926L)throw new AssertionError("CRC32");
  if(!SharedCodec.utf8("汉😀".getBytes(StandardCharsets.UTF_8)).equals("汉😀"))throw new AssertionError("UTF-8");
  try{SharedCodec.utf8(new byte[]{(byte)0xc3,0x28});throw new AssertionError("malformed UTF-8 accepted");}catch(CharacterCodingException expected){}
  System.out.println("SharedCodecTest passed");
 }
}

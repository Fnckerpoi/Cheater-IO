package com.turboio.addon;
import java.util.Arrays;
public final class DisplayCodecTest {
 public static void main(String[] args){
  if(!DisplayCodec.clip("a汉😀b",8).equals("a汉😀"))throw new AssertionError("UTF-8 clipping");
  byte[] packet=DisplayCodec.packet(7,"{}");
  if(!Arrays.equals(packet,new byte[]{8,1,16,7,26,2,123,125}))throw new AssertionError("packet");
  DisplayCodec.Envelope e=DisplayCodec.decode(packet);
  if(e==null||e.type!=7||!e.json.equals("{}"))throw new AssertionError("decode");
  if(DisplayCodec.decode(new byte[]{8,1,16,7,26,2,123})!=null)throw new AssertionError("truncated");
  System.out.println("DisplayCodecTest passed");
 }
}

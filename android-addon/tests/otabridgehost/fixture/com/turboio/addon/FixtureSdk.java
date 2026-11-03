package com.turboio.addon;
import java.util.*;
public final class FixtureSdk {
 public static String peer="peer-A"; public static boolean sdkFailure; public static int sent,failType;
 public enum Biz { MARS_FOTA,VOICE_ASSISTANT,LAUNCHER,FILE_TRANSFER,FILE_TRANSFER_LAUNCHER }
 public enum Priority { NORMAL } public enum Small { SmallCore }
 public static final class Device { public String a; public Device(String p){a=p;} public boolean b(){return true;} }
 public static final class Packet {
  public String b; public Biz c; public byte[] e;
  public Packet(byte[] p,String id,String peer,Biz biz,Object options,Priority priority,boolean a,boolean d,Object listener,Object cb){b=peer;c=biz;e=p;}
 }
 public static final class Manager {
  public static final Manager h=new Manager();
  public static List<Device> devices(){if(sdkFailure)throw new IllegalStateException("fixture SDK unavailable");return peer==null||peer.isEmpty()?Collections.<Device>emptyList():Arrays.asList(new Device(peer));}
  public void send(Packet p,kotlin.jvm.functions.Function2 cb){if(OtaFrame.decode(p.e).type==failType)throw new IllegalStateException("fixture send failure");if(Boolean.TRUE.equals(OfficialOtaBridge.allowQueue(p)))sent++;}
 }
}

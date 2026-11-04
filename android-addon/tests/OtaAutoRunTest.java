package com.turboio.addon;

/** Pure state-machine test: no android.*, no device I/O.
 *  Locks the four phases of the one-click flow: DISCOVER -> INITIAL -> AUTO_ARM -> AFTER_INSTALL,
 *  with hard attempt caps: preflight 3, arm 3, auto-readback 11, auto-release 3. */
public final class OtaAutoRunTest {
 static int n;
 static final class Box {
  long now=1000;
  boolean pending,armed,foreground,feedReady,directoryReady,blocked,readyToArm,installStarted,recoveryAllowed,freshBoot,releaseResult=true;
  String peer="device-test";
  int preflight,arm,readback,release;
  final java.util.List<String> notes=new java.util.ArrayList<>();
 }
 static Box env;
 static final OtaAutoRun.Env BACKEND=new OtaAutoRun.Env(){
  public long now(){return env.now;}
  public void note(String s){env.notes.add(s);}
  public boolean pending(){return env.pending;}
  public boolean armed(){return env.armed;}
  public boolean foreground(){return env.foreground;}
  public boolean feedReady(){return env.feedReady;}
  public boolean directoryReady(){return env.directoryReady;}
  public boolean blockedByHost(){return env.blocked;}
  public String peer(){return env.peer;}
  public boolean readyToArm(){return env.readyToArm;}
  public boolean installStarted(){return env.installStarted;}
  public void preflight(){env.preflight++;}
  public void arm(){env.arm++;}
  public boolean recoveryAllowed(){return env.recoveryAllowed;}
  public boolean freshBoot(){return env.freshBoot;}
  public void readbackAuto(){env.readback++;}
  public boolean releaseAuto(){env.release++;return env.releaseResult;}
 };
 static void ok(boolean b){n++;if(!b)throw new AssertionError("auto check "+n+" phase="+OtaAutoRun.phase()+" now="+env.now+" notes="+env.notes);}
 static void reset(){env=new Box();OtaAutoRun.attach(BACKEND);}
 static void tick(){OtaAutoRun.tick();}
 public static void main(String[] args){
  reset();
  ok(phase()==OtaAutoRun.Phase.IDLE);tick();

  // DISCOVER: feed/source missing first, then foreground+peer gates, then feedReady opens INITIAL.
  env.feedReady=false;env.foreground=false;OtaAutoRun.start();ok(phase()==OtaAutoRun.Phase.DISCOVER);
  tick();ok(phase()==OtaAutoRun.Phase.DISCOVER&&env.preflight==0);                       // feed missing: no preflight
  env.feedReady=true;tick();ok(phase()==OtaAutoRun.Phase.DISCOVER&&env.preflight==0);    // foreground false
  env.foreground=true;env.peer="";tick();ok(phase()==OtaAutoRun.Phase.DISCOVER&&env.preflight==0); // no unique link
  env.peer="device-test";tick();ok(phase()==OtaAutoRun.Phase.INITIAL&&env.preflight==0); // enters INITIAL

  // INITIAL: 8 s silence before the first silent preflight; preflight attempts capped at 3.
  env.now=1000+7999;tick();ok(phase()==OtaAutoRun.Phase.INITIAL&&env.preflight==0);
  env.now=1000+8000;tick();ok(phase()==OtaAutoRun.Phase.INITIAL&&env.preflight==1);
  env.now=1000+13000;tick();ok(env.preflight==2);env.now=1000+18000;tick();ok(env.preflight==3);
  env.now=1000+23000;tick();ok(env.preflight==3);                                          // cap reached: no 4th

  // Fresh gate replies after the final preflight still move to AUTO_ARM.
  env.readyToArm=true;tick();ok(phase()==OtaAutoRun.Phase.AUTO_ARM);
  tick();ok(env.arm==1&&phase()==OtaAutoRun.Phase.AUTO_ARM);                               // first arm invocation

  // Armed but vendor has not started install: AUTO_ARM holds (no extra arm calls).
  env.armed=true;env.now=1000+24000;tick();ok(env.arm==1&&phase()==OtaAutoRun.Phase.AUTO_ARM);
  env.installStarted=true;tick();ok(phase()==OtaAutoRun.Phase.AFTER_INSTALL);

  // AFTER_INSTALL: no readback until reconnect evidence unlocks the recovery guard.
  tick();ok(env.readback==0&&phase()==OtaAutoRun.Phase.AFTER_INSTALL);
  env.recoveryAllowed=true;env.freshBoot=false;tick();ok(env.readback==1);
  for(int i=0;i<10;i++){env.now+=5001;tick();}
  ok(env.readback==11&&env.release==0&&phase()==OtaAutoRun.Phase.AFTER_INSTALL);
  env.now+=5001;tick();ok(phase()==OtaAutoRun.Phase.STOPPED);                              // 11 readbacks, no fresh boot

  // Restart into a pending journal goes straight to AFTER_INSTALL; one fresh readback grants release.
  reset();env.pending=true;OtaAutoRun.start();ok(phase()==OtaAutoRun.Phase.AFTER_INSTALL);
  env.recoveryAllowed=true;env.freshBoot=true;tick();ok(env.release==1&&env.readback==0&&phase()==OtaAutoRun.Phase.DONE);

  // Release failure is retried at most 3 times, then the auto flow stops and keeps the guard.
  reset();env.pending=true;env.recoveryAllowed=true;env.freshBoot=true;env.releaseResult=false;
  OtaAutoRun.start();tick();ok(env.release==1&&phase()==OtaAutoRun.Phase.AFTER_INSTALL);
  env.now+=5001;tick();ok(env.release==2);env.now+=5001;tick();ok(env.release==3);
  env.now+=5001;tick();ok(phase()==OtaAutoRun.Phase.STOPPED);

  // OtaController/HdCast/Display/Todo busy keeps the auto flow waiting instead of touching the wire.
  reset();env.foreground=true;env.feedReady=true;OtaAutoRun.start();tick();ok(phase()==OtaAutoRun.Phase.INITIAL);
  reset();env.foreground=true;env.feedReady=true;env.blocked=true;OtaAutoRun.start();tick();ok(phase()==OtaAutoRun.Phase.DISCOVER);

  // Explicit stop after start: no further calls.
  reset();OtaAutoRun.start();OtaAutoRun.stop();tick();ok(phase()==OtaAutoRun.Phase.STOPPED&&env.preflight==0&&env.arm==0);
  System.out.println("OTA auto-run: "+n+" checks; four-phase flow with preflight 3 / arm 3 / readback 11 / release 3 caps.");
 }
 static OtaAutoRun.Phase phase(){return OtaAutoRun.phase();}
}

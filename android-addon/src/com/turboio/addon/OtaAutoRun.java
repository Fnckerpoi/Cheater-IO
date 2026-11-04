package com.turboio.addon;

/** One-click flasher: drives the existing OfficialOtaBridge GUARD fences through a linear,
 * capped automatic flow. Pure Java state machine with no android.* imports so the golden
 * test can run on the JVM against the same production logic that the host bridge ticker calls.
 *
 * The machine only reads Env and calls the delegated verbs; every wire-side decision
 * (fresh preflight, readyToArm, allowed, freshBoot, release journal) stays in the bridge. */
public final class OtaAutoRun {
 public enum Phase { IDLE, DISCOVER, INITIAL, AUTO_ARM, AFTER_INSTALL, STOPPED, DONE }
 /** All external facts and actions, injected by the host and by the golden test. */
 public interface Env {
  long now();
  void note(String s);
  boolean pending();
  boolean armed();
  boolean foreground();
  boolean feedReady();
  boolean blockedByHost();
  String peer();
  boolean readyToArm();
  boolean installStarted();
  void preflight();
  void arm();
  boolean recoveryAllowed();
  boolean freshBoot();
  void readbackAuto();
  boolean releaseAuto();
 }
 static final long INITIAL_SILENCE_MS=8000, RETRY_MS=5000;
 static final int PREFLIGHT_CAP=3, ARM_CAP=3, READBACK_CAP=11, RELEASE_CAP=3;
 private static Env env;
 private static volatile Phase phase=Phase.IDLE;
 private static long phaseSince,lastAction;
 private static int preflights,arms,readbacks,releases;
 private OtaAutoRun(){}
 public static synchronized void attach(Env e){
  env=e;phase=Phase.IDLE;phaseSince=0;lastAction=0;
  preflights=0;arms=0;readbacks=0;releases=0;
 }
 public static synchronized Phase phase(){return phase;}
 private static void enter(Phase p){
  phase=p;phaseSince=env.now();lastAction=0;
 }
 public static synchronized void start(){
  if(env==null||phase==Phase.DISCOVER||phase==Phase.INITIAL||phase==Phase.AUTO_ARM||phase==Phase.AFTER_INSTALL||phase==Phase.DONE)return;
  if(env.blockedByHost()){enter(Phase.DISCOVER);env.note("一键升级等待占用任务退出");return;}
  if(env.pending()){enter(Phase.AFTER_INSTALL);env.note("上轮结果保护待自动核对并解除");return;}
  enter(Phase.DISCOVER);env.note("一键升级已启动；等待官方来源与前台");
 }
 public static synchronized void stop(){
  if(env==null||phase==Phase.IDLE||phase==Phase.DONE||phase==Phase.STOPPED)return;
  enter(Phase.STOPPED);env.note("一键升级已人工停止；保护保持");
 }
 public static synchronized void tick(){
  if(env==null||phase==Phase.IDLE||phase==Phase.STOPPED||phase==Phase.DONE)return;
  long now=env.now();
  switch(phase){
   case DISCOVER:
    if(env.pending()){enter(Phase.AFTER_INSTALL);return;}
    if(env.feedReady()&&env.foreground()&&!env.peer().isEmpty()&&!env.blockedByHost())enter(Phase.INITIAL);
    return;
   case INITIAL:
    if(env.readyToArm()){enter(Phase.AUTO_ARM);return;}
    if(now-phaseSince>=INITIAL_SILENCE_MS&&preflights<PREFLIGHT_CAP&&(lastAction==0||now-lastAction>=RETRY_MS)){
     preflights++;lastAction=now;env.preflight();
    }
    return;
   case AUTO_ARM:
    if(env.installStarted()){enter(Phase.AFTER_INSTALL);env.note("官方已开始安装；进入安装后保护监控");return;}
    if(!env.armed()&&arms<ARM_CAP&&(lastAction==0||now-lastAction>=RETRY_MS)){
     arms++;lastAction=now;env.arm();
     if(arms>=ARM_CAP)env.note("自动授权尝试已达上限；保持未授权");
    }else if(arms>=ARM_CAP&&!env.armed()&&!env.installStarted()){
     enter(Phase.STOPPED);env.note("自动授权未在限次内成功；保护保持");
    }
    return;
   case AFTER_INSTALL:
    if(env.freshBoot()&&env.recoveryAllowed()){
     if(releases>=RELEASE_CAP){enter(Phase.STOPPED);env.note("自动解除保护未在限次内成功；保护保持");return;}
     if(lastAction==0||now-lastAction>=RETRY_MS){
      releases++;lastAction=now;
      if(env.releaseAuto()){enter(Phase.DONE);env.note("一键升级完成；保护已解除，不自动再次安装");}
     }
     return;
    }
    if(env.recoveryAllowed()){
     if(readbacks>=READBACK_CAP){enter(Phase.STOPPED);env.note("自动只读回查已满限次仍未取得新启动证据；保护保持");return;}
     if(lastAction==0||now-lastAction>=RETRY_MS){
      readbacks++;lastAction=now;env.readbackAuto();
     }
    }
    return;
   default:return;
  }
 }
}

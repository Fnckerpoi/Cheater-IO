package com.turboio.addon;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Official Android OTA with a passive, default-locked outgoing guard.
 * No firmware sender, no retry/resume entrypoint, no remote grant endpoint.
 */
public final class OfficialOtaBridge {
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final Set<String> INTERACTIVE=new HashSet<>(Arrays.asList("APP_MARKET","MARS_STREAMING","MARS_PHOTO_TRANSFER","LOG_REPORT","VOICE_ASSISTANT","RECORDING_SERVICE","LAUNCHER","FILE_TRANSFER","AI_SUBTITLE","TELEPROMPTER","NOTIFICATION","SCHEDULE_TODO","PROACTIVE_AI","AI_WIDGET","NAVIGATION","FILE_TRANSFER_LAUNCHER"));
 private static final OtaProfile PROFILE=OtaProfile.TMG4_3_10611;
 private static Boolean mode;private static Context app;private static SharedPreferences journal;
 private static OfficialOtaFeed feed;private static OfficialOtaGate gate;
 private static volatile boolean pending,armed,foreground;private static boolean checking,initialized;
 private static String savedCandidate="",savedProfile="",savedPeer="",lease="",lastExport="",firstReject="",versionEvidence="尚无回读";
 private static volatile String note="默认锁定；先使用官方页面下载，再单独授权";
 private static OtaRecoveryGuard recovery;private static long epoch;
 private static final int[] tx=new int[256],rx=new int[256];private static int blocked;
 private static final ArrayDeque<String> trace=new ArrayDeque<>();
 private static final OfficialOtaPreflightPoll preflightPoll=new OfficialOtaPreflightPoll();
 private static final OtaAutoRun.Env AUTO=new OtaAutoRun.Env(){
  public long now(){return SystemClock.elapsedRealtime();}
  public void note(String s){OfficialOtaBridge.note=s;}
  public boolean pending(){return OfficialOtaBridge.pending;}
  public boolean armed(){return OfficialOtaBridge.armed;}
  public boolean foreground(){return OfficialOtaBridge.foreground;}
  public boolean feedReady(){return OfficialOtaBridge.feed!=null&&OfficialOtaBridge.feed.armed();}
  public boolean blockedByHost(){return OtaController.busy()||DisplayBridge.active()||HdCast.active()||TodoRuntime.busy();}
  public String peer(){String p=NativeTransfer.otaConnection();return p==null?"":p;}
  public boolean readyToArm(){return OfficialOtaBridge.gate!=null&&OfficialOtaBridge.gate.readyToArm(SystemClock.elapsedRealtime());}
  public boolean installStarted(){return OfficialOtaBridge.gate!=null&&OfficialOtaBridge.gate.pending();}
  public void preflight(){OfficialOtaBridge.inspect();}
  public void arm(){OfficialOtaBridge.authorize(PROFILE.token);}
  public boolean recoveryAllowed(){String p=NativeTransfer.otaConnection();long n=SystemClock.elapsedRealtime();return OfficialOtaBridge.recovery!=null&&OfficialOtaBridge.recovery.allowed(p==null?"":p,n);}
  public boolean freshBoot(){return OfficialOtaBridge.gate!=null&&OfficialOtaBridge.gate.freshBoot(SystemClock.elapsedRealtime());}
  public void readbackAuto(){OfficialOtaBridge.autoReadback();}
  public boolean releaseAuto(){boolean was=OfficialOtaBridge.pending;OfficialOtaBridge.release();return !OfficialOtaBridge.pending;}
 };
 private static void trace(String direction,OtaFrame f,long now){
  if(f.type!=1&&f.type!=2&&f.type!=3&&f.type!=4&&f.type!=11)return;
  Map<?,?> j=f.json instanceof Map?(Map<?,?>)f.json:Collections.emptyMap();Object result=j.get("Result");
  while(trace.size()>=32)trace.removeFirst();
  trace.addLast(now+" "+direction+" type="+f.type+" result="+(result instanceof Boolean?result:"none")+" "+gate.idleEvidence(now));
 }
 public static synchronized boolean enabled(){
  if(mode!=null)return mode;
  try{Object c=Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);if(!(c instanceof Context))return true;app=((Context)c).getApplicationContext();String[] files=app.getAssets().list("turboio");mode=files!=null&&Arrays.asList(files).contains("official-ota-guarded.txt");}
  catch(Exception e){return true;}return mode;
 }
 static boolean busy(){return armed||pending;}
 private static String hashPeer(String peer){return OtaPackage.hash("SHA-256",peer.getBytes(StandardCharsets.UTF_8));}
 static synchronized void init(Context c){
  if(!enabled()||initialized)return;app=c.getApplicationContext();journal=app.getSharedPreferences("turboio_official_ota_guard_v1",0);
  pending=journal.getBoolean("pending",false);savedCandidate=journal.getString("candidate","");savedProfile=journal.getString("profile","");savedPeer=journal.getString("peer","");gate=newGate();initialized=true;
  if(pending)note="上轮升级结果待确认；禁止续刷，只保留官方只读回查";
  if(!OtaController.critical())try{feed=new OfficialOtaFeed(SystemClock::elapsedRealtime,()->enabled()&&!pending&&!OtaController.critical(),true,PROFILE);feed.start(18794);}catch(Exception e){feed=null;note="来源启动失败；保持锁定";}
  ((Application)app).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
   private boolean host(Activity a){return a.getClass().getName().equals("com.rayneo.venus.MainActivity");}
   public void onActivityResumed(Activity a){if(host(a))foreground=true;}
   public void onActivityStopped(Activity a){if(host(a)&&!a.isChangingConfigurations()){foreground=false;cancel();}}
   public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
  });
  MAIN.post(ticker);
 }
 private static OfficialOtaGate newGate(){return new OfficialOtaGate(peerHash->{
  if(!journal.edit().putBoolean("pending",true).putString("peer",peerHash).putString("candidate",PROFILE.sha).putString("profile",PROFILE.token).putString("phase","START_INTENT").commit())return false;
  savedPeer=peerHash;savedCandidate=PROFILE.sha;savedProfile=PROFILE.token;pending=true;note="官方开始指令已放行；不要强退、重复安装或断电";return true;
 },pendingProfile()==null?PROFILE:pendingProfile());}
 private static OtaProfile pendingProfile(){return pending?OtaProfile.fromJournal(savedCandidate,savedProfile):null;}
 private static String resultIdentity(){OtaProfile p=pendingProfile();return pending?(p==null?"未知历史候选（保护保留）":p.name):PROFILE.name;}
 private static synchronized void bindCurrent(String peer,long now){
  if(gate==null||peer==null||peer.isEmpty())return;
  if(pending&&gate.peer().isEmpty()&&hashPeer(peer).equals(savedPeer)){gate.restore(peer,now);recovery=new OtaRecoveryGuard(peer,now,false);}
  else if(!pending&&!gate.active()&&!peer.equals(gate.peer()))gate.bind(peer);
 }
 private static void settle(){
  if(gate==null)return;armed=gate.phase()==OfficialOtaGate.Phase.ARMED;
  if(!gate.active()&&!checking){NativeTransfer.releaseMessages(lease);lease="";OtaService.finish();}
  if(pending&&(gate.phase()==OfficialOtaGate.Phase.INSTALLING||gate.phase()==OfficialOtaGate.Phase.UNKNOWN)){
   if(recovery==null)recovery=new OtaRecoveryGuard(gate.peer(),SystemClock.elapsedRealtime(),false);
   // Diagnostic failure never clears the durable START_INTENT fence.
   journal.edit().putString("phase",gate.phase().name()).apply();
  }
 }
 private static final Runnable ticker=new Runnable(){public void run(){synchronized(OfficialOtaBridge.class){
  String peer=NativeTransfer.otaConnection();long now=SystemClock.elapsedRealtime();bindCurrent(peer,now);if(gate!=null)gate.tick(peer,now);if(recovery!=null)recovery.observe(peer,now);settle();exportStatus();
  if(gate!=null){for(int type:preflightPoll.due(gate.phase()==OfficialOtaGate.Phase.ARMED&&peer.equals(gate.peer()),foreground,now,t->gate.queryPending(t,now))){
   try{sendReadOnly(peer,type);}catch(Exception e){gate.fail("armed read-only refresh failed");settle();break;}
  }}
  OtaAutoRun.tick();
 }MAIN.postDelayed(this,1000);}};
 /** null means this profile is not installed. Every active-profile decision is final. */
 public static synchronized Boolean allowQueue(Object message){
  if(!enabled())return null;if(!initialized||gate==null)return false;
  try{
   Object biz=NavReflect.field(message,"c");if(!(biz instanceof Enum))return false;String name=((Enum<?>)biz).name();
   if(!"MARS_FOTA".equals(name))return !busy()||!INTERACTIVE.contains(name);
   String current=NativeTransfer.otaConnection();Object destination=NavReflect.field(message,"b"),payload=NavReflect.field(message,"e");
   if(!(destination instanceof String)||!destination.equals(current)||!(payload instanceof byte[]))throw new IllegalArgumentException();
   long now=SystemClock.elapsedRealtime();bindCurrent(current,now);
   byte[] frame=((byte[])payload).clone();OtaFrame decoded=OtaFrame.decode(frame);int type=decoded.type;tx[type]++;
   // Detach vendor-owned input array before validation and queue submission.
   java.lang.reflect.Field field=message.getClass().getDeclaredField("e");field.setAccessible(true);field.set(message,frame);
   trace("out",decoded,now);
   String before=gate.phase()+" "+gate.preflight(now)+" "+gate.idleEvidence(now)+" 前台="+foreground+" 服务="+OtaService.alive();
   boolean allowed=!OtaController.critical()&&gate.outbound(current,frame,now,foreground,OtaService.alive());
   if(!allowed){blocked++;note="发送被保护拦截："+gate.error();if(firstReject.isEmpty()){
    Object modeValue=decoded.json instanceof Map?((Map<?,?>)decoded.json).get("Mode"):null;
    firstReject="首次拦截 type="+type+" Mode="+(modeValue instanceof Number?modeValue:"非数值")+" 字段数="+(decoded.json instanceof Map?((Map<?,?>)decoded.json).size():-1)+" 二进制="+decoded.binary.length+" "+before+" 原因="+gate.error();
   }}
   settle();return allowed;
  }catch(Exception e){blocked++;gate.fail("SDK message shape/peer invalid");settle();return false;}
 }
 public static synchronized boolean allowFile(){return !enabled()||initialized&&!busy()&&!OtaController.critical();}
 /** Synchronous observation precedes Flutter scheduling; never swallows events. */
 public static synchronized void observe(String kind,Map<?,?> data){
  if(!enabled()||!initialized||data==null)return;
  try{
   if("messageReceived".equals(kind)){
    Object raw=data.get("message");if(!(raw instanceof Map))return;Map<?,?> m=(Map<?,?>)raw;if(HostBusiness.id(m.get("businessId"))!=9)return;
    Object p=m.get("deviceId"),b=m.get("payload");String current=NativeTransfer.otaConnection();
    if(!(p instanceof String)||!p.equals(current)||!(b instanceof byte[])||((byte[])b).length>8192)return;
    long now=SystemClock.elapsedRealtime();bindCurrent(current,now);byte[] bytes=((byte[])b).clone();OtaFrame decoded=OtaFrame.decode(bytes);rx[decoded.type]++;if(decoded.type==1&&decoded.binary.length==0&&decoded.json instanceof Map)versionEvidence=OfficialOtaFeed.readbackEvidence((Map<?,?>)decoded.json);gate.inbound(current,bytes,now);trace("in",decoded,now);settle();
   }
  }catch(Exception e){if(gate!=null){gate.fail("invalid official incoming event");settle();}}
 }
 static synchronized void serviceLost(){if(enabled()&&gate!=null&&gate.active()){gate.fail("foreground service lost");settle();}}
 static synchronized void cancel(){OtaAutoRun.stop();epoch++;if(gate!=null&&!pending){gate.revoke();armed=false;settle();note="未使用授权已撤销";}}
 private static OtaPackage freeze()throws IOException{
  File dataRoot=new File(app.getApplicationInfo().dataDir);File canonical;try{canonical=dataRoot.getCanonicalFile();}catch(Exception e){throw new OtaPackage.DiagnosticFailure("ROOT_RESOLVE",e); }File[] dirs={new File(canonical,"files/ota/"+PROFILE.directory),new File(canonical,"app_flutter/ota/"+PROFILE.directory)};File only=null;
  for(File d:dirs)if(d.exists()){if(only!=null)throw new IOException("ambiguous directory");only=d;}
  if(only==null)throw new IOException("not prepared");
  try{return OtaPackage.freezePreparedDirectory(dataRoot.toPath(),canonical.toPath().relativize(only.toPath()),PROFILE);}catch(Exception e){throw new OtaPackage.DiagnosticFailure("SNAPSHOT",e); }
 }
 static String preflightFailure(boolean generation,boolean busy,boolean background,boolean peer,String directory){
  return "generation="+generation+" busy="+busy+" background="+background+" peerChanged="+peer+" directory="+(directory.isEmpty()?"OK":directory);
 }
 private static void inspect(){
  final String peer;final long generation;
  synchronized(OfficialOtaBridge.class){
   if(pending||armed||checking||!foreground||OtaController.busy()||DisplayBridge.active()||HdCast.active()||TodoRuntime.busy()){note="只读预检条件未满足；未授权";return;}
   peer=NativeTransfer.connected();if(peer.isEmpty()){note="没有唯一连接眼镜";return;}
   checking=true;generation=++epoch;note="只读核对官方目录；不会授权或发送固件";
  }
  new Thread(()->{boolean ok=false;try{freeze();ok=true;}catch(Exception ignored){}final boolean valid=ok;
   MAIN.post(()->{synchronized(OfficialOtaBridge.class){checking=false;
    if(generation!=epoch||pending||armed||!foreground||!peer.equals(NativeTransfer.connected())||!valid){note="目录/连接校验未通过；未授权";settle();return;}
    try{bindCurrent(peer,SystemClock.elapsedRealtime());sendReadOnly(peer,1);sendReadOnly(peer,2);sendReadOnly(peer,11);note="官方目录 "+PROFILE.pins().length+"/"+PROFILE.pins().length+" 校验通过；已提交版本/电量/空闲只读查询，等待实际回执。授权仍为0。";}
    catch(Exception e){note="目录校验通过，但只读查询提交失败；未授权";}settle();
   }});
  },"TurboIO-official-ota-inspect").start();
 }

 private static void authorize(String token){
  final String peer;final long generation;
  synchronized(OfficialOtaBridge.class){
   if(!PROFILE.hasOfficialVersions()){note="官方查询与回读版本依据待实测；本候选尚未开放授权";return;}
   boolean tokenMatches=OfficialOtaAuthorization.tokenMatches(token,PROFILE),legacyBusy=OtaController.busy()||DisplayBridge.active()||HdCast.active()||TodoRuntime.busy();
   if(!OfficialOtaAuthorization.allowed(tokenMatches,pending,armed,checking,foreground,legacyBusy)){note="授权条件未满足："+OfficialOtaAuthorization.diagnostic(tokenMatches,pending,armed,checking,foreground,legacyBusy);return;}
   if(gate==null||!gate.readyToArm(SystemClock.elapsedRealtime())){note="请先完成只读预检，确认版本/电量/空闲均为true后再授权";return;}
   peer=NativeTransfer.connected();if(peer.isEmpty()){note="没有唯一连接眼镜";return;}
   // Service startup is asynchronous; grant only after it reports ready.
   try{app.startForegroundService(new Intent(app,OtaService.class));}catch(RuntimeException e){note="前台服务启动失败，禁止授权";return;}
   checking=true;generation=++epoch;note="重新核对官方目录并冻结 "+PROFILE.pins().length+" 个成员；尚未授权";
  }
  new Thread(()->{
   OtaPackage frozen=null;String failure="";try{frozen=freeze();}catch(Exception e){failure=OtaPackage.safeCategory(e)+"/"+OtaPackage.safeFailure(e);}final OtaPackage result=frozen;final String directoryFailure=failure;
   MAIN.post(()->{synchronized(OfficialOtaBridge.class){checking=false;
    if(generation!=epoch||!foreground||pending||!peer.equals(NativeTransfer.connected())||result==null||!OtaService.alive()||!gate.readyToArm(SystemClock.elapsedRealtime())){note="授权未通过："+preflightFailure(generation!=epoch,pending,!foreground,!peer.equals(NativeTransfer.connected()),directoryFailure)+" service="+OtaService.alive()+" fresh="+gate.readyToArm(SystemClock.elapsedRealtime())+"；未授权";OtaService.finish();return;}
    String owner="official-ota-"+UUID.randomUUID();if(!NativeTransfer.acquireMessages(owner)){note="其他任务尚未退出；未授权";OtaService.finish();return;}
    lease=owner;bindCurrent(peer,SystemClock.elapsedRealtime());gate.arm(result,peer,token,SystemClock.elapsedRealtime());armed=true;
    note="授权：1（允许自动开始）。请返回官方更新页开始安装；仍须官方新鲜预检通过。授权有效 15 分钟，退后台即撤销。";
   }});
  },"TurboIO-official-ota-freeze").start();
 }
 static synchronized String status(){String counts="\nGUARD-07 · 预检有效期2分钟 · "+firstReject;for(int i=1;i<tx.length;i++)if(tx[i]+rx[i]>0)counts+="\ntype"+i+" 出="+tx[i]+" 入="+rx[i];
  return "ANDROID · OFFICIAL-OTA-GUARD-01\n"+note+"\n候选："+PROFILE.name+"\n结果记录："+resultIdentity()+"\n授权："+(armed?"1":"0")+" · 结果保护："+(pending?"待核对":"未开始")+"\n"+(gate==null?"未就绪":"阶段："+gate.phase()+"\n"+gate.preflight(SystemClock.elapsedRealtime())+"\n通过内容校验 "+gate.chunks()+" 片 · AP 覆盖 "+gate.covered()+" / "+(pending?(pendingProfile()==null?"未知":String.valueOf(pendingProfile().apSize)):String.valueOf(PROFILE.apSize))+"\n"+gate.error())+"\n版本回读证据："+versionEvidence+"\n拦截："+blocked+counts+"\n"+(feed==null?"来源未启动":feed.status())+"\n"+(recovery==null?"":recovery.status(SystemClock.elapsedRealtime()));
 }
 private static void exportStatus(){try{String text=status()+"\n只读时序（最后32条，不含设备身份/固件正文）\n"+String.join("\n",trace);if(text.equals(lastExport))return;File root=app.getExternalFilesDir(null);if(root==null)return;File tmp=new File(root,"official-ota-guard-status.tmp"),dest=new File(root,"official-ota-guard-status.txt");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}if(tmp.renameTo(dest))lastExport=text;}catch(Exception ignored){}}
 private static void addCandidateCheck(Activity activity, LinearLayout body) {
  OtaProfile candidate = OtaProfile.TMG4_3_10611;
  File directory = activity.getExternalFilesDir("firmware");
  if (directory == null) {
   body.addView(EditorialUI.text(activity, "固件存储目录不可用", 14, EditorialUI.MUTED));
   return;
  }
  File archive = new File(directory, candidate.token + ".zip");
  TextView result = EditorialUI.text(activity, archive.getAbsolutePath(), 14, EditorialUI.MUTED);
  body.addView(result);
  TextView button = EditorialUI.button(activity, body, "校验本机 " + candidate.token, false, () -> {});
  button.setOnClickListener(v -> {
   button.setEnabled(false);
   result.setText("正在校验 " + candidate.token);
   new Thread(() -> {
    String message;
    try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
     byte[] bytes = new byte[candidate.zipSize];
     input.readFully(bytes);
     if (input.read() != -1) throw new IOException("unexpected trailing data");
     OtaPackage.verify(bytes, candidate);
     message = candidate.token + " 文件校验通过 · 16 个载荷 · 尚未发起安装";
    } catch (FileNotFoundException e) {
     message = "无法打开固件文件：" + archive.getAbsolutePath();
    } catch (IOException | SecurityException e) {
     message = "读取或校验失败：" + archive.getAbsolutePath();
    }
    String status = message;
    activity.runOnUiThread(() -> {
     if (!result.isAttachedToWindow()) return;
     result.setText(status);
     button.setEnabled(true);
    });
   }, "TurboIO-candidate-check").start();
  });
 }
 private static File resolveFixedArchive(Context c)throws IOException{
  File primaryDir=c.getExternalFilesDir("firmware");
  File primary=primaryDir==null?null:new File(primaryDir,PROFILE.token+".zip");
  if(primary!=null&&primary.isFile()&&primary.canRead())return primary;
  File downloads=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
  File fallback=downloads==null?null:new File(downloads,PROFILE.token+".zip");
  if(fallback!=null&&fallback.isFile()&&fallback.canRead())return fallback;
  throw new FileNotFoundException(primary!=null?primary.getAbsolutePath():(fallback!=null?fallback.getAbsolutePath():PROFILE.token+".zip"));
 }
 private static byte[] readFixedArchive(Context c)throws IOException{
  File archive=resolveFixedArchive(c);
  try(DataInputStream input=new DataInputStream(new BufferedInputStream(new FileInputStream(archive)))){
   byte[] bytes=new byte[PROFILE.zipSize];
   input.readFully(bytes);
   if(input.read()!=-1)throw new IOException("unexpected trailing data");
   return bytes;
  }
 }

 private static void oneClickPrepareInspectAndAuthorize(Activity a){
  final long generation;
  synchronized(OfficialOtaBridge.class){
   if(armed){note="已完成单次授权（允许自动开始），请返回官方更新页开始安装";return;}
   if(pending||checking||feed==null||OtaController.busy()){EditorialUI.notice(a,"请先处理结果保护或撤销当前任务");return;}
   if(!PROFILE.hasOfficialVersions()){EditorialUI.notice(a,"官方版本依据待实测，本候选尚未开放下载");return;}
   checking=true;generation=++epoch;note="1/3 正在从固定目录读取并校验 "+PROFILE.token+"...";
  }
  new Thread(()->{
   try{
    feed.arm(readFixedArchive(app));
    note="1/3 来源已开放；2/3 正在校验官方目录并预检...";
   }catch(Exception e){
    synchronized(OfficialOtaBridge.class){if(generation==epoch)checking=false;}
    note="1/3 固定目录读取或校验失败："+OtaPackage.safeFailure(e);
    return;
   }
   final String peer=NativeTransfer.connected();
   if(peer.isEmpty()){
    synchronized(OfficialOtaBridge.class){if(generation==epoch)checking=false;}
    note="1/3 来源已开放；请连接唯一眼镜后再点一键执行完成 2·3 步";
    return;
   }
   try{freeze();}catch(Exception e){
    synchronized(OfficialOtaBridge.class){if(generation==epoch)checking=false;}
    note="1/3 来源已开放（15分钟）；官方目录尚未下载解压，请去官方更新页下载后再点一键执行完成 2·3 步";
    return;
   }
   final boolean[] sent={false};
   MAIN.post(()->{synchronized(OfficialOtaBridge.class){
    if(generation!=epoch||pending||armed||!foreground||!peer.equals(NativeTransfer.connected())){sent[0]=false;return;}
    try{bindCurrent(peer,SystemClock.elapsedRealtime());sendReadOnly(peer,1);sendReadOnly(peer,2);sendReadOnly(peer,11);sent[0]=true;}
    catch(Exception e){note="2/3 只读预检发送失败："+OtaPackage.safeFailure(e);}
   }});
   long deadline=SystemClock.elapsedRealtime()+10000L;
   while(SystemClock.elapsedRealtime()<deadline){
    try{Thread.sleep(250);}catch(InterruptedException ignored){break;}
    synchronized(OfficialOtaBridge.class){
     if(generation!=epoch||!foreground||pending||armed||!peer.equals(NativeTransfer.connected())){
      if(generation==epoch)checking=false;
      note="2/3 预检中断，未授权";return;
     }
     if(sent[0]&&gate!=null&&gate.readyToArm(SystemClock.elapsedRealtime())){
      checking=false;
      MAIN.post(()->authorize(PROFILE.token));
      return;
     }
    }
   }
   synchronized(OfficialOtaBridge.class){
    if(generation==epoch)checking=false;
    note="2/3 只读预检未就绪（"+(gate==null?"未就绪":gate.preflight(SystemClock.elapsedRealtime()))+"）；请确认眼镜电量≥50%且空闲后重试";
   }
  },"TurboIO-official-ota-oneclick").start();
 }
 static void show(Activity a){init(a);foreground=true;EditorialUI.Screen s=new EditorialUI.Screen(a,"官方升级 · 一键授权（允许自动开始）");
  addCandidateCheck(a, s.body);
  s.body.addView(EditorialUI.text(a,"GUARD-07 · 预检有效期2分钟。固定固件目录："+PROFILE.token+".zip。授权等待期间自动刷新只读预检，开始传输即停止。",15,EditorialUI.INK));
  TextView label=EditorialUI.text(a,status(),14,EditorialUI.LIME);s.body.addView(label);
  EditorialUI.button(a,s.body,"一键执行 1·2·3（固定目录备源·预检·自动授权）",true,()->oneClickPrepareInspectAndAuthorize(a));
  EditorialUI.button(a,s.body,"选择固件，开启 15 分钟只下载",false,()->{if(pending||armed||feed==null){EditorialUI.notice(a,"请先处理结果保护或撤销授权");return;}DocumentPicker.pick(a,PROFILE.zipSize,b->{new Thread(()->{try{feed.arm(b);note="来源已开放；请去官方更新页下载，先不要安装";}catch(Exception e){note="指定包校验失败，未开放来源";}},"TurboIO-official-source").start();});});
  EditorialUI.button(a,s.body,"只读预检（不授权、不刷写）",false,OfficialOtaBridge::inspect);
  EditorialUI.button(a,s.body,"撤销未使用授权 / 关闭来源",false,()->{cancel();if(feed!=null)feed.disarm();});
  EditorialUI.button(a,s.body,"返回官方 App",false,()->s.dialog.dismiss());
  s.body.addView(EditorialUI.text(a,"通过片数仅代表出站内容校验，不代表眼镜接收或安装成功。任何断连/异常均不自动重发。升级结束后先等待自然重启，核对官方启动结果与结果记录对应的镜片菜单及功能标记。",13,EditorialUI.MUTED));
  EditorialUI.button(a,s.body,"已自然回首页：允许只读回查",false,()->EditorialUI.confirm(a,"确认升级已结束并回到首页？","若眼镜仍在更新、黑屏或结果不明，请不要确认。不重置、不重刷；只查询同一眼镜。",OfficialOtaBridge::readback));
  EditorialUI.button(a,s.body,"已检查结果对应的菜单与功能：解除结果保护",false,()->EditorialUI.confirm(a,"已核对同一眼镜 "+resultIdentity()+" 菜单与功能？","必须先有新的启动校验和空闲回执。版本号相同不等于测试 AP 刷入成功。这里只解除保护，不重试。",OfficialOtaBridge::release));
  Runnable refresh=new Runnable(){public void run(){if(!s.dialog.isShowing())return;label.setText(status());MAIN.postDelayed(this,500);}};MAIN.post(refresh);s.dialog.setOnDismissListener(d->MAIN.removeCallbacks(refresh));
 }
 private static synchronized void readback(){
  String peer=NativeTransfer.otaConnection();long now=SystemClock.elapsedRealtime();
  if(!pending||recovery==null||!recovery.confirmHome("已回首页",peer,now)||!recovery.allowed(peer,now)){note="等待最短保护时间及同一眼镜稳定连接；没有查询";return;}
  try{sendReadOnly(peer,1);sendReadOnly(peer,11);note="已提交只读回查；仍需眼镜回执和人工镜片确认";}catch(Exception e){note="只读回查提交失败，保护保留";}
 }
 private static synchronized void autoReadback(){
  String peer=NativeTransfer.otaConnection();long now=SystemClock.elapsedRealtime();
  if(!pending||recovery==null||!recovery.allowed(peer,now)){note="等待最短保护时间及同一眼镜稳定连接；没有查询";return;}
  try{sendReadOnly(peer,1);sendReadOnly(peer,11);note="自动只读回查已提交；等待眼镜回执与新启动证据";}catch(Exception e){note="自动只读回查提交失败，保护保留";}
 }
 private static synchronized void oneClick(){OtaAutoRun.attach(AUTO);OtaAutoRun.start();}
 private static void sendReadOnly(String peer,int type)throws Exception{
  if(type!=1&&type!=2&&type!=11)throw new IllegalArgumentException("not read only");
  Object manager=NavReflect.field(NavReflect.type(HostAbi.manager()),"h"),biz=NavReflect.call(NavReflect.type(HostAbi.business()),"valueOf","MARS_FOTA"),priority=NavReflect.call(NavReflect.type(HostAbi.priority()),"valueOf","NORMAL");
  Object message=NavReflect.make(HostAbi.message(),OtaFrame.encode(type,null,null),UUID.randomUUID().toString(),peer,biz,null,priority,false,false,null,null);
  Object callback=NavReflect.proxy("kotlin.jvm.functions.Function2",(name,args)->{});NavReflect.call(manager,HostAbi.send(),message,callback);
 }
 private static synchronized void release(){
  String peer=NativeTransfer.otaConnection();long now=SystemClock.elapsedRealtime();
  if(!pending||pendingProfile()==null||recovery==null||!recovery.allowed(peer,now)||!gate.freshBoot(now)||!hashPeer(peer).equals(savedPeer)){note="缺少同一眼镜新回读或人工确认；保持结果保护";return;}
  if(!journal.edit().putBoolean("pending",false).putString("phase","USER_INSPECTED_"+pendingProfile().token).commit()){note="记录写入失败，保持保护";return;}
  pending=false;armed=false;recovery=null;gate=newGate();gate.bind(peer);NativeTransfer.releaseMessages(lease);lease="";OtaService.finish();note="人工验收已记录；授权为0，不自动再次安装";
 }
}

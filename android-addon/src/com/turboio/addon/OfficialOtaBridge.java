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
 private static final OtaProfile PROFILE=OtaProfile.TMG3_REPAIRED;
 private static Boolean mode;private static Context app;private static SharedPreferences journal;
 private static OfficialOtaFeed feed;private static OfficialOtaGate gate;
 private static volatile boolean pending,armed,foreground;private static boolean checking,initialized;
 private static String savedCandidate="",savedProfile="",savedPeer="",lease="",lastExport="",firstReject="";
 private static volatile String note="默认锁定；先使用官方页面下载，再单独授权";
 private static OtaRecoveryGuard recovery;private static long epoch;
 private static final OfficialOtaBusinessAccess business=new OfficialOtaBusinessAccess();
 private static OtaProfile pendingProfile(){return OfficialOtaAttempt.pendingProfile(savedCandidate,savedProfile);}
 private static OtaProfile gateProfile(){OtaProfile previous=pendingProfile();return pending&&previous!=null?previous:PROFILE;}
 private static final int[] tx=new int[256],rx=new int[256];private static int blocked;
 private static final ArrayDeque<String> trace=new ArrayDeque<>();
 private static final OfficialOtaPreflightPoll preflightPoll=new OfficialOtaPreflightPoll();
 private static void trace(String direction,OtaFrame f,long now){
  if(f.type!=1&&f.type!=2&&f.type!=3&&f.type!=4&&f.type!=8&&f.type!=9&&f.type!=10&&f.type!=11)return;
  Map<?,?> j=f.json instanceof Map?(Map<?,?>)f.json:Collections.emptyMap();Object result=j.get("Result");
  while(trace.size()>=32)trace.removeFirst();
  trace.addLast(now+" "+direction+" type="+f.type+" result="+(result instanceof Boolean||result instanceof Number?result:"none")+" fields="+new TreeSet<>(j.keySet())+" "+gate.idleEvidence(now));
 }
 public static synchronized boolean enabled(){
  if(mode!=null)return mode;
  try{Object c=Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);if(!(c instanceof Context))return true;app=((Context)c).getApplicationContext();String[] files=app.getAssets().list("turboio");mode=files!=null&&Arrays.asList(files).contains("official-ota-guarded.txt");}
  catch(Exception e){return true;}return mode;
 }
 static synchronized boolean busy(){boolean open=refreshBusiness(NativeTransfer.otaConnection(),SystemClock.elapsedRealtime());return armed||pending&&!open;}
 private static boolean refreshBusiness(String peer,long now){
  if(gate==null){business.cancel();return false;}
  bindCurrent(peer,now);gate.tick(peer,now);if(recovery!=null)recovery.observe(peer,now);
  boolean wasOpen=business.open();
  boolean open=business.observe(peer,now,pending,pendingProfile()!=null,peer!=null&&hashPeer(peer).equals(savedPeer),foreground,armed,checking,gate.phase(),recovery!=null&&recovery.allowed(peer,now),gate.freshBootAfter(now,business.requestedAt()),gate.bootEvidenceGood());
  if(open&&!wasOpen){NativeTransfer.releaseMessages(lease);lease="";OtaService.finish();}
  return open;
 }
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
 },gateProfile());}
 private static synchronized void bindCurrent(String peer,long now){
  if(gate==null||peer==null||peer.isEmpty())return;
  if(pending&&gate.peer().isEmpty()&&hashPeer(peer).equals(savedPeer)){gate.restore(peer,now);recovery=new OtaRecoveryGuard(peer,now,true);}
  else if(!pending&&!gate.active()&&!peer.equals(gate.peer()))gate.bind(peer);
 }
 private static void settle(){
  if(gate==null)return;armed=gate.phase()==OfficialOtaGate.Phase.ARMED;
  if(!gate.active()&&!checking){NativeTransfer.releaseMessages(lease);lease="";OtaService.finish();}
  if(pending&&(gate.phase()==OfficialOtaGate.Phase.INSTALLING||gate.phase()==OfficialOtaGate.Phase.UNKNOWN)){
   if(recovery==null)recovery=new OtaRecoveryGuard(gate.peer(),SystemClock.elapsedRealtime(),true);
   // Diagnostic failure never clears the durable START_INTENT fence.
   journal.edit().putString("phase",gate.phase().name()).apply();
  }
 }
 private static final Runnable ticker=new Runnable(){public void run(){synchronized(OfficialOtaBridge.class){
  String peer=NativeTransfer.otaConnection();long now=SystemClock.elapsedRealtime();refreshBusiness(peer,now);
  settle();exportStatus();
  if(gate!=null){for(int type:preflightPoll.due(gate.phase()==OfficialOtaGate.Phase.ARMED&&peer!=null&&peer.equals(gate.peer()),foreground,now,t->gate.queryPending(t,now))){
   try{sendReadOnly(peer,type);}catch(Exception e){gate.fail("armed read-only refresh failed");settle();break;}
  }}
 }MAIN.postDelayed(this,1000);}};
 /** null means this profile is not installed. Every active-profile decision is final. */
 public static synchronized Boolean allowQueue(Object message){
  if(!enabled())return null;if(!initialized||gate==null)return false;
  try{
   Object biz=NavReflect.field(message,"c");if(!(biz instanceof Enum))return false;String name=((Enum<?>)biz).name();
   String current=NativeTransfer.otaConnection();long now=SystemClock.elapsedRealtime();boolean businessAllowed=refreshBusiness(current,now);
   Object destination=NavReflect.field(message,"b");
   if(!(destination instanceof String)||current==null||current.isEmpty()||!destination.equals(current))throw new IllegalArgumentException();
   if(!"MARS_FOTA".equals(name)){
    if(pending&&("FILE_TRANSFER".equals(name)||"FILE_TRANSFER_LAUNCHER".equals(name)))return false;
    return !INTERACTIVE.contains(name)||!armed&&(!pending||businessAllowed);
   }
   Object payload=NavReflect.field(message,"e");if(!(payload instanceof byte[]))throw new IllegalArgumentException();
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
  }catch(Exception e){business.cancel();blocked++;gate.fail("SDK message shape/peer invalid");settle();return false;}
 }
 public static synchronized boolean allowFile(){if(!enabled())return true;refreshBusiness(NativeTransfer.otaConnection(),SystemClock.elapsedRealtime());return initialized&&!pending&&!armed&&!OtaController.critical();}
 /** Synchronous observation precedes Flutter scheduling; never swallows events. */
 public static synchronized void observe(String kind,Map<?,?> data){
  if(!enabled()||!initialized||data==null)return;
  try{
   refreshBusiness(NativeTransfer.otaConnection(),SystemClock.elapsedRealtime());
   if("messageReceived".equals(kind)){
    Object raw=data.get("message");if(!(raw instanceof Map))return;Map<?,?> m=(Map<?,?>)raw;if(HostBusiness.id(m.get("businessId"))!=9)return;
    Object p=m.get("deviceId"),b=m.get("payload");String current=NativeTransfer.otaConnection();
    if(!(p instanceof String)||!p.equals(current)||!(b instanceof byte[])||((byte[])b).length>8192)return;
    long now=SystemClock.elapsedRealtime();bindCurrent(current,now);byte[] bytes=((byte[])b).clone();OtaFrame decoded=OtaFrame.decode(bytes);rx[decoded.type]++;gate.inbound(current,bytes,now);refreshBusiness(current,now);trace("in",decoded,now);settle();
   }
  }catch(Exception e){business.cancel();if(gate!=null){gate.fail("invalid official incoming event");settle();}}
 }
 static synchronized void serviceLost(){business.cancel();if(enabled()&&gate!=null&&gate.active()){gate.fail("foreground service lost");settle();}}
 static synchronized void cancel(){business.cancel();epoch++;if(gate!=null&&!pending){gate.revoke();armed=false;settle();note="未使用授权已撤销；没有自动开始";}}
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
   if(pending||armed||checking||!foreground||OtaController.busy()){note="只读预检条件未满足；未授权";return;}
   peer=NativeTransfer.connected();if(peer.isEmpty()){note="没有唯一连接眼镜";return;}
   checking=true;generation=++epoch;note="只读核对官方目录；不会授权或发送固件";
  }
  new Thread(()->{String failure="";try{freeze();}catch(Exception e){failure=OtaPackage.safeCategory(e)+"/"+OtaPackage.safeFailure(e);}final String directoryFailure=failure;
   MAIN.post(()->{synchronized(OfficialOtaBridge.class){checking=false;
    String reason=preflightFailure(generation!=epoch,pending||armed,!foreground,!peer.equals(NativeTransfer.connected()),directoryFailure);
    if(generation!=epoch||pending||armed||!foreground||!peer.equals(NativeTransfer.connected())||!directoryFailure.isEmpty()){note="只读预检未通过："+reason+"；未授权";settle();return;}
    try{bindCurrent(peer,SystemClock.elapsedRealtime());sendReadOnly(peer,1);sendReadOnly(peer,2);sendReadOnly(peer,11);note="官方目录 16/16 校验通过；已提交版本/电量/空闲只读查询，等待实际回执。授权仍为0。";}
    catch(Exception e){note="目录校验通过，但只读查询提交失败："+OtaPackage.safeFailure(e)+"；未授权";}settle();
   }});
  },"TurboIO-official-ota-inspect").start();
 }
 private static void authorize(String token){
  final String peer;final long generation;
  synchronized(OfficialOtaBridge.class){
   boolean tokenMatches=OfficialOtaAuthorization.tokenMatches(token,PROFILE),legacyBusy=OtaController.busy();
   if(!OfficialOtaAuthorization.allowed(tokenMatches,pending,armed,checking,foreground,legacyBusy)){note="授权条件未满足："+OfficialOtaAuthorization.diagnostic(tokenMatches,pending,armed,checking,foreground,legacyBusy);return;}
   if(gate==null||!gate.readyToArm(SystemClock.elapsedRealtime())){note="请先完成只读预检，确认版本/电量/空闲均为true后再授权";return;}
   peer=NativeTransfer.connected();if(peer.isEmpty()){note="没有唯一连接眼镜";return;}
   // Service startup is asynchronous; grant only after it reports ready.
   try{app.startForegroundService(new Intent(app,OtaService.class));}catch(RuntimeException e){note="前台服务启动失败，禁止授权";return;}
   checking=true;generation=++epoch;note="重新核对官方目录并冻结 16 个成员；尚未授权";
  }
  new Thread(()->{
   OtaPackage frozen=null;String failure="";try{frozen=freeze();}catch(Exception e){failure=OtaPackage.safeCategory(e)+"/"+OtaPackage.safeFailure(e);}final OtaPackage result=frozen;final String directoryFailure=failure;
   MAIN.post(()->{synchronized(OfficialOtaBridge.class){checking=false;
    if(generation!=epoch||!foreground||pending||!peer.equals(NativeTransfer.connected())||result==null||!OtaService.alive()||!gate.readyToArm(SystemClock.elapsedRealtime())){note="授权未通过："+preflightFailure(generation!=epoch,pending,!foreground,!peer.equals(NativeTransfer.connected()),directoryFailure)+" service="+OtaService.alive()+" fresh="+gate.readyToArm(SystemClock.elapsedRealtime())+"；未授权";OtaService.finish();return;}
    String owner="official-ota-"+UUID.randomUUID();if(!NativeTransfer.acquireMessages(owner)){note="其他任务尚未退出；未授权";OtaService.finish();return;}
    lease=owner;bindCurrent(peer,SystemClock.elapsedRealtime());gate.arm(result,peer,token,SystemClock.elapsedRealtime());armed=true;
    note="授权：1。请返回官方更新页开始安装；仍须官方新鲜预检通过。授权有效 15 分钟，退后台即撤销。";
   }});
  },"TurboIO-official-ota-freeze").start();
 }
 static synchronized String status(){refreshBusiness(NativeTransfer.otaConnection(),SystemClock.elapsedRealtime());String counts="\nGUARD-07 · 预检有效期2分钟 · "+firstReject;for(int i=1;i<tx.length;i++)if(tx[i]+rx[i]>0)counts+="\ntype"+i+" 出="+tx[i]+" 入="+rx[i];
  return "ANDROID · OFFICIAL-OTA-GUARD-01\n"+note+"\n候选："+PROFILE.name+"\n待核对记录："+(pending?savedProfile+" / "+savedCandidate:"无")+"\n普通消息验收："+(business.open()?"已开放":"锁定")+" · 文件共享在结果保护期间仍锁定"+"\n授权："+(armed?"1":"0")+" · 结果保护："+(pending?"待核对":"未开始")+"\n"+(gate==null?"未就绪":"阶段："+gate.phase()+"\n"+gate.preflight(SystemClock.elapsedRealtime())+"\n通过内容校验 "+gate.chunks()+" 片 · AP 覆盖 "+gate.covered()+" / "+gateProfile().apSize+"\n"+gate.error())+"\n拦截："+blocked+counts+"\n"+(feed==null?"来源未启动":feed.status())+"\n"+(recovery==null?"":recovery.status(SystemClock.elapsedRealtime()));
 }
 private static void exportStatus(){try{String text=status()+"\n只读时序（最后32条，不含设备身份/固件正文）\n"+String.join("\n",trace);if(text.equals(lastExport))return;File root=app.getExternalFilesDir(null);if(root==null)return;File tmp=new File(root,"official-ota-guard-status.tmp"),dest=new File(root,"official-ota-guard-status.txt");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}if(tmp.renameTo(dest))lastExport=text;}catch(Exception ignored){}}
 static void show(Activity a){init(a);foreground=true;EditorialUI.Screen s=new EditorialUI.Screen(a,"官方升级 · 单次授权");
  s.body.addView(EditorialUI.text(a,(PROFILE==OtaProfile.STOCK127?"原厂对照：":"修复候选：")+PROFILE.name+"\n"+(PROFILE==OtaProfile.STOCK127?"完整原包未修改；此轮用于核对官方固件升级链路。":"离线构建与完整重构审计通过；已修复生命周期/launcher/renderer常量并经Windows独立回归。"),14,EditorialUI.MUTED));
  s.body.addView(EditorialUI.text(a,"GUARD-07 · 预检有效期2分钟。授权等待期间自动刷新只读预检，开始传输即停止。实验固件有损坏风险。Android 官方负责检测、下载、解压及会话；扩展仅核对指定包和发送字节。首次 Android 实机刷写尚未验收，不能承诺不会刷坏。",15,EditorialUI.INK));
  TextView label=EditorialUI.text(a,status(),14,EditorialUI.LIME);s.body.addView(label);
  EditorialUI.button(a,s.body,"1 · 开启 15 分钟官方只下载",false,()->{if(pending||armed||feed==null){EditorialUI.notice(a,"请先处理结果保护或撤销授权");return;}DocumentPicker.pick(a,PROFILE.zipSize,b->{new Thread(()->{try{feed.arm(b);note="来源已开放；请去官方更新页下载，先不要安装";}catch(Exception e){note="指定包校验失败，未开放来源";}},"TurboIO-official-source").start();});});
  EditorialUI.button(a,s.body,"2 · 只读预检（不授权、不刷写）",false,OfficialOtaBridge::inspect);
  EditorialUI.button(a,s.body,"3 · 校验目录并允许一次试刷",false,()->{EditText input=new EditText(a);input.setSingleLine(true);input.setHint(PROFILE.token);new AlertDialog.Builder(a).setTitle("单次授权，不自动开始").setMessage("先完成只读预检。确认已在官方设置关闭自动更新，眼镜无任务、电量至少50%，且已了解实验风险。只放行已校验 "+PROFILE.token+"；输入完整标记后，返回官方页面开始。手机保持前台与供电。").setView(input).setNegativeButton("取消",null).setPositiveButton("授权",(d,w)->authorize(input.getText().toString())).show();});
  EditorialUI.button(a,s.body,"撤销未使用授权 / 关闭来源",false,()->{cancel();if(feed!=null)feed.disarm();});
  EditorialUI.button(a,s.body,"返回官方 App",false,()->s.dialog.dismiss());
  s.body.addView(EditorialUI.text(a,"通过片数仅代表出站内容校验，不代表眼镜接收或安装成功。任何断连/异常均不自动重发。升级结束后先等待自然重启，核对官方启动结果与镜片原有首页、菜单及功能。",13,EditorialUI.MUTED));
  EditorialUI.button(a,s.body,"已自然回首页：允许只读回查",false,()->EditorialUI.confirm(a,"确认升级已结束并回到首页？","若眼镜仍在更新、黑屏或结果不明，请不要确认。不重置、不重刷；只查询同一眼镜。",OfficialOtaBridge::readback));
  EditorialUI.button(a,s.body,"已检查 "+(gateProfile()==OtaProfile.STOCK127?"原厂首页/菜单/功能":gateProfile().token+" 菜单与功能")+"：解除结果保护",false,()->EditorialUI.confirm(a,"已核对同一眼镜 "+(gateProfile()==OtaProfile.STOCK127?"原厂首页、菜单与功能":gateProfile().token+" 特征菜单与功能")+"？","必须先有新的启动校验和空闲回执。版本号相同不等于测试 AP 刷入成功。这里只解除保护，不重试。",OfficialOtaBridge::release));
  EditorialUI.button(a,s.body,"上次更新失败，原有首页/菜单正常：归档失败",false,()->EditorialUI.confirm(a,"确认上次更新失败且原有功能正常？","保留上次包与设备记录，仍需同一眼镜新的启动及空闲回执。确认后授权为0，不会自动下载或重刷。",OfficialOtaBridge::recordFailure));
  Runnable refresh=new Runnable(){public void run(){if(!s.dialog.isShowing())return;label.setText(status());MAIN.postDelayed(this,500);}};MAIN.post(refresh);s.dialog.setOnDismissListener(d->MAIN.removeCallbacks(refresh));
 }
 private static synchronized void readback(){
  String peer=NativeTransfer.otaConnection();long now=SystemClock.elapsedRealtime();
  if(!pending||recovery==null||!recovery.confirmHome("已回首页",peer,now)||!recovery.allowed(peer,now)){note="等待最短保护时间及同一眼镜稳定连接；没有查询";return;}
  business.request(peer,now);
  try{sendReadOnly(peer,1);sendReadOnly(peer,11);note="已提交只读回查；仍需眼镜回执和人工镜片确认";}catch(Exception e){business.cancel();note="只读回查提交失败，保护保留";}
 }
 private static void sendReadOnly(String peer,int type)throws Exception{
  if(type!=1&&type!=2&&type!=11)throw new IllegalArgumentException("not read only");
  Object manager=NavReflect.field(NavReflect.type(HostSymbols.MANAGER_CLASS),"h"),biz=NavReflect.call(NavReflect.type(HostSymbols.BIZ_ENUM_CLASS),"valueOf","MARS_FOTA"),priority=NavReflect.call(NavReflect.type(HostSymbols.PRIO_ENUM_CLASS),"valueOf","NORMAL");
  Object message=NavReflect.make(HostSymbols.PACKET_CLASS,OtaFrame.encode(type,null,null),UUID.randomUUID().toString(),peer,biz,null,priority,false,false,null,null);
  Object callback=NavReflect.proxy("kotlin.jvm.functions.Function2",(name,args)->{});NavReflect.call(manager,HostSymbols.METHOD_SEND_PACKET,message,callback);
 }
 private static synchronized void recordFailure(){
  String peer=NativeTransfer.otaConnection();long now=SystemClock.elapsedRealtime();OtaProfile previous=pendingProfile();
  if(!OfficialOtaAttempt.failureAllowed(true,pending,previous!=null,peer!=null&&hashPeer(peer).equals(savedPeer),foreground,recovery!=null&&recovery.allowed(peer,now),gate!=null&&gate.freshBoot(now),armed,checking)){note="缺少同一眼镜新回读或失败确认条件；保留结果保护";return;}
  try{
   List<Object> history=new ArrayList<>(BoundedJson.list(BoundedJson.read(journal.getString("failed_attempts","[]").getBytes(StandardCharsets.UTF_8))));
   history.add(OtaFrame.object("candidate",savedCandidate,"profile",savedProfile,"peer",savedPeer,"phase",journal.getString("phase","UNKNOWN"),"outcome","FAILED_USER_CONFIRMED","confirmedAt",System.currentTimeMillis()));
   String retained=new String(BoundedJson.canonical(history),StandardCharsets.UTF_8);
   String oldHistory=journal.getString("failed_attempts","[]"),oldPhase=journal.getString("phase","UNKNOWN");
   boolean committed=OfficialOtaAttempt.commitFailure(()->journal.edit().putString("failed_attempts",retained).putString("phase","FAILED_USER_CONFIRMED").putBoolean("pending",false).commit(),()->journal.edit().putString("failed_attempts",oldHistory).putString("candidate",savedCandidate).putString("profile",savedProfile).putString("peer",savedPeer).putString("phase",oldPhase).putBoolean("pending",true).commit());
   if(!committed){note="失败记录提交未确认；保持结果保护";return;}
  }catch(Exception e){note="失败历史无法保留；保持结果保护";return;}
  if(feed!=null)feed.disarm();pending=false;armed=false;recovery=null;checking=false;epoch++;gate=newGate();gate.bind(peer);NativeTransfer.releaseMessages(lease);lease="";OtaService.finish();
  note="上次更新已归档为失败；授权为0。请重新选择对应ZIP并完成全新预检。";
 }
 private static synchronized void release(){
  String peer=NativeTransfer.otaConnection();long now=SystemClock.elapsedRealtime();
  if(!pending||pendingProfile()==null||recovery==null||!recovery.allowed(peer,now)||!gate.freshBoot(now)||!hashPeer(peer).equals(savedPeer)){note="缺少同一眼镜新回读或人工确认；保持结果保护";return;}
  String oldPhase=journal.getString("phase","UNKNOWN");
  if(!OfficialOtaAttempt.commitFailure(()->journal.edit().putBoolean("pending",false).putString("phase","USER_INSPECTED_"+pendingProfile().token).commit(),()->journal.edit().putBoolean("pending",true).putString("phase",oldPhase).commit())){note="记录写入失败，保持保护";return;}
  pending=false;armed=false;recovery=null;gate=newGate();gate.bind(peer);NativeTransfer.releaseMessages(lease);lease="";OtaService.finish();note="人工验收已记录；授权为0，不自动再次安装";
 }
}

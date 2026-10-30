package com.turboio.addon;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Browser authorization is available only after the dedicated server capability is verified. */
final class DshSettings {
 static final String ORIGIN="https://web.agents-anywhere.com",CLIENT="agents-anywhere-mobile";
 static final DshAuth AUTH=new DshAuth();
 static final ExecutorService IO=Executors.newFixedThreadPool(2);
 static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static volatile long accountEpoch;
 private static volatile Screen current;
 private static volatile boolean storageFailed;
 static boolean storageLocked(){return storageFailed;}
 static long authGeneration(){return accountEpoch;}
 static void lockStorage(){storageFailed=true;}
 static boolean locked(Context app){return storageFailed||ToolClient.dshLocked()||app.getSharedPreferences("turboio_settings",0).getBoolean("dsh_unknown_delivery",false);}
 static String encode(String text){try{return URLEncoder.encode(text,"UTF-8");}catch(Exception e){throw new IllegalArgumentException();}}
 static DshAuth.Storage storage(Context app) {
  return new DshAuth.Storage() {
   SharedPreferences p = app.getSharedPreferences("turboio_settings", 0);
   @Override public void save(String s, String v, String o, long st, long ep, long gen) {
    try {
     SecretStore.put(app, "auth_verifier", v);
    } catch(Exception e) {
     storageFailed = true;
     throw new IllegalStateException("keystore_failed");
    }
    if(!p.edit().putString("auth_state", s)
               .putString("auth_origin", o)
               .putLong("auth_started", st)
               .putLong("auth_epoch", ep)
               .putLong("auth_generation", gen)
               .commit()) {
     try { SecretStore.put(app, "auth_verifier", ""); } catch(Exception ignored){}
     storageFailed = true;
     throw new IllegalStateException("commit_failed");
    }
   }
   @Override public void clear() {
    try { SecretStore.put(app, "auth_verifier", ""); } catch(Exception ignored){}
    p.edit().remove("auth_state").remove("auth_origin").remove("auth_started").remove("auth_epoch").remove("auth_generation").commit();
   }
   @Override public String state() { return p.getString("auth_state", ""); }
   @Override public String verifier() { try { return SecretStore.get(app, "auth_verifier"); } catch(Exception e) { return ""; } }
   @Override public String origin() { return p.getString("auth_origin", ""); }
   @Override public long started() { return p.getLong("auth_started", 0L); }
   @Override public long epoch() { return p.getLong("auth_epoch", 0L); }
   @Override public long generation() { return p.getLong("auth_generation", 0L); }
  };
 }
 static void show(Activity activity){ToolClient.init(activity.getApplicationContext());if(current!=null)current.close();current=new Screen(activity);}
 static Runnable onClosed;
 static void notifyClosed(){Runnable r=onClosed;onClosed=null;if(r!=null)try{r.run();}catch(Exception ignored){}}
 static void callback(Context app,String uri){
  final DshAuth.Grant grant;
  final DshAuth.Storage store = storage(app);
  try{grant=AUTH.consume(uri,ORIGIN,System.currentTimeMillis(),store);}catch(Exception e){MAIN.post(()->Toast.makeText(app,"授权已取消、过期或回调无效；请重新登录。",Toast.LENGTH_LONG).show());notifyState("授权已取消、过期或回调无效；请重新登录。");return;}
  IO.execute(()->{
   try{
    String form="grant_type=authorization_code&client_id="+CLIENT+"&redirect_uri="+encode(DshAuth.REDIRECT)+"&code="+encode(grant.code)+"&code_verifier="+encode(grant.verifier);
    Map<String,Object> tokens=formPost(ORIGIN+"/api/v2/oauth/token",form);
    String token=DshDirectory.text(tokens,"access_token");
    if(token.isEmpty()||!"Bearer".equals(tokens.get("token_type")))throw new IllegalArgumentException();
    Map<String,Object> me=DshClient.httpTransport("GET",ORIGIN+"/api/v2/auth/me",token,null);
    String user=DshDirectory.text(me,"userId");if(user.isEmpty())throw new IllegalArgumentException();
    synchronized(DshClient.LOCK){
     if(grant.epoch != accountEpoch || locked(app))throw new IllegalStateException("stale_authorization");
     SharedPreferences prefs=app.getSharedPreferences("turboio_settings",0);
     if(!prefs.edit().putBoolean("dsh",false).commit()){storageFailed=true;throw new IllegalStateException();}
     try{SecretStore.put(app,"dsh_key",token);}catch(Exception e){storageFailed=true;throw e;}
     if(!prefs.edit().putString("dsh_user_id",user).putString("dsh_url",ORIGIN).putString("dsh_session_id","").putString("dsh_client_message_id","").putString("dsh_target_fingerprint","").putString("dsh_connector","").putString("dsh_project","").putString("dsh_runtime","").putString("dsh_model","").putString("dsh_permission","").commit()){
      storageFailed=true;SecretStore.put(app,"dsh_key","");throw new IllegalStateException();
     }
     accountEpoch++;AUTH.cancel(store);
    }
    notifyState("已登录并核验账户；目标配置请在 AA 首页展开设置。");MAIN.post(()->{Screen screen=current;if(screen!=null&&!screen.closed)screen.refreshSyncCard();});
   }catch(Exception e){notifyState("授权未保存；请检查服务连接或未确认任务，重新登录。");}
  });
 }
 static void notifyState(String text){MAIN.post(()->{Screen screen=current;if(screen!=null&&!screen.closed)screen.status.setText(text);});}
 static Map<String,Object> formPost(String url,String form)throws Exception{
  HttpURLConnection connection=(HttpURLConnection)URI.create(url).toURL().openConnection();
  try{connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(12000);connection.setReadTimeout(20000);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
   byte[] bytes=form.getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
   if(connection.getResponseCode()!=200)throw new IOException("token_failed");
   try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] block=new byte[4096];int n;while((n=in.read(block))!=-1){if(out.size()+n>65536)throw new IOException();out.write(block,0,n);}return DshClient.parseJsonObject(out.toString("UTF-8"));}
  }finally{connection.disconnect();}
 }
 static boolean capability(Map<String,Object> response){return DshAuth.capability(response);
 }
 static final class Policy {
  static boolean sessionCurrent(long epoch,long currentEpoch,long generation,long currentGeneration,long request,long currentRequest,String c,String p,String r,DshDirectory directory){return epoch==currentEpoch&&generation==currentGeneration&&request==currentRequest&&directory.matches(c,p,r);}
  static String savedMessage(boolean targetChanged,String selected,String oldSession,String fingerprint,String oldFingerprint,String oldMessage){return !targetChanged&&!selected.isEmpty()&&selected.equals(oldSession)&&fingerprint.equals(oldFingerprint)?oldMessage:"";}
  static DshDirectory.Option find(List<DshDirectory.Option> rows,String id){if(rows!=null&&id!=null&&!id.isEmpty())for(DshDirectory.Option row:rows)if(id.equals(row.id))return row;return null;}
  static DshDirectory.Option runtime(List<DshDirectory.Option> rows,String id){DshDirectory.Option saved=find(rows,id);return saved!=null?saved:rows.size()==1?rows.get(0):null;}
  static String directoryError(Exception error){
   if(error instanceof DshClient.HttpStatusException){int status=((DshClient.HttpStatusException)error).status;if(status==401)return "登录已失效（401），请重新网页授权登录。";if(status==403)return "目录访问被拒绝（403），请检查账户授权。";return "目录请求失败（HTTP "+status+"），请稍后刷新。";}
   if(error instanceof IOException)return "目录网络连接失败，请检查网络或电脑连接后刷新。";
   return "目录加载失败或格式不兼容，请刷新重试。";
  }
 }
 /**
  * Draft of one voice alias target: the five ids picked for THIS alias plus the live account-scoped
  * directory rows each level was picked from, stamped with the account epoch and editor session that
  * produced them. Pure Java (no Android types, no prefs) so the fail-closed save gate runs on the JVM.
  * It never reads or writes the global {@code dsh_*} binding: a draft is only ever stored into the one
  * AliasEntry the user explicitly saves, so two aliases can point at two different runtimes with two
  * different projects, models and permissions.
  */
 static final class AliasTarget {
  static final String OK="";
  /** Rows the level was picked from; every one of them came from the live account-scoped directory. */
  List<DshDirectory.Option> connectors=Collections.<DshDirectory.Option>emptyList();
  List<DshDirectory.Option> projects=Collections.<DshDirectory.Option>emptyList();
  List<DshDirectory.Option> runtimes=Collections.<DshDirectory.Option>emptyList();
  /** Same runtimes narrowed to usable DSH instances: a card can be visible but unusable. */
  List<DshDirectory.Option> usable=Collections.<DshDirectory.Option>emptyList();
  List<DshDirectory.Option> models=Collections.<DshDirectory.Option>emptyList();
  List<DshDirectory.Option> permissions=Collections.<DshDirectory.Option>emptyList();
  String connector="",project="",runtime="",model="",permission="";
  /** Account epoch and directory generation that produced the rows this draft currently holds. */
  long loadedAccount=-1,loadedTicket=-1,catalogAccount=-1,catalogTicket=-1;
  /** Catalog rows belong to exactly this runtime instance; a runtime change must clear them. */
  String catalogRuntime="";
  /** A level counts as verified only while its id is one of the live rows read for this draft. */
  static boolean verified(List<DshDirectory.Option> rows,String id){return id!=null&&!id.isEmpty()&&Policy.find(rows,id)!=null;}
  boolean childrenFresh(long accountEpoch,long ticket){return loadedAccount==accountEpoch&&loadedTicket==ticket;}
  boolean catalogFresh(long accountEpoch,long ticket){return childrenFresh(accountEpoch,ticket)&&catalogAccount==accountEpoch&&catalogTicket==ticket;}
  /** Refusal reason for an explicit save; {@link #OK} only when every level is verified in the live directory. */
  String refusal(long accountEpoch,long ticket,String baseUrl){
   if(DshClient.normalizeBaseUrl(baseUrl)==null)return "FOREIGN_TARGET";
   if(loadedAccount<0||loadedAccount!=accountEpoch)return "STALE_AUTH";
   if(loadedTicket!=ticket)return "STALE_DIRECTORY";
   if(!verified(connectors,connector))return "CONNECTOR_UNVERIFIED";
   if(!verified(projects,project))return "PROJECT_UNVERIFIED";
   if(!verified(runtimes,runtime))return "RUNTIME_UNVERIFIED";
   if(!verified(usable,runtime))return "RUNTIME_UNAVAILABLE";
   if(!catalogFresh(accountEpoch,ticket)||!runtime.equals(catalogRuntime))return "STALE_CATALOG";
   if(!verified(models,model))return "MODEL_UNVERIFIED";
   if(!verified(permissions,permission))return "PERMISSION_UNVERIFIED";
   if(!DshClient.validBinding(baseUrl,connector,project,runtime,model,permission))return "IDENTITY_REQUIRED";
   return OK;
  }
  /** A parent change drops descendant rows, so a stale descendant id can never stay "verified". */
  void dropProjectAndRuntimes(){
   projects=Collections.<DshDirectory.Option>emptyList();runtimes=projects;usable=projects;
   project=runtime="";dropCatalog();
  }
  void dropCatalog(){
   models=Collections.<DshDirectory.Option>emptyList();permissions=models;model=permission="";
   catalogAccount=-1;catalogTicket=-1;catalogRuntime="";
  }
  void clear(){
   connectors=Collections.<DshDirectory.Option>emptyList();dropProjectAndRuntimes();
   connector="";loadedAccount=-1;loadedTicket=-1;
  }
  /** Order: connector, project, runtime, model, permission. Snapshotted before the live re-check. */
  List<String> ids(){return Arrays.asList(connector,project,runtime,model,permission);}
  /** Alias identity of the stored entry for one runtime instance; routing keeps one alias per instance. */
  static DshAliasRegistry.AliasEntry snapshotOf(DshAliasRegistry table,String runtimeId){
   if(table==null)return null;
   for(DshAliasRegistry.AliasEntry entry:table.listSnapshot())if(entry.runtimeId.equals(runtimeId))return entry;
   return null;
  }
  /**
   * Two DIFFERENT aliases must never share one runtime instance: upsert would silently drop the other
   * mapping, which is easy to hit now that every alias picks its own target. Renaming the entry that
   * already owns the instance (same alias key, or the very entry being edited) stays allowed.
   */
  static String ownerConflict(DshAliasRegistry table,String alias,String runtimeId,String editingKey){
   String key=DshAliasRegistry.normalizeAlias(alias);
   if(key.isEmpty())return "";
   DshAliasRegistry.AliasEntry owner=snapshotOf(table,runtimeId);
   if(owner==null||key.equals(owner.aliasKey)||owner.aliasKey.equals(editingKey))return "";
   return owner.alias;
  }
  /** Why the live directory does not yet verify this alias's own target. The target is never borrowed from the binding. */
  static String message(String reason){
   if(OK.equals(reason))return "已核验";
   if("FOREIGN_TARGET".equals(reason))return "服务地址未核验";
   if("STALE_AUTH".equals(reason))return "账户已变化，请重新登录并刷新目录";
   if("STALE_DIRECTORY".equals(reason))return "目录已变化，请点击重新读取目录";
   if("STALE_CATALOG".equals(reason))return "该运行时的模型与权限目录尚未核验";
   if("CONNECTOR_UNVERIFIED".equals(reason))return "未为本别名选择在线目录中的电脑";
   if("PROJECT_UNVERIFIED".equals(reason))return "未为本别名选择在线目录中的项目";
   if("RUNTIME_UNVERIFIED".equals(reason))return "未为本别名选择在线目录中的 DSH 运行时";
   if("RUNTIME_UNAVAILABLE".equals(reason))return "本别名所选运行时当前不是可用的 dsh 运行时";
   if("MODEL_UNVERIFIED".equals(reason))return "未为本别名选择该运行时目录中的模型";
   if("PERMISSION_UNVERIFIED".equals(reason))return "未为本别名选择该运行时目录中的执行权限";
   if("IDENTITY_REQUIRED".equals(reason))return "本别名目标身份或模型权限快照不完整";
   return "本别名目标未核验";
  }
 }
 static final class Screen {
  final Activity activity;final Context app;final EditorialUI.Screen ui;final TextView status;
  final Map<String,EditorialUI.CardText> cards=new LinkedHashMap<>();
  final Map<String,List<DshDirectory.Option>> options=new LinkedHashMap<>();
  final Map<String,DshDirectory.Option> chosen=new LinkedHashMap<>();
  final DshDirectory directory=new DshDirectory();
  final TextView syncState,syncSession,syncReply,syncTime,syncNote;
  final TextView endSessionButton;
  private boolean closed;
  private long catalogAccount=-1;
  private long sessionRequest,sessionAccount=-1,sessionGeneration=-1,restoreAccount=-1;
  private List<DshDirectory.Option> usableRuntimes=Collections.emptyList();
  private String sessionConnector="",sessionProject="",sessionRuntime="";
  final Map<String,String> restore=new LinkedHashMap<>();
  private final long screenEpoch=SystemClock.elapsedRealtime();
  private static final String CONNECTORS="connectors",PROJECTS="projects",RUNTIMES="runtimes",MODELS="models",PERMISSIONS="permissions",SESSIONS="sessions";
  Screen(Activity a){
   activity=a;app=a.getApplicationContext();ui=new EditorialUI.Screen(a,"Agent-Anywhere设置");
   SharedPreferences prefs=app.getSharedPreferences("turboio_settings",0);
   status=EditorialUI.text(a,prefs.getString("dsh_user_id","").isEmpty()?"尚未通过网页授权登录。":"已登录并核验账户；目标配置请在 AA 首页展开设置。",14,EditorialUI.MUTED);
   ui.body.addView(status);
   EditorialUI.button(a,ui.body,"网页授权登录",true,this::login);
   EditorialUI.button(a,ui.body,"退出登录",false,this::logout);
   // Task and session sync card lives above the save button; disposal buttons are appended inside it.
   LinearLayout syncCard=EditorialUI.card(a,ui.body);
   syncCard.addView(EditorialUI.text(a,"当前任务与会话同步",15,EditorialUI.INK));
   syncState=EditorialUI.text(a,"状态：就绪",13,EditorialUI.MUTED);
   syncSession=EditorialUI.text(a,"",12,EditorialUI.MUTED);
   syncReply=EditorialUI.text(a,"",12,EditorialUI.MUTED);
   syncTime=EditorialUI.text(a,"最近同步：尚未同步",12,EditorialUI.MUTED);
   syncNote=EditorialUI.text(a,"",12,EditorialUI.MUTED);
   syncCard.addView(syncState);syncCard.addView(syncSession);syncCard.addView(syncReply);syncCard.addView(syncTime);syncCard.addView(syncNote);
   EditorialUI.button(activity,syncCard,"刷新任务状态",false,this::refreshTask);
   syncCard.addView(EditorialUI.text(a,"结束会话：停止在最近一次 DSH 会话中继续追加消息，下次派发将新建会话；只影响本机跟踪，远端会话不受影响。",12,EditorialUI.MUTED));
   endSessionButton=EditorialUI.button(activity,syncCard,"结束当前会话（下次新建）",false,this::endSession);
   syncCard.addView(EditorialUI.text(a,"未确认提交处置：仅在派发结果未知且无法核对时使用强制放弃，放弃不会取消远端任务。",12,EditorialUI.MUTED));
  EditorialUI.button(activity,syncCard,"查询并核对",false,this::queryAndReconcile);
  EditorialUI.button(activity,syncCard,"强制放弃本地追踪",false,()->forceRelease());
  ui.body.addView(EditorialUI.text(a,"电脑、项目、运行时、会话、模型与权限请在 AA 首页展开配置。",13,EditorialUI.MUTED));
  // Small voice alias entry: maps a spoken name to the runtime instance this account already verified.
  voiceAliasCard();
  refreshSyncCard();
   ui.dialog.setOnDismissListener(d->notifyClosed());
  }
  void close(){closed=true;options.clear();chosen.clear();directory.reset();notifyClosed();}
  void connectorsCard(){
   EditorialUI.CardText card=EditorialUI.optionCard(activity,ui.body,"电脑",()->EditorialUI.pickOption(activity,"电脑",options.get(CONNECTORS),chosen.get(CONNECTORS),choice->pickConnector(choice)));
   cards.put(CONNECTORS,card);
  }
  void projectsCard(){
   EditorialUI.CardText card=EditorialUI.optionCard(activity,ui.body,"项目与路径",()->EditorialUI.pickOption(activity,"项目与路径",options.get(PROJECTS),chosen.get(PROJECTS),choice->{
    pickProject(choice);
   }));
   cards.put(PROJECTS,card);
  }
  boolean selectionLocked(){if(locked(app)){status.setText("存在未确认提交或存储故障，配置保持锁定。");return true;}return false;}
  void pickProject(DshDirectory.Option choice){
   if(selectionLocked())return;
   String id=choice==null?"":choice.id;if(id.equals(directory.project()))return;
   directory.project(id);if(id.isEmpty())chosen.remove(PROJECTS);else chosen.put(PROJECTS,choice);
   clearSessions();draw(PROJECTS);loadSessions();
  }
  void sessionsCard(){
   cards.put(SESSIONS,EditorialUI.optionCard(activity,ui.body,"Session",()->{
    if(selectionLocked())return;
    final long request=sessionRequest,epoch=accountEpoch,ticket=directory.generation();
    EditorialUI.pickOption(activity,"Session",options.get(SESSIONS),chosen.get(SESSIONS),choice->{
     if(selectionLocked()||request!=sessionRequest||!valid(epoch,ticket,screenEpoch)||choice==null||Policy.find(options.get(SESSIONS),choice.id)==null)return;
     chosen.put(SESSIONS,choice);draw(SESSIONS);
    });
   }));
  }
  void clearSessions(){sessionRequest++;sessionAccount=-1;chosen.remove(SESSIONS);options.put(SESSIONS,Collections.emptyList());draw(SESSIONS);}
  boolean validSessions(long epoch,long ticket,long request,String c,String p,String r){return valid(epoch,ticket,screenEpoch)&&Policy.sessionCurrent(epoch,accountEpoch,ticket,directory.generation(),request,sessionRequest,c,p,r,directory);}
  void loadSessions(){
   final String c=directory.connector(),p=directory.project(),r=directory.runtime();if(c.isEmpty()||p.isEmpty()||r.isEmpty())return;
   final long epoch=accountEpoch,ticket=directory.generation(),request=++sessionRequest;
   sessionAccount=epoch;sessionGeneration=ticket;sessionConnector=c;sessionProject=p;sessionRuntime=r;
   options.put(SESSIONS,new ArrayList<>(Collections.singletonList(DshDirectory.newSession())));
   String saved=restore.get(SESSIONS);boolean same=c.equals(restore.get(CONNECTORS))&&p.equals(restore.get(PROJECTS))&&r.equals(restore.get(RUNTIMES));
   if(same&&saved!=null&&!saved.isEmpty())chosen.put(SESSIONS,new DshDirectory.Option(saved,saved,"会话尚未核验；请等待目录刷新"));
   else if(app.getSharedPreferences("turboio_settings",0).getString("dsh_session_id","").isEmpty())chosen.put(SESSIONS,DshDirectory.newSession());
   draw(SESSIONS);
   IO.execute(()->{try{
    String token=SecretStore.get(app,"dsh_key");if(token.isEmpty())throw new IllegalStateException("not_authenticated");
    List<DshDirectory.Option> rows=DshDirectory.sessions(DshDirectory.rows(DshClient.httpTransport("GET",ORIGIN+"/api/v2/sessions/list",token,null),"sessions"),c,p,r);
    MAIN.post(()->{if(!validSessions(epoch,ticket,request,c,p,r))return;
     List<DshDirectory.Option> all=new ArrayList<>();all.add(DshDirectory.newSession());all.addAll(rows);options.put(SESSIONS,all);
     DshDirectory.Option current=chosen.get(SESSIONS),verified=current==null?null:Policy.find(all,current.id);
     if(verified!=null)chosen.put(SESSIONS,verified);
     else if(current!=null){chosen.put(SESSIONS,new DshDirectory.Option(current.id,current.label,current.id+" · 当前目录未找到；不会自动新建"));status.setText("当前会话未在此实例目录找到，请明确选择会话或新建。");}
     draw(SESSIONS);
    });
   }catch(Exception error){MAIN.post(()->{if(validSessions(epoch,ticket,request,c,p,r)){status.setText(Policy.directoryError(error));cards.get(SESSIONS).subtitle.setText(Policy.directoryError(error)+" 可明确选择新建；旧会话未被替换。");}});}});
  }
  void runtimesCard(){
   EditorialUI.CardText card=EditorialUI.optionCard(activity,ui.body,"DSH 运行时",()->EditorialUI.pickOption(activity,"DSH 运行时",options.get(RUNTIMES),chosen.get(RUNTIMES),choice->pickRuntime(choice)));
   cards.put(RUNTIMES,card);
  }
  void modelsCard(){
   EditorialUI.CardText card=EditorialUI.optionCard(activity,ui.body,"模型与推理选项",()->EditorialUI.pickOption(activity,"模型与推理选项",options.get(MODELS),chosen.get(MODELS),choice->{if(choice==null||choice.id.isEmpty())return;chosen.put(MODELS,choice);draw(MODELS);}));
   cards.put(MODELS,card);
  }
  void permissionsCard(){
   EditorialUI.CardText card=EditorialUI.optionCard(activity,ui.body,"执行权限",()->EditorialUI.pickOption(activity,"执行权限",options.get(PERMISSIONS),chosen.get(PERMISSIONS),choice->{if(choice==null||choice.id.isEmpty())return;chosen.put(PERMISSIONS,choice);draw(PERMISSIONS);}));
   cards.put(PERMISSIONS,card);
  }
  void pickConnector(DshDirectory.Option choice){
   if(selectionLocked())return;
   if(choice==null||choice.id.isEmpty()){directory.connector("");chosen.remove(CONNECTORS);clearChildren();draw(CONNECTORS);status.setText("已清空电脑选择，项目、运行时、模型与权限同步清空。");return;}
   if(choice.id.equals(directory.connector())){chosen.put(CONNECTORS,choice);draw(CONNECTORS);return;}
   long ticket=directory.connector(choice.id);
   chosen.put(CONNECTORS,choice);
   clearChildren();
   draw(CONNECTORS);
   loadChildren(choice.id,ticket);
  }
  void pickRuntime(DshDirectory.Option choice){
   if(selectionLocked())return;
   if(choice==null||choice.id.isEmpty()){directory.runtime("");chosen.remove(RUNTIMES);clearCatalog();draw(RUNTIMES);status.setText("已清空运行时选择，模型与权限同步清空。");return;}
   if(choice.id.equals(directory.runtime())){chosen.put(RUNTIMES,choice);draw(RUNTIMES);return;}
   long ticket=directory.runtime(choice.id);
   chosen.put(RUNTIMES,choice);
   clearCatalog();
   draw(RUNTIMES);
   loadCatalogs(choice.id,ticket);loadSessions();
  }
  // Empty parent clears descendants; a real parent change clears stale children before reloading.
  void clearChildren(){
   clearSessions();usableRuntimes=Collections.emptyList();
   chosen.remove(PROJECTS);chosen.remove(RUNTIMES);chosen.remove(MODELS);chosen.remove(PERMISSIONS);
   options.put(PROJECTS,Collections.emptyList());options.put(RUNTIMES,Collections.emptyList());options.put(MODELS,Collections.emptyList());options.put(PERMISSIONS,Collections.emptyList());
   draw(PROJECTS);draw(RUNTIMES);draw(MODELS);draw(PERMISSIONS);
  }
  void clearCatalog(){
   catalogAccount=-1;clearSessions();
   chosen.remove(MODELS);chosen.remove(PERMISSIONS);
   options.put(MODELS,Collections.emptyList());options.put(PERMISSIONS,Collections.emptyList());
   draw(MODELS);draw(PERMISSIONS);
  }
  void draw(String key){
   EditorialUI.CardText card=cards.get(key);if(card==null)return;
   List<DshDirectory.Option> rows=options.get(key)==null?Collections.<DshDirectory.Option>emptyList():options.get(key);
   DshDirectory.Option current=chosen.get(key);
   if(current==null||current.id.isEmpty()){card.title.setText(rows.isEmpty()?"暂无可选项":"请选择");card.subtitle.setText(rows.isEmpty()?"点击查看可用选项":""+rows.size()+" 个可选项");return;}
   card.title.setText(current.label);
   card.subtitle.setText(current.subtitle.isEmpty()?"":""+current.subtitle);
  }
  interface Fetch {void run(String token)throws Exception;}
  void fetch(Fetch work){long epoch=accountEpoch,ticket=directory.generation(),screen=screenEpoch;IO.execute(()->{try{String token=SecretStore.get(app,"dsh_key");if(token.isEmpty()){MAIN.post(()->{if(valid(epoch,ticket,screen))status.setText("尚未登录，请先网页授权登录后刷新目录。");});return;}work.run(token);}catch(Exception e){MAIN.post(()->{if(valid(epoch,ticket,screen))status.setText(Policy.directoryError(e));});}});}
  boolean valid(long epoch,long ticket,long screen){return !closed&&!activity.isFinishing()&&epoch==accountEpoch&&directory.current(ticket)&&screen==screenEpoch;}
  void login(){
   synchronized(DshClient.LOCK){if(locked(app)){status.setText("存在未确认提交，禁止更换账户或授权。");return;}}
   clearSessions();chosen.clear();restore.clear();directory.reset();
   final DshAuth.Storage store = storage(app);
   AUTH.cancel(store);long epoch=++accountEpoch;long screen=screenEpoch;
   synchronized(DshClient.LOCK){
    if(locked(app)){status.setText("存在未确认提交，禁止登录。");return;}
    String state=AUTH.begin(ORIGIN,System.currentTimeMillis(),epoch,store);
    String url=ORIGIN+"/#/mobile-oauth?response_type=code&client_id="+CLIENT+"&redirect_uri="+encode(DshAuth.REDIRECT)+"&code_challenge="+AUTH.challenge()+"&code_challenge_method=S256&state="+encode(state);
    try{
     activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
     status.setText("正在打开浏览器，请在官方页面确认授权…");
    }catch(Exception e){
     AUTH.cancel(store);
     status.setText("无法打开浏览器，授权未开始。");
    }
   }
  }
  void logout(){synchronized(DshClient.LOCK){if(locked(app)){status.setText("存在未确认提交，保留账户与任务记录；请先在网页核对。");return;}try{SharedPreferences prefs=app.getSharedPreferences("turboio_settings",0);if(!prefs.edit().putBoolean("dsh",false).commit()){storageFailed=true;throw new IllegalStateException();}try{SecretStore.put(app,"dsh_key","");}catch(Exception e){storageFailed=true;throw e;}if(!prefs.edit().putString("dsh_user_id","").putString("dsh_session_id","").putString("dsh_client_message_id","").putString("dsh_target_fingerprint","").putString("dsh_connector","").putString("dsh_project","").putString("dsh_runtime","").putString("dsh_model","").putString("dsh_permission","").commit()){storageFailed=true;throw new IllegalStateException();}accountEpoch++;AUTH.cancel(storage(app));directory.reset();for(String key:Arrays.asList(CONNECTORS,PROJECTS,RUNTIMES,SESSIONS,MODELS,PERMISSIONS)){options.put(key,Collections.<DshDirectory.Option>emptyList());chosen.remove(key);draw(key);}status.setText("已退出登录；远端任务不会因此取消。");}catch(Exception e){status.setText("退出未完整保存，工具已禁用；请重试。");}}}
  void loadConnectors(){
   SharedPreferences prefs=app.getSharedPreferences("turboio_settings",0);restore.clear();
   String[] keys={CONNECTORS,PROJECTS,RUNTIMES,SESSIONS,MODELS,PERMISSIONS},names={"connector","project","runtime","session_id","model","permission"};
   if(ORIGIN.equals(prefs.getString("dsh_url","")))for(int i=0;i<keys.length;i++)restore.put(keys[i],prefs.getString("dsh_"+names[i],""));
   if(restoreAccount==accountEpoch&&!chosen.isEmpty()){restore.clear();for(Map.Entry<String,DshDirectory.Option> entry:chosen.entrySet())restore.put(entry.getKey(),entry.getValue().id);}
   restoreAccount=accountEpoch;clearSessions();usableRuntimes=Collections.emptyList();status.setText("正在刷新电脑目录…");catalogAccount=-1;directory.reset();for(String key:Arrays.asList(CONNECTORS,PROJECTS,RUNTIMES,SESSIONS,MODELS,PERMISSIONS)){options.put(key,Collections.<DshDirectory.Option>emptyList());chosen.remove(key);draw(key);}long epoch=accountEpoch,ticket=directory.generation(),screen=screenEpoch;fetch(token->{Map<String,Object> response=DshClient.httpTransport("GET",ORIGIN+"/api/v2/connectors",token,null);List<DshDirectory.Option> values=new ArrayList<>();for(Map<String,Object> row:DshDirectory.rows(response,"connectors")){String id=DshDirectory.text(row,"id");if(!id.isEmpty())values.add(new DshDirectory.Option(id,DshDirectory.text(row,"name"),runtimeNote(row)));}MAIN.post(()->{if(valid(epoch,ticket,screen)){options.put(CONNECTORS,values);draw(CONNECTORS);status.setText(values.isEmpty()?"电脑目录已刷新，暂无电脑。":"电脑目录已刷新，请选择电脑。");DshDirectory.Option saved=Policy.find(values,restore.get(CONNECTORS));if(saved!=null)pickConnector(saved);}});});}
  // Connector subtitle reflects the connector's runtime states; empty runtimes show no available runtime.
  private String runtimeNote(Map<String,Object> row){
   Object runtimes=row.get("runtimes");
   if(!(runtimes instanceof List))return "";
   String connector=DshDirectory.text(row,"id");
   List<Map<String,Object>> runtimeRows=DshDirectory.rows(row,"runtimes");
   for(Map<String,Object> runtime:runtimeRows){String bound=DshDirectory.text(runtime,"connectorId");if(bound.isEmpty())runtime.put("connectorId",connector);}
   List<DshDirectory.Option> cards=DshDirectory.runtimeCards(runtimeRows,connector);
   if(cards.isEmpty())return "无可用运行时";
   StringBuilder note=new StringBuilder();
   for(DshDirectory.Option card:cards){if(note.length()>0)note.append("；");note.append(card.label).append(" · ").append(card.subtitle);}
   return note.toString();
  }
  boolean restoringConnector(){return directory.connector().equals(restore.get(CONNECTORS));}
  void restoreChoice(String key){DshDirectory.Option saved=Policy.find(options.get(key),restore.get(key));if(saved!=null)chosen.put(key,saved);draw(key);}
  void loadChildren(String connector,long ticket){
   long epoch=accountEpoch,screen=screenEpoch;status.setText("正在读取项目与 DSH 运行时目录…");
   fetch(token->{Map<String,Object> ps=DshClient.httpTransport("GET",ORIGIN+"/api/v2/projects",token,null);Map<String,Object> rs=DshClient.httpTransport("GET",ORIGIN+"/api/v2/connectors/"+encode(connector)+"/runtimes",token,null);List<DshDirectory.Option> p=DshDirectory.projects(DshDirectory.rows(ps,"projects"),connector);if(!connector.equals(DshDirectory.text(rs,"connectorId")))throw new IllegalArgumentException("runtime_connector_mismatch");List<Map<String,Object>> runtimeRows=DshDirectory.rows(rs,"runtimes");for(Map<String,Object> row:runtimeRows){String bound=DshDirectory.text(row,"connectorId");if(!bound.isEmpty()&&!connector.equals(bound))throw new IllegalArgumentException("runtime_connector_mismatch");row.put("connectorId",connector);}List<DshDirectory.Option> r=DshDirectory.runtimeCards(runtimeRows,connector),usable=DshDirectory.runtimes(runtimeRows,connector);MAIN.post(()->{if(valid(epoch,ticket,screen)){usableRuntimes=usable;options.put(PROJECTS,p);options.put(RUNTIMES,r);draw(PROJECTS);draw(RUNTIMES);if(restoringConnector()){restoreChoice(PROJECTS);DshDirectory.Option project=chosen.get(PROJECTS);if(project!=null)directory.project(project.id);}status.setText(usable.isEmpty()?"目录已刷新，无可用 DSH 运行时。":"目录已刷新，请选择项目与 DSH 运行时。");DshDirectory.Option runtime=Policy.runtime(usable,restoringConnector()?restore.get(RUNTIMES):null);if(runtime!=null)pickRuntime(Policy.find(r,runtime.id));}});});
  }
  void loadCatalogs(String runtime,long ticket){
   long epoch=accountEpoch,screen=screenEpoch;String connector=directory.connector();status.setText("正在读取运行时模型与权限目录…");
   fetch(token->{String path=ORIGIN+"/api/v2/connectors/"+encode(connector)+"/runtimes/"+encode(runtime)+"/catalogs/";List<DshDirectory.Option> ms=DshDirectory.catalog(DshClient.httpTransport("GET",path+"model",token,null),true);List<DshDirectory.Option> ps=DshDirectory.catalog(DshClient.httpTransport("GET",path+"permission",token,null),false);MAIN.post(()->{if(valid(epoch,ticket,screen)){options.put(MODELS,ms);options.put(PERMISSIONS,ps);if(restoringConnector()&&directory.runtime().equals(restore.get(RUNTIMES))){restoreChoice(MODELS);restoreChoice(PERMISSIONS);}draw(MODELS);draw(PERMISSIONS);catalogAccount=epoch;status.setText("目录已刷新，请确认项目、模型和执行权限后保存。");}});});
  }
  void save(){synchronized(DshClient.LOCK){
   if(locked(app)){status.setText("存在未确认提交，配置保持锁定。");return;}
   DshDirectory.Option visibleConnector=chosen.get(CONNECTORS),visibleProject=chosen.get(PROJECTS),visibleRuntime=chosen.get(RUNTIMES);
   if(visibleConnector==null||visibleProject==null||visibleRuntime==null||visibleConnector.id.isEmpty()||visibleProject.id.isEmpty()||visibleRuntime.id.isEmpty()||!directory.matches(visibleConnector.id,visibleProject.id,visibleRuntime.id)){status.setText("当前目标选择无效，请重新选择。");return;}
   if(catalogAccount!=accountEpoch||Policy.find(usableRuntimes,directory.runtime())==null){status.setText("账户或目录已变化，请重新刷新目标。");return;}
   DshDirectory.Option model=chosen.get(MODELS),permission=chosen.get(PERMISSIONS);
   if(model==null||permission==null||model.id.isEmpty()||permission.id.isEmpty()||directory.project().isEmpty()||directory.runtime().isEmpty()){status.setText("请完成电脑、项目、运行时、模型与权限选择。");return;}
   DshDirectory.Option session=chosen.get(SESSIONS);
   if(!validSessions(sessionAccount,sessionGeneration,sessionRequest,sessionConnector,sessionProject,sessionRuntime)||session==null||Policy.find(options.get(SESSIONS),session.id)==null){status.setText("请明确选择此实例的会话或新建；当前会话未核验。");return;}
   try{
   SharedPreferences prefs=app.getSharedPreferences("turboio_settings",0);
   if(prefs.getString("dsh_user_id","").isEmpty()||SecretStore.get(app,"dsh_key").isEmpty())throw new IllegalStateException();
   // Re-saving the same target keeps the session tracking so the continuation chain survives; only a changed binding drops it.
   boolean targetChanged=!DshClient.sameTarget(prefs.getString("dsh_url",""),prefs.getString("dsh_connector",""),prefs.getString("dsh_project",""),prefs.getString("dsh_runtime",""),ORIGIN,directory.connector(),directory.project(),directory.runtime())
   ||!model.id.equals(prefs.getString("dsh_model",""))||!permission.id.equals(prefs.getString("dsh_permission",""));
   SharedPreferences.Editor editor=prefs.edit().putBoolean("dsh",true).putString("dsh_url",ORIGIN).putString("dsh_connector",directory.connector()).putString("dsh_project",directory.project()).putString("dsh_runtime",directory.runtime()).putString("dsh_model",model.id).putString("dsh_permission",permission.id);
   String selected=DshDirectory.NEW_SESSION.equals(session.id)?"":DshClient.validateSessionId(session.id);
   String fingerprint=selected.isEmpty()?"":new DshClient.Config(true,ORIGIN,SecretStore.get(app,"dsh_key"),directory.connector(),directory.project(),directory.runtime(),model.id,permission.id).targetFingerprint();
   String message=Policy.savedMessage(targetChanged,selected,prefs.getString("dsh_session_id",""),fingerprint,prefs.getString("dsh_target_fingerprint",""),prefs.getString("dsh_client_message_id",""));
   if(locked(app)||!validSessions(sessionAccount,sessionGeneration,sessionRequest,sessionConnector,sessionProject,sessionRuntime))return;
   editor.putString("dsh_session_id",selected).putString("dsh_client_message_id",message).putString("dsh_target_fingerprint",fingerprint);
   if(!editor.commit()){storageFailed=true;throw new IllegalStateException();}
   String[] names={"url","connector","project","runtime","model","permission","session_id","client_message_id","target_fingerprint"};
   String[] values={ORIGIN,directory.connector(),directory.project(),directory.runtime(),model.id,permission.id,selected,message,fingerprint};
   if(!prefs.getBoolean("dsh",false)||prefs.getBoolean("dsh_unknown_delivery",false)){storageFailed=true;throw new IllegalStateException();}
   for(int i=0;i<names.length;i++)if(!values[i].equals(prefs.getString("dsh_"+names[i],""))){storageFailed=true;throw new IllegalStateException();}
   lastSessionId=selected;lastMessageId=message;lastState=selected.isEmpty()?"就绪":message.isEmpty()?"已选择会话":"就绪";lastReply="";lastNote="";syncStamp=0;refreshSyncCard();draw(SESSIONS);
   status.setText(selected.isEmpty()?"已保存并启用 DSH 工具；下次发送时新建会话。":"已保存并绑定所选会话，下次发送将在此会话继续；模型与权限选择仅用于新建，未修改已有远端会话。");
   }catch(Exception e){status.setText("保存失败，未确认启用状态。");}}}
  // ===== Task and session sync card =====
  void refreshSyncCard(){
   SharedPreferences prefs=app.getSharedPreferences("turboio_settings",0);
   boolean pending=ToolClient.pendingLocked();
   Map<String,Object> snapshot=ToolClient.pendingSnapshot();
   String snapshotSession=stringOf(snapshot,"sessionId"),snapshotMessage=stringOf(snapshot,"clientMessageId");
   String sessionId=lastSessionId.isEmpty()?snapshotSession:lastSessionId;
   String messageId=lastMessageId.isEmpty()?snapshotMessage:lastMessageId;
   if(sessionId.isEmpty())sessionId=prefs.getString("dsh_session_id","");
   if(messageId.isEmpty())messageId=prefs.getString("dsh_client_message_id","");
   syncState.setText("状态："+(pending?"未确认提交（锁定）":(lastState.isEmpty()?"就绪":lastState)));
   syncSession.setText(sessionDisplay(sessionId,messageId));
   syncReply.setText(lastReply.isEmpty()?"最新回复：暂无":"最新回复："+DshClient.truncate(lastReply,300));
   String note=lastNote.isEmpty()?stringOf(snapshot,"message"):lastNote;
   syncNote.setText(note.isEmpty()?"":"提示："+note);
   syncTime.setText("最近同步："+(syncStamp==0?"尚未同步":new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT).format(new java.util.Date(syncStamp))));
   // The end-session entry stays disabled while a submission is unconfirmed or storage is locked; tracking is kept conservatively.
   boolean lockedState=locked(app);
   if(endSessionButton!=null){endSessionButton.setEnabled(!lockedState);endSessionButton.setAlpha(lockedState?0.4f:1.0f);}
   if(lockedState)syncNote.setText("提示：存在未确认提交，保留会话跟踪；结束会话已禁用。"+(note.isEmpty()?"":"\n"+note));
  }
  private long syncStamp;
  // ===== Voice alias routing: one alias -> its own verified connector+runtime+project+model+permission =====
  private TextView aliasTitle,aliasNote;
  /** Independent picks of the alias being edited: the global dsh_* binding is never read or written here. */
  private final AliasTarget aliasDraft=new AliasTarget();
  /** Saved ids of the alias being edited; a seeded id only counts once the live directory offers it again. */
  private final Map<String,String> aliasRestore=new LinkedHashMap<>();
  private final Map<String,EditorialUI.CardText> aliasCards=new LinkedHashMap<>();
  private TextView aliasEditorNote;
  /** Alias key of the entry whose dialog is open; empty for a new alias. It only gates renames. */
  private String aliasDraftKey="";
  /** One editor session per opened dialog; every async directory row is bound to it. */
  private long aliasRequest;
  private static final String A_CONNECTORS="alias_connectors",A_PROJECTS="alias_projects",A_RUNTIMES="alias_runtimes",A_MODELS="alias_models",A_PERMISSIONS="alias_permissions";
  /** The saved service address, accepted only when it is the AA origin whose directory the editor verifies against. */
  String aliasBaseUrl(){
   String url=app.getSharedPreferences("turboio_settings",0).getString("dsh_url","");
   return ORIGIN.equals(url)?url:"";
  }
  void voiceAliasCard(){
   LinearLayout card=EditorialUI.card(activity,ui.body);
   card.addView(EditorialUI.text(activity,"语音别名（仅本机映射）",15,EditorialUI.INK));
   aliasTitle=EditorialUI.text(activity,"",13,EditorialUI.MUTED);aliasNote=EditorialUI.text(activity,"",12,EditorialUI.MUTED);
   card.addView(aliasTitle);card.addView(aliasNote);
   EditorialUI.button(activity,card,"新建语音别名",false,()->openVoiceAliasEditor(""));
   EditorialUI.button(activity,card,"编辑已有别名",false,this::editExistingVoiceAlias);
   EditorialUI.button(activity,card,"清空某个别名",false,this::clearVoiceAlias);
   card.addView(EditorialUI.text(activity,"每个别名自己选择电脑、项目、DSH 运行时、模型与执行权限，保存前逐项对该账户的在线目录重新核验；任一项未核验即拒绝保存，不会借用已保存绑定，也不会改动已保存绑定。别名不改动 AA 服务端配置：本机把口语名称精确解析为该别名指向的运行时实例，配置两个及以上别名时 dsh_dispatch 必须携带 target，缺失即本地拒绝。",12,EditorialUI.MUTED));
   refreshVoiceAlias();
  }
  void refreshVoiceAlias(){
   if(closed||aliasTitle==null)return;
   DshAliasRegistry table=voiceAliasTable();
   if(table==null){aliasTitle.setText("别名不可用：账户或服务地址未核验。");aliasNote.setText("别名只在已登录且服务地址核验后可用；切换账户不会复用旧映射。");return;}
   List<DshAliasRegistry.AliasEntry> entries=table.listSnapshot();
   aliasTitle.setText(entries.isEmpty()?"当前没有语音别名；未点名时沿用已保存绑定。":"已配置 "+entries.size()+" 个别名："+join(table.aliasValuesSnapshot()));
   // Each alias keeps its own target: show it so a per-alias pick can never be mistaken for the saved binding.
   StringBuilder detail=new StringBuilder();
   for(DshAliasRegistry.AliasEntry entry:entries){
    if(detail.length()>0)detail.append("\n");
    detail.append(entry.alias).append("：电脑 ").append(entry.connectorId).append(" · 项目 ").append(entry.projectId)
     .append(" · 运行时 ").append(entry.runtimeId).append(" · 模型 ").append(DshClient.truncate(entry.model,40))
     .append(" · 权限 ").append(entry.permission);
   }
   String current=app.getSharedPreferences("turboio_settings",0).getString(ToolClient.ALIAS_COLUMN,"");
   aliasNote.setText((current.isEmpty()?"当前语音目标：未标记。":"当前语音目标："+current+"（模型口径不作归属证据）。")
    +(detail.length()==0?"":"\n"+detail));
  }
  static String join(List<String> values){StringBuilder out=new StringBuilder();for(String value:values){if(out.length()>0)out.append("、");out.append(value);}return out.toString();}
  /** Alias table of the currently authenticated account; foreign or damaged documents fail closed. */
  DshAliasRegistry voiceAliasTable(){
   SharedPreferences settings=app.getSharedPreferences("turboio_settings",0);
   return voiceAliasTableFor(settings.getString("dsh_user_id",""),settings.getString("dsh_url",""),
    app.getSharedPreferences(ToolClient.ALIAS_FILE,0).getString(ToolClient.ALIAS_DOC,""));
  }
  static DshAliasRegistry voiceAliasTableFor(String user,String url,String document){
   if(user==null||user.isEmpty()||DshClient.normalizeBaseUrl(url)==null)return null;
   if(document==null||document.isEmpty())return new DshAliasRegistry(user,url);
   try{return DshAliasRegistry.deserializeSnapshot(document,user,url);}catch(RuntimeException damaged){return null;}
  }
  /** Lists the configured aliases so the user can pick which independent target to edit. */
  void editExistingVoiceAlias(){
   if(selectionLocked())return;
   DshAliasRegistry table=voiceAliasTable();
   if(table==null){status.setText("账户或服务地址未核验，别名未保存。");return;}
   List<DshAliasRegistry.AliasEntry> entries=table.listSnapshot();
   if(entries.isEmpty()){status.setText("当前没有语音别名；请先新建语音别名。");return;}
   String[] names=new String[entries.size()];
   for(int i=0;i<entries.size();i++)names[i]=entries.get(i).alias+"\n"+aliasDetail(entries.get(i));
   new android.app.AlertDialog.Builder(activity).setTitle("选择要编辑的别名")
    .setItems(names,(d,w)->openVoiceAliasEditor(entries.get(w).aliasKey)).setNegativeButton("取消",null).show();
  }
  void clearVoiceAlias(){
   if(selectionLocked())return;
   DshAliasRegistry table=voiceAliasTable();
   if(table==null){status.setText("账户或服务地址未核验，别名未改动。");return;}
   List<DshAliasRegistry.AliasEntry> entries=table.listSnapshot();
   if(entries.isEmpty()){status.setText("当前没有语音别名可清空。");return;}
   String[] names=new String[entries.size()];
   for(int i=0;i<entries.size();i++)names[i]=entries.get(i).alias+"\n"+aliasDetail(entries.get(i));
   new android.app.AlertDialog.Builder(activity).setTitle("选择要清空的别名")
    .setItems(names,(d,w)->{
     final DshAliasRegistry.AliasEntry chosen=entries.get(w);
     EditorialUI.confirm(activity,"清空该别名","只移除本机对该别名及其运行时实例的映射；本地会话跟踪与远端会话都不会因此改动，也不会影响其他别名。",()->deleteVoiceAlias(chosen.aliasKey));
    }).setNegativeButton("取消",null).show();
  }
  /** The per-alias target as stored: shown so an independent pick is never mistaken for the saved binding. */
  static String aliasDetail(DshAliasRegistry.AliasEntry entry){
   return "运行时 "+entry.runtimeId+" · 项目 "+entry.projectId+" · 权限 "+entry.permission;
  }
  /** Opens the per-alias target editor. A stored entry only seeds the pickers; nothing is reused at save time. */
  void openVoiceAliasEditor(String aliasKey){
   if(selectionLocked())return;
   DshAliasRegistry table=voiceAliasTable();
   if(table==null){status.setText("账户或服务地址未核验，别名未保存。");return;}
   // Every row this editor can verify comes from the ORIGIN directory, so only that address may be stamped.
   if(aliasBaseUrl().isEmpty()){status.setText("服务地址与 AA 目录不一致，别名编辑器未打开。");return;}
   SharedPreferences prefs=app.getSharedPreferences("turboio_settings",0);
   if(prefs.getString("dsh_user_id","").isEmpty()){status.setText("尚未登录，别名编辑器未打开。");return;}
   try{if(SecretStore.get(app,"dsh_key").isEmpty()){status.setText("尚未登录，别名编辑器未打开。");return;}}catch(Exception unavailable){status.setText("凭据不可用，别名编辑器未打开。");return;}
   DshAliasRegistry.AliasEntry existing=aliasKey.isEmpty()?null:table.getSnapshot(aliasKey);
   if(!aliasKey.isEmpty()&&existing==null){status.setText("该别名已不存在，未打开编辑器。");refreshVoiceAlias();return;}
   // A fresh draft per dialog: the stored target becomes a seed, and every level must verify against the live directory again.
   aliasRequest++;aliasDraftKey=aliasKey;aliasDraft.clear();aliasRestore.clear();aliasCards.clear();
   if(existing!=null){
    aliasRestore.put(A_CONNECTORS,existing.connectorId);aliasRestore.put(A_PROJECTS,existing.projectId);
    aliasRestore.put(A_RUNTIMES,existing.runtimeId);aliasRestore.put(A_MODELS,existing.model);aliasRestore.put(A_PERMISSIONS,existing.permission);
   }
   LinearLayout box=EditorialUI.column(activity);
   box.setPadding(EditorialUI.dp(activity,20),EditorialUI.dp(activity,12),EditorialUI.dp(activity,20),EditorialUI.dp(activity,4));
   box.addView(EditorialUI.text(activity,"别名（口语名称）",13,EditorialUI.MUTED));
   final EditText input=new EditText(activity);
   input.setSingleLine();
   input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
   input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(DshAliasRegistry.MAX_ALIAS_CODE_POINTS)});
   input.setHint("例如：办公室电脑");
   if(existing!=null)input.setText(existing.alias);
   input.setSelection(input.getText().length());
   box.addView(input);
   box.addView(EditorialUI.text(activity,"本别名的目标（与已保存绑定互不影响）",13,EditorialUI.MUTED));
   aliasOptionCard(box,A_CONNECTORS,"电脑");
   aliasOptionCard(box,A_PROJECTS,"项目");
   aliasOptionCard(box,A_RUNTIMES,"DSH 运行时");
   aliasOptionCard(box,A_MODELS,"模型与推理选项");
   aliasOptionCard(box,A_PERMISSIONS,"执行权限");
   aliasEditorNote=EditorialUI.text(activity,"正在读取该账户的电脑目录…",12,EditorialUI.MUTED);
   box.addView(aliasEditorNote);
   EditorialUI.button(activity,box,"重新读取目录",false,()->loadAliasDirectory());
   box.addView(EditorialUI.text(activity,"同一账户内别名唯一，一个运行时实例最多一个别名；保存只写入本别名，不改动已保存绑定，也不改动 AA 服务端配置。",12,EditorialUI.MUTED));
   ScrollView scroll=new ScrollView(activity);
   scroll.addView(box,new LinearLayout.LayoutParams(-1,-2));
   final String editingKey=aliasKey;
   android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(activity);
   builder.setTitle(editingKey.isEmpty()?"新建语音别名":"编辑语音别名").setView(scroll)
    .setOnDismissListener(d->closeVoiceAliasEditor())
    .setPositiveButton("保存",null)
    .setNegativeButton("取消",null);
   if(!editingKey.isEmpty())builder.setNeutralButton("清空该别名",(d,w)->deleteVoiceAlias(editingKey));
   final android.app.AlertDialog dialog=builder.show();
   // Own the 保存 click so a refused save keeps the dialog open and the draft intact (fail-closed, retry in place).
   dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{if(saveVoiceAlias(input.getText().toString()))dialog.dismiss();});
   loadAliasDirectory();
  }
  /** The draft and its card handles only belong to the open dialog; dropping them also drops in-flight rows. */
  void closeVoiceAliasEditor(){
   aliasRequest++;aliasDraftKey="";aliasDraft.clear();aliasRestore.clear();aliasCards.clear();aliasEditorNote=null;
  }
  void aliasOptionCard(LinearLayout parent,String key,String title){
   aliasCards.put(key,EditorialUI.optionCard(activity,parent,title,()->pickAliasOption(key)));
  }
  List<DshDirectory.Option> aliasRows(String key){
   if(A_CONNECTORS.equals(key))return aliasDraft.connectors;
   if(A_PROJECTS.equals(key))return aliasDraft.projects;
   if(A_RUNTIMES.equals(key))return aliasDraft.runtimes;
   if(A_MODELS.equals(key))return aliasDraft.models;
   return aliasDraft.permissions;
  }
  String aliasIdOf(String key){
   if(A_CONNECTORS.equals(key))return aliasDraft.connector;
   if(A_PROJECTS.equals(key))return aliasDraft.project;
   if(A_RUNTIMES.equals(key))return aliasDraft.runtime;
   if(A_MODELS.equals(key))return aliasDraft.model;
   return aliasDraft.permission;
  }
  void aliasSetId(String key,String id){
   if(A_CONNECTORS.equals(key))aliasDraft.connector=id;
   else if(A_PROJECTS.equals(key))aliasDraft.project=id;
   else if(A_RUNTIMES.equals(key))aliasDraft.runtime=id;
   else if(A_MODELS.equals(key))aliasDraft.model=id;
   else aliasDraft.permission=id;
   if(id==null||id.isEmpty())aliasRestore.remove(key);else aliasRestore.put(key,id);
  }
  static String aliasLevelTitle(String key){
   if(A_CONNECTORS.equals(key))return "电脑";
   if(A_PROJECTS.equals(key))return "项目";
   if(A_RUNTIMES.equals(key))return "DSH 运行时";
   if(A_MODELS.equals(key))return "模型与推理选项";
   return "执行权限";
  }
  /** A seed counts only when the live rows still contain it; otherwise the level stays unselected. */
  DshDirectory.Option aliasSeed(String key,List<DshDirectory.Option> rows){
   DshDirectory.Option found=Policy.find(rows,aliasRestore.get(key));
   if(found==null)return null;
   aliasSetId(key,found.id);
   return found;
  }
  void setAliasNote(String text){if(aliasEditorNote!=null)aliasEditorNote.setText(text);}
  void drawAlias(String key){
   EditorialUI.CardText card=aliasCards.get(key);if(card==null)return;
   List<DshDirectory.Option> rows=aliasRows(key);
   DshDirectory.Option current=Policy.find(rows,aliasIdOf(key));
   if(current==null){card.title.setText(rows.isEmpty()?"暂无可选项":"请选择");card.subtitle.setText(rows.isEmpty()?"点击查看可用选项":""+rows.size()+" 个可选项");return;}
   card.title.setText(current.label);
   card.subtitle.setText((current.subtitle.isEmpty()?"":current.subtitle+" · ")+"本别名已选");
  }
  void drawAllAlias(){for(String key:Arrays.asList(A_CONNECTORS,A_PROJECTS,A_RUNTIMES,A_MODELS,A_PERMISSIONS))drawAlias(key);}
  /** Mirrors the save gate into the dialog so the user sees what is still missing before pressing 保存. */
  void drawAliasVerification(){
   String reason=aliasDraft.refusal(accountEpoch,directory.generation(),aliasBaseUrl());
   if(AliasTarget.OK.equals(reason)){setAliasNote("本别名的电脑、项目、运行时、模型与权限均已在该账户在线目录中核验，可保存。");return;}
   setAliasNote("尚不能保存："+AliasTarget.message(reason)+"。任一项未核验都不会保存，也不会借用已保存绑定的取值。");
  }
  void pickAliasOption(String key){
   if(selectionLocked())return;
   final long epoch=accountEpoch;
   EditorialUI.pickOption(activity,aliasLevelTitle(key),aliasRows(key),Policy.find(aliasRows(key),aliasIdOf(key)),choice->{
    if(closed||epoch!=accountEpoch)return;
    if(A_CONNECTORS.equals(key))pickAliasConnector(choice);
    else if(A_RUNTIMES.equals(key))pickAliasRuntime(choice);
    else if(A_MODELS.equals(key)||A_PERMISSIONS.equals(key)){if(choice==null||choice.id.isEmpty())return;aliasSetId(key,choice.id);drawAlias(key);drawAliasVerification();}
    else{aliasSetId(key,choice==null?"":choice.id);drawAlias(key);drawAliasVerification();}
   });
  }
  void pickAliasConnector(DshDirectory.Option choice){
   String picked=choice==null?"":choice.id;
   // Only a real computer change drops the descendant seeds; re-picking the same one keeps this alias's target.
   if(!picked.equals(aliasDraft.connector)){aliasRestore.remove(A_PROJECTS);aliasRestore.remove(A_RUNTIMES);dropAliasCatalogSeed();}
   aliasSetId(A_CONNECTORS,picked);
   aliasDraft.dropProjectAndRuntimes();
   drawAllAlias();
   if(choice==null||choice.id.isEmpty()){setAliasNote("已清空本别名的电脑选择，项目、运行时、模型与权限同步清空。");return;}
   loadAliasChildren(choice.id);
  }
  void pickAliasRuntime(DshDirectory.Option choice){
   String picked=choice==null?"":choice.id;
   if(!picked.equals(aliasDraft.runtime))dropAliasCatalogSeed();
   aliasSetId(A_RUNTIMES,picked);
   aliasDraft.dropCatalog();
   drawAllAlias();
   if(choice==null||choice.id.isEmpty()){setAliasNote("已清空本别名的运行时选择，模型与权限同步清空。");return;}
   loadAliasCatalog(choice.id);
  }
  /** A parent change must not re-seed descendants from the previous target of this alias. */
  void dropAliasCatalogSeed(){aliasRestore.remove(A_MODELS);aliasRestore.remove(A_PERMISSIONS);}
  void loadAliasDirectory(){
   if(selectionLocked())return;
   long epoch=accountEpoch,ticket=directory.generation(),screen=screenEpoch,request=++aliasRequest;
   aliasDraft.loadedAccount=-1;aliasDraft.loadedTicket=-1;aliasDraft.connectors=Collections.<DshDirectory.Option>emptyList();
   setAliasNote("正在读取该账户的电脑目录…");
   aliasFetch(epoch,ticket,screen,request,token->{
    Map<String,Object> response=DshClient.httpTransport("GET",ORIGIN+"/api/v2/connectors",token,null);
    List<DshDirectory.Option> values=new ArrayList<>();
    for(Map<String,Object> row:DshDirectory.rows(response,"connectors")){String id=DshDirectory.text(row,"id");if(!id.isEmpty())values.add(new DshDirectory.Option(id,DshDirectory.text(row,"name"),runtimeNote(row)));}
    MAIN.post(()->{
     if(!valid(epoch,ticket,screen)||request!=aliasRequest)return;
    aliasDraft.connectors=values;drawAlias(A_CONNECTORS);
    // An empty live listing still counts as fresh evidence: the level simply has nothing verifiable to pick.
    aliasDraft.loadedAccount=epoch;aliasDraft.loadedTicket=ticket;
    if(values.isEmpty()){setAliasNote("该账户当前没有电脑可选，别名无法保存。");return;}
    DshDirectory.Option choice=aliasSeed(A_CONNECTORS,values);
     if(choice==null){setAliasNote("请为本别名选择电脑；项目、运行时、模型与权限会在选择后读取。");drawAliasVerification();return;}
     loadAliasChildren(choice.id);
    });
   });
  }
  /** Children come from the drafted connector only: the same account-scoped evidence the UI save() gate uses. */
  void loadAliasChildren(String connector){
   long epoch=accountEpoch,ticket=directory.generation(),screen=screenEpoch,request=++aliasRequest;
   setAliasNote("正在读取本别名目标的项目与 DSH 运行时目录…");
   aliasFetch(epoch,ticket,screen,request,token->{
    Map<String,Object> ps=DshClient.httpTransport("GET",ORIGIN+"/api/v2/projects",token,null);
    Map<String,Object> rs=DshClient.httpTransport("GET",ORIGIN+"/api/v2/connectors/"+encode(connector)+"/runtimes",token,null);
     if(!connector.equals(DshDirectory.text(rs,"connectorId")))throw new IllegalArgumentException("runtime_connector_mismatch");
     List<DshDirectory.Option> p=DshDirectory.projects(DshDirectory.rows(ps,"projects"),connector);
    List<Map<String,Object>> runtimeRows=DshDirectory.rows(rs,"runtimes");
    for(Map<String,Object> row:runtimeRows){String bound=DshDirectory.text(row,"connectorId");if(!bound.isEmpty()&&!connector.equals(bound))throw new IllegalArgumentException("runtime_connector_mismatch");row.put("connectorId",connector);}
    final List<DshDirectory.Option> cards=DshDirectory.runtimeCards(runtimeRows,connector),usable=DshDirectory.runtimes(runtimeRows,connector);
    MAIN.post(()->{
     if(!valid(epoch,ticket,screen)||request!=aliasRequest)return;
     aliasDraft.projects=p;aliasDraft.runtimes=cards;aliasDraft.usable=usable;
     aliasDraft.loadedAccount=epoch;aliasDraft.loadedTicket=ticket;aliasDraft.dropCatalog();
     aliasSeed(A_PROJECTS,p);
     DshDirectory.Option runtime=aliasSeed(A_RUNTIMES,cards);
     drawAlias(A_PROJECTS);drawAlias(A_RUNTIMES);
     if(runtime==null){setAliasNote("请为本别名选择 DSH 运行时；只有可用的 dsh 运行时可保存。模型与权限会在选择后读取。");drawAliasVerification();return;}
     loadAliasCatalog(runtime.id);
   });});
  }
  /** Catalog rows belong to the drafted runtime instance; a mismatched or missing selection is refused. */
  void loadAliasCatalog(String runtime){
   String connector=aliasDraft.connector;
   long epoch=accountEpoch,ticket=directory.generation(),screen=screenEpoch,request=++aliasRequest;
   setAliasNote("正在读取本别名运行时的模型与执行权限目录…");
   aliasFetch(epoch,ticket,screen,request,token->{
    String path=ORIGIN+"/api/v2/connectors/"+encode(connector)+"/runtimes/"+encode(runtime)+"/catalogs/";
    final List<DshDirectory.Option> ms=DshDirectory.catalog(DshClient.httpTransport("GET",path+"model",token,null),true);
    final List<DshDirectory.Option> ps=DshDirectory.catalog(DshClient.httpTransport("GET",path+"permission",token,null),false);
    MAIN.post(()->{
     if(!valid(epoch,ticket,screen)||request!=aliasRequest)return;
     aliasDraft.models=ms;aliasDraft.permissions=ps;
     aliasDraft.catalogAccount=epoch;aliasDraft.catalogTicket=ticket;aliasDraft.catalogRuntime=runtime;
     aliasSeed(A_MODELS,ms);aliasSeed(A_PERMISSIONS,ps);
     drawAlias(A_MODELS);drawAlias(A_PERMISSIONS);drawAliasVerification();
    });
   });
  }
  /** Same authenticated runner as the rest of the screen; a directory failure also shows inside the dialog. */
  void aliasFetch(long epoch,long ticket,long screen,long request,Fetch work){
   fetch(token->{try{work.run(token);}catch(Exception failure){final String message=Policy.directoryError(failure);MAIN.post(()->{if(valid(epoch,ticket,screen)&&request==aliasRequest)setAliasNote(message+" 未核验的目标不会被保存。");});throw failure;}});
  }
  /** Explicit commit: the table changes only here, and only while every level of THIS alias is still verified. */
  boolean saveVoiceAlias(String alias){
   if(closed||activity.isFinishing())return false;
   if(selectionLocked())return false;
   DshAliasRegistry table=voiceAliasTable();
   if(table==null){status.setText("账户或服务地址已变化，别名未保存。");return false;}
   String baseUrl=aliasBaseUrl();
   String reason=aliasDraft.refusal(accountEpoch,directory.generation(),baseUrl);
   if(!AliasTarget.OK.equals(reason)){status.setText("别名未保存："+AliasTarget.message(reason)+"。电脑、项目、运行时、模型与权限必须全部核验。");drawAllAlias();drawAliasVerification();return false;}
   List<String> picked=aliasDraft.ids();
   String owner=AliasTarget.ownerConflict(table,alias,picked.get(2),aliasDraftKey);
   if(!owner.isEmpty()){status.setText("别名未保存：该运行时实例已指向别名「"+owner+"」，请先清空该别名；不会静默顶替其他别名。");return false;}
   try{table.upsertSnapshot(alias,baseUrl,picked.get(0),picked.get(2),picked.get(1),picked.get(3),picked.get(4));}
   catch(RuntimeException invalid){status.setText("别名未保存："+aliasError(invalid));refreshVoiceAlias();return false;}
   if(!persistVoiceAliasTable(table)){status.setText("别名保存失败，原有别名配置保持有效。");return false;}
   status.setText("已保存该别名自己选择的电脑、项目、运行时、模型与权限；已保存绑定未被改动，只有点名该别名时才路由到该运行时实例。");
   refreshVoiceAlias();
   return true;
  }
  void deleteVoiceAlias(String aliasKey){
   if(selectionLocked())return;
   DshAliasRegistry table=voiceAliasTable();
   if(table==null){status.setText("账户或服务地址已变化，别名未改动。");return;}
   DshAliasRegistry.AliasEntry existing=table.getSnapshot(aliasKey);
   if(existing==null){status.setText("该别名已不存在。");refreshVoiceAlias();return;}
   try{table.deleteSnapshot(existing.aliasKey);}catch(RuntimeException invalid){status.setText("别名未改动。");return;}
   if(!persistVoiceAliasTable(table)){status.setText("别名清空失败，原有别名配置保持有效。");return;}
   // The audit label may stay when a submission is unconfirmed; the alias mapping itself is already gone.
   if(clearVoiceAliasLabel(existing.runtimeId))status.setText("存在未确认提交，别名映射已移除但会话跟踪标签保留；请先在网页核对。");
   else status.setText("已清空该运行时实例的语音别名；本地会话跟踪与远端会话均未改动。");
   refreshVoiceAlias();
  }
  /** One durable write of the whole table; a failure keeps the previous document and never locks DSH dispatch. */
  boolean persistVoiceAliasTable(DshAliasRegistry table){
   if(table==null||!table.usable())return false;
   String document=DshAliasRegistry.serializeSnapshot(table);
   SharedPreferences aliases=app.getSharedPreferences(ToolClient.ALIAS_FILE,0);
   synchronized(DshClient.LOCK){
    if(locked(app))return false;
    if(!aliases.edit().putString(ToolClient.ALIAS_DOC,document).commit())return false;
   }
   return document.equals(aliases.getString(ToolClient.ALIAS_DOC,""));
  }
  /** Clears the audit label when the deleted alias owns the current binding. Locked state keeps everything. */
  boolean clearVoiceAliasLabel(String runtimeId){
   SharedPreferences prefs=app.getSharedPreferences("turboio_settings",0);
   if(!runtimeId.equals(prefs.getString("dsh_runtime",""))||prefs.getString(ToolClient.ALIAS_COLUMN,"").isEmpty())return false;
   synchronized(DshClient.LOCK){
    if(locked(app))return true;
    return !prefs.edit().putString(ToolClient.ALIAS_COLUMN,"").commit();
   }
  }
  static String aliasError(RuntimeException invalid){
   String code=invalid.getMessage()==null?"ALIAS_INVALID":invalid.getMessage();
   if("ALIAS_EMPTY".equals(code))return "别名不能为空";
   if("ALIAS_CONTROL".equals(code))return "别名含控制或不可见字符";
   if("ALIAS_LENGTH".equals(code))return "别名长度需为 1-32 个码点";
   if("ALIAS_NOT_NORMALIZED".equals(code))return "别名规范化后为空或等价";
   if("ALIAS_TAKEN".equals(code))return "该别名已指向其他运行时实例，请先清空旧别名";
   if("ALIAS_LIMIT".equals(code))return "别名数量已达上限";
   if("IDENTITY_REQUIRED".equals(code)||"SELECTION_REQUIRED".equals(code))return "目标身份或模型权限快照不完整";
   if("FOREIGN_TARGET".equals(code))return "别名不属于当前账户或服务地址";
   return "别名格式不被接受";
  }
  private String lastReply="";
  private String lastState="",lastNote="";
  private String lastSessionId="",lastMessageId="";
  static String stringOf(Map<String,Object> snapshot,String key){if(snapshot==null)return "";Object value=snapshot.get(key);return value instanceof String?(String)value:"";}
  static String sessionDisplay(String sessionId,String messageId){
   boolean noSession=sessionId==null||sessionId.isEmpty(),noMessage=messageId==null||messageId.isEmpty();
   if(noSession&&noMessage)return "会话：暂无";
   return "会话："+abbreviate(sessionId)+" · 消息 "+abbreviate(messageId);
  }
  static String abbreviate(String value){
   if(value==null)return "";
   return value.length()<=20?value:value.substring(0,20)+"…";
  }
  void refreshTask(){
   if(!ToolClient.pendingLocked()&&!app.getSharedPreferences("turboio_settings",0).getBoolean("dsh_unknown_delivery",false)&&app.getSharedPreferences("turboio_settings",0).getString("dsh_session_id","").isEmpty()){lastState="无当前跟踪会话";lastNote="";lastReply="";lastSessionId="";lastMessageId="";status.setText(lastState);refreshSyncCard();return;}
   if(!ToolClient.pendingLocked()&&!dshReady()){status.setText("尚未启用 DSH 工具，请先完成选择并保存。");return;}
   status.setText("正在查询电脑端 DSH 任务状态…");
   long epoch=accountEpoch,screen=screenEpoch;
   IO.execute(()->{
    try{
     // dsh_status takes no arguments; modelSecret is only used by other tools to filter secrets, empty is the documented calling convention.
     Map<String,Object> out=DshClient.parseJsonObject(new ToolClient(app).call("dsh_status",new org.json.JSONObject(),modelSecret()).toString());
     MAIN.post(()->{if(!closed&&epoch==accountEpoch&&screen==screenEpoch)applyStatus(out);});
    }catch(Exception e){MAIN.post(()->{if(!closed&&epoch==accountEpoch&&screen==screenEpoch){syncStamp=System.currentTimeMillis();lastState="同步失败（查询异常）";lastNote="dsh_status 查询失败，请检查网络或电脑端 DSH。";status.setText(lastState+"："+lastNote);refreshSyncCard();}});}
   });
  }
  private boolean dshReady(){
   try{return new DshClient.Config(app.getSharedPreferences("turboio_settings",0).getBoolean("dsh",false),app.getSharedPreferences("turboio_settings",0).getString("dsh_url",""),SecretStore.get(app,"dsh_key"),app.getSharedPreferences("turboio_settings",0).getString("dsh_connector",""),app.getSharedPreferences("turboio_settings",0).getString("dsh_project",""),app.getSharedPreferences("turboio_settings",0).getString("dsh_runtime",""),app.getSharedPreferences("turboio_settings",0).getString("dsh_model",""),app.getSharedPreferences("turboio_settings",0).getString("dsh_permission","")).ready();}catch(Exception e){return false;}
  }
  private String modelSecret(){
   try{return SecretStore.get(app,"dsh_key");}catch(Exception e){return "";}
  }
  void applyStatus(Map<String,Object> out){
   lastNote=stringOf(out,"message");
   syncStamp=System.currentTimeMillis();
   String state=stringOf(out,"status");
   lastReply=stringOf(out,"answer");
   if("submitted".equals(state)||"running".equals(state))lastState="正在执行";
   else if("waiting_approval".equals(state))lastState="等待权限审批";
   else if("session_selected".equals(state))lastState="已选择会话，尚无本机发送的本轮消息";
   else if("completed".equals(state))lastState="已完成";
   else if("failed".equals(state))lastState="失败";
   else if("unknown_delivery".equals(state))lastState="未确认提交（锁定）";
   else lastState=state.isEmpty()?"未知":state;
   syncState.setText(ToolClient.pendingLocked()&&"unknown_delivery".equals(state)?"状态：未确认提交（锁定）":"状态："+lastState);
   String sessionId=stringOf(out,"sessionId");
   if(!sessionId.isEmpty())lastSessionId=sessionId;
   syncSession.setText(sessionDisplay(lastSessionId.isEmpty()?"":lastSessionId,lastMessageId));
   syncReply.setText(lastReply.isEmpty()?"最新回复：暂无":"最新回复："+DshClient.truncate(lastReply,300));
   status.setText("任务状态："+lastState+(lastNote.isEmpty()?"":"；"+lastNote));
   refreshSyncCard();
  }
  // Disposal entry: reconcile only updates the card when evidence matches this submission.
  void queryAndReconcile(){
   Map<String,Object> snapshot=ToolClient.pendingSnapshot();
   if(!ToolClient.pendingLocked()){status.setText("当前没有未确认提交。");return;}
   status.setText("正在核对未确认提交…");
   long epoch=accountEpoch,screen=screenEpoch;
   IO.execute(()->{
    try{
     Map<String,Object> out=DshClient.parseJsonObject(new ToolClient(app).call("dsh_status",new org.json.JSONObject(),modelSecret()).toString());
     MAIN.post(()->{if(!closed&&epoch==accountEpoch&&screen==screenEpoch)reconcile(out,snapshot);});
    }catch(Exception e){MAIN.post(()->{if(!closed&&epoch==accountEpoch&&screen==screenEpoch){syncState.setText("状态：未确认提交（锁定）");syncNote.setText("核对查询失败，锁定保持；请稍后重试或在网页核对。");status.setText("核对未完成：查询异常，锁定保持。");}});}
   });
  }
  void reconcile(Map<String,Object> out,Map<String,Object> snapshot){
   syncStamp=System.currentTimeMillis();
   String state=stringOf(out,"status");
   // Only matching evidence may touch the card's session line; absence never unlocks.
   boolean matched="submitted".equals(state)||"running".equals(state)||"waiting_approval".equals(state)||"completed".equals(state)||"failed".equals(state)||"idle_no_reply".equals(state);
   if(matched){
    applyStatus(out);
    status.setText("已核对到本次提交的状态记录；锁定解除需以明确证据为准。");
    if(ToolClient.pendingLocked())syncState.setText("状态：未确认提交（锁定）");
   }else{
    syncState.setText("状态：未确认提交（锁定）");
    syncNote.setText("未能确认本次提交的远端执行情况；保持锁定，请在 Agents-Anywhere 网页核对。");
    status.setText("核对未确认：远端状态与本次提交无法匹配，锁定保持。");
   }
  }
  void forceRelease(){
   if(!ToolClient.pendingLocked()){status.setText("当前没有未确认提交。");return;}
   EditorialUI.confirm(activity,"强制放弃本地追踪","这只会解除本机对本次提交的追踪并恢复派发能力，远端可能已经执行并继续运行，不会因此被取消。",()->{
    try{
     Map<String,Object> out=ToolClient.releasePendingTracking(true);
     boolean failed=!"released".equals(stringOf(out,"status"));
     if(!failed){syncStamp=System.currentTimeMillis();lastReply="";status.setText("已解除本机追踪；远端任务不受影响，不会自动重发。");refreshSyncCard();}
     else{syncNote.setText("存储故障，无法解除锁定。");status.setText("存储故障，无法解除锁定。");}
    }catch(Exception e){syncNote.setText("存储故障，无法解除锁定。");status.setText("存储故障，无法解除锁定。");}
   });
  }
  // Explicit local-only session end: clears tracking so the next dispatch creates a new session. Locked or unconfirmed state refuses.
  void endSession(){
   if(ToolClient.dshLocked()||app.getSharedPreferences("turboio_settings",0).getBoolean("dsh_unknown_delivery",false)){status.setText("存在未确认提交，保留会话跟踪；请先在网页核对或强制放弃本地追踪。");refreshSyncCard();return;}
   try{
    boolean ended=ToolClient.endSession(app);
    if(!ended){status.setText("存在未确认提交，保留会话跟踪；请先在网页核对或强制放弃本地追踪。");refreshSyncCard();return;}
    lastSessionId="";lastMessageId="";lastState="";lastReply="";lastNote="";syncStamp=System.currentTimeMillis();
    status.setText("已停止跟踪当前会话；下次派发将新建；远端会话不受影响。");
    refreshSyncCard();
   }catch(Exception e){syncNote.setText("存储故障，无法结束会话跟踪。");status.setText("存储故障，无法结束会话跟踪。");}
  }
 }
}

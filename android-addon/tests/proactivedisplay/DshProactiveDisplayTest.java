package com.turboio.addon;
import android.os.Handler;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.util.*;
public final class DshProactiveDisplayTest {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message+" phase="+NavGlasses.phase());}
 static final class Timer implements DshProactiveReport.Scheduler {
  final List<Job> jobs=new ArrayList<>();
  static final class Job implements DshProactiveReport.Handle {Runnable task;boolean canceled;Job(Runnable r){task=r;}public void cancel(){canceled=true;}}
  public DshProactiveReport.Handle schedule(Runnable r,long delay){Job j=new Job(r);jobs.add(j);return j;}
  int live(){return (int)jobs.stream().filter(j->!j.canceled).count();}
  void poll(){while(!jobs.isEmpty()){Job j=jobs.remove(0);if(!j.canceled){j.task.run();return;}}throw new AssertionError("no poll");}
 }
 static final class Phone implements DshProactiveDisplay.Listener {
  int updates,terminals,nullDashboards;Map<String,Object> last;String display; DshDashboardModel.View dashboard; int dashboards; final List<String> nullTrace=new ArrayList<>();
  public void onDashboard(DshDashboardModel.View view){if(view==null){nullDashboards++;nullTrace.add(Arrays.toString(new Throwable().getStackTrace()));}dashboard=view;dashboards++;}
  public void onReport(Map<String,Object> result){check(Thread.holdsLock(DshClient.LOCK)||"stopped".equals(result.get("status")),"phone draw host-atomic");updates++;if("completed".equals(result.get("status")))terminals++;last=result;}
  public void onDisplayStatus(String value){display=value;}
 }
  static final class Fixture {
  final ToolClient host=new ToolClient();final Timer timer=new Timer();
  final DshProactiveDisplay display=new DshProactiveDisplay(host,timer,SystemClock::elapsedRealtime);
  final Phone phone=new Phone();
  Fixture(){host.current=round("m1");host.remote=success();display.subscribe(phone);}
  void start(){display.start(host.current);}
  void finish(){start();timer.poll();Handler.drain();}
  // Same display, second submitted round: the lease must survive and refresh in place.
  void finishRound(String message){host.current=round(message);start();timer.poll();Handler.drain();}
 }
  static DshProactiveReport.RoundIdentity round(String message){return new DshProactiveReport.RoundIdentity("account",1,1,"target","session",message,false);}
  static Map<String,Object> success(){return DshClient.mapOf("status","completed","completionEvidence","assistant_done_and_idle","answer",String.join("",Collections.nCopies(700,"a")));}
  static Map<String,Object> otherSuccess(){return DshClient.mapOf("status","completed","completionEvidence","assistant_done_and_idle","answer",String.join("",Collections.nCopies(700,"b")));}
  static void ready(){String sid=NavReflect.last().sid();byte[] data=NavCore.packet(7,"{\"sid\":\""+sid+"\",\"code\":1}");data[3]=8;NavGlasses.event("messageReceived",DshClient.mapOf("message",DshClient.mapOf("deviceId",NavReflect.peer,"businessId",19,"payload",data)));Handler.drain();check("ready".equals(NavGlasses.phase()),"configuration acknowledged");}
  static void cleanup(Fixture f){f.display.stop();Handler.drain();f.display.confirmGlassesClosed();Handler.drain();check(!NavGlasses.active(),"own lease released explicitly");}
  static long packets(int type){return NavReflect.messages.stream().filter(m->m.type()==type).count();}
  // Option A internals stay private; tests reach them the same way the vendor bridge does.
  static Field displayField(String name)throws Exception{Field f=DshProactiveDisplay.class.getDeclaredField(name);f.setAccessible(true);return f;}
  static boolean held(DshProactiveDisplay d)throws Exception{return displayField("glassesHeld").getBoolean(d);}
  static long epochOf(DshProactiveDisplay d)throws Exception{return displayField("glassesEpoch").getLong(d);}
  static Object ownerOf(DshProactiveDisplay d)throws Exception{return displayField("owner").get(d);}
  static long idleMs()throws Exception{return displayField("GLASSES_IDLE_MS").getLong(null);}
  static boolean reopenable(DshProactiveDisplay d)throws Exception{return NavGlasses.canReenter(ownerOf(d),epochOf(d));}
  // Time steps stay at one second so the fake main queue never trips its runaway guard while NavGlasses ticks.
  static void advance(long millis){for(long done=0;done<millis;done+=1000L)Handler.advance(1000L);}
  static void advanceUntilIdleStop()throws Exception{
   long limit=idleMs()+30000L;
   for(long done=0;done<limit;done+=1000L){Handler.advance(1000L);if(!"ready".equals(NavGlasses.phase()))return;}
   throw new AssertionError("idle timeout never closed the lease");
  }
  static String lastOpenSid(){List<NavReflect.Message> ms=NavReflect.messages;for(int i=ms.size()-1;i>=0;i--)if(ms.get(i).type()==7)return ms.get(i).sid();return "";}
  static String lastLensText()throws Exception{
   List<NavReflect.Message> ms=NavReflect.messages;
   for(int i=ms.size()-1;i>=0;i--)if(ms.get(i).type()==5){
    Map<String,Object> json=DshClient.parseJsonObject(NavCore.decode(ms.get(i).payload).json);
    Object content=json.get("content");if(content instanceof Map)return String.valueOf(((Map<?,?>)content).get("source_transcript"));}
   return "";
  }
  // C8-C12 from the composite HUD plan: one ASR subtitle shares the AA board's single transcript
  // instead of opening a second vendor session.
  static int sectionOf(String text,String needle){int i=text.indexOf(needle);return i<0?-1:(int)text.substring(0,i).chars().filter(c->c=='\n').count();}
  static void c8()throws Exception{
   Fixture f=new Fixture();f.host.remote=DshClient.mapOf("status","running");f.start();f.timer.poll();Handler.drain();
   check(f.display.showDashboard(),"C8 a running AA round opens the composite lease");
   ready();long opens=packets(7),texts=packets(5);String sid=lastOpenSid();long epoch=epochOf(f.display);Object owner=ownerOf(f.display);
   f.display.onAsr("AA 还在跑我说的话",false,"asr-c8");Handler.drain();advance(1000);
   String partial=lastLensText();
   check(packets(7)==opens&&sid.equals(lastOpenSid()),"C8 an ASR partial never opens a second session");
   check(packets(5)==texts+1,"C8 the partial rides one throttled type 5");
   check(partial.contains("[语音]")&&partial.contains("我说的话")&&partial.contains("当前 DSH 目标")&&partial.contains("运行中"),"C8 one transcript carries the subtitle and the AA board");
   check(sectionOf(partial,"[语音]")<sectionOf(partial,"---")&&sectionOf(partial,"---")<sectionOf(partial,"当前 DSH 目标"),"C8 upper subtitle, separator, lower board order");
   check(held(f.display)&&epoch==epochOf(f.display)&&owner==ownerOf(f.display),"C8 the same owner and epoch still hold the lease");
   texts=packets(5);
   f.display.onAsr("我把话说完了",true,"asr-c8");Handler.drain();advance(1000);
   String done=lastLensText();
   check(packets(7)==opens&&packets(5)==texts+1,"C8 a final event refreshes the same lease once");
   check(done.contains("[识别]")&&done.contains("我把话说完了")&&done.contains("当前 DSH 目标"),"C8 the finalized subtitle keeps the board underneath");
   check("ready".equals(NavGlasses.phase()),"C8 the composite keeps the lease live");
   cleanup(f);
  }
  static void c9()throws Exception{
   Fixture f=new Fixture();f.finish();ready();long opens=packets(7),texts=packets(5);String sid=lastOpenSid();
   f.display.onAsr("结果出来后我又说一句",false,"asr-c9");Handler.drain();advance(1000);
   check(packets(7)==opens&&sid.equals(lastOpenSid())&&packets(5)==texts+1,"C9 a late partial refreshes the existing lease only");
   String lens=lastLensText();
   check(lens.contains(String.join("",Collections.nCopies(100,"a"))),"C9 the completed AA result survives the partial");
   check(lens.contains("[语音]")&&lens.contains("我又说一句"),"C9 the partial joins the same transcript");
   check(f.phone.dashboard!=null&&f.phone.dashboard.status==DshDashboardModel.Status.COMPLETED&&!f.phone.dashboard.resultLine.isEmpty(),"C9 the phone board keeps the completed result");
   check(held(f.display),"C9 the lease stays held for a partial after the terminal round");
   cleanup(f);
  }
  static void c10()throws Exception{
   Fixture f=new Fixture();f.host.remote=DshClient.mapOf("status","running");f.start();f.timer.poll();Handler.drain();
   check(f.display.showDashboard(),"C10 the board opens the composite lease");
   ready();long opens=packets(7),texts=packets(5);String sid=lastOpenSid();long epoch=epochOf(f.display);
   f.display.onAsr("这句还没说完",false,"asr-c10");Handler.drain();advance(1000);
   check(packets(5)==texts+1&&lastLensText().contains("这句还没说完"),"C10 the live partial reaches the lens");
   texts=packets(5);
   f.host.remote=success();f.timer.poll();Handler.drain();advance(1000);
   check(packets(7)==opens&&sid.equals(lastOpenSid())&&epoch==epochOf(f.display),"C10 an AA refresh keeps owner, epoch and sid");
   String lens=lastLensText();
   check(lens.contains("这句还没说完")&&lens.contains("[语音]"),"C10 an AA result refresh preserves the live subtitle");
   check(lens.contains(String.join("",Collections.nCopies(100,"a")))&&lens.contains("已完成"),"C10 the AA refresh shows the new status and result");
   check("completed".equals(f.phone.last.get("status"))&&f.phone.terminals==1,"C10 the phone still gets the AA terminal report");
   check(held(f.display),"C10 the lease stays held through the refresh");
   cleanup(f);
  }
  static void c11()throws Exception{
   Fixture f=new Fixture();f.finish();ready();long opens=packets(7);String sid=lastOpenSid();
   f.display.onAsr("说完了的一句话",true,"asr-c11");Handler.drain();advance(1000);
   String lens=lastLensText();
   check(lens.contains("[识别]")&&lens.contains("说完了的一句话")&&lens.contains("已完成"),"C11 the settling subtitle shares one transcript with the board");
   long texts=packets(5);
   advance(DshCompositeHudModel.DEFAULT_SETTLING_MS);
   check(packets(7)==opens&&sid.equals(lastOpenSid()),"C11 settlement never closes or reopens the session");
   check(packets(5)==texts+1,"C11 settlement publishes the board once more");
   String after=lastLensText();
   check(!after.contains("[识别]")&&!after.contains("[语音]")&&!after.contains("说完了的一句话"),"C11 the whole ASR section clears at settling");
   check(after.contains("完整结果请到 AA 查看。")&&after.contains("已完成")&&after.contains(String.join("",Collections.nCopies(100,"a"))),"C11 the board returns to its full copy once the subtitle settles");
   check("ready".equals(NavGlasses.phase())&&held(f.display),"C11 the lease stays ours after settlement");
   texts=packets(5);advance(3000);check(packets(5)==texts,"C11 a settled subtitle publishes nothing again");
   cleanup(f);
  }
  static void setProducer(int index,boolean on){
   if(index==0)NativeNavigation.running=on; else if(index==1)ReaderBridge.running=on;
   else if(index==2)MusicBridge.running=on; else NativeTransfer.running=on;
  }
  static void c12()throws Exception{
   // Each higher-priority business owns the lens: ASR must never open or refresh a competing session.
   String[] producers={"native navigation","reader bridge","music bridge","native transfer"};
   for(int p=0;p<producers.length;p++){
    setProducer(p,true);
    Fixture busy=new Fixture();long opens=packets(7),texts=packets(5);busy.finish();
    check(packets(7)==opens&&packets(5)==texts&&"idle".equals(NavGlasses.phase()),"C12 "+producers[p]+" blocks our session open");
    check(((String)busy.phone.last.get("displayNote")).contains("AA"),"C12 "+producers[p]+" contention keeps the summary on the phone");
    busy.display.onAsr("这个来源正占用镜片",false,"asr-c12"+p);Handler.drain();
    busy.display.onAsr("说完了",true,"asr-c12"+p);Handler.drain();advance(3000);
   check(packets(7)==opens&&packets(5)==texts,"C12 ASR alone never seizes the "+producers[p]+" channel");
   check(!held(busy.display)&&busy.phone.terminals==1&&busy.phone.dashboard!=null,
    "C12 the phone keeps the board while "+producers[p]+" owns the lens");
   cleanup(busy);
   setProducer(p,false);
  }
  // Contention is not fatal: the phrase is retained and the next authorized AA open carries it.
  Fixture f=new Fixture();long opens=packets(7),texts=packets(5);
  setProducer(3,true);f.finish();
  check(packets(7)==opens&&"idle".equals(NavGlasses.phase()),"C12 a busy lens keeps our round off the channel");
  f.display.onAsr("占用期间说的话",false,"asr-c12x");Handler.drain();
  f.display.onAsr("占用期间说完了",true,"asr-c12x");Handler.drain();advance(3000);
   check(packets(7)==opens&&packets(5)==texts,"C12 nothing is sent while another producer owns the lens");
   check(!held(f.display)&&f.phone.terminals==1&&((String)f.phone.last.get("answer")).contains("aaaa"),
    "C12 a contended open leaves no held lease and the phone keeps the result");
   setProducer(3,false);
   f.host.remote=otherSuccess();f.finishRound("m2");
   check(packets(7)==opens+1,"C12 only the authorized AA round opens the lens");
   ready();advance(1000);
   check(packets(5)==texts+1&&lastLensText().contains("占用期间说完了")&&lastLensText().contains("[识别]"),"C12 the authorized open carries the retained subtitle");
   check(lastLensText().contains("bbbb"),"C12 that same transcript carries the newer AA result");
   cleanup(f);
   // An unverified lens exit leaves an uncertain lease: composite writes and reopens are both blocked.
   f=new Fixture();f.finish();ready();NavReflect.rejectStop=true;advanceUntilIdleStop();NavReflect.rejectStop=false;
   check("uncertain".equals(NavGlasses.phase()),"C12 a refused exit leaves an uncertain lease");
   opens=packets(7);texts=packets(5);
   f.display.onAsr("不确定时不能刷新",false,"asr-c12b");Handler.drain();advance(2000);
   check(packets(7)==opens&&packets(5)==texts,"C12 an uncertain lease blocks composite sends and reopens");
   check(held(f.display)&&((String)f.phone.last.get("answer")).length()>0&&f.phone.terminals==1,"C12 the phone keeps the result while the lens is stuck");
   check(f.phone.dashboard!=null&&f.phone.dashboard.status==DshDashboardModel.Status.COMPLETED,"C12 the phone board still shows the AA result");
   cleanup(f);
   // Rapid partial bursts coalesce to the newest state at the vendor cadence.
   f=new Fixture();f.finish();ready();advance(1000);opens=packets(7);texts=packets(5);
   for(String s:new String[]{"第一版","第二版","第三版最终"}){f.display.onAsr(s,false,"asr-c12c");Handler.drain();}
   check(packets(5)==texts&&packets(7)==opens,"C12 per callback sends never bypass the throttle");
   advance(1000);String lens=lastLensText();
   check(packets(5)==texts+1&&lens.contains("第三版最终")&&!lens.contains("第一版")&&lens.contains("当前 DSH 目标"),"C12 only the latest partial reaches the lens once");
   cleanup(f);
  }
  // A1-A10 from the Option A lease design (stream A red-first matrix).
  static void a1()throws Exception{
   Fixture f=new Fixture();f.finish();ready();String sid=lastOpenSid();long opens=packets(7),texts=packets(5);
   f.host.remote=otherSuccess();f.finishRound("m2");
   check(!((String)f.phone.last.get("displayNote")).contains("AA"),"A1 second round presents on the lens");
   check(packets(7)==opens,"A1 second round never reserves a fresh session");
   check(sid.equals(lastOpenSid()),"A1 second round keeps the same sid");
   check(held(f.display),"A1 lease stays held across rounds");
   advance(1000);
   check(packets(5)==texts+1,"A1 refreshed summary reaches the lens once");
   check(lastLensText().contains("bbbb"),"A1 the newer summary is the text on the lens");
   check("ready".equals(NavGlasses.phase()),"A1 re-bound guard keeps the lease live");
   check(f.host.queries==2,"A1 each round polled once");
   cleanup(f);
  }
  static void a2()throws Exception{
   Fixture f=new Fixture();f.finish();ready();
   Handler.advance(15000);
   check("ready".equals(NavGlasses.phase()),"A2 no fifteen second auto close");
   check(NavGlasses.active()&&held(f.display),"A2 lease still owned after the old close window");
   advance(idleMs());
   check(!"ready".equals(NavGlasses.phase()),"A2 the idle timer closes instead");
   cleanup(f);
  }
  static void a3()throws Exception{
   Fixture f=new Fixture();f.finish();ready();String sid=lastOpenSid();
   advanceUntilIdleStop();
   check(held(f.display),"A3 idle close keeps the lease until reopen settles");
   check(reopenable(f.display),"A3 idle timeout leaves a reopen eligible lease");
   long opens=packets(7);Handler.advance(9000);
   f.host.remote=otherSuccess();f.finishRound("m2");
   check(packets(7)==opens+1,"A3 next round reopens the same owner");
   check(!sid.equals(lastOpenSid()),"A3 reopen mints a new sid");
   check(!reopenable(f.display),"A3 reopen consumes the eligibility");
   check(!((String)f.phone.last.get("displayNote")).contains("AA"),"A3 reopened round presents on the lens");
   ready();check("ready".equals(NavGlasses.phase()),"A3 reopened session acknowledges");
   cleanup(f);
  }
  static void a4()throws Exception{
   Fixture f=new Fixture();f.finish();ready();advanceUntilIdleStop();
   long opens=packets(7);check(f.timer.live()==0,"A4 no monitor timer left after the terminal round");
   f.host.remote=otherSuccess();f.finishRound("m2");
   check(packets(7)==opens,"A4 reopen inside the eight second window opens nothing");
   check(f.timer.live()==1,"A4 delayed reopen retry scheduled");
   check(((String)f.phone.last.get("displayNote")).contains("AA"),"A4 phone keeps the summary while the reopen waits");
   Handler.advance(9000);f.timer.poll();Handler.drain();
   check(packets(7)==opens+1,"A4 retry after eight seconds reopens the lease");
   ready();check("ready".equals(NavGlasses.phase()),"A4 reopened lease acknowledges");
   cleanup(f);
  }
  static void a5()throws Exception{
   Fixture f=new Fixture();f.finish();ready();
   NavReflect.rejectStop=true;advanceUntilIdleStop();NavReflect.rejectStop=false;
   check(NavGlasses.reopenState(ownerOf(f.display),epochOf(f.display))!=NavGlasses.ReopenState.ELIGIBLE,"A5 an unverified lens exit is not reopenable");
   long opens=packets(7);Handler.advance(9000);
   f.host.remote=otherSuccess();f.finishRound("m2");
   check(packets(7)==opens,"A5 an impossible reopen never steals the channel");
   check(f.timer.live()==0,"A5 no retry once the reopen is impossible");
   check(((String)f.phone.last.get("displayNote")).contains("AA"),"A5 fallback keeps the summary on the phone");
   check(held(f.display),"A5 the unverified lease still needs a user confirmation");
   cleanup(f);
  }
  static void a6()throws Exception{
   Fixture f=new Fixture();f.finish();ready();long opens=packets(7);String sid=lastOpenSid();
   check(held(f.display),"A6 lease held before the user releases");
   f.display.stop();Handler.drain();
   check(f.display.confirmGlassesClosed(),"A6 explicit own confirmation succeeds");
   check(!held(f.display),"A6 release clears the held flag");
   check(!NavGlasses.active(),"A6 user release returns the channel to idle");
   check(NavGlasses.reopenState(ownerOf(f.display),epochOf(f.display))==NavGlasses.ReopenState.FAILED,"A6 user release is never reopen eligible");
   f.host.remote=otherSuccess();f.finishRound("m2");
   check(packets(7)==opens+1&&(!sid.equals(lastOpenSid())),"A6 next round reserves a fresh session");
   ready();check("ready".equals(NavGlasses.phase()),"A6 fresh session acknowledges");
   cleanup(f);
  }
  static void a7()throws Exception{
   // Identity discipline on the manual refresh path: a round the host no longer owns must not write to
   // the lens, and it must not silently drop the lease that the next round still needs.
   Fixture f=new Fixture();f.finish();ready();long texts=packets(5),opens=packets(7);
   f.host.current=round("moved-on");
   check(!f.display.showDashboard(),"A7 a retired identity cannot refresh the held lens");
   check(packets(5)==texts&&packets(7)==opens&&"ready".equals(NavGlasses.phase())&&held(f.display),"A7 stale refresh writes nothing and keeps our lease");
   cleanup(f);
   // A queued reopen retry is dropped once its ticket stops being current.
   f=new Fixture();f.finish();ready();advanceUntilIdleStop();opens=packets(7);
   f.host.remote=otherSuccess();f.finishRound("m2");
   check(f.timer.live()==1,"A7 reopen retry armed while the identity is still current");
   f.host.current=round("moved-on");
   advance(9000);f.timer.poll();Handler.drain();
   check(packets(7)==opens,"A7 an invalidated ticket cannot reopen the lens");
   check(!"preparing".equals(NavGlasses.phase())&&!"starting".equals(NavGlasses.phase())&&!"ready".equals(NavGlasses.phase()),"A7 the closed lease never reopens");
   cleanup(f);
  }
  static void a8()throws Exception{
   Fixture f=new Fixture();f.finish();ready();long opens=packets(7);String sid=lastOpenSid();
   check(f.display.showDashboard(),"A8 manual dashboard refreshes the held lease");
   check(packets(7)==opens&&sid.equals(lastOpenSid()),"A8 manual refresh never opens a new session");
   check(held(f.display),"A8 lease stays ours after the manual refresh");
   Handler.advance(15000);check("ready".equals(NavGlasses.phase()),"A8 manual refresh has no fifteen second close");
   cleanup(f);
  }
  static void a9()throws Exception{
   Fixture f=new Fixture();f.host.remote=DshClient.mapOf("status","running");f.start();f.timer.poll();Handler.drain();
   check(f.display.showDashboard(),"A9 manual dashboard opens the lease");
   ready();Handler.advance(15000);
   check("ready".equals(NavGlasses.phase())&&held(f.display),"A9 no fifteen second stop after a manual dashboard");
   advanceUntilIdleStop();
   check(held(f.display)&&reopenable(f.display),"A9 the idle timer closes a manual lease reopenably");
   cleanup(f);
  }
  static void a10()throws Exception{
   Fixture f=new Fixture();f.finish();ready();Handler.advance(5000);f.display.stop();Handler.drain();
   check(f.display.confirmGlassesClosed(),"A10 old lease explicitly released");
   long opens=packets(7);f.host.remote=otherSuccess();f.finishRound("new-lease");ready();String newSid=lastOpenSid();
   check(packets(7)==opens+1,"A10 a released lease reopens for the newer epoch");
   Handler.advance(15000);
   check("ready".equals(NavGlasses.phase())&&newSid.equals(lastOpenSid()),"A10 no stale stop callback can close a newer epoch");
   cleanup(f);
  }
  // H1-H6: the single composite HUD switch. Off suspends only the lens; the phone-side AA monitoring,
  // results and the retained dashboard survive, and no queued ASR or timer may write or reopen afterwards.
  static void h1()throws Exception{
   Fixture f=new Fixture();f.host.remote=DshClient.mapOf("status","running");f.start();f.timer.poll();Handler.drain();
   check(f.display.hudEnabled(),"H1 a fresh coordinator is default-on");
   check(f.display.showDashboard(),"H1 an authorized AA round opens the lease");ready();
   f.display.onAsr("开启时说的话",false,"asr-h1");Handler.drain();advance(1000);
   check(lastLensText().contains("开启时说的话"),"H1 the enabled subtitle rides the lease");
   long opens=packets(7),texts=packets(5),closes=packets(3);
   f.display.setHudEnabled(false);Handler.drain();
   check(!f.display.hudEnabled(),"H1 the switch reads back as off");
   check(packets(7)==opens&&packets(5)==texts,"H1 disabling publishes nothing new to the lens");
   check(packets(3)==closes+1&&"stopping".equals(NavGlasses.phase()),"H1 disabling requests only our own lease exit");
   check(NavGlasses.ownedEpoch(ownerOf(f.display))==epochOf(f.display),"H1 the owned owner+epoch is still the one we asked to close");
   check(f.phone.last!=null&&"running".equals(f.phone.last.get("status")),"H1 the latest AA report is retained, never nulled");
   check(f.phone.dashboard!=null&&f.phone.dashboard.status==DshDashboardModel.Status.RUNNING,"H1 the phone board keeps the retained snapshot");
   check(f.phone.nullDashboards==0 || f.phone.nullDashboards==1,"H1 switch handling adds no null dashboard callback beyond initial subscription");
   // The AA watcher keeps its own lifecycle while the HUD is off, so the phone still gets the terminal result.
   f.host.remote=success();f.timer.poll();Handler.drain();
   check(f.phone.terminals==1&&((String)f.phone.last.get("answer")).contains("aaaa"),"H1 AA phone reporting continues with the HUD off");
   check(f.phone.last.get("displayNote")==null||((String)f.phone.last.get("displayNote")).contains("HUD"),"H1 the off state does not claim lens success");
   check(packets(7)==opens&&packets(5)==texts,"H1 an AA terminal result cannot open the lens while disabled");
   check(f.phone.dashboard!=null&&f.phone.dashboard.status==DshDashboardModel.Status.COMPLETED,"H1 the retained board keeps the completed result");
   check(f.display.confirmGlassesClosed(),"H1 the owned lease clears through the normal confirmation flow");
   cleanup(f);
  }
  static void h2()throws Exception{
   // Producer exclusion: suspending our HUD must never touch another producer's live session.
   Object other=new Object();NavGlasses.Handle caption=NavGlasses.reserve(other,1);
   check(caption!=null&&NavGlasses.begin(caption,"other producer",false),"H2 another producer owns the channel");
   ready();long opens=packets(7),closes=packets(3);
   Fixture f=new Fixture();f.host.remote=DshClient.mapOf("status","running");f.start();f.timer.poll();Handler.drain();
   check(!f.display.showDashboard(),"H2 a busy channel keeps our round off the lens");
   f.display.setHudEnabled(false);Handler.drain();
   check(packets(7)==opens&&packets(3)==closes,"H2 disabling sends nothing for a session we never owned");
   check("ready".equals(NavGlasses.phase()),"H2 the other producer keeps its live session");
   check(!f.display.confirmGlassesClosed(),"H2 we cannot confirm a lease we never owned");
   NavGlasses.stop(caption);NavGlasses.confirmIdle(caption);Handler.drain();cleanup(f);
  }
  static void h3()throws Exception{
   // Queued ASR work and an armed settlement timer from the enabled generation both become invalid.
   Fixture f=new Fixture();f.finish();ready();
   f.display.onAsr("说完的一句",true,"asr-h3");Handler.drain();advance(1000);
   check(lastLensText().contains("说完的一句"),"H3 the enabled generation reached the lens");
   long texts=packets(5),opens=packets(7);
   f.display.onAsr("排队中的回调",false,"asr-h3"); // queued, not drained yet
   f.display.setHudEnabled(false);Handler.drain();
   check(packets(5)==texts,"H3 a queued ASR callback cannot write once the switch committed");
   advance(DshCompositeHudModel.DEFAULT_SETTLING_MS+2000);
   check(packets(5)==texts&&packets(7)==opens,"H3 the old settlement timer neither writes nor reopens");
   check(f.phone.dashboard!=null&&f.phone.dashboard.status==DshDashboardModel.Status.COMPLETED,"H3 suspended timers leave the phone board untouched");
   check(f.phone.nullDashboards==1,"H3 the only null dashboard was the initial empty subscription");
   cleanup(f);
  }
  static void h4()throws Exception{
   // Re-enable must not steal an idle channel nor replay a stale caption; only a new authorized round opens.
   Fixture f=new Fixture();f.finish();ready();
   f.display.onAsr("关闭前的旧字幕",true,"asr-h4");Handler.drain();advance(1000);
   long opens=packets(7);
   f.display.setHudEnabled(false);Handler.drain();
   check(f.display.confirmGlassesClosed(),"H4 the owned lease exits through confirmation");
   long texts=packets(5);
   f.display.setHudEnabled(true);Handler.drain();advance(3000);
   check(packets(7)==opens&&packets(5)==texts,"H4 re-enabling alone opens or writes nothing");
   check(!NavGlasses.active(),"H4 an idle re-enable leaves the lens free for other producers");
   f.host.remote=otherSuccess();f.finishRound("m2");
   check(packets(7)==opens+1,"H4 only the authorized AA round opens the lens again");
   ready();advance(1000);
   check(packets(5)==texts+1,"H4 the authorized frame is submitted once");
   String lens=lastLensText();
   check(lens.contains("bbbb")&&!lens.contains("关闭前的旧字幕")&&!lens.contains("[识别]"),"H4 no replayed stale caption on the new authorized frame");
   cleanup(f);
  }
  static void h5()throws Exception{
   // An explicit AA stop stays its own lifecycle: unlike a HUD suspend it does clear the phone side.
   Fixture f=new Fixture();f.finish();ready();f.display.setHudEnabled(false);Handler.drain();
   f.display.stop();Handler.drain();
   check(f.phone.last!=null&&"stopped".equals(f.phone.last.get("status")),"H5 an explicit AA stop keeps its own report semantics");
   check(f.phone.nullDashboards>0,"H5 an explicit AA stop may clear the dashboard, unlike a HUD suspend");
   check(!f.display.hudEnabled(),"H5 an explicit AA stop never re-enables the HUD switch");
   cleanup(f);
  }
  static void h6()throws Exception{
   // Toggle generation discipline: only the newest command decides, older resume jobs are dropped.
   Fixture f=new Fixture();f.host.remote=DshClient.mapOf("status","running");f.start();f.timer.poll();Handler.drain();
   check(f.display.showDashboard(),"H6 the round opens our lease");ready();long opens=packets(7),texts=packets(5);
   f.display.setHudEnabled(true);f.display.setHudEnabled(false);Handler.drain();advance(3000);
   check(!f.display.hudEnabled(),"H6 the newest toggle wins over an older command");
   check(packets(7)==opens&&packets(5)==texts,"H6 the superseded resume job writes nothing");
   f.display.onAsr("关闭之后说的话",false,"asr-h6");Handler.drain();advance(2000);
   check(packets(7)==opens&&packets(5)==texts,"H6 ASR after the newest off command writes nothing");
   check(!held(f.display)||"stopping".equals(NavGlasses.phase())||"uncertain".equals(NavGlasses.phase()),"H6 a suspended HUD leaves only its own requested exit");
   cleanup(f);
  }
  static void h7()throws Exception{
   // A default-on coordinator at startup has no AA authorization yet: ASR alone can never open the lens.
   Fixture f=new Fixture();
   check(f.display.hudEnabled(),"H7 the composite HUD starts enabled without touching the lens");
   check(!NavGlasses.active(),"H7 an idle default-on startup opens no lens channel");
   long opens=packets(7),closes=packets(3),texts=packets(5);
   f.display.onAsr("没有 AA 轮次时的字幕",false,"asr-h7");Handler.drain();
   f.display.onAsr("没有 AA 轮次说完了",true,"asr-h7");Handler.drain();advance(3000);
   check(packets(7)==opens&&packets(5)==texts&&packets(3)==closes,"H7 ASR without an authorized AA round opens or writes nothing");
   check(!held(f.display)&&!NavGlasses.active(),"H7 the phone keeps control until an AA round authorizes the display");
   check(!f.display.showDashboard(),"H7 without a valid round there is nothing to present on the lens");
   // Now the authorized path opens it, and the retained subtitle rides that same single session.
   f.host.remote=DshClient.mapOf("status","running");f.start();f.timer.poll();Handler.drain();
   check(f.display.showDashboard(),"H7 the AA-authorized round opens the single lens session");ready();
   texts=packets(5);f.display.onAsr("授权之后的新字幕",false,"asr-h7b");Handler.drain();advance(1000);
   check(packets(7)==opens+1&&packets(5)==texts+1,"H7 the authorized session carries the newer subtitle only");
   check(lastLensText().contains("授权之后的新字幕"),"H7 the newer phrase reaches the lens");
   cleanup(f);
  }
  static void h8()throws Exception{
   // Re-applying the SAME switch value must not invalidate the armed subtitle settlement: otherwise an
   // unrelated AA dispatch (which re-asserts the preference) would orphan a finalized caption on the lens.
   Fixture f=new Fixture();f.finish();ready();
   f.display.onAsr("说完就要结算",true,"asr-h8");Handler.drain();advance(1000);
   check(lastLensText().contains("说完就要结算"),"H8 the finalized caption is on the lens");
   long texts=packets(5),opens=packets(7);
   // The host re-asserts the persisted value on every submitted round, so an idempotent command must be a no-op.
   f.display.setHudEnabled(true);Handler.drain();
   advance(DshCompositeHudModel.DEFAULT_SETTLING_MS);
   check(packets(7)==opens,"H8 settlement never reopens the session");
   check(packets(5)==texts+1,"H8 the still-armed settlement timer publishes the board once more");
   String after=lastLensText();
   check(!after.contains("说完就要结算")&&!after.contains("[识别]"),"H8 the caption clears on schedule after a same-value re-assert");
   check(after.contains("当前 DSH 目标")&&after.contains("已完成"),"H8 the retained AA board alone remains after settling");
   cleanup(f);
  }
 public static void main(String[] args)throws Exception{
  a1();a2();a3();a4();a5();a6();a7();a8();a9();a10();
  c8();c9();c10();c11();c12();
  h1();h2();h3();h4();h5();h6();h7();h8();
  Fixture f=new Fixture();f.host.remote=DshClient.mapOf("status","running");f.start();f.timer.poll();Handler.drain();check(f.phone.dashboard!=null&&f.phone.dashboard.status==DshDashboardModel.Status.RUNNING,"running dashboard shown");check(f.display.showDashboard(),"manual dashboard requests owned subtitle");cleanup(f);
  f=new Fixture();f.finish();check(f.host.queries==1,"one terminal poll");check(f.phone.terminals==1,"one terminal phone update");check(((String)f.phone.last.get("answer")).length()==500,"phone summary stays bounded, never full claim");ready();long firstTexts=packets(5);Handler.advance(1000);check(packets(5)==firstTexts+1,"summary text submitted once");String sent=NavCore.decode(NavReflect.last().payload).json;String summary=String.valueOf(((Map)DshClient.parseJsonObject(sent).get("content")).get("source_transcript"));check(summary.length()<=300&&summary.contains("当前 DSH 目标")&&summary.contains("AA")&&summary.contains(String.join("",Collections.nCopies(100,"a"))),"structured bounded glasses summary");Handler.advance(15000);check("ready".equals(NavGlasses.phase()),"no fifteen second own close");check(NavGlasses.active()&&held(f.display),"the lease outlives the old close window");check(!NavGlasses.canReenter(ownerOf(f.display),epochOf(f.display)),"nothing was closed yet, so nothing may reopen");f.display.stop();Handler.drain();check(f.display.confirmGlassesClosed(),"explicit own confirmation succeeds after a requested exit");Handler.drain();cleanup(f);
  f=new Fixture();f.start();f.timer.poll();f.host.current=round("m2");Handler.drain();check(f.phone.updates==0&&!NavGlasses.active(),"queued result after identity change dropped");cleanup(f);
  f=new Fixture();f.finish();int count=f.phone.updates;f.host.current=round("m2");Phone next=new Phone();f.display.unsubscribe(f.phone);f.display.subscribe(next);check(next.updates==0,"stale cache not replayed");cleanup(f);
  f=new Fixture();f.start();f.display.stop();f.host.current=round("m2");f.start();f.timer.poll();Handler.drain();check("m2".equals(f.phone.last.get("clientMessageId")),"old queued clear cannot wipe new result");cleanup(f);
  f=new Fixture();f.start();f.display.unsubscribe(f.phone);f.timer.poll();Handler.drain();check(f.host.queries==1&&f.phone.updates==0,"screen close keeps task monitoring");next=new Phone();f.display.subscribe(next);check(next.updates==1,"reopened screen replays current result");cleanup(f);
  Object other=new Object();NavGlasses.Handle caption=NavGlasses.reserve(other,1);check(caption!=null&&NavGlasses.begin(caption,"subtitle",false),"subtitle owns channel");ready();long before=packets(7);f=new Fixture();f.finish();check(packets(7)==before&&"ready".equals(NavGlasses.phase()),"busy subtitle not stolen");check(f.phone.last.containsKey("displayNote"),"busy fallback phone note");f.display.stop();Handler.drain();check("ready".equals(NavGlasses.phase()),"local stop never closes subtitle");check(!f.display.confirmGlassesClosed(),"cannot confirm another producer");NavGlasses.stop(caption);NavGlasses.confirmIdle(caption);Handler.drain();
  NavReflect.peer="";f=new Fixture();f.finish();check(f.phone.terminals==1,"disconnected glasses phone fallback");cleanup(f);NavReflect.peer="caption-test-peer";
  f=new Fixture();f.host.networkFailure=true;f.start();f.timer.poll();f.timer.poll();f.timer.poll();Handler.drain();check("unknown".equals(f.phone.last.get("status")),"network error not remote failed");check(!NavGlasses.active(),"network error no glasses success");cleanup(f);
  f=new Fixture();f.finish();ready();long texts=packets(5);ToolClient pinned=f.host;NavReflect.duringTextConstruction=()->pinned.current=round("changed");Handler.advance(1000);check(packets(5)==texts,"identity churn during SDK packet construction blocks send");cleanup(f);
  f=new Fixture();f.finish();ready();texts=packets(5);Fixture atomic=f;final Thread[] mutator=new Thread[1];NavReflect.duringTextConstruction=()->{mutator[0]=new Thread(()->{synchronized(DshClient.LOCK){atomic.host.current=round("next");}});mutator[0].start();};Handler.advance(1000);mutator[0].join(2000);check(!mutator[0].isAlive(),"producer host lock releases without deadlock");cleanup(f);
  f=new Fixture();f.finish();ready();Handler.advance(5000);f.display.stop();Handler.drain();check(f.display.confirmGlassesClosed(),"old lease explicitly released");f.host.current=round("new-lease");f.finish();ready();String newSid=NavReflect.last().sid();Handler.advance(10000);check("ready".equals(NavGlasses.phase())&&newSid.equals(NavReflect.last().sid()),"no stale close callback can stop a newer owner epoch");cleanup(f);
  System.out.println("DshProactiveDisplayTest: "+checks+" checks passed");
 }
}

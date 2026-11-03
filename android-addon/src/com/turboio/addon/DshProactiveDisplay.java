package com.turboio.addon;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Process-local single-round monitor. Never owns a remote task or a subtitle lease. */
final class DshProactiveDisplay implements DshProactiveReport.Sink {
    interface Listener {
        void onReport(Map<String,Object> report);
        default void onDisplayStatus(String status) {}
        default void onDashboard(DshDashboardModel.View view) {}
    }
    private static DshProactiveDisplay instance;
    static synchronized DshProactiveDisplay get(Context context) {
        if (instance == null) instance = new DshProactiveDisplay(context.getApplicationContext());
        return instance;
    }
    // A speech callback must never build the monitor: only an existing one owns a lens transcript.
    static synchronized DshProactiveDisplay existing() { return instance; }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ToolClient client;
    private final DshProactiveReport.Clock clock;
    private final DshProactiveReport.Scheduler scheduler;
    private final DshDashboardModel dashboard=new DshDashboardModel();
    private long dashboardGeneration;
    private DshProactiveReport.Ticket dashboardTicket;
    private String shown="";
    private boolean manual;
    private final DshProactiveReport monitor;
    // One transcript for both feeds: the vendor ASR phrase plus the AA board. Pure model, no timers.
    private final DshCompositeHudModel compositeHud=new DshCompositeHudModel();
    // Vendor ASR session token we currently follow; anything older is a stale callback.
    private String hudSession="";
    // Settlement token: only the newest finalized phrase may clear the subtitle.
    private long asrGeneration;
    private final Object owner = new Object();
    private long controlEpoch, glassesEpoch;
    /**
     * The composite HUD switch, kept here so every lens write and every delayed callback has one answer to
     * consult. It is NOT the AA lifecycle: turning the HUD off suspends lens publishing only. The AA round,
     * its phone listener, its dashboard snapshot and the remote task all stay exactly as they were, which is
     * why this path must never reuse {@link #stop()} (that one clears latest/dashboard and reports null).
     */
    private volatile boolean hudEnabled=true;
    /** Toggle generation: an off command invalidates older on/off callbacks, ASR work and timers. */
    private long hudGeneration;
    // Idle lease without a refresh; NavGlasses' own expiry timer then closes it reopenably.
    private static final long GLASSES_IDLE_MS = 600000L;
    // Vendor rule: a lens exit must be settled before the same owner may reopen.
    private static final long REOPEN_DELAY_MS = 8000L;
    // Main-thread lease state survives stop/new round until the user confirms lens exit. glassesEpoch
    // stays monotonic because NavGlasses rejects a reused epoch per owner, so glassesHeld alone says
    // whether we still own the lease; after a reopen the sid changes and only owner+epoch can prove it.
    private boolean glassesHeld;
    private Listener listener;
    private DshProactiveReport.Ticket latestTicket;
    private Map<String,Object> latest;
    private DshProactiveReport.RoundIdentity startingRound;
    private long startingEpoch;

    private DshProactiveDisplay(Context app) {
        this(new ToolClient(app), newScheduler(), SystemClock::elapsedRealtime);
    }
    // Injectable polling clock/scheduler lets integration tests run the actual display with a delayed MAIN queue.
    DshProactiveDisplay(ToolClient client,DshProactiveReport.Scheduler scheduler,DshProactiveReport.Clock clock) {
        this.client=client; this.clock=clock; this.scheduler=scheduler;
        monitor=new DshProactiveReport(client::isCurrentRound,client::queryRound,this,scheduler,clock);
    }
    private static DshProactiveReport.Scheduler newScheduler() {
        ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor();
        return (task,delay) -> {
            ScheduledFuture<?> future=executor.schedule(task,delay,TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        };
    }
    // Lock order: display -> host -> core ticket. Core calls its ports outside its lock.
    // In particular, start/stop below NEVER hold DshClient.LOCK while entering the monitor.
    synchronized void start(DshProactiveReport.RoundIdentity round) {
        if (!client.isCurrentRound(round)) return;
        final long command=++controlEpoch;
        final long startedAt=clock.millis();
        main.post(() -> {
            synchronized(this) {
                if(command!=controlEpoch)return;
                synchronized(DshClient.LOCK) {
                    if(!client.isCurrentRound(round))return;
                    // The lens lease intentionally survives a new round: the next terminal result refreshes it
                    // in place instead of closing and re-opening the same channel.
                    manual=false; latestTicket=null;startingRound=round;startingEpoch=command;
                    dashboardTicket=null; dashboardGeneration=dashboard.start(round,"当前 DSH 目标",startedAt);
                    if(listener!=null)listener.onDashboard(dashboard.snapshot(startedAt));
                    latest=DshClient.mapOf("status","submitted","sessionId",round.sessionId,
                        "clientMessageId",round.clientMessageId,"answer","","message","本轮已提交，正在查询结果。");
                    if(listener!=null)listener.onReport(new LinkedHashMap<>(latest));
                }
            }
        });
        monitor.start(round);
    }
    synchronized void stop() {
        monitor.stop();
        final long command=++controlEpoch;
        main.post(() -> {
            synchronized(this) {
                if(command!=controlEpoch)return;
                closeGlasses(); manual=false; latest=null; latestTicket=null; dashboardTicket=null;
                // An explicit stop drops the subtitle too; the lens lease is going away anyway.
                hudSession=""; asrGeneration++; compositeHud.startSession(null);
                if(listener!=null)listener.onDashboard(null);
                if(listener!=null) {
                    Map<String,Object> report=new LinkedHashMap<>();
                    report.put("status","stopped");
                    report.put("message","已停止本地监听；远端任务继续，提交记录已保留。");
                    listener.onReport(report);
                }
            }
        });
    }
    synchronized void subscribe(Listener value) {
        listener=value;
        synchronized(DshClient.LOCK) {
            if(latest!=null && (latestTicket!=null?valid(latestTicket):startingEpoch==controlEpoch&&client.isCurrentRound(startingRound))) value.onReport(new LinkedHashMap<>(latest));
        }
        synchronized(DshClient.LOCK) {
            if(latestTicket!=null && valid(latestTicket)) value.onDashboard(dashboard.snapshot(clock.millis()));
            else if(startingEpoch==controlEpoch && client.isCurrentRound(startingRound)) value.onDashboard(dashboard.snapshot(clock.millis()));
            else value.onDashboard(null);
        }
        notifyDisplayStatus();
    }
    synchronized void unsubscribe(Listener value) { if(listener==value)listener=null; }
    /** Current publish eligibility of this coordinator; the settings page reads it back after a commit. */
    synchronized boolean hudEnabled() { return hudEnabled; }
    /**
     * Applies the one persisted composite switch. Off: revokes lens publish eligibility, invalidates the
     * toggle/ASR/timer generations so no delayed settlement, expiry or reopen callback can write or reopen,
     * and requests a normal clear/stop confirmation of ONLY the lease this owner+epoch holds. Phone-side AA
     * monitoring, the retained dashboard and the latest result are untouched, and the phone listener is
     * re-notified with the retained snapshot rather than null. On: restores eligibility and resumes only
     * through a live AA-authorized round; an idle startup never opens a lens by itself.
     *
     * @return the generation this command published, so a caller can detect a newer override
     */
    synchronized long setHudEnabled(boolean enabled) {
        // Idempotent: re-asserting the persisted value (the host does this for every submitted round) must
        // not churn the generations, or an armed subtitle settlement would be orphaned on the lens.
        if (hudEnabled == enabled) {
            if (enabled) main.post(() -> { synchronized(this) { if (hudEnabled) notifyDisplayStatus(); } });
            return hudGeneration;
        }
        final long command=++hudGeneration;
        hudEnabled=enabled;
        // An off edge retires the subtitle work of the previous generation: the settlement timer and any
        // queued ASR callback compare against asrGeneration, so a post-disable write becomes impossible.
        asrGeneration++;
        if (!enabled) {
            // Stop polling only in the sense of "no lens publishing": the monitor keeps watching the round,
            // so the phone card and the retained board continue to update while the HUD is off.
            hudSession=""; compositeHud.startSession(null); shown="";
            main.post(() -> {
                synchronized(this) {
                    if (command!=hudGeneration || hudEnabled) return;
                    synchronized(DshClient.LOCK) {
                        // Release only our own lease through NavGlasses' normal confirmation flow. Uncertain
                        // or busy stays uncertain: we never claim a closed lens we cannot prove.
                        if (glassesHeld) NavGlasses.stop(owner,glassesEpoch);
                        notifyDisplayStatus();
                        // Keep the phone in charge: the listener sees the retained snapshot, never null here.
                        Listener sink=listener;
                        if (sink!=null) {
                            if (latest!=null) sink.onReport(new LinkedHashMap<>(latest));
                            sink.onDashboard(dashboard.snapshot(clock.millis()));
                        }
                    }
                }
            });
            return command;
        }
        main.post(() -> {
            synchronized(this) {
                if (command!=hudGeneration || !hudEnabled) return;
                synchronized(DshClient.LOCK) {
                    // Re-join the AA-authorized round we already own, at most. No reserve, no begin, no
                    // reopen: if the lease is gone, the next authorized present (a real AA round) opens it.
                    DshProactiveReport.Ticket ticket=liveTicket();
                    if (ticket==null || !glassesHeld) { notifyDisplayStatus(); return; }
                    String phase=NavGlasses.phase();
                    if (!phase.equals("starting") && !phase.equals("ready")) { notifyDisplayStatus(); return; }
                    long now=clock.millis();
                    if (!NavGlasses.bindGuard(owner,glassesEpoch,guardFor(ticket))) { notifyDisplayStatus(); return; }
                    String content=text(dashboard.snapshot(now));
                    if (NavGlasses.update(owner,glassesEpoch,content,now+GLASSES_IDLE_MS)) shown=content;
                    notifyDisplayStatus();
                    Listener sink=listener;
                    if (sink!=null && latest!=null) sink.onReport(new LinkedHashMap<>(latest));
                    if (sink!=null) sink.onDashboard(dashboard.snapshot(clock.millis()));
                }
            }
        });
        return command;
    }
    /** Called only after an explicit user confirmation; never force-clears a different producer. */
    synchronized boolean confirmGlassesClosed() {
        if(!glassesHeld)return false;
        if(!NavGlasses.confirmIdle(owner,glassesEpoch)) { notifyDisplayStatus(); return false; }
        glassesHeld=false; notifyDisplayStatus(); return true;
    }
    private boolean valid(DshProactiveReport.Ticket ticket) {
        return ticket!=null && ticket.isValid() && client.isCurrentRound(ticket.round());
    }
    public void onObservation(DshProactiveReport.Ticket ticket,Map<String,Object> report,long observedAtMillis) {
        final Map<String,Object> frame=new LinkedHashMap<>(report);
        main.post(() -> { synchronized(this) { synchronized(DshClient.LOCK) {
            if(!valid(ticket) || startingEpoch!=controlEpoch || !ticket.round().equals(startingRound)) { clearInvalid(ticket); return; }
            DshDashboardModel.View before=dashboard.snapshot(observedAtMillis);
            dashboard.accept(dashboardGeneration,ticket.round(),frame,observedAtMillis); dashboardTicket=ticket;
            DshDashboardModel.View after=dashboard.snapshot(observedAtMillis);
            publishDashboard(ticket,before,after);
            scheduleExpiry(ticket,after.lastObservedMillis);
        } } });
    }
    /**
     * Vendor ASR callback forwarded by TurboAddon on the main thread. It only joins the composite
     * transcript of a lease this display already owns: ASR alone never reserves a lens session, and a
     * stopping/uncertain lease is never written to or reopened from here.
     */
    void onAsr(String text,boolean finished,String session) {
        final String phrase=text==null?"":text;
        final String token=session==null?"":session;
        final long generation=hudGeneration;
        main.post(() -> {
            synchronized(this) {
                synchronized(DshClient.LOCK) {
                    // A callback queued before a toggle commit can never write, and a suspended HUD does
                    // not join a lens transcript at all: the phone keeps everything.
                    if(generation!=hudGeneration||!hudEnabled)return;
                    // No live AA generation means no channel of ours to join, so keep the phone in charge.
                    if(!roundLive())return;
                    if(!token.equals(hudSession)) { hudSession=token; compositeHud.startSession(token); }
                    long now=clock.millis();
                    if(!compositeHud.acceptAsr(phrase,finished,token,now))return;
                    asrGeneration++;
                    publishComposite(now);
                    // Both sections expire, so the newest accepted phrase owns the clearing timer.
                    armAsrSettlement(now);
                }
            }
        });
    }
    // Clear the subtitle back to the board alone, on the same owner and epoch.
    private void armAsrSettlement(long finalizedAt) {
        final long token=asrGeneration;
        final long generation=hudGeneration;
        long delay=Math.max(1L,finalizedAt+compositeHud.settlingMillis()-clock.millis());
        main.postDelayed(() -> {
            synchronized(this) {
                synchronized(DshClient.LOCK) {
                    if(token!=asrGeneration || generation!=hudGeneration || !hudEnabled || !roundLive())return;
                    long now=clock.millis();
                    if(compositeHud.compose(currentBoard(now),now).asrPhase!=DshCompositeHudModel.Phase.IDLE)return;
                    publishComposite(now);
                }
            }
        },delay);
    }
    /** True while one AA generation is still ours to display. Callers hold display then DshClient.LOCK. */
    private boolean roundLive() {
        if(liveTicket()!=null)return true;
        return startingEpoch==controlEpoch && client.isCurrentRound(startingRound);
    }
    private DshProactiveReport.Ticket liveTicket() {
        if(valid(dashboardTicket))return dashboardTicket;
        return valid(latestTicket)?latestTicket:null;
    }
    // ASR refresh of our own lease: identity check, producer guard, then in-place type 5 text.
    private void publishComposite(long now) {
        if(!hudEnabled)return;
        DshCompositeHudModel.Frame frame=compositeHud.compose(currentBoard(now),now);
        if(frame.text.equals(shown))return;
        if(!glassesHeld)return;
        String phase=NavGlasses.phase();
        if(!phase.equals("starting")&&!phase.equals("ready"))return;
        DshProactiveReport.Ticket ticket=liveTicket();
        // The guard must follow the currently live ticket, since a lease outlives its round.
        // Without a live ticket there is no authority to write, so the model keeps the phrase for the
        // next authorized AA present instead of sending on ASR's own account.
        if(ticket==null||!NavGlasses.bindGuard(owner,glassesEpoch,guardFor(ticket)))return;
        if(NavGlasses.update(owner,glassesEpoch,frame.text,now+GLASSES_IDLE_MS))shown=frame.text;
    }
    private DshDashboardModel.View currentBoard(long now) {
        return dashboard.snapshot(now);
    }
    @Override public void onUpdate(DshProactiveReport.Ticket ticket,Map<String,Object> report) { enqueue(ticket,report,false); }
    @Override public void onTerminal(DshProactiveReport.Ticket ticket,Map<String,Object> report) { enqueue(ticket,report,true); }
    private void enqueue(DshProactiveReport.Ticket ticket,Map<String,Object> report,boolean terminal) {
        final Map<String,Object> copy=new LinkedHashMap<>(report);
        copy.put("clientMessageId",ticket.round().clientMessageId);
        main.post(() -> {
            synchronized(this) {
                synchronized(DshClient.LOCK) {
                    // An old callback must not close a newer lease or replace a newer phone result.
                    if(!valid(ticket)){ clearInvalid(ticket); return; }
                    if("timeout".equals(copy.get("status")) || "network_errors".equals(copy.get("stopReason"))) {
                        DshDashboardModel.View before=dashboard.snapshot(clock.millis());
                        dashboard.accept(dashboardGeneration,ticket.round(),copy,clock.millis());
                        dashboardTicket=ticket; publishDashboard(ticket,before,dashboard.snapshot(clock.millis()));
                    }
                    if(terminal && Boolean.TRUE.equals(copy.get("glassesEligible")) && !manual) presentGlasses(ticket,copy);
                    if(!valid(ticket))return;
                    latestTicket=ticket; latest=copy;
                    if(listener!=null)listener.onReport(new LinkedHashMap<>(copy));
                }
            }
        });
    }
    private void clearInvalid(DshProactiveReport.Ticket ticket) {
        if(ticket!=null && dashboardTicket!=null && ticket.generation()==dashboardTicket.generation() && startingEpoch==controlEpoch) { dashboardTicket=null; if(listener!=null)listener.onDashboard(null); }
    }
    private String text(DshDashboardModel.View view) {
        return compositeHud.compose(view,clock.millis()).text;
    }
    private void publishDashboard(DshProactiveReport.Ticket ticket,DshDashboardModel.View before,DshDashboardModel.View after) {
        if(!valid(ticket)) { clearInvalid(ticket); return; }
        if(listener!=null)listener.onDashboard(after);
        String next=text(after);
        // The phone board always refreshes; only the lens copy obeys the HUD switch.
        if(hudEnabled && manual && glassesHeld && !next.equals(shown) && valid(ticket)) {
            if(NavGlasses.update(owner,glassesEpoch,next)) shown=next;
        }
    }
    private void scheduleExpiry(DshProactiveReport.Ticket ticket,long observed) {
        if(observed<0)return;
        long delay=Math.max(1,observed+DshDashboardModel.DEFAULT_FRESHNESS_MS+1-clock.millis());
        main.postDelayed(() -> { synchronized(this) { synchronized(DshClient.LOCK) {
            if(!valid(ticket) || (dashboardTicket==null || ticket.generation()!=dashboardTicket.generation())) { clearInvalid(ticket); return; }
            DshDashboardModel.View view=dashboard.snapshot(clock.millis());
            if(view.lastObservedMillis!=observed)return;
            publishDashboard(ticket,view,view);
        } } },delay);
    }
    synchronized boolean showDashboard() {
        synchronized(DshClient.LOCK) {
            if(!hudEnabled) {
                if(listener!=null)listener.onDisplayStatus("眼镜合成 HUD 已关闭；先重新开启该开关，才会在镜片上显示看板与字幕。");
                return false;
            }
            DshProactiveReport.Ticket ticket=dashboardTicket;
            if(!valid(ticket)) { clearInvalid(ticket); return false; }
            String content=text(dashboard.snapshot(clock.millis()));
            boolean ok=presentLocked(ticket,content,clock.millis()+GLASSES_IDLE_MS);
            if(ok) { manual=true; shown=content; }
            else if(!glassesHeld) {
                // Nobody can take the channel and nothing of ours is on the lens: keep the board on the phone.
                if(listener!=null)listener.onDisplayStatus("眼镜通道忙，当前看板保留在手机。");
            }
            notifyDisplayStatus(); return ok;
        }
    }
    // Lease lifecycle (Option A): one owner epoch per lease, in-place refresh while the channel is open,
    // reopen after an idle close, and never a steal of somebody else's session. Callers already hold
    // display then DshClient.LOCK and have validated the ticket.
    private boolean presentLocked(DshProactiveReport.Ticket ticket,String summary,long expires) {
        if(glassesHeld) {
            String phase=NavGlasses.phase();
            if(phase.equals("starting")||phase.equals("ready")) return refreshLocked(ticket,summary,expires);
            if(phase.equals("stopping")||phase.equals("uncertain")) return reopenOrSchedule(ticket,summary,expires);
            // Already idle: the vendor cleared the session, so a fresh reserve is allowed again.
            glassesHeld=false;
        }
        return openLocked(ticket,summary,expires);
    }
    /** In-place refresh: same sid, no new session, and the idle timer moves forward with the new text. */
    private boolean refreshLocked(DshProactiveReport.Ticket ticket,String summary,long expires) {
        // A lease outlives its round, so the producer guard must follow the currently live ticket.
        if(!NavGlasses.bindGuard(owner,glassesEpoch,guardFor(ticket))) return false;
        return NavGlasses.update(owner,glassesEpoch,summary,expires);
    }
    private boolean openLocked(DshProactiveReport.Ticket ticket,String summary,long expires) {
        NavGlasses.Handle handle=NavGlasses.reserve(owner,++glassesEpoch);
        if(handle==null)return false;
        if(!valid(ticket)||!NavGlasses.begin(handle,summary,false,guardFor(ticket))) { afterFailedOpenLocked(); return false; }
        glassesHeld=true;
        // begin() carries no expiry, so arm the idle timer on the same text; otherwise the channel is
        // only released by the one hour lifetime or a manual release.
        NavGlasses.update(owner,glassesEpoch,summary,expires);
        return true;
    }
    // A failed open may or may not have reached the SDK. Only a proven idle channel can be dropped
    // silently; anything else still needs the user's lens-exit confirmation.
    private void afterFailedOpenLocked() {
        NavGlasses.stop(owner,glassesEpoch);
        glassesHeld=!"idle".equals(NavGlasses.phase());
    }
    private NavGlasses.ProducerGuard guardFor(DshProactiveReport.Ticket ticket) {
        return new NavGlasses.ProducerGuard() {
            // The vendor-side checkpoints consult this on their own thread, so the switch also revokes the
            // right to keep sending: a suspended HUD closes only this owner's lease instead of refreshing.
            @Override public boolean allowed() {
                synchronized(DshProactiveDisplay.this) {
                    return hudEnabled && valid(ticket);
                }
            }
            @Override public boolean run(NavGlasses.ProducerAction action)throws Exception {
                synchronized(DshProactiveDisplay.this) {
                    if(!hudEnabled || !valid(ticket))return false;
                    action.run();return true;
                }
            }
        };
    }
    /** Reopen only the same owner, never before the vendor's eight second exit-settle window. */
    private boolean reopenOrSchedule(DshProactiveReport.Ticket ticket,String summary,long expires) {
        // A suspended HUD never reclaims a lens: reopen needs both the switch and the authorized ticket.
        if(!hudEnabled)return false;
        long closeAt=NavGlasses.closedAt(owner,glassesEpoch);
        if(closeAt<=0)return false; // identity moved on: we no longer own this lease, so never steal it
        long now=clock.millis();
        if(now-closeAt<REOPEN_DELAY_MS) { scheduleReopen(ticket,summary,expires,REOPEN_DELAY_MS-(now-closeAt)); return false; }
        long candidate=glassesEpoch+1;
        // Only commit the new epoch when the SDK accepted it; NavGlasses keeps the old one on failure.
        if(!NavGlasses.reopen(owner,glassesEpoch,candidate,summary,expires,now,null))return false;
        glassesEpoch=candidate; glassesHeld=true;
        // reopen() clears the vendor-side guard; re-bind so identity churn still closes only this lease.
        NavGlasses.bindGuard(owner,glassesEpoch,guardFor(ticket));
        return true;
    }
    // Delayed retry through the injected scheduler, so tests never need real time.
    private void scheduleReopen(DshProactiveReport.Ticket ticket,String summary,long expires,long delay) {
        scheduler.schedule(() -> main.post(() -> {
            synchronized(this) {
                synchronized(DshClient.LOCK) {
                    if(!valid(ticket)||!glassesHeld||!hudEnabled)return;
                    reopenOrSchedule(ticket,summary,clock.millis()+GLASSES_IDLE_MS);
                }
            }
        }),delay);
    }
    private void presentGlasses(DshProactiveReport.Ticket ticket,Map<String,Object> report) {
        if(!hudEnabled){report.put("displayNote","眼镜合成 HUD 已关闭，结果保留在手机；重新开启后由新一轮 AA 授权显示。");notifyDisplayStatus();return;}
        Object value=report.get("answer");String answer=value instanceof String?(String)value:"";
        if(answer.trim().isEmpty())return;
        String summary=text(dashboard.snapshot(clock.millis()));
        long expires=clock.millis()+GLASSES_IDLE_MS;
        if(presentLocked(ticket,summary,expires))
            report.put("displayNote","已提交眼镜摘要显示请求，镜片效果以实机为准。");
        else
            report.put("displayNote","眼镜通道忙，摘要保留在手机；完整结果请到 AA 查看。");
        notifyDisplayStatus();
    }
    private void closeGlasses() {
        // Display-initiated exit is never reopen eligible; only NavGlasses' own idle timer may re-arm a reopen.
        if(glassesHeld)NavGlasses.stop(owner,glassesEpoch);
        notifyDisplayStatus();
    }
    private void notifyDisplayStatus() {
        if(listener!=null)listener.onDisplayStatus(!glassesHeld?"主动回报眼镜通道：未占用。":
            "主动回报眼镜通道："+NavGlasses.status()+"。如镜片已退出，请点击确认释放；不确定时先用眼镜按钮退出。");
    }
}

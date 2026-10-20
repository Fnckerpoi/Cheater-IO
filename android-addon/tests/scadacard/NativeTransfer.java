package com.turboio.addon;

import android.os.SystemClock;
import java.util.*;

/** Deterministic test transport with a controllable main-loop clock. */
final class NativeTransfer {
    interface Receiver{void message(String peer,byte[] bytes);}
    static Receiver receiver;static String peer="scada-test-peer",lease="";static final List<byte[]> packets=new ArrayList<>();static Runnable fail;
    static final FakeMain MAIN=new FakeMain();
    static final class FakeMain{
        private static final class Delayed{final Runnable task;final long due;Delayed(Runnable task,long due){this.task=task;this.due=due;}}
        private final List<Runnable> ready=new ArrayList<>();private final List<Delayed> delayed=new ArrayList<>();
        void post(Runnable r){ready.add(r);}
        void postDelayed(Runnable r,long millis){removeCallbacks(r);delayed.add(new Delayed(r,SystemClock.elapsedRealtime()+millis));}
        void removeCallbacks(Runnable r){ready.removeIf(x->x==r);delayed.removeIf(x->x.task==r);}
        void drain(){while(!ready.isEmpty()){Runnable r=ready.remove(0);r.run();}}
        void advance(long millis){SystemClock.now+=millis;for(;;){int found=-1;long due=Long.MAX_VALUE;for(int i=0;i<delayed.size();i++)if(delayed.get(i).due<=SystemClock.elapsedRealtime()&&delayed.get(i).due<due){found=i;due=delayed.get(i).due;}if(found<0)break;Runnable r=delayed.remove(found).task;r.run();drain();}}
    }
    static void receive(Receiver r){receiver=r;}
    static String connected(){return peer;}
    static boolean acquireMessages(String owner){if(!lease.isEmpty()||peer.isEmpty())return false;lease=owner;return true;}
    static void releaseMessages(String owner){if(lease.equals(owner))lease="";}
    static void sendMessage(String owner,String device,byte[] payload,Runnable failed){if(!owner.equals(lease)||!peer.equals(device))throw new IllegalStateException();packets.add(payload.clone());fail=failed;}
}

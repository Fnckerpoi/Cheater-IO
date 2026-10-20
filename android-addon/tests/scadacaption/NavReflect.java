package com.turboio.addon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class NavReflect {
    interface Callback { void accept(String name, Object[] args) throws Exception; }
    static final class Message {
        final byte[] payload;
        final String device;
        Callback callback;
        Message(byte[] payload, String device) { this.payload = payload; this.device = device; }
        int type() { return NavCore.decode(payload).type; }
        String sid() { return new org.json.JSONObject(NavCore.decode(payload).json).optString("sid"); }
        void complete(Object error) throws Exception { callback.accept("invoke", new Object[]{this, error}); }
    }
    private static final Object manager = new Object();
    static String peer = "caption-test-peer";
    static boolean rejectStop;
    static int rejectType = -1;
    static int throwType = -1;
    static long advanceOnTextConstruction;
    static Runnable duringTextConstruction;
    static final List<Message> messages = new ArrayList<>();
    static Object type(String name) { return name; }
    static Object field(Object target, String name) { return "h".equals(name) ? manager : peer; }
    static Object make(String name, Object... args) {
        Message message=new Message((byte[])args[0], (String)args[2]);
        if(message.type()==5){android.os.SystemClock.now+=advanceOnTextConstruction;advanceOnTextConstruction=0;Runnable action=duringTextConstruction;duringTextConstruction=null;if(action!=null)action.run();}
        return message;
    }
    static Object proxy(String name, Callback callback) { return callback; }
    static Object call(Object target, String name, Object... args) throws Exception {
        if ("g".equals(name)) return peer.isEmpty() ? Collections.emptyList() : Collections.singletonList(peer);
        if ("b".equals(name)) return true;
        if ("valueOf".equals(name)) return args[0];
        if ("u".equals(name)) {
            Message message = (Message)args[0];
            message.callback = (Callback)args[1];
            messages.add(message);
            if (message.type() == throwType) throw new IllegalStateException("SDK threw");
            message.complete((rejectStop && message.type() == 3) || message.type() == rejectType ? "SDK rejected" : null);
            return null;
        }
        throw new IllegalArgumentException(name);
    }
    static Message last() { return messages.get(messages.size() - 1); }
}

final class NativeNavigation { static boolean running; static boolean active() { return running; } }
final class ReaderBridge { static boolean running; static boolean active() { return running; } }
final class MusicBridge { static boolean running; static boolean active() { return running; } }
final class NativeTransfer { static boolean running; static boolean busy() { return running; } }
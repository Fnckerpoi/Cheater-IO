package android.os;

import java.util.ArrayList;
import java.util.List;

public final class Handler {
    private static final class Task {
        final Runnable callback;
        final long due;
        Task(Runnable callback, long due) { this.callback = callback; this.due = due; }
    }
    private static final List<Task> tasks = new ArrayList<>();
    public Handler(Looper looper) {}
    public boolean post(Runnable callback) { return postDelayed(callback, 0); }
    public boolean postDelayed(Runnable callback, long millis) {
        tasks.add(new Task(callback, SystemClock.elapsedRealtime() + millis));
        return true;
    }
    public void removeCallbacks(Runnable callback) { tasks.removeIf(task -> task.callback == callback); }
    public static void drain() { advance(0); }
    public static void advance(long millis) {
        long target = SystemClock.now + millis;
        int executed = 0;
        for (;;) {
            int next = -1;
            for (int index = 0; index < tasks.size(); index++) {
                if (tasks.get(index).due <= target && (next < 0 || tasks.get(index).due < tasks.get(next).due)) next = index;
            }
            if (next < 0) break;
            if (++executed > 1000) throw new AssertionError("runaway main loop");
            Task task = tasks.remove(next);
            SystemClock.now = Math.max(SystemClock.now, task.due);
            task.callback.run();
        }
        SystemClock.now = target;
    }
}

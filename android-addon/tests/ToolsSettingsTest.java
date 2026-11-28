package com.turboio.addon;

public final class ToolsSettingsTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) {
        for (boolean storageLocked : new boolean[]{false, true}) {
            for (boolean unknownDelivery : new boolean[]{false, true}) {
                TurboToolsHarness.reset(storageLocked, unknownDelivery);
                TurboToolsHarness.showTools();
                Switch.current.checked = true;
                AlertDialog.current.positive.click();
                check(TurboToolsHarness.prefs.getBoolean("search", false), "Search must save independently of DSH locks: storage=" + storageLocked + " pending=" + unknownDelivery);
                check(AlertDialog.current.dismissed, "Successful save closes dialog");
                check(TurboToolsHarness.prefs.getBoolean("dsh_unknown_delivery", false) == unknownDelivery, "DSH delivery lock preserved");
                check(ToolClient.locked == storageLocked, "DSH storage lock preserved");
                check("existing-search-key".equals(SecretStore.searchKey), "Blank editor preserves stored search key");
                check(TurboToolsHarness.prefs.otherWrites == 0, "Search save must not write DSH binding or credentials");
            }
        }
        TurboToolsHarness.reset(true, true);
        SecretStore.searchKey = "";
        TurboToolsHarness.showTools();
        Switch.current.checked = true;
        AlertDialog.current.positive.click();
        check(!TurboToolsHarness.prefs.getBoolean("search", false), "Missing key must refuse enabling");
        check(!AlertDialog.current.dismissed, "Invalid save leaves editor open");

        TurboToolsHarness.reset(true, true);
        TurboToolsHarness.showTools();
        Switch.current.checked = true;
        EditText.current.text = "  replacement-key  ";
        AlertDialog.current.positive.click();
        check("replacement-key".equals(SecretStore.searchKey), "Explicit replacement key is trimmed and saved");
        check(TurboToolsHarness.prefs.getBoolean("search", false), "Replacement key can enable search under DSH lock");

        TurboToolsHarness.reset(false, false);
        TurboToolsHarness.prefs.commitOk = false;
        TurboToolsHarness.showTools();
        Switch.current.checked = true;
        AlertDialog.current.positive.click();
        check(!AlertDialog.current.dismissed, "Failed preference commit must not report success");
        System.out.println("PASS " + checks + " tools settings checks");
    }
}

class Activity {}
class View { interface Click { void run(View v); } Click callback; void setOnClickListener(Click c) { callback=c; } void click() { callback.run(this); } }
class LinearLayout { void addView(Object v) {} }
class ScrollView { ScrollView(Activity a) {} void addView(Object v) {} }
class Switch { static Switch current; boolean checked; Switch(Activity a) {} void setText(String s) { if(s.contains("TinyFish")) current=this; } void setChecked(boolean b) { checked=b; } boolean isChecked() { return checked; } }
class EditText { static EditText current; String text=""; EditText() {} int length() { return text.length(); } String getText() { return text; } }
class AlertDialog {
    static final int BUTTON_POSITIVE=1; static AlertDialog current; final View positive=new View(); boolean dismissed;
    interface OnShow { void run(AlertDialog d); } interface ButtonListener { void run(AlertDialog d,int w); }
    OnShow onShow; void setOnShowListener(OnShow s) { onShow=s; } View getButton(int n) { return positive; }
    void show() { current=this; onShow.run(this); } void dismiss() { dismissed=true; }
    static class Builder { Builder(Activity a) {} Builder setTitle(String s) { return this; } Builder setView(Object v) { return this; }
        Builder setNegativeButton(String s,ButtonListener l) { return this; } Builder setPositiveButton(String s,ButtonListener l) { return this; } AlertDialog create() { return new AlertDialog(); } }
}
class SharedPreferences {
    final java.util.Map<String,Boolean> values=new java.util.HashMap<>(); boolean commitOk=true; int otherWrites;
    boolean getBoolean(String key,boolean fallback) { return values.containsKey(key)?values.get(key):fallback; }
    String getString(String key,String fallback) { return fallback; }
    Editor edit() { return new Editor(); }
    class Editor { String key; boolean value; Editor putBoolean(String k,boolean v) { key=k;value=v;return this; }
        Editor putString(String key,String value) { otherWrites++; return this; }
        boolean commit() { if (!"search".equals(key)) otherWrites++; if(commitOk)values.put(key,value);return commitOk; } }
}
class SecretStore { static String searchKey; static String get(Object app,String name) { if(!"search_key".equals(name))throw new AssertionError("Unrelated credential read");return searchKey; }
    static void put(Object app,String name,String value) { if(!"search_key".equals(name))throw new AssertionError("Unrelated credential write");searchKey=value; } }
class ToolClient { static boolean locked; static boolean dshLocked() { return locked; } static boolean validKnowledge(String url) { return false; } }
class DshClient { static final Object LOCK=new Object(); }
class DshSettings { static void show(Activity a) {} }
class McpSettings { static void show(Activity a) {} }
class Toast { static final int LENGTH_LONG=1; static Toast makeText(Activity a,String text,int duration) { return new Toast(); } void show() {} }

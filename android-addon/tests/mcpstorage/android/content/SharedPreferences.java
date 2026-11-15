package android.content;
public interface SharedPreferences {
 String getString(String k,String d); boolean getBoolean(String k,boolean d); long getLong(String k,long d); boolean contains(String k); Editor edit();
 interface Editor { Editor putString(String k,String v); Editor putBoolean(String k,boolean v); Editor putLong(String k,long v); Editor remove(String k); Editor clear(); boolean commit(); }
}

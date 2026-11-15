package com.turboio.addon;
import android.content.Context;
/** Test boundary for Android Keystore; exercises separate named storage, not device encryption. */
final class SecretStore {
 static boolean failWrite;
 static void put(Context c,String n,String s)throws Exception {if(failWrite)throw new IllegalStateException("keystore_failed");if(!c.getSharedPreferences("turboio_private",0).edit().putString(n,s).commit())throw new IllegalStateException("storage_failed");}
 static String get(Context c,String n)throws Exception {return c.getSharedPreferences("turboio_private",0).getString(n,"");}
}

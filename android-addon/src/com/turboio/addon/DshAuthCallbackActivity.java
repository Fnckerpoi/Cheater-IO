package com.turboio.addon;
import android.app.Activity;
import android.os.Bundle;
public final class DshAuthCallbackActivity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state);String callback=getIntent()==null||getIntent().getData()==null?"":getIntent().getData().toString();DshSettings.callback(getApplicationContext(),callback);finish();}
}

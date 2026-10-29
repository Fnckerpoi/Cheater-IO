package com.turboio.addon;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public final class AaEmbeddedActivity extends Activity {
    private Class<?> entry; private View root;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try {
            AaPayloadLoader.Loaded payload=AaPayloadLoader.load(this);
            entry=payload.entry;
            Bundle launch=new Bundle();launch.putInt("abiVersion",1);
            if(state!=null)launch.putBundle("savedState",state.getBundle("aa"));
            root=(View)entry.getMethod("create",Activity.class,android.content.Context.class,Bundle.class,Object.class).invoke(null,this,payload.context,launch,new AaHostBridge(this));
            if(root==null||root.getContext().getClassLoader()!=payload.context.getClassLoader())throw new IllegalStateException("payload_context_mismatch");
            setContentView(root);
        } catch(Exception | LinkageError failure){showFailure("create",failure);}
    }
    private void showFailure(String stage,Throwable failure){
        Throwable cause=failure instanceof java.lang.reflect.InvocationTargetException&&failure.getCause()!=null?failure.getCause():failure;
        String code=cause.getClass().getSimpleName();
        android.util.Log.e("TurboIO-AA","payload_"+stage+":"+code);
        TextView text=new TextView(this);text.setText("AA 工作台加载失败："+stage+" / "+code+"。请检查内嵌构建与资源兼容性。");text.setPadding(32,48,32,32);setContentView(text);
    }
    private void event(String name,Bundle state){if(root!=null&&entry!=null)try{entry.getMethod("lifecycle",View.class,String.class,Bundle.class).invoke(null,root,name,state);}catch(Exception | LinkageError failure){showFailure(name,failure);}}
    void settingsChanged(){if(!isFinishing()&&!isDestroyed())event("refresh",new Bundle());}
    @Override protected void onStart(){super.onStart();event("start",new Bundle());}
    @Override protected void onResume(){super.onResume();event("resume",new Bundle());}
    @Override protected void onPause(){event("pause",new Bundle());super.onPause();}
    @Override protected void onStop(){event("stop",new Bundle());super.onStop();}
    @Override protected void onSaveInstanceState(Bundle state){Bundle saved=new Bundle();event("save",saved);state.putBundle("aa",saved);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){event("destroy",new Bundle());if(root!=null&&entry!=null)try{entry.getMethod("dispose",View.class).invoke(null,root);}catch(Exception ignored){}root=null;entry=null;super.onDestroy();}
}

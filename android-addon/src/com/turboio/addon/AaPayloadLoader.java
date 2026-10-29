package com.turboio.addon;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.view.LayoutInflater;
import dalvik.system.DexClassLoader;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;

final class AaPayloadLoader {
    static final String PREFIX="turboio/aa/";
    static final String ENTRY="com.agentsanywhere.embedded.PayloadEntry";
    static final class Loaded {
        final Context context; final Class<?> entry;
        Loaded(Context c,Class<?> e){context=c;entry=e;}
    }
    static boolean available(Context host){try(InputStream in=host.getAssets().open(PREFIX+"payload.json")){return in.read()!=-1;}catch(IOException missing){return false;}}
    static Loaded load(Context host) throws Exception {
        JSONObject manifest=new JSONObject(new String(read(host.getAssets().open(PREFIX+"payload.json"),16384),"UTF-8"));
        String hash=manifest.getString("sha256");
        if(manifest.getInt("schemaVersion")!=1||manifest.getInt("abiVersion")!=1||!ENTRY.equals(manifest.getString("entry"))||!hash.matches("[0-9a-f]{64}"))throw new IOException("payload_manifest_invalid");
        File dir=new File(host.getCodeCacheDir(),"aa-embedded");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("payload_cache_failed");
        File apk=new File(dir,hash+".apk");
        synchronized(AaPayloadLoader.class){
            if(!apk.exists()) {
                File staging=File.createTempFile("payload-",".apk",dir);
                try {
                    try(InputStream in=host.getAssets().open(PREFIX+"aa-embedded-payload.apk");FileOutputStream out=new FileOutputStream(staging)) {
                        if(!staging.setReadOnly())throw new IOException("payload_readonly_failed");
                        byte[] block=new byte[65536];int n;long total=0;
                        while((n=in.read(block))!=-1){total+=n;if(total>268435456)throw new IOException("payload_too_large");out.write(block,0,n);}
                        out.getFD().sync();
                    }
                    if(!hash.equals(sha(staging)))throw new IOException("payload_hash_mismatch");
                    if(!staging.renameTo(apk))throw new IOException("payload_publish_failed");
                } finally {if(staging.exists())staging.delete();}
            }
            if(!hash.equals(sha(apk))||!apk.setReadOnly())throw new IOException("payload_hash_mismatch");
        }
        PackageInfo info=host.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),0);
        if(info==null||info.applicationInfo==null)throw new IOException("payload_package_invalid");
        ApplicationInfo app=info.applicationInfo;
        app.sourceDir=apk.getAbsolutePath();app.publicSourceDir=apk.getAbsolutePath();
        Resources resources=host.getPackageManager().getResourcesForApplication(app);
        ClassLoader loader=new DexClassLoader(apk.getAbsolutePath(),dir.getAbsolutePath(),null,Context.class.getClassLoader());
        PayloadContext context=new PayloadContext(host,app,resources,loader);
        Class<?> entry=loader.loadClass(ENTRY);
        if(entry.getClassLoader()!=loader)throw new IOException("payload_classloader_mismatch");
        return new Loaded(context,entry);
    }
    static byte[] read(InputStream stream,int limit)throws IOException {
        try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("payload_limit");out.write(b,0,n);}return out.toByteArray();}
    }
    static String sha(File file)throws Exception {MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    static final class PayloadContext extends ContextWrapper {
        final ApplicationInfo info;final Resources resources;final ClassLoader loader;final Resources.Theme theme;
        PayloadContext(Context host,ApplicationInfo app,Resources res,ClassLoader cl){super(host);info=app;resources=res;loader=cl;theme=res.newTheme();theme.applyStyle(android.R.style.Theme_Material_Light_NoActionBar,true);}
        @Override public Resources getResources(){return resources;}
        @Override public AssetManager getAssets(){return resources.getAssets();}
        @Override public ClassLoader getClassLoader(){return loader;}
        @Override public Resources.Theme getTheme(){return theme;}
        @Override public void setTheme(int id){theme.applyStyle(id,true);}
        @Override public Context getApplicationContext(){return this;}
        @Override public void registerComponentCallbacks(android.content.ComponentCallbacks callback){getBaseContext().getApplicationContext().registerComponentCallbacks(callback);}
        @Override public void unregisterComponentCallbacks(android.content.ComponentCallbacks callback){getBaseContext().getApplicationContext().unregisterComponentCallbacks(callback);}
        @Override public ApplicationInfo getApplicationInfo(){return info;}
        @Override public String getPackageName(){return info.packageName;}
        @Override public String getPackageCodePath(){return info.sourceDir;}
        @Override public String getPackageResourcePath(){return info.publicSourceDir;}
        @Override public Object getSystemService(String name){return Context.LAYOUT_INFLATER_SERVICE.equals(name)?LayoutInflater.from(getBaseContext()).cloneInContext(this):super.getSystemService(name);}
    }
}

package com.turboio.addon;
import android.content.Context;
import android.content.SharedPreferences;
public final class McpConnectionStoreTest {
 static int passed,failed;
 interface Check {void run()throws Exception;}
 static void test(String name,Check c)throws Exception{try{c.run();passed++;System.out.println("PASS "+name);}catch(AssertionError|Exception e){failed++;System.out.println("FAIL "+name+": "+e);}}
 static void fails(Check c)throws Exception{try{c.run();}catch(IllegalStateException expected){return;}throw new AssertionError("storage failure hidden");}
 static Context.Prefs privatePrefs(Context c){return (Context.Prefs)c.getSharedPreferences("turboio_private",0);}
 static void original(Context c){McpConnectionStore.Snapshot s=McpConnectionStore.snapshot(c);eq(true,s.enabled);eq("https://kb.example/mcp",s.endpoint);eq("one",s.token);eq(1L,s.revision);}
 static void restart(Context c){((Context.Prefs)prefs(c)).restart();privatePrefs(c).restart();}
 static void eq(Object a,Object b){if(!a.equals(b))throw new AssertionError("expected "+a+" got "+b);}
 static void rejects(Check c)throws Exception{try{c.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("invalid endpoint accepted");}
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("turboio_settings",0);}
 public static void main(String[] args)throws Exception{
  test("defaults_disabled_without_personal_endpoint",()->{McpConnectionStore.Snapshot s=McpConnectionStore.snapshot(new Context());eq(false,s.enabled);eq("",s.endpoint);eq("",s.token);eq(0L,s.revision);});
  test("saves_endpoint_switch_and_token",()->{Context c=new Context();McpConnectionStore.save(c,true," https://kb.example/mcp ","secret-one",false);McpConnectionStore.Snapshot s=McpConnectionStore.snapshot(c);eq(true,s.enabled);eq("https://kb.example/mcp",s.endpoint);eq("secret-one",s.token);eq(false,prefs(c).contains("cortrix_token"));});
  test("blank_token_keeps_existing",()->{Context c=new Context();McpConnectionStore.save(c,true,"https://kb.example/mcp","secret-one",false);McpConnectionStore.save(c,false,"https://kb.example/mcp","  ",false);eq("secret-one",McpConnectionStore.snapshot(c).token);});
  test("clear_token_is_explicit_and_wins",()->{Context c=new Context();McpConnectionStore.save(c,true,"https://kb.example/mcp","secret-one",false);McpConnectionStore.save(c,true,"https://kb.example/mcp","replacement",true);eq("",McpConnectionStore.snapshot(c).token);});
  test("revision_changes_only_with_configuration",()->{Context c=new Context();McpConnectionStore.save(c,true,"https://kb.example/mcp","one",false);long rev=McpConnectionStore.snapshot(c).revision;McpConnectionStore.save(c,true,"https://kb.example/mcp","",false);eq(rev,McpConnectionStore.snapshot(c).revision);McpConnectionStore.save(c,false,"https://kb.example/mcp","",false);eq(++rev,McpConnectionStore.snapshot(c).revision);McpConnectionStore.save(c,false,"https://other.example/mcp","",false);eq(++rev,McpConnectionStore.snapshot(c).revision);McpConnectionStore.save(c,false,"https://other.example/mcp","two",false);eq(++rev,McpConnectionStore.snapshot(c).revision);McpConnectionStore.save(c,false,"https://other.example/mcp","",true);eq(++rev,McpConnectionStore.snapshot(c).revision);});
  test("rejects_insecure_remote_without_mutation",()->{Context c=new Context();McpConnectionStore.save(c,true,"https://kb.example/mcp","one",false);for(String endpoint:new String[]{"http://remote.example/mcp","ftp://remote.example/mcp","https://user:pass@kb.example/mcp","not a url"})rejects(()->McpConnectionStore.save(c,true,endpoint,"two",false));eq("one",McpConnectionStore.snapshot(c).token);eq(1L,McpConnectionStore.snapshot(c).revision);rejects(()->McpConnectionStore.save(c,true,"","",false));});
  test("allows_http_loopback_only",()->{for(String host:new String[]{"localhost","127.0.0.1","[::1]"}){Context c=new Context();McpConnectionStore.save(c,true,"http://"+host+":8000/mcp","",false);eq(true,McpConnectionStore.snapshot(c).enabled);}});
  test("migrates_legacy_token_once",()->{Context c=new Context();prefs(c).edit().putString("cortrix_token","legacy").putString("cortrix_endpoint","https://kb.example/mcp").commit();McpConnectionStore.Snapshot s=McpConnectionStore.snapshot(c);eq("legacy",s.token);eq(false,prefs(c).contains("cortrix_token"));eq("legacy",SecretStore.get(c,"cortrix_token"));eq(s.revision,McpConnectionStore.snapshot(c).revision);});
  test("migration_preserves_existing_encrypted_token",()->{Context c=new Context();SecretStore.put(c,"cortrix_token","newer");prefs(c).edit().putString("cortrix_token","old").commit();eq("newer",McpConnectionStore.snapshot(c).token);eq(false,prefs(c).contains("cortrix_token"));});
  test("failed_secret_migration_retains_legacy_for_retry",()->{Context c=new Context();prefs(c).edit().putString("cortrix_token","legacy").commit();SecretStore.failWrite=true;try{McpConnectionStore.snapshot(c);throw new AssertionError("failure hidden");}catch(IllegalStateException expected){}finally{SecretStore.failWrite=false;}eq("legacy",prefs(c).getString("cortrix_token",""));eq("legacy",McpConnectionStore.snapshot(c).token);});
  test("failed_preferences_save_reports_failure_and_restores_token",()->{Context c=new Context();McpConnectionStore.save(c,true,"https://kb.example/mcp","one",false);((Context.Prefs)prefs(c)).failCommit=true;try{McpConnectionStore.save(c,false,"https://other.example/mcp","two",false);throw new AssertionError("failure hidden");}catch(IllegalStateException expected){}eq("one",SecretStore.get(c,"cortrix_token"));});
  test("failed_plaintext_removal_reports_failure_and_retries",()->{Context c=new Context();prefs(c).edit().putString("cortrix_token","legacy").commit();((Context.Prefs)prefs(c)).failCommit=true;try{McpConnectionStore.snapshot(c);throw new AssertionError("failure hidden");}catch(IllegalStateException expected){}eq(true,prefs(c).contains("cortrix_token"));((Context.Prefs)prefs(c)).failCommit=false;eq("legacy",McpConnectionStore.snapshot(c).token);eq(false,prefs(c).contains("cortrix_token"));});
  test("failed_save_blocks_snapshot_and_identical_retry_until_recovery",()->{
   Context c=new Context();McpConnectionStore.save(c,true,"https://kb.example/mcp","one",false);
   Context.Prefs p=(Context.Prefs)prefs(c);p.failCommit=true;
   fails(()->McpConnectionStore.save(c,false,"https://other.example/mcp","",false));
   fails(()->McpConnectionStore.snapshot(c));
   fails(()->McpConnectionStore.save(c,false,"https://other.example/mcp","",false));
   p.failCommit=false;original(c);restart(c);original(c);
   McpConnectionStore.save(c,false,"https://other.example/mcp","",false);
   eq(2L,McpConnectionStore.snapshot(c).revision);eq("one",McpConnectionStore.snapshot(c).token);
  });
  test("failed_credential_commit_cannot_publish_same_revision_new_token",()->{
   Context c=new Context();McpConnectionStore.save(c,true,"https://kb.example/mcp","one",false);
   privatePrefs(c).failCommit=true;
   fails(()->McpConnectionStore.save(c,true,"https://kb.example/mcp","two",false));
   fails(()->McpConnectionStore.snapshot(c));
   privatePrefs(c).failCommit=false;original(c);restart(c);original(c);
   McpConnectionStore.save(c,true,"https://kb.example/mcp","two",false);
   eq("two",McpConnectionStore.snapshot(c).token);eq(2L,McpConnectionStore.snapshot(c).revision);
  });
  test("failed_migration_cleanup_keeps_retry_required_even_when_memory_removed_key",()->{
   Context c=new Context();prefs(c).edit().putString("cortrix_token","legacy").commit();
   Context.Prefs p=(Context.Prefs)prefs(c);p.failCommit=true;
   fails(()->McpConnectionStore.snapshot(c));fails(()->McpConnectionStore.snapshot(c));
   p.failCommit=false;eq("legacy",McpConnectionStore.snapshot(c).token);
   eq(1L,McpConnectionStore.snapshot(c).revision);restart(c);
   eq(false,prefs(c).contains("cortrix_token"));eq("legacy",McpConnectionStore.snapshot(c).token);
   eq(1L,McpConnectionStore.snapshot(c).revision);
  });
  test("failed_credential_clear_restores_prior_generation",()->{
   Context c=new Context();McpConnectionStore.save(c,true,"https://kb.example/mcp","one",false);
   ((Context.Prefs)prefs(c)).failCommit=true;
   fails(()->McpConnectionStore.save(c,false,"https://other.example/mcp","",true));
   ((Context.Prefs)prefs(c)).failCommit=false;original(c);restart(c);original(c);
  });
  test("failed_save_survives_unrelated_writes_to_both_preference_files",()->{
   Context c=new Context();McpConnectionStore.save(c,true,"https://kb.example/mcp","one",false);
   Context.Prefs p=(Context.Prefs)prefs(c), secret=privatePrefs(c);p.failAtCommit=p.commits+1;secret.failAtCommit=secret.commits+1;
   fails(()->McpConnectionStore.save(c,false,"https://other.example/mcp","two",false));
   p.failCommit=false;secret.failCommit=false;
   prefs(c).edit().putString("navigation_mode","walk").commit();
   privatePrefs(c).edit().putString("unrelated_secret","value").commit();
   restart(c);original(c);
  });
  test("failed_journal_prepare_and_failed_recovery_cleanup_survive_unrelated_writes",()->{
   Context c=new Context();McpConnectionStore.save(c,true,"https://kb.example/mcp","one",false);
   Context.Prefs p=(Context.Prefs)prefs(c), secret=privatePrefs(c), journal=(Context.Prefs)c.getSharedPreferences("turboio_mcp_recovery",0);p.failAtCommit=p.commits+1;
   fails(()->McpConnectionStore.save(c,false,"https://other.example/mcp","two",false));
   p.failCommit=false;p.edit().putString("navigation_mode","drive").commit();restart(c);original(c);
   journal.failCommit=true;
   fails(()->McpConnectionStore.save(c,false,"https://other.example/mcp","two",false));
   p.failCommit=false;secret.failCommit=false;journal.failCommit=false;
   p.edit().putString("navigation_mode","transit").commit();secret.edit().putString("unrelated_secret","value").commit();
   restart(c);original(c);
  });
  test("restart_during_failed_rollback_never_mixes_generation",()->{
   Context c=new Context();McpConnectionStore.save(c,true,"https://kb.example/mcp","one",false);
   Context.Prefs p=(Context.Prefs)prefs(c), secret=privatePrefs(c);
   p.failAtCommit=p.commits+1;secret.failAtCommit=secret.commits+1;
   fails(()->McpConnectionStore.save(c,false,"https://other.example/mcp","two",false));
   restart(c);p.failCommit=false;original(c);
  });
  System.out.println("McpConnectionStoreTest: "+passed+" passed, "+failed+" failed");
  if(failed!=0)throw new AssertionError("storage suite failures: "+failed);
 }
}

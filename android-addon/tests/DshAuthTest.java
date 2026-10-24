package com.turboio.addon;
import java.util.*;
public final class DshAuthTest {
 static int n;static void ok(boolean b){if(!b)throw new AssertionError("auth "+n);n++;}
 static void reject(Runnable r){try{r.run();throw new AssertionError("accepted");}catch(IllegalArgumentException e){n++;}}
 public static void main(String[] args){
  String origin="https://web.agents-anywhere.com";DshAuth auth=new DshAuth();String state=auth.begin(origin,10);
  ok(state.matches("[A-Za-z0-9_-]{43}"));ok(auth.challenge().matches("[A-Za-z0-9_-]{43}"));
  String callback=DshAuth.REDIRECT+"?code=abc&state="+state;
  reject(()->auth.consume(callback,origin,300011));reject(()->auth.consume(callback,"https://wrong.example",11));
  reject(()->auth.consume(callback+"&access_token=secret",origin,11));reject(()->auth.consume(callback.replace("oauth","wrong"),origin,11));
  DshAuth.Grant grant=auth.consume(callback,origin,11);ok(grant.code.equals("abc"));ok(grant.verifier.matches("[A-Za-z0-9_-]{43}"));reject(()->auth.consume(callback,origin,12));
  String next=auth.begin(origin,20);auth.cancel();reject(()->auth.consume(DshAuth.REDIRECT+"?code=a&state="+next,origin,21));
  reject(()->new DshAuth().consume(callback,origin,12));
  String deny=auth.begin(origin,30);reject(()->auth.consume(DshAuth.REDIRECT+"?error=access_denied&state="+deny,origin,31));reject(()->auth.consume(DshAuth.REDIRECT+"?code=a&state="+deny,origin,32));
  Map<String,Object> caps=DshClient.mapOf("client_id","agents-anywhere-mobile","available",true,"redirect_uri",DshAuth.REDIRECT,"token_endpoint","/api/v2/oauth/token","code_challenge_methods_supported",Arrays.asList("S256"));
  ok(DshAuth.capability(caps));caps.put("token_endpoint","https://evil.example/token");ok(!DshAuth.capability(caps));caps.put("token_endpoint","/api/v2/oauth/token");caps.put("client_id","wrong-client");ok(!DshAuth.capability(caps));
  String fresh=auth.begin(origin,40);reject(()->auth.consume(DshAuth.REDIRECT+"?code=a&state="+fresh+"&state="+fresh,origin,41));

  // 测试持久化 Storage 抗进程死亡恢复
  final Map<String,String> disk = new HashMap<>();
  final long[] diskStarted = new long[1];
  final long[] diskEpoch = new long[1];
  final long[] diskGen = new long[1];
  DshAuth.Storage storage = new DshAuth.Storage() {
   public void save(String s, String v, String o, long st, long ep, long gen) { disk.put("s", s); disk.put("v", v); disk.put("o", o); diskStarted[0] = st; diskEpoch[0] = ep; diskGen[0] = gen; }
   public void clear() { disk.clear(); diskStarted[0] = 0; diskEpoch[0] = 0; diskGen[0] = 0; }
   public String state() { return disk.getOrDefault("s", ""); }
   public String verifier() { return disk.getOrDefault("v", ""); }
   public String origin() { return disk.getOrDefault("o", ""); }
   public long started() { return diskStarted[0]; }
   public long epoch() { return diskEpoch[0]; }
   public long generation() { return diskGen[0]; }
  };
  DshAuth firstProc = new DshAuth();
  String persistentState = firstProc.begin(origin, 100, 5, storage);
  ok(!persistentState.isEmpty() && persistentState.equals(storage.state()));
  // 模拟进程死亡：创建全新的 secondProc (内存完全为空)
  DshAuth secondProc = new DshAuth();
  DshAuth.Grant resumedGrant = secondProc.consume(DshAuth.REDIRECT + "?code=resumed_code&state=" + persistentState, origin, 150, storage);
  ok("resumed_code".equals(resumedGrant.code));
  ok(resumedGrant.verifier.matches("[A-Za-z0-9_-]{43}"));
  ok(resumedGrant.epoch == 5);
  ok(storage.state().isEmpty()); // 一次性消费后已清除

  System.out.println("PASS "+n+" auth checks");
 }
}

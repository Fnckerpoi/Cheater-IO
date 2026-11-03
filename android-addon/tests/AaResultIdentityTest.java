package com.turboio.addon;
/** Exercises actual returned failures and thrown exceptions without Android or network. */
public final class AaResultIdentityTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[] args)throws Exception{
  for(String outcome:new String[]{"submitted","ended","failed","unknown_delivery","exception"}){
   String[] actual={"initial"};AaBridgePolicy.ResultIdentity id=new AaBridgePolicy.ResultIdentity(1,"initial","R");
   Runnable fresh=()->{String error=id.check(1,actual[0],"R");if(!error.isEmpty())throw new IllegalStateException(error);};
   boolean stale=false;
   try{AaBridgePolicy.callFresh(()->{actual[0]="foreign-message";if("exception".equals(outcome))throw new Exception("old failure");return outcome;},fresh);}
   catch(IllegalStateException e){stale="STALE_SELECTION".equals(e.getMessage());}
   catch(Exception oldFailure){}
   ok(stale);
   actual[0]="initial";
   if("exception".equals(outcome)){
    try{AaBridgePolicy.callFresh(()->{throw new Exception("current failure");},fresh);throw new AssertionError("swallowed exception");}
    catch(Exception e){ok("current failure".equals(e.getMessage()));}
   }else ok(outcome.equals(AaBridgePolicy.callFresh(()->outcome,fresh)));
   synchronized(DshClient.LOCK){actual[0]="own-pending";id.committed(actual[0],"R");}
   ok("unknown_delivery".equals(AaBridgePolicy.callFresh(()->"unknown_delivery",fresh)));
   synchronized(DshClient.LOCK){actual[0]="own-final";id.committed(actual[0],"R");}
   ok("submitted".equals(AaBridgePolicy.callFresh(()->"submitted",fresh)));
   actual[0]="foreign-same-session-new-message";
   ok("STALE_SELECTION".equals(id.check(1,actual[0],"R")));
   ok("STALE_AUTH".equals(id.check(2,"own-final","R")));
   ok("STALE_SELECTION".equals(id.check(1,"own-final","aliases-changed")));
  }
  System.out.println("AaResultIdentityTest "+checks+" checks passed");
 }
}

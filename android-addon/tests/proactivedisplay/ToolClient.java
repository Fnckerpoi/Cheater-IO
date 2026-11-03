package com.turboio.addon;
import android.content.Context;
import java.util.Map;
/** Fake durable host; production display/core/NavGlasses are compiled unchanged. */
final class ToolClient {
 DshProactiveReport.RoundIdentity current;
 Map<String,Object> remote;
 int queries; boolean networkFailure;
 ToolClient(){} ToolClient(Context context){}
 boolean isCurrentRound(DshProactiveReport.RoundIdentity round){synchronized(DshClient.LOCK){return round!=null&&round.equals(current);}}
 Map<String,Object> queryRound(DshProactiveReport.RoundIdentity round)throws Exception{
  if(Thread.holdsLock(DshClient.LOCK))throw new AssertionError("network under host lock");
  queries++;if(networkFailure)throw new java.io.IOException("offline");return remote;
 }
}

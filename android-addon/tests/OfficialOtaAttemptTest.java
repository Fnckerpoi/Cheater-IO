package com.turboio.addon;
public final class OfficialOtaAttemptTest {
 public static void main(String[] args){
  int checks=0;
  OtaProfile[] profiles={OtaProfile.TMG3,OtaProfile.STOCK127};
  for(OtaProfile p:profiles){if(OfficialOtaAttempt.pendingProfile(p.sha,p.token)!=p)throw new AssertionError("lost pending identity");checks++;
   if(OfficialOtaAttempt.pendingProfile(p.sha,"wrong")!=null||OfficialOtaAttempt.pendingProfile("wrong",p.token)!=null)throw new AssertionError("identity weakened");checks++;
  }
  if(OfficialOtaAttempt.pendingProfile(OtaProfile.TMIG1.sha,OtaProfile.TMIG1.token)!=null)throw new AssertionError("unreviewed profile");checks++;
  for(int mask=0;mask<512;mask++){
   boolean[] v=new boolean[9];for(int n=0;n<9;n++)v[n]=(mask&(1<<n))!=0;
   boolean expected=v[0]&&v[1]&&v[2]&&v[3]&&v[4]&&v[5]&&v[6]&&!v[7]&&!v[8];
   if(OfficialOtaAttempt.failureAllowed(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8])!=expected)throw new AssertionError("failure policy mask "+mask);checks++;
  }
  boolean[] memory={true},disk={true};
  boolean committed=OfficialOtaAttempt.commitFailure(()->{memory[0]=false;return false;},()->{memory[0]=true;});
  disk[0]=memory[0];if(committed||!memory[0]||!disk[0])throw new AssertionError("commit failure escaped pending fence");checks++;
  if(!OfficialOtaAttempt.commitFailure(()->true,()->{throw new AssertionError("restored successful commit");}))throw new AssertionError("successful commit rejected");checks++;
  System.out.println("Official OTA attempt: "+checks+" identity/failure policy checks passed");
 }
}

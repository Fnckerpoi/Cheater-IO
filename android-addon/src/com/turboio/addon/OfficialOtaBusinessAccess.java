package com.turboio.addon;

/** Explicit memory lease for interactive non-OTA business traffic after OTA install pending.
 * Unlocks interactive business traffic ONLY after explicit user readback click confirms
 * home screen on the same peer, and fresh type1 + type11 replies pass.
 * Never persists across process restart; revokes immediately on peer disconnect/change,
 * new active OTA transfer, cancel, clock reversal, or negative boot evidence.
 */
public final class OfficialOtaBusinessAccess {
 private String peer="";
 private long requestedAt=-1;
 private long grantedAt=-1;
 private boolean granted;

 public synchronized void request(String targetPeer,long now){
  peer=targetPeer==null?"":targetPeer;
  requestedAt=now;
  grantedAt=-1;
  granted=false;
 }

 public synchronized void cancel(){
  peer="";
  requestedAt=-1;
  grantedAt=-1;
  granted=false;
 }

 public synchronized long requestedAt(){
  return requestedAt;
 }

 public synchronized boolean open(){
  return granted;
 }

 public synchronized boolean observe(String current,long now,boolean pending,boolean knownProfile,boolean sameSavedPeer,boolean foreground,boolean armed,boolean checking,OfficialOtaGate.Phase phase,boolean recoveryAllowed,boolean freshBootAfter,boolean bootEvidenceGood){
  if(current==null||current.isEmpty()||!current.equals(peer)){
   cancel();
   return false;
  }
  if(now<requestedAt){
   cancel();
   return false;
  }
  if(!pending||!knownProfile||!sameSavedPeer||!foreground||armed||checking){
   cancel();
   return false;
  }
  if(phase!=OfficialOtaGate.Phase.UNKNOWN&&phase!=OfficialOtaGate.Phase.INSTALLING){
   cancel();
   return false;
  }
  if(granted){
   if(now<grantedAt||!bootEvidenceGood){
    cancel();
    return false;
   }
   return true;
  }
  if(requestedAt>=0&&recoveryAllowed&&freshBootAfter){
   granted=true;
   grantedAt=now;
   return true;
  }
  return false;
 }
}

package com.turboio.addon;

final class AaBridgePolicy {
    /** Result attribution: only the statuses the DSH client really reports count as usable answers. */
    static boolean successful(String status){return "submitted".equals(status)||"completed".equals(status)||"ended".equals(status)||"session_selected".equals(status)||"running".equals(status)||"waiting_approval".equals(status)||"idle_no_reply".equals(status);}
    static boolean accepted(String status){return "submitted".equals(status);}
    static boolean completed(String status){return "completed".equals(status);}
    static boolean unknownDelivery(String status){return "unknown_delivery".equals(status);}

    /**
     * Request guard executed inside the DSH lock, before and at the pending-save boundary.
     * The expected session must still be the stored one, so a workbench that browses another
     * session can never send into the bound one.
     */
    static String check(long expectedAuth,long actualAuth,String expectedRevision,String actualRevision,String expectedSession,String actualSession){
        if(expectedAuth!=actualAuth)return "STALE_AUTH";
        if(expectedRevision==null||expectedRevision.isEmpty()||!expectedRevision.equals(actualRevision))return "STALE_SELECTION";
        if(!sessionMatches(expectedSession,actualSession))return "STALE_SELECTION";
        return "";
    }
    static String checkDispatch(long expectedAuth,long actualAuth,String expectedRevision,String actualRevision,String expectedSession,String actualSession,boolean newSession){
        if(newSession){
            String base=checkIdentity(expectedAuth,actualAuth,expectedRevision,actualRevision);
            if(!base.isEmpty())return base;
            return "".equals(expectedSession)&&"".equals(actualSession)?"":"STALE_SELECTION";
        }
        return check(expectedAuth,actualAuth,expectedRevision,actualRevision,expectedSession,actualSession);
    }
    private static String checkIdentity(long expectedAuth,long actualAuth,String expectedRevision,String actualRevision){
        if(expectedAuth!=actualAuth)return "STALE_AUTH";
        return expectedRevision!=null&&!expectedRevision.isEmpty()&&expectedRevision.equals(actualRevision)?"":"STALE_SELECTION";
    }
    /** A stored session may be empty only when the request explicitly asks for a new session. */
    static boolean sessionMatches(String expected,String actual){return expected!=null&&!expected.isEmpty()&&expected.equals(actual==null?"":actual);}
}

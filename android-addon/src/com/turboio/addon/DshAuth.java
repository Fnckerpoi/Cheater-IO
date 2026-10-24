package com.turboio.addon;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/** In-memory or persisted authorization transaction: survives process restarts within TTL using wall-clock time. */
public final class DshAuth {
    public static final String REDIRECT = "agents-anywhere://oauth/callback";
    public static final long TTL = 300000;
    private String state = "", verifier = "", origin = "";
    private long started, generation, epoch;
    private boolean consumed;

    public interface Storage {
        void save(String state, String verifier, String origin, long started, long epoch, long generation);
        void clear();
        String state();
        String verifier();
        String origin();
        long started();
        long epoch();
        long generation();
    }

    public static final class Grant {
        public final String code, verifier, origin;
        public final long generation, epoch;
        Grant(String code,String verifier,String origin,long generation,long epoch) {
            this.code=code;this.verifier=verifier;this.origin=origin;this.generation=generation;this.epoch=epoch;
        }
    }
    public static boolean capability(Map<String,Object> response){
        return "agents-anywhere-mobile".equals(response.get("client_id"))&&Boolean.TRUE.equals(response.get("available"))&&REDIRECT.equals(response.get("redirect_uri"))&&"/api/v2/oauth/token".equals(response.get("token_endpoint"))&&response.get("code_challenge_methods_supported") instanceof List&&((List<?>)response.get("code_challenge_methods_supported")).equals(Collections.singletonList("S256"));
    }
    private static String random() {
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public synchronized String begin(String server,long now) {
        return begin(server, now, 0, null);
    }
    public synchronized String begin(String server,long now,long ep,Storage storage) {
        String normalized=DshClient.normalizeBaseUrl(server);
        if(normalized==null)throw new IllegalArgumentException("invalid_origin");
        origin=normalized;state=random();verifier=random();started=now;epoch=ep;consumed=false;generation++;
        if(storage!=null) storage.save(state, verifier, origin, started, epoch, generation);
        return state;
    }
    public synchronized String challenge() {
        try {return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));}
        catch(Exception e){throw new IllegalStateException("pkce_failed");}
    }
    public synchronized long generation(){return generation;}
    public synchronized boolean current(long ticket){return ticket==generation;}
    public synchronized void cancel(){cancel(null);}
    public synchronized void cancel(Storage storage){
        generation++;state="";verifier="";origin="";consumed=true;
        if(storage!=null) storage.clear();
    }
    public synchronized Grant consume(String callback,String expectedOrigin,long now) {
        return consume(callback, expectedOrigin, now, null);
    }
    public synchronized Grant consume(String callback,String expectedOrigin,long now,Storage storage) {
        try {
            if(callback==null||callback.length()>8192)throw new IllegalArgumentException();
            URI uri=new URI(callback);
            if(!"agents-anywhere".equals(uri.getScheme())||!"oauth".equals(uri.getHost())||!"/callback".equals(uri.getRawPath())||uri.getRawUserInfo()!=null||uri.getPort()!=-1||uri.getRawFragment()!=null)throw new IllegalArgumentException();
            Map<String,String> args=new HashMap<>();
            if(uri.getRawQuery()==null)throw new IllegalArgumentException();
            for(String pair:uri.getRawQuery().split("&")) {
                String[] kv=pair.split("=",2);if(kv.length!=2)throw new IllegalArgumentException();
                String key=java.net.URLDecoder.decode(kv[0],"UTF-8"),value=java.net.URLDecoder.decode(kv[1],"UTF-8");
                if(args.put(key,value)!=null)throw new IllegalArgumentException();
            }
            boolean denied="access_denied".equals(args.get("error"));
            if(denied){if(!args.containsKey("state") || !args.keySet().stream().allMatch(k->Arrays.asList("error","error_description","state").contains(k)))throw new IllegalArgumentException();}
            else if(args.size()!=2||!args.containsKey("code")||!args.containsKey("state"))throw new IllegalArgumentException();
            
            String curState = state;
            String curVerifier = verifier;
            String curOrigin = origin;
            long curStarted = started;
            long curGen = generation;
            long curEpoch = epoch;
            if(curState.isEmpty() && storage != null) {
                curState = storage.state();
                curVerifier = storage.verifier();
                curOrigin = storage.origin();
                curStarted = storage.started();
                curGen = storage.generation();
                curEpoch = storage.epoch();
            }
            if(consumed||curState.isEmpty()||now<curStarted||now-curStarted>TTL||!curOrigin.equals(DshClient.normalizeBaseUrl(expectedOrigin))||!MessageDigest.isEqual(curState.getBytes(StandardCharsets.UTF_8),args.get("state").getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException();
            if(denied){cancel(storage);throw new IllegalArgumentException("denied");}
            String code=args.get("code");if(code.isEmpty()||code.length()>2048||code.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException();
            consumed=true;Grant grant=new Grant(code,curVerifier,curOrigin,curGen,curEpoch);state="";verifier="";
            if(storage!=null) storage.clear();
            return grant;
        }catch(Exception e){throw new IllegalArgumentException("invalid_auth_callback");}
    }
}

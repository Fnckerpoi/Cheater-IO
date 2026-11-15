package com.turboio.addon;

import org.json.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reaches the search service on networks whose DNS does not answer for its host: the name is resolved over HTTPS and the request goes to the address, with the certificate still verified for the host. */
final class SearchRoute {
    static final String HOST="api.search.tinyfish.ai";
    private static final String[] RESOLVERS={"https://223.5.5.5/resolve","https://223.6.6.6/resolve","https://doh.pub/dns-query"};
    private static volatile long systemLookupRetryAt;
    private static volatile String[] cachedAddresses;
    private static volatile long cachedAt;

    /** A service answer is the same on every address, so it is reported once; only transport failures move on. */
    static final class ServiceAnswer extends IOException { ServiceAnswer(String message) { super(message); } }

    /** A failed system lookup takes many seconds, so callers go straight to get() for ten minutes after one. */
    static boolean systemLookupFailing() { return System.currentTimeMillis()<systemLookupRetryAt; }
    static void systemLookupFailed() { systemLookupRetryAt=System.currentTimeMillis()+600000; }

    /** Answers whether the platform resolver can name the host, without waiting longer than the caller should. */
    static boolean systemResolves() {
        if(systemLookupFailing()) return false;
        final AtomicBoolean answered=new AtomicBoolean(false);
        Thread lookup=new Thread(()->{try{answered.set(InetAddress.getAllByName(HOST).length>0);}catch(Exception ignored){}},"TurboIO-search-dns");
        lookup.setDaemon(true); lookup.start();
        try { lookup.join(2500); } catch(InterruptedException ignored) { Thread.currentThread().interrupt(); }
        if(!answered.get()) systemLookupFailed();
        return answered.get();
    }

    static JSONObject get(String path,String key) throws Exception {
        if(key.matches("(?s).*[\\p{Cntrl}].*")) throw new IllegalArgumentException();
        IOException failure=new UnknownHostException(HOST);
        for(String address:addresses()) {
            try(Socket plain=new Socket()) {
                try { plain.connect(new InetSocketAddress(InetAddress.getByName(address),443),12000); }
                catch(IOException unreachable) { failure=unreachable; continue; }
                SSLSocket socket=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(plain,HOST,443,true);
                socket.setSoTimeout(20000);
                SSLParameters parameters=socket.getSSLParameters(); parameters.setEndpointIdentificationAlgorithm("HTTPS"); socket.setSSLParameters(parameters);
                socket.startHandshake();
                OutputStream out=socket.getOutputStream();
                out.write(("GET "+path+" HTTP/1.0\r\nHost: "+HOST+"\r\nX-API-Key: "+key+"\r\nAccept: application/json\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1)); out.flush();
                return body(socket.getInputStream());
            } catch(ServiceAnswer answered) { throw answered; }
            catch(IOException refused) { failure=refused; }
        }
        throw failure;
    }

    static List<String> addresses() throws IOException {
        String[] known=cachedAddresses;
        if(known!=null&&System.currentTimeMillis()-cachedAt<60000) return Arrays.asList(known);
        for(String resolver:RESOLVERS) {
            HttpURLConnection connection=(HttpURLConnection)new URL(resolver+"?name="+HOST+"&type=A").openConnection();
            try {
                connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
                connection.setRequestProperty("Accept","application/dns-json");
                if(connection.getResponseCode()!=200) continue;
                List<String> found;
                try(InputStream in=connection.getInputStream()) { found=addresses(new String(read(in,65536),StandardCharsets.UTF_8)); }
                if(!found.isEmpty()) { cachedAddresses=found.toArray(new String[0]); cachedAt=System.currentTimeMillis(); return found; }
            } catch(IOException|JSONException unanswered) {
            } finally { connection.disconnect(); }
        }
        throw new UnknownHostException(HOST);
    }

    static List<String> addresses(String answer) throws JSONException {
        List<String> found=new ArrayList<>(); JSONArray rows=new JSONObject(answer).optJSONArray("Answer");
        for(int i=0;rows!=null&&i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i); String data=row==null?"":row.optString("data","");
            if(row!=null&&row.optInt("type")==1&&data.matches("((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)")) found.add(data);
        }
        return found;
    }

    static JSONObject body(InputStream stream) throws Exception {
        InputStream in=new BufferedInputStream(stream); String[] status=line(in).split(" ",3);
        if(status.length<2||!status[0].startsWith("HTTP/1.")||!status[1].matches("\\d{3}")) throw new IOException("status");
        if(!status[1].equals("200")&&!status[1].equals("202")) throw new ServiceAnswer("HTTP "+status[1]);
        while(!line(in).isEmpty()) {}
        return new JSONObject(new String(read(in,1048576),StandardCharsets.UTF_8));
    }
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); int c;
        while((c=in.read())!=-1&&c!='\n') { if(out.size()>=8192) throw new IOException("limit"); if(c!='\r') out.write(c); }
        return out.toString("ISO-8859-1");
    }
    private static byte[] read(InputStream in,int cap) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] block=new byte[8192]; int n;
        while((n=in.read(block))!=-1) { if(out.size()+n>cap) throw new IOException("limit"); out.write(block,0,n); }
        return out.toByteArray();
    }
}

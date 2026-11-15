package com.turboio.addon;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class SearchRouteTest {
 static int passed,failed;
 interface Check {void run()throws Exception;}
 static void test(String name,Check c){try{c.run();passed++;System.out.println("PASS "+name);}catch(AssertionError|Exception e){failed++;System.out.println("FAIL "+name+": "+e);}}
 static void eq(Object a,Object b){if(!a.equals(b))throw new AssertionError("expected "+a+" got "+b);}
 static InputStream wire(String text){return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));}
 static void refuses(String message,Check c)throws Exception{try{c.run();}catch(IOException expected){eq(message,expected.getMessage());return;}throw new AssertionError("accepted");}
 public static void main(String[] args){
  test("reads_every_ipv4_answer",()->eq(Arrays.asList("203.0.113.7","203.0.113.8"),SearchRoute.addresses("{\"Status\":0,\"Answer\":[{\"name\":\"api.search.tinyfish.ai.\",\"TTL\":60,\"type\":1,\"data\":\"203.0.113.7\"},{\"name\":\"api.search.tinyfish.ai.\",\"type\":1,\"TTL\":60,\"data\":\"203.0.113.8\"}]}")));
  test("skips_aliases_and_names",()->eq(Collections.singletonList("203.0.113.9"),SearchRoute.addresses("{\"Status\":0,\"Answer\":[{\"type\":5,\"data\":\"edge.example.net.\"},{\"type\":1,\"data\":\"edge.example.net\"},{\"type\":1,\"data\":\"999.0.113.9\"},{\"type\":1,\"data\":\"203.0.113.9\"}]}")));
  test("no_answer_is_empty",()->eq(Collections.emptyList(),SearchRoute.addresses("{\"Status\":2,\"Question\":{\"name\":\"api.search.tinyfish.ai.\",\"type\":1}}")));
  test("returns_json_body_after_headers",()->{JSONObject data=SearchRoute.body(wire("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"results\":[{\"title\":\"中山天气\"}]}"));eq("中山天气",data.getJSONArray("results").getJSONObject(0).getString("title"));});
  test("reports_http_status",()->refuses("HTTP 401",()->SearchRoute.body(wire("HTTP/1.1 401 Unauthorized\r\nContent-Length: 2\r\n\r\n{}"))));
  test("service_answer_is_distinct_from_transport_failure",()->{try{SearchRoute.body(wire("HTTP/1.1 403 Forbidden\r\n\r\n{}"));throw new AssertionError("accepted");}catch(SearchRoute.ServiceAnswer expected){eq("HTTP 403",expected.getMessage());}catch(Exception other){throw new AssertionError("wrong failure "+other);}});
  test("refuses_non_http_reply",()->refuses("status",()->SearchRoute.body(wire("SSH-2.0-OpenSSH\r\n"))));
  test("bounds_body_size",()->{char[] fill=new char[1048577];Arrays.fill(fill,' ');refuses("limit",()->SearchRoute.body(wire("HTTP/1.1 200 OK\r\n\r\n"+new String(fill))));});
  test("remembers_failed_system_lookup",()->{eq(false,SearchRoute.systemLookupFailing());SearchRoute.systemLookupFailed();eq(true,SearchRoute.systemLookupFailing());});
  test("answers_platform_lookup_within_budget",()->{long t=System.currentTimeMillis();boolean resolved=SearchRoute.systemResolves();long spent=System.currentTimeMillis()-t;if(resolved)eq(true,resolved);else eq(true,spent<6000);});
  System.out.println(passed+" passed, "+failed+" failed");if(failed>0)System.exit(1);
 }
}

package com.turboio.addon;
import java.util.*;
import java.io.IOException;
public final class DshSettingsPolicyTest {
 static int checks;
 static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  DshDirectory.Option a=new DshDirectory.Option("a","A"),b=new DshDirectory.Option("b","B");
  List<DshDirectory.Option> two=Arrays.asList(a,b),one=Collections.singletonList(a);
  check(DshSettings.Policy.find(two,"a")==a);
  check(DshSettings.Policy.find(two,"missing")==null);
  check(DshSettings.Policy.find(two,"")==null);
  check(DshSettings.Policy.find(null,"a")==null);
  check(DshSettings.Policy.runtime(two,null)==null);
  check(DshSettings.Policy.runtime(two,"b")==b);
  check(DshSettings.Policy.runtime(two,"missing")==null);
  check(DshSettings.Policy.runtime(one,null)==a);
  check(DshSettings.Policy.runtime(Collections.<DshDirectory.Option>emptyList(),"a")==null);
  check(DshSettings.Policy.directoryError(new DshClient.HttpStatusException(401)).contains("401"));
  check(DshSettings.Policy.directoryError(new DshClient.HttpStatusException(403)).contains("403"));
  check(DshSettings.Policy.directoryError(new DshClient.HttpStatusException(500)).contains("500"));
  check(DshSettings.Policy.directoryError(new IOException("SECRET")).contains("网络"));
  check(!DshSettings.Policy.directoryError(new IOException("SECRET")).contains("SECRET"));
  check(DshSettings.Policy.directoryError(new IllegalArgumentException("SECRET")).contains("格式"));
  Map<String,Object> row=new HashMap<>();row.put("connectorId","c");row.put("runtimeType","dsh");row.put("runtimeId","a");row.put("status","stopped");row.put("available",true);row.put("configured",true);row.put("active",true);
  check(DshDirectory.runtimeCards(Collections.singletonList(row),"c").size()==1);
  check(DshSettings.Policy.runtime(DshDirectory.runtimes(Collections.singletonList(row),"c"),null)==null);
  row.put("status","running");check(DshSettings.Policy.runtime(DshDirectory.runtimes(Collections.singletonList(row),"c"),null).id.equals("a"));
  System.out.println("DshSettingsPolicyTest: "+checks+" passed");
 }
}

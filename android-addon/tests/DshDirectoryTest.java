package com.turboio.addon;
import java.util.*;
public final class DshDirectoryTest {
 static int n;static void ok(boolean b){if(!b)throw new AssertionError("directory "+n);n++;}
 static Map<String,Object> m(Object...x){return DshClient.mapOf(x);}
 public static void main(String[] args){
  DshDirectory d=new DshDirectory();long first=d.connector("c");d.project("p");long second=d.runtime("r");ok(!d.current(first));ok(d.current(second));ok("p".equals(d.project()));d.connector("other");ok(d.project().isEmpty()&&d.runtime().isEmpty());
  ok(DshDirectory.projects(Arrays.asList(m("id","p","connectorId","c","name","Project","path","C:/Project")),"other").isEmpty());
  Map<String,Object> runtime=m("connectorId","c","runtimeId","r","runtimeType","dsh","status","running","active",true,"available",true,"configured",true,"displayName","DSH");
  ok(DshDirectory.runtimes(Arrays.asList(runtime),"c").size()==1);runtime.put("status","starting");ok(DshDirectory.runtimes(Arrays.asList(runtime),"c").isEmpty());
  String id="dsh:model:WyJtYWdwaWUiLCJncm91cC9zbWFydC1zcGxpdCIsbnVsbF0";
  Map<String,Object> response=m("catalog",m("runtime","dsh","models",Arrays.asList(m("displayName","Model","selectionId",null,"reasoningItems",Arrays.asList(m("displayName","Off","selectionId",id))))));
  List<DshDirectory.Option> options=DshDirectory.catalog(response,true);ok(options.size()==1&&id.equals(options.get(0).id)&&options.get(0).label.equals("Model")&&options.get(0).subtitle.equals("Off"));
  try{DshDirectory.catalog(m("catalog",m("runtime","other","models",Collections.emptyList())),true);throw new AssertionError();}catch(IllegalArgumentException e){n++;}
  d.connector("A");d.project("P");d.runtime("R");ok(d.matches("A","P","R"));long ticket=d.generation();d.connector("");ok(!d.matches("A","P","R")&&!d.current(ticket)&&d.project().isEmpty()&&d.runtime().isEmpty());
  d.connector("A");d.project("P");d.runtime("R");d.project("");ok(!d.matches("A","P","R"));
  d.project("P");ticket=d.generation();d.runtime("");ok(!d.matches("A","P","R")&&!d.current(ticket));
  d.runtime("R");ticket=d.generation();d.reset();ok(!d.matches("A","P","R")&&!d.current(ticket));
  // Queue callbacks until after requests capture their generation.
  ArrayDeque<Runnable> queue=new ArrayDeque<>();DshDirectory controller=new DshDirectory();
  controller.reset();long initial=controller.generation();queue.add(()->controller.connector(""));queue.add(()->controller.runtime(""));while(!queue.isEmpty())queue.remove().run();ok(controller.current(initial));
  controller.connector("A");controller.project("P");controller.runtime("R");controller.reset();long refresh=controller.generation();queue.add(()->controller.connector(""));queue.add(()->controller.runtime(""));while(!queue.isEmpty())queue.remove().run();ok(controller.current(refresh));
  controller.connector("B");long children=controller.generation();queue.add(()->controller.runtime(""));queue.add(()->controller.project(""));while(!queue.isEmpty())queue.remove().run();ok(controller.current(children));
  // Response rebuild automatically selects the existing empty placeholder.
  controller.runtime("");controller.connector("B");ok(controller.current(children));
  controller.project("P");controller.runtime("R");long catalog=controller.generation();controller.runtime("");ok(!controller.current(catalog)&&controller.runtime().isEmpty());
  long parent=controller.generation();controller.connector("");ok(!controller.current(parent)&&controller.connector().isEmpty());
  DshDirectory.SelectionGate gate=new DshDirectory.SelectionGate();Object oldAdapter=new Object(),newAdapter=new Object();gate.bind(oldAdapter);gate.bind(newAdapter);
  ok(!gate.accepts(oldAdapter,newAdapter,0,0));ok(gate.accepts(newAdapter,newAdapter,0,0));ok(!gate.accepts(newAdapter,newAdapter,0,1));
  // Card selector: projects() must surface workspacePath as the subtitle; fall back to path; never end with " · ".
  List<DshDirectory.Option> withWorkspace=DshDirectory.projects(Arrays.asList(m("id","p1","connectorId","c","name","Project","workspacePath","C:/repo/workspace")),"c");
  ok(withWorkspace.size()==1&&"Project".equals(withWorkspace.get(0).label)&&"C:/repo/workspace".equals(withWorkspace.get(0).subtitle));
  List<DshDirectory.Option> withPath=DshDirectory.projects(Arrays.asList(m("id","p2","connectorId","c","name","Project","path","C:/repo/path")),"c");
  ok(withPath.size()==1&&"Project".equals(withPath.get(0).label)&&"C:/repo/path".equals(withPath.get(0).subtitle));
  List<DshDirectory.Option> noPath=DshDirectory.projects(Arrays.asList(m("id","p3","connectorId","c","name","Project")),"c");
  ok(noPath.size()==1&&"Project".equals(noPath.get(0).label)&&noPath.get(0).subtitle.isEmpty()&&!noPath.get(0).toString().endsWith(" · "));
  List<DshDirectory.Option> workingDir=DshDirectory.projects(Arrays.asList(m("id","p4","connectorId","c","name","Project","workingDirectory","C:/repo/wd")),"c");
  ok(workingDir.size()==1&&"C:/repo/wd".equals(workingDir.get(0).subtitle));
  // Card selector: routing-group rows without reasoningItems survive as selectable options.
  String routingId="dsh:model:WyJtYWdwaWUiLCJncm91cC9zbWFydC1zcGxpdCIsbnVsbF0";
  Map<String,Object> routingResponse=m("catalog",m("runtime","dsh","models",Arrays.asList(
    m("displayName","难题给强模型，简单的给便宜模型","selectionId",routingId),
    m("displayName","NoSelection","selectionId",null),
    m("displayName","BadSelection","selectionId","not-a-selection"))));
  List<DshDirectory.Option> routing=DshDirectory.catalog(routingResponse,true);
  ok(routing.size()==1&&routingId.equals(routing.get(0).id)&&!routing.get(0).label.isEmpty()&&routing.get(0).label.equals("难题给强模型，简单的给便宜模型"));
  // Runtime cards keep unavailable DSH runtimes visible with a state note instead of dropping them.
  Map<String,Object> offline=m("connectorId","c","runtimeId","r2","runtimeType","dsh","status","starting","active",true,"available",false,"configured",true,"displayName","DSH 备用");
  List<DshDirectory.Option> cards=DshDirectory.runtimeCards(Arrays.asList(offline),"c");
  ok(cards.size()==1&&"r2".equals(cards.get(0).id)&&cards.get(0).subtitle.contains("starting")&&cards.get(0).subtitle.contains("无可用运行时"));
  Map<String,Object> healthy=m("connectorId","c","runtimeId","r1","runtimeType","dsh","status","running","active",true,"available",true,"configured",true,"displayName","DSH");
  List<DshDirectory.Option> healthyCards=DshDirectory.runtimeCards(Arrays.asList(healthy),"c");
  ok(healthyCards.size()==1&&healthyCards.get(0).subtitle.equals("dsh · running"));
  System.out.println("PASS "+n+" directory checks");
 }
}

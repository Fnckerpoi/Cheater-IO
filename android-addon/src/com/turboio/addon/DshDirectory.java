package com.turboio.addon;

import java.util.*;

/** Account and parent selection generations bind every asynchronous directory result. */
public final class DshDirectory {
    private long generation;
    private String connector="",project="",runtime="";
    public synchronized long reset(){connector=project=runtime="";return ++generation;}
    public synchronized long connector(String id){if(connector.equals(id))return generation;connector=id;project=runtime="";return ++generation;}
    public synchronized long runtime(String id){if(runtime.equals(id))return generation;runtime=id;return ++generation;}
    public synchronized void project(String id){project=id;}
    public synchronized long generation(){return generation;}
    public synchronized boolean current(long ticket){return ticket==generation;}
    public synchronized boolean matches(String c,String p,String r){return !connector.isEmpty()&&!project.isEmpty()&&!runtime.isEmpty()&&connector.equals(c)&&project.equals(p)&&runtime.equals(r);}
    public synchronized String connector(){return connector;}
    public synchronized String project(){return project;}
    public synchronized String runtime(){return runtime;}
    public static final class SelectionGate {
        private Object adapter;
        public void bind(Object value){adapter=value;}
        public boolean accepts(Object eventAdapter,Object currentAdapter,int eventPosition,int currentPosition){return adapter==eventAdapter&&adapter==currentAdapter&&eventPosition==currentPosition;}
    }
    public static final class Option {
        public final String id,label,subtitle;
        public Option(String id,String label){this(id,label,"");}
        public Option(String id,String label,String subtitle){this.id=id;this.label=label;this.subtitle=subtitle==null?"":subtitle;}
        @Override public String toString(){return label;}
    }
    @SuppressWarnings("unchecked")
    public static List<Map<String,Object>> rows(Map<String,Object> response,String key){
        Object value=response.get(key);
        if(!(value instanceof List))throw new IllegalArgumentException("invalid_directory_response");
        List<Map<String,Object>> result=new ArrayList<>();
        for(Object row:(List<?>)value){if(!(row instanceof Map))throw new IllegalArgumentException("invalid_directory_row");result.add((Map<String,Object>)row);}
        return result;
    }
    public static String text(Map<String,Object> row,String key){Object value=row.get(key);return value instanceof String?(String)value:"";}
    public static List<Option> projects(List<Map<String,Object>> rows,String connector){
        List<Option> result=new ArrayList<>();
        for(Map<String,Object> row:rows)if(connector.equals(text(row,"connectorId"))&&!text(row,"id").isEmpty()){
            String name=text(row,"name");
            String path=firstNonEmpty(text(row,"workspacePath"),text(row,"path"),text(row,"workingDirectory"));
            result.add(new Option(text(row,"id"),name,path));
        }
        return result;
    }
    private static String firstNonEmpty(String... values){for(String value:values)if(value!=null&&!value.isEmpty())return value;return "";}
    /** Card selector needs every DSH runtime of the connector; state goes to the subtitle. */
    public static List<Option> runtimeCards(List<Map<String,Object>> rows,String connector){
        List<Option> result=new ArrayList<>();
        for(Map<String,Object> row:rows){
            if(!connector.equals(text(row,"connectorId"))||!"dsh".equals(text(row,"runtimeType"))||text(row,"runtimeId").isEmpty())continue;
            String name=firstNonEmpty(text(row,"displayName"),text(row,"name"),text(row,"runtimeId"));
            String status=text(row,"status").isEmpty()?"未知状态":text(row,"status");
            boolean usable="running".equals(text(row,"status"))&&Boolean.TRUE.equals(row.get("available"))&&Boolean.TRUE.equals(row.get("configured"))&&Boolean.TRUE.equals(row.get("active"));
            result.add(new Option(text(row,"runtimeId"),name,usable?"dsh · "+status:"dsh · "+status+"（无可用运行时）"));
        }
        return result;
    }
    public static List<Option> runtimes(List<Map<String,Object>> rows,String connector){
        List<Option> result=new ArrayList<>();
        for(Map<String,Object> row:rows)if(connector.equals(text(row,"connectorId"))&&"dsh".equals(text(row,"runtimeType"))&&"running".equals(text(row,"status"))&&Boolean.TRUE.equals(row.get("available"))&&Boolean.TRUE.equals(row.get("configured"))&&Boolean.TRUE.equals(row.get("active"))&&!text(row,"runtimeId").isEmpty())result.add(new Option(text(row,"runtimeId"),text(row,"displayName").isEmpty()?text(row,"name"):text(row,"displayName")));
        return result;
    }
    @SuppressWarnings("unchecked")
    public static List<Option> catalog(Map<String,Object> response,boolean model){
        Object raw=response.get("catalog");if(!(raw instanceof Map))throw new IllegalArgumentException("missing_catalog");
        Map<String,Object> catalog=(Map<String,Object>)raw;
        if(!"dsh".equals(text(catalog,"runtime")))throw new IllegalArgumentException("wrong_runtime");
        List<Option> result=new ArrayList<>();
        for(Map<String,Object> row:rows(catalog,model?"models":"permissions")){
            String label=text(row,"displayName"),selection=text(row,"selectionId").isEmpty()?text(row,"id"):text(row,"selectionId");
            if(label.isEmpty())throw new IllegalArgumentException("invalid_selection");
            if(!model){Map<String,Object> metadata=row.get("metadata") instanceof Map?(Map<String,Object>)row.get("metadata"):Collections.emptyMap();if(!Boolean.TRUE.equals(metadata.get("enabled")))continue;}
            Object efforts=row.get("reasoningItems");
            if(model&&efforts instanceof List&&!((List<?>)efforts).isEmpty()){
                for(Map<String,Object> effort:rows(row,"reasoningItems")){
                    String id=text(effort,"selectionId"),name=text(effort,"displayName");
                    if(!DshClient.validSelection(id,true)||name.isEmpty())continue;
                    result.add(new Option(id,label,name));
                }
            }else {if(!DshClient.validSelection(selection,model))continue;result.add(new Option(selection,label,text(row,"description")));}
        }
        return result;
    }
}


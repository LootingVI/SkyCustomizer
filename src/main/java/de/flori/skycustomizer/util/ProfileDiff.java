package de.flori.skycustomizer.util;

import com.google.gson.*;
import java.util.*;

public final class ProfileDiff {
    public record Change(String path,String before,String after) {}
    private ProfileDiff() {}
    public static List<Change> compare(JsonElement before,JsonElement after){List<Change> out=new ArrayList<>();walk("",before,after,out);return List.copyOf(out);}
    private static void walk(String path,JsonElement a,JsonElement b,List<Change> out){
        if(Objects.equals(a,b))return;
        if(a!=null&&b!=null&&a.isJsonObject()&&b.isJsonObject()){
            Set<String> keys=new TreeSet<>(a.getAsJsonObject().keySet());keys.addAll(b.getAsJsonObject().keySet());
            for(String key:keys)walk(path.isEmpty()?key:path+"."+key,a.getAsJsonObject().get(key),b.getAsJsonObject().get(key),out);
        }else out.add(new Change(path,a==null?"(missing)":a.toString(),b==null?"(missing)":b.toString()));
    }
}

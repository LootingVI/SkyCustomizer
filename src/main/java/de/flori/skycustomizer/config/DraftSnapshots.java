package de.flori.skycustomizer.config;

import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;

final class DraftSnapshots {
    private static final Gson JSON = new Gson();
    static String capture(Object target, String... names) {
        JsonObject state = new JsonObject();
        try { for (String name : names) { Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);state.add(name,JSON.toJsonTree(f.get(target))); } }
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        return state.toString();
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    static void restore(Object target,String json,String... names) {
        JsonObject state=JsonParser.parseString(json).getAsJsonObject();
        try { for(String name:names){Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);
            Object value=JSON.fromJson(state.get(name),f.getGenericType());
            if(Modifier.isFinal(f.getModifiers())) {
                Object current=f.get(target);
                if(current instanceof Map m){m.clear();m.putAll((Map)value);}
                else if(current instanceof Collection c){c.clear();c.addAll((Collection)value);}
                else if(current.getClass().isArray())System.arraycopy(value,0,current,0,Array.getLength(current));
            } else f.set(target,value);
        }}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
}

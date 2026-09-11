package de.flori.skycustomizer.util;

public final class GradientText {
    private GradientText() {}
    public static String create(String text,String start,String end){
        int[] points=text.codePoints().toArray();StringBuilder out=new StringBuilder();
        for(int i=0;i<points.length;i++){
            float t=points.length<2?0:(float)i/(points.length-1);
            out.append('&').append(ColorUtil.toHex(ColorUtil.lerp(start,end,t))).appendCodePoint(points[i]);
        }return out.toString();
    }
}

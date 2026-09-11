import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

public class GenerateUiAssets {
    static final Path ROOT = Path.of("src/main/resources/assets/skycustomizer/textures/gui");
    public static void main(String[] args) throws Exception {
        Files.createDirectories(ROOT);
        for (boolean border : new boolean[]{false,true}) {
            BufferedImage image = new BufferedImage(96,96,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = graphics(image); g.setColor(Color.WHITE);
            if (border) { g.setStroke(new BasicStroke(4)); g.draw(new RoundRectangle2D.Float(2,2,92,92,60,60)); }
            else g.fill(new RoundRectangle2D.Float(0,0,96,96,64,64));
            g.dispose(); ImageIO.write(image,"png",ROOT.resolve(border ? "border.png" : "panel.png").toFile());
        }
        for (int i=0;i<12;i++) {
            BufferedImage image = new BufferedImage(96,96,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = graphics(image); g.scale(4,4); g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            switch(i) {
                case 0 -> { g.draw(new Ellipse2D.Float(6,6,12,12)); g.draw(new Ellipse2D.Float(9.5f,9.5f,5,5)); for(int n=0;n<8;n++){double a=n*Math.PI/4;g.draw(new Line2D.Double(12+6*Math.cos(a),12+6*Math.sin(a),12+9*Math.cos(a),12+9*Math.sin(a)));} }
                case 1 -> { g.draw(path(3,7,14,7,21,12,14,17,3,17,3,7)); g.draw(new Ellipse2D.Float(6,10.5f,3,3)); }
                case 2 -> { Path2D p=new Path2D.Float();for(int n=0;n<10;n++){double a=n*Math.PI/5-Math.PI/2,r=n%2==0?9:4; if(n==0)p.moveTo(12+Math.cos(a)*r,12+Math.sin(a)*r);else p.lineTo(12+Math.cos(a)*r,12+Math.sin(a)*r);}p.closePath();g.draw(p); }
                case 3 -> { for(int y:new int[]{5,12,19}){g.fill(new Ellipse2D.Float(3,y-1,2,2));g.drawLine(8,y,21,y);} }
                case 4 -> { g.draw(path(7,3,17,3,22,9,12,22,2,9,7,3));g.drawLine(2,9,22,9);g.draw(path(7,3,9,9,12,22,15,9,17,3)); }
                case 5 -> { g.draw(path(12,2,21,6,20,14,17,19,12,22,7,19,4,14,3,6,12,2));g.draw(path(8,12,11,15,17,9)); }
                case 6 -> { g.draw(new RoundRectangle2D.Float(3,3,18,14,5,5));g.draw(path(8,17,7,22,14,17));g.drawLine(7,8,17,8);g.drawLine(7,12,14,12); }
                case 7 -> { Path2D p=new Path2D.Float();p.moveTo(2,12);p.curveTo(7,2,17,2,22,12);p.curveTo(17,22,7,22,2,12);g.draw(p);g.draw(new Ellipse2D.Float(8,8,8,8)); }
                case 8 -> { for(int x:new int[]{3,14}) for(int y:new int[]{3,14})g.draw(new RoundRectangle2D.Float(x,y,7,7,2,2)); }
                case 9 -> { g.drawLine(6,6,18,18);g.drawLine(6,18,18,6); }
                case 10 -> { g.draw(new Ellipse2D.Float(3,3,12,12));g.drawLine(14,14,21,21); }
                case 11 -> { g.draw(path(3,18,7,11,11,14,16,4,21,9));g.drawLine(3,22,21,22); }
            }
            g.dispose();ImageIO.write(image,"png",ROOT.resolve("icon_"+i+".png").toFile());
        }
    }
    static Graphics2D graphics(BufferedImage image){Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);return g;}
    static Path2D path(float... xy){Path2D p=new Path2D.Float();p.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)p.lineTo(xy[i],xy[i+1]);return p;}
}

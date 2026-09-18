package online.ui;

import common.system.fake.FakeImage;
import common.system.fake.ImageBuilder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loader for the original Tobidasu! Battle Cats multiplayer UI textures.
 *
 * The source files are Nintendo CGFX/BCTEX containers.  The cut rectangles are
 * read from the matching imgcut CSVs rather than duplicated in Java code.
 */
public final class Pvp3dsAssets {
    private static final String ROOT="/online/pvp3ds/";
    private static final int[] TILE_ORDER={
            0,1,4,5,2,3,6,7,
            8,9,12,13,10,11,14,15
    };
    private static final int[][] ETC1_MODIFIERS={
            {2,8},{5,17},{9,29},{13,42},{18,60},{24,80},{33,106},{47,183}
    };
    private static final Map<String,Sheet> SHEETS=new HashMap<>();
    private static final Map<String,FakeImage> FAKE_IMAGES=new HashMap<>();
    private static String lastError;

    private Pvp3dsAssets(){}

    public static synchronized boolean available(){
        try{
            image("ui_battle_multi_icon","アイコン：ふっとばし");
            image("ui_battle_multi_reel","効果名：ふっとばし");
            image("ui_battle_multi_cutin","ふっとばし発動!");
            image("ui_battle_multi","ルーレット点灯中ランプ");
            lastError=null;
            return true;
        }catch(RuntimeException e){
            lastError=e.toString();
            System.err.println("BCU PvP 3DS assets: "+lastError);
            return false;
        }
    }

    public static synchronized String diagnostic(){return lastError==null?"OK":lastError;}

    public static synchronized BufferedImage image(String sheet,String label){
        Sheet s=SHEETS.get(sheet);
        if(s==null){s=load(sheet);SHEETS.put(sheet,s);}
        Rectangle r=s.parts.get(label);
        if(r==null)throw new IllegalArgumentException("Unknown 3DS imgcut part: "+sheet+" / "+label);
        return s.image.getSubimage(r.x,r.y,r.width,r.height);
    }

    /** Small generated label used above the full roulette gauge. */
    public static synchronized FakeImage tapImage(){
        String key="#generated/TAP";
        FakeImage cached=FAKE_IMAGES.get(key);
        if(cached!=null)return cached;
        BufferedImage image=new BufferedImage(160,64,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        try{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Font font=new Font(Font.SANS_SERIF,Font.BOLD,46);
            g.setFont(font);
            FontMetrics fm=g.getFontMetrics();
            String text="TAP!";
            int x=(image.getWidth()-fm.stringWidth(text))/2;
            int y=(image.getHeight()-fm.getHeight())/2+fm.getAscent();
            g.setStroke(new BasicStroke(7f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            java.awt.font.GlyphVector gv=font.createGlyphVector(g.getFontRenderContext(),text);
            Shape outline=gv.getOutline(x,y);
            g.setColor(Color.BLACK);g.draw(outline);
            g.setColor(Color.WHITE);g.fill(outline);
        }finally{g.dispose();}
        cached=buildFake(image,key);
        return cached;
    }

    /** Cached renderer-native view of an imgcut part for BattleBox/FakeGraphics. */
    public static synchronized FakeImage fakeImage(String sheet,String label){
        String key=sheet+"\n"+label;
        FakeImage result=FAKE_IMAGES.get(key);
        if(result!=null)return result;
        if(ImageBuilder.builder==null)throw new IllegalStateException("ImageBuilder is not initialized");
        result=buildFake(image(sheet,label),key);
        return result;
    }

    private static FakeImage buildFake(BufferedImage image,String key){
        if(ImageBuilder.builder==null)throw new IllegalStateException("ImageBuilder is not initialized");
        try{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            if(!ImageIO.write(image,"png",out))throw new IOException("PNG writer is unavailable");
            byte[] png=out.toByteArray();
            FakeImage result=ImageBuilder.builder.build(() -> new ByteArrayInputStream(png));
            FAKE_IMAGES.put(key,result);
            return result;
        }catch(IOException e){
            throw new IllegalStateException("Cannot build PvP render image: "+key,e);
        }
    }

    static synchronized void clearForTests(){SHEETS.clear();FAKE_IMAGES.clear();}

    private static Sheet load(String name){
        try(InputStream raw=Pvp3dsAssets.class.getResourceAsStream(ROOT+name+".bctex");
            InputStream cut=Pvp3dsAssets.class.getResourceAsStream(ROOT+name+".imgcut.csv")){
            if(raw==null||cut==null)throw new IOException("Missing PvP 3DS resource: "+name);
            byte[] bytes=readAll(raw);
            BufferedImage image=decodeBctex(bytes);
            Map<String,Rectangle> parts=parseImgcut(cut,image.getWidth(),image.getHeight());
            return new Sheet(image,parts);
        }catch(IOException e){
            throw new IllegalStateException("Cannot load PvP 3DS asset "+name,e);
        }
    }

    private static byte[] readAll(InputStream in)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))>=0)out.write(buf,0,n);
        return out.toByteArray();
    }

    private static Map<String,Rectangle> parseImgcut(InputStream in,int width,int height)throws IOException{
        BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
        String magic=stripBom(r.readLine());
        if(!"[imgcut]".equals(magic))throw new IOException("Invalid imgcut header");
        if(r.readLine()==null)throw new IOException("Invalid imgcut version");
        if(r.readLine()==null)throw new IOException("Missing imgcut image name");
        String countLine=r.readLine();
        if(countLine==null)throw new IOException("Missing imgcut count");
        int count=Integer.parseInt(countLine.trim());
        Map<String,Rectangle> result=new LinkedHashMap<>();
        for(int i=0;i<count;i++){
            String line=r.readLine();
            if(line==null)throw new IOException("Truncated imgcut");
            String[] p=line.split(",",5);
            if(p.length!=5)throw new IOException("Invalid imgcut row: "+line);
            int x=Integer.parseInt(p[0]),y=Integer.parseInt(p[1]),w=Integer.parseInt(p[2]),h=Integer.parseInt(p[3]);
            if(x<0||y<0||w<=0||h<=0||x+w>width||y+h>height)throw new IOException("imgcut outside texture: "+line);
            result.put(p[4],new Rectangle(x,y,w,h));
        }
        return result;
    }

    private static String stripBom(String s){
        return s!=null&&!s.isEmpty()&&s.charAt(0)=='\uFEFF'?s.substring(1):s;
    }

    private static BufferedImage decodeBctex(byte[] data)throws IOException{
        int magic=find(data,new byte[]{'T','X','O','B'});
        if(magic<4)throw new IOException("TXOB not found");
        int base=magic-4;
        if(u32(data,base)!=0x20000011L)throw new IOException("Unsupported TXOB type");
        int height=i32(data,base+0x18),width=i32(data,base+0x1C);
        int format=i32(data,base+0x34);
        int imageOff=base+0x38+i32(data,base+0x38);
        int dataSize=i32(data,imageOff+8);
        int dataOff=imageOff+0x0C+i32(data,imageOff+0x0C);
        if(width<=0||height<=0||width>2048||height>2048||dataSize<0||dataOff<0||dataOff+dataSize>data.length)
            throw new IOException("Invalid BCTEX geometry");
        BufferedImage out=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        switch(format){
            case 0: decodeRgba8(data,dataOff,width,height,out);break;
            case 4: decodeRgba4(data,dataOff,width,height,out);break;
            case 13:decodeEtc1A4(data,dataOff,width,height,out);break;
            default:throw new IOException("Unsupported BCTEX pixel format: "+format);
        }
        return out;
    }

    private static void decodeRgba8(byte[] data,int off,int width,int height,BufferedImage out){
        int cursor=off;
        for(int y=0;y<height;y+=8)for(int x=0;x<width;x+=8){
            for(int i=0;i<64;i++){
                int x2=i&7,y2=i>>>3;
                int pos=TILE_ORDER[(x2&3)+((y2&3)<<2)]+16*(x2>>>2)+32*(y2>>>2);
                int p=cursor+pos*4;
                int a=data[p]&255,b=data[p+1]&255,g=data[p+2]&255,r=data[p+3]&255;
                if(x+x2<width&&y+y2<height)out.setRGB(x+x2,y+y2,argb(a,r,g,b));
            }
            cursor+=256;
        }
    }

    private static void decodeRgba4(byte[] data,int off,int width,int height,BufferedImage out){
        int cursor=off;
        for(int y=0;y<height;y+=8)for(int x=0;x<width;x+=8){
            for(int i=0;i<64;i++){
                int x2=i&7,y2=i>>>3;
                int pos=TILE_ORDER[(x2&3)+((y2&3)<<2)]+16*(x2>>>2)+32*(y2>>>2);
                int p=cursor+pos*2,b0=data[p]&255,b1=data[p+1]&255;
                int a=(b0&15)*17,b=(b0>>>4)*17,g=(b1&15)*17,r=(b1>>>4)*17;
                if(x+x2<width&&y+y2<height)out.setRGB(x+x2,y+y2,argb(a,r,g,b));
            }
            cursor+=128;
        }
    }

    private static void decodeEtc1A4(byte[] data,int off,int width,int height,BufferedImage out){
        int cursor=off;
        for(int y=0;y<height;y+=8)for(int x=0;x<width;x+=8){
            for(int iy=0;iy<8;iy+=4)for(int ix=0;ix<8;ix+=4){
                long alpha=u64(data,cursor);cursor+=8;
                long block=u64(data,cursor);cursor+=8;
                boolean diff=((block>>>33)&1L)!=0,flip=((block>>>32)&1L)!=0;
                int r1,g1,b1,r2,g2,b2;
                if(diff){
                    int r=(int)((block>>>59)&31),g=(int)((block>>>51)&31),b=(int)((block>>>43)&31);
                    r1=expand5(r);g1=expand5(g);b1=expand5(b);
                    r2=expand5(r+signed3((int)((block>>>56)&7)));
                    g2=expand5(g+signed3((int)((block>>>48)&7)));
                    b2=expand5(b+signed3((int)((block>>>40)&7)));
                }else{
                    r1=(int)((block>>>60)&15)*17;g1=(int)((block>>>52)&15)*17;b1=(int)((block>>>44)&15)*17;
                    r2=(int)((block>>>56)&15)*17;g2=(int)((block>>>48)&15)*17;b2=(int)((block>>>40)&15)*17;
                }
                int table1=(int)((block>>>37)&7),table2=(int)((block>>>34)&7);
                for(int py=0;py<4;py++)for(int px=0;px<4;px++){
                    int xx=x+ix+px,yy=y+iy+py;if(xx>=width||yy>=height)continue;
                    int bit=px*4+py;
                    int val=(int)((block>>>bit)&1),sign=((block>>>(bit+16))&1L)!=0?-1:1;
                    boolean first=(flip&&py<2)||(!flip&&px<2);
                    int mod=ETC1_MODIFIERS[first?table1:table2][val]*sign;
                    int r=clamp((first?r1:r2)+mod),g=clamp((first?g1:g2)+mod),b=clamp((first?b1:b2)+mod);
                    int a=(int)((alpha>>>(bit*4))&15)*17;
                    out.setRGB(xx,yy,argb(a,r,g,b));
                }
            }
        }
    }

    private static int expand5(int v){v=Math.max(0,Math.min(31,v));return (v<<3)|((v&0x1C)>>>2);}
    private static int signed3(int v){return (v&4)!=0?v-8:v;}
    private static int clamp(int v){return v<0?0:Math.min(255,v);}
    private static int argb(int a,int r,int g,int b){return (a<<24)|(r<<16)|(g<<8)|b;}

    private static int find(byte[] data,byte[] key){
        outer:for(int i=0;i<=data.length-key.length;i++){
            for(int j=0;j<key.length;j++)if(data[i+j]!=key[j])continue outer;
            return i;
        }
        return -1;
    }
    private static int i32(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8)|((b[p+2]&255)<<16)|(b[p+3]<<24);}
    private static long u32(byte[] b,int p){return i32(b,p)&0xffffffffL;}
    private static long u64(byte[] b,int p){
        long v=0;for(int i=0;i<8;i++)v|=(long)(b[p+i]&255)<<(i*8);return v;
    }

    private static final class Sheet{
        final BufferedImage image;final Map<String,Rectangle> parts;
        Sheet(BufferedImage image,Map<String,Rectangle> parts){this.image=image;this.parts=parts;}
    }
}

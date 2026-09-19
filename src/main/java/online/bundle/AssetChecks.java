package online.bundle;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.*;

/** Checks allocation counts before handing animation/image bytes to legacy BCU loaders. */
final class AssetChecks {
    private AssetChecks() {}
    static long check(String path,byte[] bytes) throws IOException {
        if(path.endsWith(".png")) {
            if(bytes.length<24 || bytes[0]!=(byte)137 || bytes[1]!='P' || bytes[2]!='N' || bytes[3]!='G')throw new IOException("Invalid PNG");
            try(ImageInputStream in=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers=ImageIO.getImageReaders(in);
                if(!readers.hasNext())throw new IOException("Unreadable PNG");
                ImageReader r=readers.next();
                try {r.setInput(in,true,true);int w=r.getWidth(0),h=r.getHeight(0);
                    if(w<=0 || h<=0 || w>8192 || h>8192 || (long)w*h>16_777_216)throw new IOException("Sprite dimensions exceed PvP limit");return (long)w*h;
                }finally{r.dispose();}
            }
        }
        if(!path.endsWith(".txt"))return 0;
        if(bytes.length>4*1024*1024)throw new IOException("Animation text limit");
        String s=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        String[] lines=s.split("\\r?\\n",-1);
        if(path.endsWith("animation_count.txt")) {
            int count=count(lines,0,7);
            if(count!=1 && count!=4 && count!=5 && count!=7)throw new IOException("Invalid animation layout");
        }else if(path.endsWith("imgcut.txt")) {
            int n=count(lines,3,2048);if(lines.length<4+n)throw new IOException("Truncated imgcut");
        }else if(path.endsWith("mamodel.txt")) {
            int n=count(lines,2,1024);if(lines.length<5+n)throw new IOException("Truncated mamodel");
            String[] scales=lines[3+n].split(",");if(scales.length<3)throw new IOException("Invalid model scale");
            for(int i=0;i<3;i++)if(Integer.parseInt(scales[i].trim())==0)throw new IOException("Zero model scale");
            int m=count(lines,4+n,1024);if(lines.length<5+n+m)throw new IOException("Truncated model configuration");
        }else if(path.contains("maanim_")) {
            int n=count(lines,2,2048),at=3,total=0;
            for(int i=0;i<n;i++) {int keys=count(lines,at+1,10000);total+=keys;if(total>50000)throw new IOException("Animation keyframe limit");at+=2+keys;}
            if(at>lines.length)throw new IOException("Truncated animation");
        }
        return 0;
    }
    private static int count(String[] lines,int index,int max) throws IOException {
        try {int n=Integer.parseInt(lines[index].trim());if(n<0 || n>max)throw new IOException("Animation count limit");return n;}
        catch(IndexOutOfBoundsException | NumberFormatException e){throw new IOException("Invalid animation count",e);}
    }
}

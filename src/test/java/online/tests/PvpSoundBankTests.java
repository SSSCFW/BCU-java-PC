package online.tests;

import online.ui.PvpSoundBank;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class PvpSoundBankTests {
    public static void run() throws Exception {
        for(PvpSoundBank.Sound sound:PvpSoundBank.Sound.values()){
            Check.that(PvpSoundBank.resourceAvailable(sound),"PvP OGG resource exists: "+sound);
            try(InputStream in=PvpSoundBank.class.getResourceAsStream(PvpSoundBank.resourcePath(sound))){
                byte[] header=new byte[4];
                Check.equal(4,in.read(header),"PvP sound has an OGG header: "+sound);
                Check.equal("OggS",new String(header,StandardCharsets.US_ASCII),"PvP sound is converted to OGG/Vorbis: "+sound);
            }
        }
    }
    public static void main(String[] args)throws Exception{run();System.out.println("PvP sound-bank resource tests passed");}
}

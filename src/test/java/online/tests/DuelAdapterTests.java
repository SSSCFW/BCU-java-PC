package online.tests;
import com.google.gson.*;
import online.net.duel.DuelRoster;
import online.sync.*;
import java.util.*;
public final class DuelAdapterTests {
 public static void run() throws Exception {
  JsonObject o=JsonParser.parseString("{\"mode\":\"DUEL_1V1\",\"players\":[{\"id\":700,\"name\":\"host\",\"seat\":\"right\",\"team\":1},{\"id\":9,\"name\":\"guest\",\"seat\":\"left\",\"team\":0}]}").getAsJsonObject();
  DuelRoster d=DuelRoster.read(o);
  Check.equal(1,d.leftIndex(),"left not inferred from ID or admission order");
  Check.equal(0,d.indexOf(700),"server identity maps to local engine slot");
  Map<Integer,Integer> m=new TreeMap<>();m.put(700,1);m.put(9,4);
  InputFrame f=d.toDuel(new ResolvedFrame(40,m));
  Check.equal(4,f.left,"guest input is left");Check.equal(1,f.right,"host input is right");
  Check.rejects(()->d.indexOf(0),"foreign participant rejected");
  JsonObject bad=o.deepCopy();bad.getAsJsonArray("players").get(1).getAsJsonObject().addProperty("seat","right");
  Check.rejects(()->DuelRoster.read(bad),"duplicate seat rejected");
 }
}

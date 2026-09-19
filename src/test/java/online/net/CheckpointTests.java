package online.net;
import online.net.core.LockstepState;
import online.tests.Check;
import java.util.Arrays;
public final class CheckpointTests {
 public static void run() throws Exception {
  LockstepState state=new LockstepState(Arrays.asList(31,70),32);
  for(int tick=0;tick<120;tick++){state.input(31,tick,0);state.input(70,tick,0);state.resolve();}
  String a=String.join("",java.util.Collections.nCopies(64,"a"));
  String b=String.join("",java.util.Collections.nCopies(64,"b"));
  state.checkpoint(31,60,a);state.checkpoint(31,120,a);
  Check.rejects(()->state.checkpoint(70,60,b),"late checkpoint mismatch cannot be hidden by newer checkpoint");
 }
}

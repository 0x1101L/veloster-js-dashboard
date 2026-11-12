import com.veloster.dashboard.debugger.JsDiagState;
public class JsDiagStateTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  JsDiagState s=new JsDiagState();
  check(s.snapshot().nav==-1&&s.snapshot().wifiState==-1,"initial state unknown");
  s.event("navigation.observer","registered");
  check(s.snapshot().nav==1&&s.snapshot().navNotice==0,"observer registration is not notification");
  s.event("navigation.notification","received");s.event("navigation.state_destination","12");
  check(s.snapshot().navNotice==1&&s.snapshot().navValue==12,"received state");
  s.event("navigation.state.error","SecurityException");
  check(s.snapshot().nav==1&&s.snapshot().navValue==-1&&s.snapshot().navError==1,"query denial clears value without implying registration failure");
  s.event("navigation.observer.stopped","stopped");
  check(s.snapshot().nav==0&&s.snapshot().navNotice==-1,"stop clears notification");
  s.wifi(1,1,3,1);s.hscan(2);
  check(s.snapshot().wifiState==3&&s.snapshot().hscan==2,"wifi and socket status");
  s.event("cluster.service","present; matching descriptor; method permissions unverified");
  check(s.snapshot().clusterService==1&&s.snapshot().cluster==-1,"Binder discovery is distinct from class discovery");
  s.event("cluster.service","descriptor mismatch");check(s.snapshot().clusterService==2,"wrong Binder contract rejected");
  s.event("cluster.service","absent or inaccessible");check(s.snapshot().clusterService==0,"absence does not assert no hardware");
  s.event("navigation.route.observer","registered");
  check(s.snapshot().routeWatch==1&&s.snapshot().routeNotice==0,"registration is not a route value");
  s.event("navigation.route_state","-1");
  check(s.snapshot().routeValue==-1&&s.snapshot().routeNotice==1&&s.snapshot().tbt==-1,"all raw integer values kept; no TBT inferred");
  s.event("navigation.route.invalid","bad extra");check(s.snapshot().routeNotice==-1,"malformed payload clears old state");
  s.event("navigation.route_state","3");s.event("navigation.route.stopped","stopped");
  check(s.snapshot().routeWatch==0&&s.snapshot().routeNotice==-1,"stop clears route status");
  s.event("navigation.route.observer.error","SecurityException");check(s.snapshot().routeWatch==2,"route registration failure");
  check(s.snapshot().routeError==1,"permission failure has a bounded display reason");
  s.event("navigation.route.starting","start");s.event("navigation.route.source","sticky");s.event("navigation.route_state","-3",1000);
  s.event("navigation.route.observer","registered");
  check(s.snapshot(1600).routeValue==-3&&s.snapshot(1600).routeSource==1&&s.snapshot(1600).routeAgeMs==600,"sticky reception age is app age and registration preserves it");
  long count=s.snapshot().routeCount;s.snapshot(9000);check(s.snapshot().routeCount==count,"screen refresh never increments receptions");
  s.event("navigation.route.invalid","bad");check(s.snapshot(9000).routeAgeMs==-1&&s.snapshot().routeInvalidCount>0,"invalid input cannot retain a received value age");
  s.event("navigation.state_destination","-1",2000);s.event("navigation.notification","received",2200);
  check(s.snapshot(2500).navValueKnown&&s.snapshot().navValue==-1&&s.snapshot(2500).navAgeMs==500&&s.snapshot(2500).navNoticeAgeMs==300,"provider raw integer and notification ages kept separately");
  s.event("binding.bcm","registered",100);s.event("binding.nmode","registered",100);
  s.event("binding.bcm","received",200);s.event("binding.bcm","received",300);
  check(s.source("bcm",500).count==2&&s.source("bcm",500).ageMs==200,"source-specific receive count and age");
  check(s.source("nmode",500).count==0&&s.source("nmode",500).ageMs==-1,"registration is independent of reception");
  s.event("binding.media.0","received",100);s.event("binding.media.0","invalid",100);s.event("binding.media.0","received",200);s.event("binding.media.0","processed",200);
  check(s.source("media.0",500).count==2&&s.source("media.0",500).processed==1&&s.source("media.0",500).invalid==1&&s.source("media.0",500).processedAgeMs==300,"arrival and processing kept independently");
  s.event("binding.bcm","stopped",600);check(s.source("bcm",700).status.equals("stopped"),"stop retained separately from reception");
  s.reset();check(s.source("bcm",700).status.equals("unknown"),"reset clears source states");
  check(s.snapshot().wifiState==-1&&s.snapshot().hscan==-1&&s.snapshot().routeWatch==-1&&s.snapshot().clusterService==-1,"reset");
  check(s.snapshot().routeCount==0&&s.snapshot().navQueryCount==0&&s.snapshot().routeAgeMs==-1,"reset clears diagnostic counts and ages");
  System.out.println("PASS: diagnostic observation states");
 }
}

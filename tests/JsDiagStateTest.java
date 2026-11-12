import com.veloster.vehicle.JsDiagState;
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
  check(s.snapshot().nav==2&&s.snapshot().navValue==-1,"error clears old value");
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
  s.reset();check(s.snapshot().wifiState==-1&&s.snapshot().hscan==-1&&s.snapshot().routeWatch==-1&&s.snapshot().clusterService==-1,"reset");
  System.out.println("PASS: diagnostic observation states");
 }
}

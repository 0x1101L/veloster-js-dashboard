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
  s.reset();check(s.snapshot().wifiState==-1&&s.snapshot().hscan==-1,"reset");
  System.out.println("PASS: diagnostic observation states");
 }
}

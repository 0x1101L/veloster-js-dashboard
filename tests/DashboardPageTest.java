import com.veloster.dashboard.view.DashboardPage;
public class DashboardPageTest {
 static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 public static void main(String[] args){
  for(int i=0;i<5;i++){float center=DashboardPage.left(i)+DashboardPage.width(i)/2;check(DashboardPage.tap(center,20,center,21)==i,"tap selects every page");check(DashboardPage.tap(center,55,center,21)==-1,"drag from content is not page switch");}
  check(DashboardPage.tap(30,20,160,20)==-1,"drag between tabs rejected");check(DashboardPage.hit(540,20)==-1&&DashboardPage.hit(590,20)==-1&&DashboardPage.hit(700,20)==-1,"settings and back are not tabs");check(DashboardPage.normalize(-1)==DashboardPage.OVERVIEW&&DashboardPage.normalize(99)==DashboardPage.OVERVIEW,"invalid stored page returns overview");
  DashboardPage.State state=new DashboardPage.State();
  for(int i=0;i<=DashboardPage.PERFORMANCE;i++){state.restore(i);for(int j=0;j<=DashboardPage.PERFORMANCE;j++)check(state.is(j)==(i==j),"one active page "+i);}
  check(state.restore(5)==DashboardPage.OVERVIEW,"legacy saved diagnostic opens overview");
  check(state.restore(999)==DashboardPage.OVERVIEW,"invalid saved state falls back");
  System.out.println("PASS: five dashboard page targets and drag rejection");
 }
}

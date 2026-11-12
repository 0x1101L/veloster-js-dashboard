import com.veloster.dashboard.view.DashboardPage;
public class DashboardPageTest {
 static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 public static void main(String[] args){
  for(int i=0;i<5;i++){float center=DashboardPage.left(i)+DashboardPage.width(i)/2;check(DashboardPage.tap(center,20,center,21)==i,"tap selects every page");check(DashboardPage.tap(center,55,center,21)==-1,"drag from content is not page switch");}
  check(DashboardPage.tap(30,20,160,20)==-1,"drag between tabs rejected");check(DashboardPage.hit(540,20)==-1&&DashboardPage.hit(700,20)==-1,"language and back are not tabs");check(DashboardPage.normalize(-1)==DashboardPage.OVERVIEW&&DashboardPage.normalize(99)==DashboardPage.OVERVIEW,"invalid stored page returns overview");
  check(DashboardPage.select(DashboardPage.OVERVIEW,DashboardPage.OVERVIEW,true)==DashboardPage.DIAG,"JS overview opens diag");
  check(DashboardPage.select(DashboardPage.DIAG,DashboardPage.OVERVIEW,true)==DashboardPage.OVERVIEW,"JS diag returns overview");
  check(DashboardPage.select(DashboardPage.F1,DashboardPage.OVERVIEW,true)==DashboardPage.OVERVIEW,"other page opens overview");
  check(DashboardPage.select(DashboardPage.OVERVIEW,DashboardPage.OVERVIEW,false)==DashboardPage.OVERVIEW,"other profiles do not open diag");
  DashboardPage.State state=new DashboardPage.State();
  for(int i=0;i<=DashboardPage.DIAG;i++){state.restore(i,true);for(int j=0;j<=DashboardPage.DIAG;j++)check(state.is(j)==(i==j),"one active page "+i);}
  check(state.restore(DashboardPage.DIAG,false)==DashboardPage.OVERVIEW,"non-JS restored diagnostic is rejected");
  check(state.selectPage(DashboardPage.OVERVIEW,true)==DashboardPage.DIAG,"JS selection enters diagnostic");
  check(state.selectPage(DashboardPage.OVERVIEW,true)==DashboardPage.OVERVIEW,"JS selection returns overview");
  check(state.restore(999,true)==DashboardPage.OVERVIEW,"invalid saved state falls back");
  System.out.println("PASS: five dashboard page targets and drag rejection");
 }
}

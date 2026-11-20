import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeCatalog;
import com.veloster.dashboard.theme.ThemeRepository;
import com.veloster.dashboard.theme.ThemeSelectionDraft;
import com.veloster.dashboard.view.overview.OverviewLayout;
import com.veloster.dashboard.vehicle.Telemetry;

public final class ThemeLayoutTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void verifyProfile(boolean pd,boolean js){
        OverviewLayout value=new OverviewLayout();value.theme(ThemeSpec.auroraFocus());value.pdProfile(pd||js);value.jsProfile(js);
        int[] order={7,0,8,9,10,1,2,29,14,13,15,16,17,11,12,18,22,26,25,27};
        if(js)order[18]=Telemetry.CLUSTER_FUEL;
        if(pd||js){order[3]=Telemetry.ACCEL_PEDAL;order[10]=9;order[16]=Telemetry.PD_TCS_ENABLED;}
        boolean[] seen=new boolean[Telemetry.LABELS.length];
        check(value.count()==20&&value.theme().columns==5&&value.theme().rows==4,"every profile uses the fixed 5x4 overview grid");
        for(int i=0;i<value.count();i++){
            int signal=value.signalAt(i);seen[signal]=true;
            check(signal==order[i],"profile slot mapping retained at "+i);
            float x=value.left(signal),y=value.top(signal);
            check(value.hit(x+value.width(signal)/2,y+value.height(signal)/2)==signal,"profile hit test uses active page");
        }
        for(int signal:order)check(seen[signal],"profile signal remains reachable: "+signal);
    }
    public static void main(String[] args){
        ThemeCatalog catalog=new ThemeCatalog();
        ThemeRepository repository=new ThemeRepository(catalog);
        check(catalog.size()==2&&"original".equals(catalog.resolve(null).id),"Bundled catalog has stable Original fallback");
        check("aurora_focus".equals(repository.load("aurora_focus",ThemeSpec.VERSION).id),"Known current theme resolves by stable ID");
        check("original".equals(repository.load("missing",1).id),"Unknown theme falls back to Original");
        check("original".equals(repository.load("aurora_focus",ThemeSpec.VERSION+1).id),"Unsupported schema falls back to Original");
        ThemeSelectionDraft cancelled=new ThemeSelectionDraft(ThemeSpec.original());cancelled.choose(ThemeSpec.auroraFocus());
        check("aurora_focus".equals(cancelled.selected().id)&&"original".equals(cancelled.cancel().id),"cancel rolls back the uncommitted preview draft");
        ThemeSelectionDraft applied=new ThemeSelectionDraft(ThemeSpec.original());applied.choose(ThemeSpec.auroraFocus());
        check("aurora_focus".equals(applied.apply().id)&&applied.closed(),"apply returns the selected ID for persistence");
        OverviewLayout layout=new OverviewLayout();
        check(layout.count()==20&&layout.theme().columns==5&&layout.theme().rows==4,"Original preserves all 20 overview slots");
        check(layout.left(layout.signalAt(0))==0&&layout.top(layout.signalAt(0))==50,"Original starts at the content edge after the shared region gap");
        check(layout.width(layout.signalAt(0))==150.4f&&layout.height(layout.signalAt(0))==98.5f,"Original distributes the available content across its grid");
        for(int i=0;i<20;i++){
            int signal=layout.signalAt(i);float x=layout.left(signal),y=layout.top(signal);
            check(layout.hit(x+layout.width(signal)/2,y+layout.height(signal)/2)==signal,"Original hit mapping");
        }
        layout.theme(ThemeSpec.auroraFocus());
        check(layout.count()==20&&layout.theme().columns==5&&layout.theme().rows==4,"Focus uses the fixed 5x4 grid");
        boolean[] seen=new boolean[Telemetry.LABELS.length];
        for(int i=0;i<layout.count();i++){
            int signal=layout.signalAt(i);seen[signal]=true;
            float x=layout.left(signal),y=layout.top(signal),w=layout.width(signal),h=layout.height(signal);
            check(x>=0&&y>=38&&x+w<=800&&y+h<=480,"Focus card remains inside its content bounds");
            check(layout.hit(x+w/2,y+h/2)==signal,"Focus draw and hit geometry agree");
            check(w==160f&&h==110.5f,"Focus uses the available width and height");
            for(int j=i+1;j<layout.count();j++){
                int other=layout.signalAt(j);float ox=layout.left(other),oy=layout.top(other);
                check(x+w<=ox||ox+layout.width(other)<=x||y+h<=oy||oy+layout.height(other)<=y,"Focus cards do not overlap");
            }
        }
        for(int signal:new int[]{7,0,8,9,10,1,2,29,14,13,15,16,17,11,12,18,22,26,25,27})
            check(seen[signal],"Every overview signal remains reachable: "+signal);
        layout.pdProfile(true);layout.jsProfile(true);
        check(layout.signalAt(18)==Telemetry.CLUSTER_FUEL,"JS cluster fuel retains its original signal slot");
        layout.pdProfile(true);check(layout.signalAt(3)==Telemetry.ACCEL_PEDAL,"PD accelerator profile override remains in its existing slot");
        check(layout.signalAt(16)==Telemetry.PD_TCS_ENABLED,"PD status group remains in its existing slot");
        verifyProfile(false,false);verifyProfile(false,true);verifyProfile(true,false);
        System.out.println("PASS ThemeLayoutTest");
    }
}

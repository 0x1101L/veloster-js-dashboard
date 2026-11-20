package com.veloster.dashboard.theme;

import android.graphics.Canvas;
import android.content.Context;
import com.veloster.dashboard.view.DashboardPage;
import com.veloster.dashboard.view.JsPitRenderer;
import com.veloster.dashboard.view.PitRenderer;
import com.veloster.dashboard.view.overview.OverviewLayout;
import com.veloster.dashboard.vehicle.ShiftGuide;
import com.veloster.dashboard.vehicle.Telemetry;

/** Read-only fixed telemetry fixture rendered by the same Dashboard Canvas renderers. */
public final class ThemePreviewController {
    private final boolean pd,js,jsn;
    private final int menuPosition,menuDock;
    private final Context context;
    private final Telemetry fixture;
    private final OverviewLayout layout=new OverviewLayout();
    private final PitRenderer pit=new PitRenderer();
    private final JsPitRenderer jsPit=new JsPitRenderer();
    private ThemeSpec draft=ThemeSpec.original();
    private boolean menuIcons;

    public ThemePreviewController(boolean pd,boolean js,boolean jsn){
        this(null,pd,js,jsn);
    }

    public ThemePreviewController(Context context,boolean pd,boolean js,boolean jsn){
        this(context,pd,js,jsn,ThemeSpec.MENU_THEME);
    }

    public ThemePreviewController(Context context,boolean pd,boolean js,boolean jsn,int menuPosition){
        this(context,pd,js,jsn,menuPosition,ThemeSpec.DOCK_THEME);
    }

    public ThemePreviewController(Context context,boolean pd,boolean js,boolean jsn,int menuPosition,int menuDock){
        this.menuPosition=menuPosition;this.menuDock=menuDock;this.context=context;this.pd=pd;this.js=js;this.jsn=jsn;fixture=createFixture(pd,js,jsn);
        layout.pdProfile(pd||js);layout.jsProfile(js);
        setDraft(draft);
    }

    private static Telemetry createFixture(boolean pd,boolean js,boolean jsn){
        Telemetry data=new Telemetry();data.profile(pd,js,jsn);
        data.speed(75,1000);data.units(new byte[]{2,60,108},1000);
        data.rpm(new byte[]{0,0x22,0x38,0,0,0,0,0},1000);
        data.temperatures(new byte[]{(byte)176,(byte)185},1000);
        byte[] additional=new byte[40];additional[4]=3;additional[7]=55;additional[1]=10;
        additional[20]=(byte)0xff;additional[21]=(byte)0x79;additional[22]=1;additional[29]=35;
        additional[31]=37;additional[33]=36;additional[35]=35;data.additional(additional,1000);
        data.gforce(new byte[]{(byte)0x89,0x31,0,0,0x7d,0x1f,0,0},1000);
        if(js){data.hscanJs(0xc7,0x04,new byte[]{0,0,0,0,0,0x10,0,0},1000);
            data.hscanJs(0xc7,0x1d,new byte[]{0,5,0,0,0,0,0,0},1000);
            byte[] dct=new byte[37];dct[0]=0x62;dct[1]=1;dct[2]=(byte)0xa4;dct[33]=68;dct[35]=49;data.jsDctTemperatureReply(dct,1000);
        }else if(pd){data.hscanPd(0xc7,7,new byte[]{0,0,74,8,32,0,0,0},1000);}
        else {data.hscanJsn(0xc7,7,new byte[]{0,0,74,8,32,0,0,0},1000);}
        return data;
    }

    public void setDraft(ThemeSpec value){draft=ThemeCatalog.withMenuPosition(value==null?ThemeSpec.original():value,menuPosition).withMenuDock(menuDock);layout.theme(draft);ThemeResources resources=context==null?null:ThemeResources.load(context,draft);pit.setTheme(draft,resources);jsPit.setTheme(draft,resources);applyMenuPresentation();}
    public void setMenuIcons(boolean value){menuIcons=value;applyMenuPresentation();}
    private void applyMenuPresentation(){DashboardPage.Menu menu=new DashboardPage.Menu();menu.icons(draft.shell().left()||menuIcons);pit.setPageMenu(menu);jsPit.setPageMenu(menu);}
    public void setMediaBarPolicy(int policy){pit.setMediaDisplay(policy,false);jsPit.setMediaDisplay(policy,false);}
    public ThemeSpec draft(){return draft;}
    public OverviewLayout layout(){return layout;}
    public void draw(Canvas canvas,int width,int height){
        long now=2000;
        if(js){jsPit.setOverviewLayout(layout);jsPit.draw(canvas,width,height,false,fixture,now,ShiftGuide.DEFAULT,false,null);}
        else {pit.setOverviewLayout(layout);pit.draw(canvas,width,height,false,fixture,now,ShiftGuide.DEFAULT,false,null);}
    }
}

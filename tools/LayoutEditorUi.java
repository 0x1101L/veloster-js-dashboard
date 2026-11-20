import com.veloster.dashboard.theme.ThemeJsonParser;
import com.veloster.dashboard.theme.ThemeSpec;
import android.content.SharedPreferences;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.basic.BasicTreeUI;

/** Theme authoring controls around the same live DashboardView used by the preview. */
final class LayoutEditorUi {
    interface ThemeRefresh { String refresh(String themeId, boolean followTheme, String previewRole); }
    private static final Color INK = new Color(0x18263a), PAPER = new Color(0xf5f7fa),
            WHITE = Color.WHITE, RULE = new Color(0xdbe2eb), MUTED = new Color(0x52637b),
            ACTIVE = new Color(0x2156ba), TINT = new Color(0xdfeaff), ERROR = new Color(0xa43129),
            CANVAS = new Color(0xe8edf4), HANDLE = new Color(0x9aaac0);
    private enum Document {
        OVERVIEW("overview","전체 조망"), F1("f1","F1"), GAUGES("gauges","게이지"),
        GRAPHS("graphs","그래프"), PERFORMANCE("performance","퍼포먼스"),
        SETTINGS("settings","설정 페이지"), DIAGNOSTICS("diagnostics","진단"),
        SHELL("shell","화면 틀"), COLORS("colors","색상"), FONTS("fonts","글꼴"),
        COMPONENTS("components","컴포넌트");
        final String id, label;
        Document(String id,String label){this.id=id;this.label=label;}
        @Override public String toString(){return label;}
    }
    private enum Area {
        PAGE("페이지","편집할 페이지",Document.OVERVIEW,Document.F1,Document.GAUGES,Document.GRAPHS,
            Document.PERFORMANCE,Document.SETTINGS,Document.DIAGNOSTICS),
        THEME("테마 설정","편집할 테마 항목",Document.SHELL,Document.COLORS,Document.FONTS,Document.COMPONENTS);
        final String label, prompt;
        final Document[] documents;
        Area(String label,String prompt,Document... documents){this.label=label;this.prompt=prompt;this.documents=documents;}
        @Override public String toString(){return label;}
    }
    private static final int OUTLINE_MIN=184, CANVAS_MIN=480, INSPECTOR_MIN=330;
    private static final Map<String,String> NAMES = new HashMap<>();
    static {
        String[][] names = {{"shell","화면 틀"},{"frame","바깥 틀"},{"sideMenuBar","왼쪽 메뉴"},
            {"topMenuBar","상단 메뉴"},{"contentArea","본문"},{"mediaArea","미디어 영역"},
            {"bottomMediaBar","하단 미디어 바"},{"navigationPosition","메뉴 위치"},{"width","너비"},
            {"height","높이"},{"padding","안쪽 여백 (패딩)"},{"horizontal","가로"},{"vertical","세로"},
            {"top","위"},{"right","오른쪽"},{"bottom","아래"},{"left","왼쪽"},{"gap","영역 사이 간격"},
            {"itemGap","항목 간격"},{"itemGapX","열 간격"},{"itemGapY","행 간격"},{"alignment","정렬"},
            {"buttonHeight","버튼 높이"},{"cornerShape","모서리 모양"},{"cornerRadius","모서리 반경"},
            {"appearance","메뉴 모양"},{"presentation","메뉴 표시 방식"},{"overview","조망 배치"},
            {"grid","카드 그리드"},{"cards","카드 목록"},{"cardDefaults","공통 카드"},
            {"cardTypes","카드 종류별 설정"},{"tirePressure","타이어 공기압"},{"dctTemperature","DCT 온도"},
            {"fuelEconomy","연비"},{"stability","자세 제어"},{"viewport","화면 표시 범위"},
            {"titleSize","제목 크기"},{"valueSize","값 크기"},{"unitSize","단위 크기"},
            {"labelSize","라벨 크기"},{"unitPosition","단위 위치"},{"tokens","색상 토큰"},
            {"titleValueGap","제목과 값 사이"},{"titleUnitGap","제목과 단위 사이"},{"valueUnitGap","값과 단위 사이"},
            {"valueGaugeGap","값과 게이지 사이"},{"barHeight","막대 높이"},{"dialSize","다이얼 크기"},
            {"rowGap","행 사이"},{"columnGap","열 사이"},{"labelValueGap","라벨과 값 사이"},{"gaugeWidth","게이지 너비"},
            {"fonts","글꼴 파일"},{"typography","타이포그래피"},{"weights","글꼴 굵기"},
            {"material","표면 재질"},{"f1Grid","F1 그리드"},{"cells","그리드 칸"},
            {"background","배경"},{"row","행"},{"column","열"},{"rowSpan","행 범위"},{"columnSpan","열 범위"}};
        for (String[] pair : names) NAMES.put(pair[0],pair[1]);
    }

    private final JPanel root = new JPanel(new BorderLayout());
    private final JPanel fields = new InspectorFields();
    private final JComboBox<String> themes = new JComboBox<>();
    private final JComboBox<Area> areas = new JComboBox<>(Area.values());
    private final JComboBox<Document> documents = new JComboBox<>();
    private final JLabel documentLabel = label("편집할 페이지",11,MUTED);
    private final Map<Area,Document> lastDocument = new EnumMap<>(Area.class);
    private final JTree tree = outlineTree();
    private final JLabel title = label("속성",21,INK);
    private final JLabel status = label("렌더러에 연결하는 중",12,MUTED);
    private final JLabel actual = label("",12,MUTED);
    private final Map<String,String> themeNames = new HashMap<>();
    private final JButton save = button("테마 저장",true);
    private final JButton discard = button("초안 취소",false);
    private final JCheckBoxMenuItem follow = new JCheckBoxMenuItem("테마 메뉴 규격 적용",true);
    private final ThemeRefresh refresh;
    private final JScrollPane propertyScroll;
    private final SharedPreferences layoutPreferences;
    private JSplitPane workspaceSplit;
    private JSplitPane canvasSplit;
    private boolean applyingWidths, widthsInitialized;
    private int preferredOutline, preferredInspector;
    private final Set<String> dirty = new HashSet<>();
    private final Map<List<Object>,DefaultMutableTreeNode> nodes = new LinkedHashMap<>();
    private boolean loading, pending;
    private String themeId = "f1_2", role = "overview";
    private String documentSource = "";
    private List<Object> selected = Collections.emptyList();
    private JComponent deferredFocus;
    private JTextField editedField;
    private List<Object> editedPath;
    private int scrollAfterEdit;

    LayoutEditorUi(JPanel liveScreen,JComboBox<String> car,JComboBox<String> sampleState,
                   Runnable languageAction,Runnable themeSettingsAction,Runnable capture,
                   SharedPreferences panelPreferences,ThemeRefresh refresh) {
        this.layoutPreferences=panelPreferences;
        preferredOutline=panelPreferences.getInt("outlineWidth",220);
        preferredInspector=panelPreferences.getInt("inspectorWidth",380);
        this.refresh = refresh;
        root.setBackground(PAPER);

        JPanel header = bar(WHITE,10,18);
        header.setBorder(BorderFactory.createCompoundBorder(ruleBelow(),header.getBorder()));
        header.add(label("Dashboard Studio",19,INK),BorderLayout.WEST);
        status.setHorizontalAlignment(SwingConstants.RIGHT);
        header.add(status,BorderLayout.CENTER);
        JPanel actions = flow(WHITE,FlowLayout.RIGHT);
        actions.add(labeledControl("편집 테마",themes));
        combo(themes,192,id->themeNames.getOrDefault(id,id));
        actions.add(Box.createHorizontalStrut(8));
        actions.add(discard);
        actions.add(save);
        header.add(actions,BorderLayout.EAST);
        root.add(header,BorderLayout.NORTH);

        JPanel navigator = new JPanel(new BorderLayout());
        navigator.setBackground(PAPER);
        JPanel selectors = column(PAPER);
        selectors.setBorder(new EmptyBorder(12,14,14,14));
        combo(areas,180);
        combo(documents,180);
        selectors.add(stackedControl(label("편집 영역",14,INK),areas));
        selectors.add(Box.createVerticalStrut(10));
        selectors.add(stackedControl(documentLabel,documents));
        navigator.add(selectors,BorderLayout.NORTH);
        JScrollPane treeScroll = scroll(tree,PAPER);
        treeScroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1,0,0,0,RULE),new EmptyBorder(8,8,8,8)));
        tree.getAccessibleContext().setAccessibleName("문서 구조");
        navigator.add(treeScroll,BorderLayout.CENTER);

        JPanel canvas = new JPanel(new BorderLayout());
        canvas.setBackground(CANVAS);
        JPanel canvasBar = bar(WHITE,8,14);
        canvasBar.setBorder(BorderFactory.createCompoundBorder(ruleBelow(),canvasBar.getBorder()));
        canvasBar.add(label("미리보기",14,INK),BorderLayout.WEST);
        JPanel input = flow(WHITE,FlowLayout.RIGHT);
        combo(car,158);
        combo(sampleState,100,LayoutEditorUi::stateName);
        input.add(labeledControl("차량",car));
        input.add(labeledControl("데이터",sampleState));
        canvasBar.add(input,BorderLayout.EAST);
        canvas.add(canvasBar,BorderLayout.NORTH);
        JPanel display = new JPanel(new BorderLayout());
        display.setBackground(CANVAS);
        liveScreen.setOpaque(false);
        display.setBorder(new EmptyBorder(16,14,16,14));
        display.add(liveScreen); canvas.add(display,BorderLayout.CENTER);
        JPanel tools = bar(WHITE,4,9);
        tools.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1,0,0,0,RULE),tools.getBorder()));
        follow.setToolTipText("위치와 독 모양의 앱 설정을 무시하고 테마 규격으로 확인합니다.");
        JPopupMenu viewOptions = new JPopupMenu();
        JMenuItem language = new JMenuItem("대시보드 언어");
        viewOptions.add(language);
        viewOptions.add(follow);
        viewOptions.addSeparator();
        actual.setBorder(new EmptyBorder(6,12,6,12));
        viewOptions.add(actual);
        JButton view = button("보기 옵션 ▾",false);
        view.addActionListener(e -> viewOptions.show(view,0,-viewOptions.getPreferredSize().height));
        tools.add(view,BorderLayout.WEST);
        JPanel viewActions = flow(WHITE,FlowLayout.RIGHT);
        JButton themeSettings = button("앱 테마 설정",false),
            screenshot = button("캔버스 캡처",false);
        themeSettings.setToolTipText("캔버스에서 대시보드의 테마 설정을 엽니다. 편집 테마의 JSON은 바뀌지 않습니다.");
        language.addActionListener(e -> languageAction.run());
        themeSettings.addActionListener(e -> themeSettingsAction.run());
        screenshot.addActionListener(e -> capture.run());
        viewActions.add(themeSettings); viewActions.add(screenshot);
        tools.add(viewActions,BorderLayout.EAST);
        canvas.add(tools,BorderLayout.SOUTH);

        JPanel inspector = new JPanel(new BorderLayout());
        inspector.setBackground(WHITE);
        JPanel inspectorHeader = new JPanel(new BorderLayout(8,3));
        inspectorHeader.setBackground(WHITE);
        inspectorHeader.setBorder(BorderFactory.createCompoundBorder(ruleBelow(),new EmptyBorder(12,16,12,12)));
        inspectorHeader.add(title,BorderLayout.CENTER);
        inspector.add(inspectorHeader,BorderLayout.NORTH);
        fields.setBorder(new EmptyBorder(8,16,16,16));
        propertyScroll = scroll(fields,WHITE);
        propertyScroll.getVerticalScrollBar().setUnitIncrement(18);
        inspector.add(propertyScroll,BorderLayout.CENTER);
        canvas.setMinimumSize(new Dimension(CANVAS_MIN,0));
        inspector.setMinimumSize(new Dimension(INSPECTOR_MIN,0));
        navigator.setMinimumSize(new Dimension(OUTLINE_MIN,0));
        canvasSplit=split(canvas,inspector,1.0,"속성 패널 폭 조절");
        workspaceSplit=split(navigator,canvasSplit,0.0,"구조 패널 폭 조절");
        root.add(workspaceSplit,BorderLayout.CENTER);
        root.setPreferredSize(new Dimension(1440,820));
        workspaceSplit.setMinimumSize(new Dimension(1020,300));
        workspaceSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,e->dividerMoved(true,(Integer)e.getNewValue()));
        canvasSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,e->dividerMoved(false,(Integer)e.getNewValue()));
        workspaceSplit.addComponentListener(new ComponentAdapter(){
            @Override public void componentResized(ComponentEvent e){applyPanelWidths(false);}
        });
        SwingUtilities.invokeLater(()->applyPanelWidths(false));

        syncSelectors();
        themes.addActionListener(e -> {
            if (!loading && themes.getSelectedItem()!=null) {
                if (pending) syncSelectors();
                else send("load",String.valueOf(themes.getSelectedItem()),role);
            }
        });
        areas.addActionListener(e -> {
            if(loading||pending)return;
            Area area=(Area)areas.getSelectedItem();
            Document target=lastDocument.getOrDefault(area,area.documents[0]);
            showArea(area,target);
            if(!target.id.equals(role))send("load",themeId,target.id);
        });
        documents.addActionListener(e -> {
            if(loading||pending)return;
            Document target=(Document)documents.getSelectedItem();
            if(target!=null&&!target.id.equals(role))send("load",themeId,target.id);
        });
        tree.addTreeSelectionListener(e -> {
            if (!loading && tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode) {
                Node item = (Node)((DefaultMutableTreeNode)tree.getLastSelectedPathComponent()).getUserObject();
                selected = item.path;
                showProperties(item,true);
            }
        });
        follow.addActionListener(e -> refreshCanvas(""));
        save.addActionListener(e -> send("save",themeId));
        discard.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(root,"이 테마의 저장하지 않은 변경을 버릴까요?",
                    "초안 취소",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION) send("reset",themeId);
        });
        updateActions();
    }

    JPanel panel() {return root;}
    boolean followsTheme() {return follow.isSelected();}
    boolean canClose() {
        if (pending) return false;
        return dirty.isEmpty() || JOptionPane.showConfirmDialog(root,"저장하지 않은 테마 변경을 버리고 닫을까요?",
                "에디터 닫기",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;
    }
    void requestCurrent() {send("load",themeId,role);}
    void setThemes(List<String> ids) {
        setThemes(ids,Collections.emptyMap());
    }
    void setThemes(List<String> ids,Map<String,ThemeSpec> catalog) {
        loading=true;
        for (String id : ids) {
            ThemeSpec theme=catalog.get(id);
            if(theme!=null) themeNames.put(id,theme.displayName);
            themes.addItem(id);
        }
        syncSelectors();
    }

    void handle(String line) {
        String[] parts = line.split("\\t",-1);
        if (parts.length==6 && parts[0].equals("LAYOUT_EDITOR_DOC")) {
            String nextTheme=decode(parts[1]), nextRole=decode(parts[2]), chain=decode(parts[3]), json=decode(parts[4]);
            SwingUtilities.invokeLater(() -> setDocument(nextTheme,nextRole,chain,json,parts[5].equals("1")));
        } else if (parts.length==3 && parts[0].equals("LAYOUT_EDITOR_STATUS")) {
            boolean ok=parts[1].equals("1");
            String message=decode(parts[2]);
            SwingUtilities.invokeLater(() -> {
                pending=false;
                if (ok) dirty.remove(themeId);
                else syncSelectors();
                editedPath=null;
                showStatus(message,ok?ACTIVE:ERROR);
                if (deferredFocus!=null) deferredFocus.requestFocusInWindow();
                deferredFocus=null;
                updateActions();
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void setDocument(String nextTheme,String nextRole,String chain,String json,boolean unsaved) {
        boolean same=themeId.equals(nextTheme)&&role.equals(nextRole)&&!nodes.isEmpty();
        Node document=new Node((Map<String,Object>)ThemeJsonParser.parse(json),false);
        DefaultMutableTreeNode top=nodes.get(Collections.emptyList());
        boolean reuseTree=same&&sameTree(top,document,true);
        themeId=nextTheme; role=nextRole; pending=false;
        if (unsaved) dirty.add(themeId); else dirty.remove(themeId);
        if(!same)syncSelectors();
        loading=true;
        if(reuseTree) {
            updateTreeValues(top,document,true);
        } else {
            Set<List<Object>> expanded=new HashSet<>();
            if (same) for (Map.Entry<List<Object>,DefaultMutableTreeNode> entry:nodes.entrySet())
                if (tree.isExpanded(treePath(entry.getValue()))) expanded.add(entry.getKey());
            JViewport viewport=(JViewport)tree.getParent();
            Point scroll=viewport.getViewPosition();
            nodes.clear();
            top=appendTree(document,true);
            tree.setModel(new DefaultTreeModel(top));
            tree.expandPath(treePath(top));
            for (List<Object> item:expanded) if (nodes.containsKey(item)) tree.expandPath(treePath(nodes.get(item)));
            if(same)viewport.setViewPosition(scroll);
        }
        List<Object> preferred=role.equals("shell")?Arrays.asList("shell","sideMenuBar"):
                role.equals("overview")?Collections.singletonList(themeId.equals("original")?"overview":"cardDefaults"):Collections.emptyList();
        if (!same || !nodes.containsKey(selected)) selected=preferred;
        DefaultMutableTreeNode target=nodes.get(selected);
        if (target==null || target==top) target=top.getChildCount()>0?(DefaultMutableTreeNode)top.getChildAt(0):top;
        Node node=(Node)target.getUserObject();
        selected=node.path;
        if(!reuseTree) {
            tree.setSelectionPath(treePath(target));
            if(!same)tree.scrollPathToVisible(treePath(target));
        }
        loading=false;
        refreshCanvas(same?"":role);
        documentSource=chain;
        showProperties(node,!same);
        if (same && editedPath!=null) {
            JTextField next=editedField;
            boolean returnFocus=deferredFocus instanceof JTextField;
            SwingUtilities.invokeLater(() -> {
                propertyScroll.getVerticalScrollBar().setValue(scrollAfterEdit);
                if (next!=null && returnFocus) {next.requestFocusInWindow();next.selectAll();}
            });
        }
        editedPath=null;
        showStatus(unsaved?"저장하지 않은 변경":"",unsaved?ACTIVE:MUTED);
        deferredFocus=null;
        updateActions();
    }

    private void refreshCanvas(String previewRole) {
        setCanvasStatus(refresh.refresh(themeId,follow.isSelected(),previewRole));
    }
    void setCanvasStatus(String detail) {
        if(detail.equals(actual.getText()))return;
        actual.setText(detail);
        actual.setToolTipText(detail);
    }
    private void showStatus(String message,Color ink) {
        status.setText(message);
        status.setForeground(ink);
        status.setToolTipText(message.isEmpty()?null:message);
    }

    private void syncSelectors() {
        for(Area area:Area.values())for(Document document:area.documents)if(document.id.equals(role)){
            lastDocument.put(area,document);
            showArea(area,document);
        }
        loading=true;themes.setSelectedItem(themeId);loading=false;
    }

    private void showArea(Area area,Document document){
        loading=true;
        areas.setSelectedItem(area);
        documentLabel.setText(area.prompt);
        if(documents.getItemCount()==0||documents.getItemAt(0)!=area.documents[0])
            documents.setModel(new DefaultComboBoxModel<>(area.documents));
        documents.setSelectedItem(document);
        loading=false;
    }

    private void updateActions() {
        themes.setEnabled(!pending);
        areas.setEnabled(!pending);
        documents.setEnabled(!pending);
        boolean hasDraft=dirty.contains(themeId);
        save.setEnabled(!pending&&hasDraft);
        discard.setVisible(hasDraft);
        discard.setEnabled(!pending&&hasDraft);
    }

    private DefaultMutableTreeNode appendTree(Node data,boolean rootNode) {
        DefaultMutableTreeNode treeNode=new DefaultMutableTreeNode(data);
        nodes.put(data.path,treeNode);
        for (Node child:data.children) {
            if (visibleInTree(data,child,rootNode)) treeNode.add(appendTree(child,false));
        }
        return treeNode;
    }
    private static boolean visibleInTree(Node parent,Node child,boolean rootNode) {
        return rootNode || child.container() || "array".equals(parent.kind);
    }
    private static boolean sameTree(DefaultMutableTreeNode existing,Node next,boolean rootNode) {
        Node old=(Node)existing.getUserObject();
        if(!old.path.equals(next.path)||!old.name().equals(next.name()))return false;
        int index=0;
        for(Node child:next.children)if(visibleInTree(next,child,rootNode)){
            if(index>=existing.getChildCount()
                    ||!sameTree((DefaultMutableTreeNode)existing.getChildAt(index++),child,false))return false;
        }
        return index==existing.getChildCount();
    }
    private static void updateTreeValues(DefaultMutableTreeNode existing,Node next,boolean rootNode) {
        existing.setUserObject(next);
        int index=0;
        for(Node child:next.children)if(visibleInTree(next,child,rootNode))
            updateTreeValues((DefaultMutableTreeNode)existing.getChildAt(index++),child,false);
    }

    private void showProperties(Node node,boolean top) {
        fields.removeAll();
        title.setText(node.name());
        String keyPath=node.keyPath();
        title.setToolTipText(keyPath.isEmpty()?documentSource:keyPath+"  |  "+documentSource);
        if (node.path.equals(Arrays.asList("shell","sideMenuBar"))) {
            Node position=(Node)nodes.get(Collections.singletonList("shell")).getUserObject();
            for(Node child:position.children) if ("navigationPosition".equals(child.key())) {
                if ("top".equals(child.value())) {
                    note("너비와 패딩은 왼쪽 메뉴를 사용할 때 적용됩니다.");
                    if (!child.fixed) {
                        JButton enable=button("메뉴를 왼쪽으로 배치",false);
                        enable.addActionListener(e->edit("set",child,"\"left\"",enable)); addFull(enable);
                    }
                }
            }
        }
        if (node.path.size()==1 && node.key().equals("cardDefaults")) note("종류별 카드에서 별도로 지정한 방향은 그 값이 우선합니다.");
        if (node.path.equals(Arrays.asList("shell","mediaArea"))) note("와이드 화면의 오른쪽 미디어 영역에 적용됩니다.");
        if (node.path.equals(Arrays.asList("shell","bottomMediaBar"))) note("하단 미디어 바가 보일 때 적용됩니다.");
        if (node.fixed) note("이 값은 현재 테마 규격에서 고정되어 있습니다.");
        for (Node child : node.container()?node.children:Collections.singletonList(node)) {
            if ("object".equals(child.kind) && !child.children.isEmpty()
                    && child.children.stream().allMatch(n->!n.container())) {
                fields.add(sectionHeading(child.name()));
                for (Node leaf:child.children) addProperty(leaf);
                if (child.resettable && !child.fixed) addReset(child,"그룹 기본값으로");
            } else if (child.container()) {
                JButton open=button(child.name()+(child.kind.equals("array")?"  ("+child.children.size()+")":"  ›"),false);
                open.addActionListener(e -> select(child.path)); addFull(open);
            } else addProperty(child);
        }
        if (node.container() && !node.fixed) {
            fields.add(Box.createVerticalStrut(12));
            if (node.resettable) addReset(node,"이 그룹을 기본값으로");
            if ("array".equals(node.kind)) {
                JButton append=button("배열 항목 추가…",false);
                append.addActionListener(e->addItem(node)); addFull(append);
            } else if (node.children.isEmpty() || node.stored()) {
                JButton append=button("JSON 속성 추가…",false);
                append.addActionListener(e->addItem(node)); addFull(append);
            }
        }
        if (node.inArray() && node.path.get(node.path.size()-1) instanceof Number && !node.fixed) {
            int index=((Number)node.path.get(node.path.size()-1)).intValue();
            Node parent=(Node)nodes.get(node.path.subList(0,node.path.size()-1)).getUserObject();
            fields.add(Box.createVerticalStrut(9));
            if(index>0) mutationButton("위로 이동",node,"move","-1");
            if(index+1<parent.children.size()) mutationButton("아래로 이동",node,"move","1");
            mutationButton("항목 삭제",node,"delete");
        }
        fields.revalidate(); fields.repaint();
        if (top) SwingUtilities.invokeLater(() -> propertyScroll.getVerticalScrollBar().setValue(0));
    }

    private void addProperty(Node node) {
        JPanel line=new JPanel(new BorderLayout(4,0));
        line.setOpaque(false);
        line.setBorder(ruleBelow());
        JPanel detail=column(WHITE); detail.setBorder(new EmptyBorder(7,0,7,0));
        JLabel name=label(node.name(),13,INK); name.setToolTipText(node.keyPath()); detail.add(name);
        String origin=node.owner()==null?node.defaultLabel():node.owner().equals(themeId)?"이 테마":node.owner()+" 상속";
        if (!node.present() && node.owner()==null) origin="미지정";
        JLabel originText=label(origin,11,node.owner()!=null&&node.owner().equals(themeId)?ACTIVE:MUTED);
        originText.setToolTipText(origin);
        detail.add(originText); line.add(detail,BorderLayout.CENTER);
        JPanel input=flow(WHITE,FlowLayout.RIGHT);
        input.setBorder(new EmptyBorder(6,0,6,0));
        Object current=node.value();
        if (node.fixed) {
            input.add(label(current==null?"—":optionName(String.valueOf(current)),13,MUTED));
        } else if (node.data.containsKey("enum") || "boolean".equals(node.kind)) {
            List<?> choices="boolean".equals(node.kind)?Arrays.asList(Boolean.TRUE,Boolean.FALSE):(List<?>)node.data.get("enum");
            JComboBox<Object> box=new JComboBox<>(choices.toArray());
            combo(box,112,item->item==null?"미지정":optionName(String.valueOf(item)));
            box.setSelectedItem(current);
            box.getAccessibleContext().setAccessibleName(node.name());
            box.addActionListener(e -> {
                Object next=box.getSelectedItem();
                if (next!=null && !Objects.equals(next,current)) edit("set",node,json(next),box);
            });
            input.add(box);
        } else {
            boolean number="integer".equals(node.kind)||"number".equals(node.kind)||current instanceof Number;
            JTextField edit=new JTextField(current==null?"":String.valueOf(current),number?4:8) {
                @Override protected void paintComponent(Graphics graphics) {
                    paintSurface(graphics,getWidth(),getHeight(),PAPER);
                    super.paintComponent(graphics);
                }
            };
            edit.setPreferredSize(new Dimension(number?56:112,30));
            edit.setFont(font(13,Font.PLAIN));
            edit.setForeground(INK);
            edit.setBackground(PAPER);
            edit.setBorder(controlBorder(new Insets(3,7,3,7)));
            edit.setOpaque(false);
            if(number)edit.setHorizontalAlignment(JTextField.CENTER);
            if (node.path.equals(editedPath)) editedField=edit;
            edit.getAccessibleContext().setAccessibleName(node.name());
            String constraint=node.data.containsKey("minimum")?node.data.get("minimum")+"–"+node.data.get("maximum")+" · Enter로 적용":"Enter로 적용";
            edit.setToolTipText(constraint);
            edit.addActionListener(e -> {
                String text=edit.getText();
                if (number || node.kind.isEmpty() && !(current instanceof String)) {
                    if (text.trim().isEmpty()) { edit.setText(current==null?"":String.valueOf(current)); return; }
                    edit("set",node,text.trim(),edit);
                } else edit("set",node,quote(text),edit);
            });
            if (number && current instanceof Number && "integer".equals(node.kind)) {
                int value=((Number)current).intValue();
                JPanel stepper=new JPanel(new BorderLayout());
                stepper.setOpaque(false);
                stepper.add(step("−",node,value-1,edit),BorderLayout.WEST);
                stepper.add(edit,BorderLayout.CENTER);
                stepper.add(step("+",node,value+1,edit),BorderLayout.EAST);
                input.add(stepper);
            } else input.add(edit);
        }
        if (node.resettable && !node.fixed) {
            JButton reset=compactButton("↶",28,30);
            reset.setToolTipText("이 테마의 값을 지우고 상속값 또는 렌더러 기본값 사용");
            reset.getAccessibleContext().setAccessibleName(node.name()+" 기본값으로");
            reset.addActionListener(e -> edit("inherit",node,null,reset));
            input.add(reset);
        } else input.add(Box.createHorizontalStrut(28));
        line.add(input,BorderLayout.EAST);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE,line.getPreferredSize().height));
        fields.add(line);
    }

    private JButton step(String label,Node node,int next,JTextField origin) {
        JButton control=compactButton(label,28,30);
        control.getAccessibleContext().setAccessibleName(node.name()+("−".equals(label)?" 줄이기":" 늘리기"));
        if (node.data.get("minimum") instanceof Number && next<((Number)node.data.get("minimum")).intValue()
                || node.data.get("maximum") instanceof Number && next>((Number)node.data.get("maximum")).intValue()) control.setEnabled(false);
        control.addActionListener(e -> edit("set",node,String.valueOf(next),origin));
        return control;
    }

    private void note(String text) {
        JLabel note=label("<html>"+text+"</html>",12,MUTED);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setBorder(new EmptyBorder(8,0,10,0)); fields.add(note);
    }
    private static JPanel sectionHeading(String text) {
        JPanel section=new JPanel(new BorderLayout());
        section.setOpaque(false);
        JLabel heading=label(text,13,INK);
        heading.setFont(font(13,Font.BOLD));
        section.setBorder(new EmptyBorder(15,0,5,0));
        section.add(heading,BorderLayout.CENTER);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE,section.getPreferredSize().height));
        return section;
    }
    private void addReset(Node node,String name) {
        JButton reset=button(name,false); addFull(reset);
        reset.addActionListener(e->edit("inherit",node,null,reset));
    }
    private void mutationButton(String label,Node node,String command,String... extra) {
        JButton button=button(label,false); addFull(button);
        button.addActionListener(e->sendEdit(button,node,command,extra));
    }
    private void addItem(Node node) {
        JPanel form=column(WHITE);
        JTextField key=new JTextField(24); JTextArea value=new JTextArea("{}",5,24);
        if (!"array".equals(node.kind)) {form.add(label("속성 이름",12,INK));form.add(key);}
        form.add(label("JSON 값",12,INK));form.add(new JScrollPane(value));
        if (JOptionPane.showConfirmDialog(root,form,"항목 추가",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)
            sendEdit(null,node,"add",key.getText().trim(),value.getText());
    }
    private void select(List<Object> item) {
        DefaultMutableTreeNode target=nodes.get(item);
        if (target!=null) {tree.setSelectionPath(treePath(target));tree.scrollPathToVisible(treePath(target));}
    }
    private void addFull(JButton button) {
        button.setAlignmentX(Component.LEFT_ALIGNMENT); fields.add(button); fields.add(Box.createVerticalStrut(5));
    }

    private void edit(String command,Node node,String value,JComponent focus) {
        if (value==null) sendEdit(focus,node,command); else sendEdit(focus,node,command,value);
    }
    private void sendEdit(JComponent focus,Node node,String command,String... extra) {
        if (pending) return;
        deferredFocus=focus; editedPath=node.path;
        scrollAfterEdit=propertyScroll.getVerticalScrollBar().getValue();
        String[] values=new String[extra.length+1];
        values[0]=json(node.path);System.arraycopy(extra,0,values,1,extra.length);
        send(command,values);
    }
    private void send(String command,String... args) {
        if (pending) return;
        pending=true; updateActions();
        showStatus("적용 중…",MUTED);
        StringBuilder line=new StringBuilder("LAYOUT_EDITOR\t").append(command);
        for (String arg:args) line.append('\t').append(Base64.getEncoder().encodeToString(arg.getBytes(StandardCharsets.UTF_8)));
        System.out.println(line);System.out.flush();
    }

    private static String decode(String value) {return new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);}
    private static TreePath treePath(DefaultMutableTreeNode node) {return new TreePath(node.getPath());}
    private static Font font(int size,int style) {
        Font base=UIManager.getFont("Label.font");
        return base==null?new Font(Font.DIALOG,style,size):base.deriveFont(style,(float)size);
    }
    private static JLabel label(String text,int size,Color color) {
        JLabel label=new JLabel(text);label.setForeground(color);label.setFont(font(size,size>=14?Font.BOLD:Font.PLAIN));return label;
    }
    private static javax.swing.border.Border ruleBelow() {
        return BorderFactory.createMatteBorder(0,0,1,0,RULE);
    }
    private static JPanel labeledControl(String text,JComponent control) {
        JPanel group=new JPanel(new BorderLayout(6,0));
        group.setOpaque(false);
        JLabel name=label(text,12,MUTED);
        name.setLabelFor(control);
        group.add(name,BorderLayout.WEST);
        group.add(control,BorderLayout.CENTER);
        return group;
    }
    private static JPanel stackedControl(JLabel name,JComponent control) {
        JPanel group=new JPanel(new BorderLayout(0,5));
        group.setOpaque(false);
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        name.setLabelFor(control);
        group.add(name,BorderLayout.NORTH);
        group.add(control,BorderLayout.CENTER);
        group.setMaximumSize(new Dimension(Integer.MAX_VALUE,group.getPreferredSize().height));
        return group;
    }
    private static JPanel column(Color color) {
        JPanel panel=new JPanel();panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));panel.setBackground(color);return panel;
    }
    private static JPanel flow(Color color,int align) {
        JPanel panel=new JPanel(new FlowLayout(align,5,0));panel.setBackground(color);return panel;
    }
    private static JPanel bar(Color color,int y,int x) {
        JPanel panel=new JPanel(new BorderLayout(10,0));panel.setBackground(color);panel.setBorder(new EmptyBorder(y,x,y,x));return panel;
    }
    private static JButton button(String text,boolean primary) {
        JButton button=new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                ButtonModel model=getModel();
                Color fill=primary?(isEnabled()?ACTIVE:CANVAS):null;
                if(isEnabled()&&(model.isPressed()||model.isRollover()))
                    fill=primary?new Color(0x174897):model.isPressed()?TINT:RULE;
                if(fill!=null)paintSurface(graphics,getWidth(),getHeight(),fill);
                super.paintComponent(graphics);
            }
        };
        button.setUI(new BasicButtonUI(){
            @Override protected void paintText(Graphics g,AbstractButton b,Rectangle bounds,String text) {
                g.setColor(b.isEnabled()?b.getForeground():new Color(0x718096));
                g.drawString(text,bounds.x,bounds.y+g.getFontMetrics().getAscent());
            }
        });
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setFocusPainted(false);
        button.setForeground(primary?WHITE:INK);
        button.setFont(font(12,primary?Font.BOLD:Font.PLAIN));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(controlBorder(new Insets(7,10,7,10),null));
        return button;
    }
    private static JButton compactButton(String text,int width,int height) {
        JButton control=button(text,false);
        control.setPreferredSize(new Dimension(width,height));
        control.setBorder(controlBorder(new Insets(4,4,4,4),null));
        return control;
    }
    private static void paintSurface(Graphics graphics,int width,int height,Color fill) {
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(fill);
        g.fillRoundRect(0,0,width,height,10,10);
        g.dispose();
    }
    private static AbstractBorder controlBorder(Insets padding) {return controlBorder(padding,RULE);}
    private static AbstractBorder controlBorder(Insets padding,Color stroke) {
        return new AbstractBorder(){
            @Override public Insets getBorderInsets(Component c,Insets into){
                into.set(padding.top,padding.left,padding.bottom,padding.right);
                return into;
            }
            @Override public void paintBorder(Component c,Graphics graphics,int x,int y,int width,int height){
                if(stroke==null&&!c.hasFocus())return;
                Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(c.hasFocus()?ACTIVE:stroke);
                g.drawRoundRect(x,y,width-1,height-1,10,10);
                g.dispose();
            }
        };
    }
    private static JScrollPane scroll(Component view,Color color) {
        JScrollPane scroller=new JScrollPane(view);
        scroller.setBorder(null);
        scroller.getViewport().setBackground(color);
        JScrollBar bar=scroller.getVerticalScrollBar();
        bar.setBackground(color);
        bar.setUI(new BasicScrollBarUI(){
            @Override protected JButton createDecreaseButton(int orientation){return hiddenButton();}
            @Override protected JButton createIncreaseButton(int orientation){return hiddenButton();}
            @Override protected void paintThumb(Graphics graphics,JComponent c,Rectangle bounds){
                if(bounds.isEmpty()||!c.isEnabled())return;
                Graphics2D g=(Graphics2D)graphics.create(bounds.x+2,bounds.y+2,6,Math.max(1,bounds.height-4));
                paintSurface(g,6,Math.max(1,bounds.height-4),HANDLE);
                g.dispose();
            }
            @Override protected void paintTrack(Graphics g,JComponent c,Rectangle bounds) {}
            private JButton hiddenButton(){
                JButton button=new JButton();
                button.setPreferredSize(new Dimension(0,0));
                return button;
            }
        });
        bar.setPreferredSize(new Dimension(10,0));
        return scroller;
    }
    private static <T> void combo(JComboBox<T> box,int width) {combo(box,width,String::valueOf);}
    private static <T> void combo(JComboBox<T> box,int width,Function<T,String> caption) {
        box.setPreferredSize(new Dimension(width,32));box.setMaximumSize(new Dimension(Integer.MAX_VALUE,32));
        box.setFont(font(12,Font.PLAIN));
        box.setForeground(INK); box.setBackground(WHITE);
        box.setOpaque(false);
        box.setBorder(controlBorder(new Insets(1,1,1,1)));
        box.setUI(new BasicComboBoxUI() {
            @Override public void paint(Graphics graphics,JComponent c){
                paintSurface(graphics,c.getWidth(),c.getHeight(),box.isEnabled()?WHITE:PAPER);
                super.paint(graphics,c);
            }
            @Override public void paintCurrentValueBackground(Graphics graphics,Rectangle bounds,boolean focus) {}
            @Override protected JButton createArrowButton() {
                JButton arrow=new JButton() {
                    @Override protected void paintComponent(Graphics graphics){
                        paintChevron(graphics,getWidth()/2,getHeight()/2,true,MUTED);
                    }
                };
                arrow.setPreferredSize(new Dimension(24,24));
                arrow.setOpaque(false);
                arrow.setFocusPainted(false);
                arrow.setBorder(null);
                return arrow;
            }
        });
        box.setRenderer(new DefaultListCellRenderer(){
            @Override @SuppressWarnings("unchecked")
            public Component getListCellRendererComponent(JList<?> list,Object item,int index,boolean selected,boolean focus){
                super.getListCellRendererComponent(list,item,index,selected,focus);
                setText(caption.apply((T)item));
                setBorder(new EmptyBorder(5,8,5,4));
                setFont(box.getFont());
                setOpaque(index>=0);
                if(index>=0){setBackground(selected?TINT:WHITE);setForeground(selected?ACTIVE:INK);}
                return this;
            }
        });
    }
    private static void paintChevron(Graphics graphics,int x,int y,boolean down,Color ink){
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(ink);
        if(down)g.drawPolyline(new int[]{x-3,x,x+3},new int[]{y-2,y+1,y-2},3);
        else g.drawPolyline(new int[]{x-1,x+2,x-1},new int[]{y-3,y,y+3},3);
        g.dispose();
    }
    private static JTree outlineTree() {
        JTree outline=new JTree(new DefaultMutableTreeNode("연결 중")){
            @Override protected void paintComponent(Graphics graphics){
                Rectangle selected=getPathBounds(getSelectionPath());
                if(selected!=null){
                    Graphics2D row=(Graphics2D)graphics.create(0,selected.y+1,getWidth(),selected.height-2);
                    paintSurface(row,getWidth(),selected.height-2,TINT);
                    row.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        outline.setOpaque(false);
        outline.setRootVisible(false);
        outline.setShowsRootHandles(true);
        outline.setRowHeight(31);
        outline.setFont(font(13,Font.PLAIN));
        outline.setUI(new BasicTreeUI(){
            @Override protected void paintHorizontalLine(Graphics g,JComponent c,int y,int left,int right){}
            @Override protected void paintVerticalLine(Graphics g,JComponent c,int x,int top,int bottom){}
        });
        BasicTreeUI ui=(BasicTreeUI)outline.getUI();
        ui.setLeftChildIndent(10);
        ui.setRightChildIndent(10);
        ui.setExpandedIcon(treeArrow(true));
        ui.setCollapsedIcon(treeArrow(false));
        outline.setCellRenderer(new TreeCellRenderer(){
            private final JLabel text=new JLabel();
            @Override public Component getTreeCellRendererComponent(JTree tree,Object value,boolean selected,
                    boolean expanded,boolean leaf,int row,boolean focus){
                text.setText(tree.convertValueToText(value,selected,expanded,leaf,row,focus));
                text.setFont(font(13,selected?Font.BOLD:Font.PLAIN));
                text.setForeground(selected?ACTIVE:INK);
                text.setBorder(focus?BorderFactory.createLineBorder(ACTIVE):new EmptyBorder(1,1,1,1));
                return text;
            }
        });
        return outline;
    }
    private static Icon treeArrow(boolean down){
        return new Icon(){
            public int getIconWidth(){return 10;}
            public int getIconHeight(){return 10;}
            public void paintIcon(Component c,Graphics g,int x,int y){paintChevron(g,x+5,y+5,down,MUTED);}
        };
    }
    private static final class InspectorFields extends JPanel implements Scrollable {
        InspectorFields() {setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setBackground(WHITE);}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle v,int axis,int direction){return 18;}
        public int getScrollableBlockIncrement(Rectangle v,int axis,int direction){return Math.max(18,v.height-45);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }
    private static int innerWidth(JSplitPane split) {
        Insets insets=split.getInsets();
        return split.getWidth()-insets.left-insets.right-split.getDividerSize();
    }
    private int sideSpace() {
        Insets insets=canvasSplit.getInsets();
        return innerWidth(workspaceSplit)-insets.left-insets.right-canvasSplit.getDividerSize()-CANVAS_MIN;
    }
    private void dividerMoved(boolean outline,int location) {
        if(!widthsInitialized||applyingWidths||location<0)return;
        JSplitPane split=outline?workspaceSplit:canvasSplit;
        int fromLeft=location-split.getInsets().left;
        int width=outline?fromLeft:innerWidth(split)-fromLeft;
        setPreferredPanelWidth(outline,width);
        SwingUtilities.invokeLater(()->applyPanelWidths(!outline));
    }
    private void setPreferredPanelWidth(boolean outline,int width) {
        if(outline)preferredOutline=clamp(width,OUTLINE_MIN,sideSpace()-INSPECTOR_MIN);
        else preferredInspector=clamp(width,INSPECTOR_MIN,innerWidth(canvasSplit)-CANVAS_MIN);
        try {
            layoutPreferences.edit().putInt(outline?"outlineWidth":"inspectorWidth",
                    outline?preferredOutline:preferredInspector).apply();
        } catch (IllegalStateException|SecurityException ignored) { }
    }
    private void applyPanelWidths(boolean inspectorFirst) {
        if(workspaceSplit.getWidth()==0)return;
        int available=sideSpace();
        int rightLimit=inspectorFirst?available-OUTLINE_MIN:available-preferredOutline;
        int left=clamp(preferredOutline,OUTLINE_MIN,available-(inspectorFirst?
                clamp(preferredInspector,INSPECTOR_MIN,rightLimit):INSPECTOR_MIN));
        applyingWidths=true;
        try {
            workspaceSplit.setDividerLocation(workspaceSplit.getInsets().left+left);
            workspaceSplit.doLayout();
            int center=innerWidth(canvasSplit);
            int right=clamp(preferredInspector,INSPECTOR_MIN,center-CANVAS_MIN);
            canvasSplit.setDividerLocation(canvasSplit.getInsets().left+center-right);
            canvasSplit.doLayout();
            widthsInitialized=true;
        } finally {applyingWidths=false;}
    }
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(value,max));}
    private static JSplitPane split(Component left,Component right,double weight,String name) {
        JSplitPane pane=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,left,right){
            @Override protected void paintChildren(Graphics graphics){
                super.paintChildren(graphics);
                if(getLeftComponent()==null||getHeight()<36)return;
                Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                Component panel=getLeftComponent();
                int x=panel.getX()+panel.getWidth()+getDividerSize()/2, cy=getHeight()/2;
                g.setColor(RULE);
                g.drawLine(x,0,x,getHeight()-1);
                boolean hovered=Boolean.TRUE.equals(getClientProperty("dividerHovered"));
                g.setColor(isFocusOwner()||hovered?ACTIVE:HANDLE);
                g.fillRoundRect(x-1,cy-13,3,26,3,3);
                g.dispose();
            }
        };
        pane.setResizeWeight(weight);pane.setContinuousLayout(true);
        pane.setOneTouchExpandable(false);
        pane.getAccessibleContext().setAccessibleName(name);
        pane.setDividerLocation(0.5);
        pane.setUI(new BasicSplitPaneUI(){
            @Override public BasicSplitPaneDivider createDefaultDivider(){
                BasicSplitPaneDivider divider=new BasicSplitPaneDivider(this){
                    @Override public void setDividerSize(int size){super.setDividerSize(size);setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));}
                };
                divider.setBackground(PAPER);
                divider.addMouseListener(new MouseAdapter(){
                    @Override public void mouseEntered(MouseEvent e){setHovered(true);}
                    @Override public void mouseExited(MouseEvent e){setHovered(false);}
                    private void setHovered(boolean hovered){
                        pane.putClientProperty("dividerHovered",hovered);
                        pane.repaint(divider.getBounds());
                    }
                });
                return divider;
            }
        });
        pane.setBorder(new EmptyBorder(0,0,0,0));
        pane.setDividerSize(10);
        return pane;
    }
    private static String optionName(String option) {
        switch(option) {
            case "left":return "왼쪽";case "top":return "상단";case "labels":return "텍스트";
            case "icons":return "아이콘";case "dock":return "독";case "buttons":return "버튼";
            case "center":return "가운데";case "start":return "시작";case "end":return "끝";
            case "true":return "켜기";case "false":return "끄기";
            case "valueRight":return "값 오른쪽";case "topRight":return "오른쪽 위";
            case "bottomLeft":return "왼쪽 아래";case "bottomRight":return "오른쪽 아래";
            default:return option;
        }
    }
    private static String stateName(String value) {
        switch(value) {
            case "received":return "수신됨";case "waiting":return "대기";case "invalid":return "무효";
            case "held":return "마지막 값";case "expired":return "만료";default:return value;
        }
    }
    static String quote(String value) {
        return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";
    }
    static String json(Object value) {
        if(value==null)return "null";
        if(value instanceof Map) {
            StringJoiner members=new StringJoiner(",","{","}");
            for(Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()) members.add(quote(String.valueOf(entry.getKey()))+":"+json(entry.getValue()));
            return members.toString();
        }
        if(value instanceof List) {
            StringJoiner elements=new StringJoiner(",","[","]");
            for(Object item:(List<?>)value)elements.add(json(item));return elements.toString();
        }
        return value instanceof Number||value instanceof Boolean?value.toString():quote(String.valueOf(value));
    }

    static Map<String,Object> map(Object... pairs) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);
        return result;
    }
    private static Map<String,Object> insets(ThemeSpec.Insets value) {
        return map("top",value.top,"right",value.right,"bottom",value.bottom,"left",value.left);
    }
    static String inspect(ThemeSpec theme) throws IllegalAccessException {
        ThemeSpec.Shell s=theme.shell();
        // Defaults and computed insets come from the loaded production ThemeSpec.
        Map<String,Object> side=map("alignment",s.alignment,"buttonHeight",s.buttonHeight,
                "gap",s.groupGap,"itemGap",s.itemGap,"appearance",s.dock()?"dock":"buttons",
                "presentation",s.menuIcons()?"icons":"labels","padding",
                map("vertical",s.sideMenuPadding.top,"left",s.sideMenuPadding.left,"right",s.sideMenuPadding.right));
        if(s.left())side.put("width",s.width);
        Map<String,Object> shell=map("navigationPosition",s.position,
                "frame",map("padding",map("horizontal",s.framePaddingHorizontal,"vertical",s.framePaddingVertical),
                        "gap",map("horizontal",s.frameGapHorizontal,"vertical",s.frameGapVertical),
                        "cornerRadius",s.screenCornerRadius,"cornerShape",s.screenCorner.name().toLowerCase(Locale.ROOT)),
                "sideMenuBar",s.left()?side:Collections.emptyMap(),
                "topMenuBar",map("itemGap",s.topMenuItemGap,"presentation",s.menuIcons()?"icons":"labels","padding",insets(s.topMenuPadding)),
                "contentArea",map("padding",insets(s.contentPadding)),
                "mediaArea",map("padding",insets(s.mediaPadding)),
                "bottomMediaBar",map("padding",insets(s.bottomMediaPadding)));
        Map<String,Object> types=map(), overview=map("shell",shell,"cardTypes",types);
        String[] keys={"cardDefaults","tirePressure","dctTemperature","fuelEconomy","stability"};
        ThemeSpec.OverviewCardKind[] kinds=ThemeSpec.OverviewCardKind.values();
        for(int i=0;i<kinds.length;i++) {
            ThemeSpec.OverviewCardStyle card=theme.overviewCardStyle(kinds[i]);
            Map<String,Object> style=map("padding",insets(card.padding));
            for(java.lang.reflect.Field field:ThemeSpec.OverviewCardStyle.class.getFields())
                if(field.getType()==int.class||field.getType()==String.class)style.put(field.getName(),field.get(card));
            (i==0?overview:types).put(keys[i],style);
        }
        return json(overview);
    }

    private static final class Node {
        final Map<String,Object> data;
        final String kind;
        final List<Object> path;
        final List<Node> children=new ArrayList<>();
        final boolean resettable,fixed;
        @SuppressWarnings("unchecked")
        Node(Map<String,Object> data,boolean locked) {
            this.data=data;kind=String.valueOf(data.get("kind"));path=(List<Object>)data.get("path");
            resettable=Boolean.TRUE.equals(data.get("resettable"));fixed=locked||Boolean.TRUE.equals(data.get("fixed"));
            for(Map<String,Object> child:(List<Map<String,Object>>)data.get("children"))children.add(new Node(child,fixed));
        }
        boolean inArray(){return path.stream().anyMatch(p->p instanceof Number);}
        boolean container(){return kind.equals("object")||kind.equals("array");}
        boolean stored(){return Boolean.TRUE.equals(data.get("stored"));}
        boolean present(){return Boolean.TRUE.equals(data.get("present"));}
        String key(){return path.isEmpty()?"":String.valueOf(path.get(path.size()-1));}
        Object value(){return data.get("value");}
        String owner(){return (String)data.get("owner");}
        String defaultLabel(){return String.valueOf(data.getOrDefault("defaultLabel","렌더러 기본"));}
        String name(){
            if(path.isEmpty())return "속성";
            if(path.get(path.size()-1) instanceof Number) {
                for(Node child:children)if("id".equals(child.key())&&child.value()!=null)return child.value().toString();
                return "항목 "+(((Number)path.get(path.size()-1)).intValue()+1);
            }
            return NAMES.getOrDefault(key(),key());
        }
        String keyPath(){StringJoiner join=new StringJoiner(" / ");for(Object item:path)join.add(item.toString());return join.toString();}
        @Override public String toString(){return name();}
    }
}

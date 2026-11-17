import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;

/** Desktop controls around the production DashboardView canvas. */
final class DashboardPreviewUi {
    private static final Color BACKGROUND=new Color(0xf6f7f9);
    private static final Color BORDER=new Color(0xdce1e6);
    private static final Color TEXT=new Color(0x24313c);
    private static final Color MUTED=new Color(0x65737f);
    private static final Color ACCENT=new Color(0x007aff);

    static void installLookAndFeel() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch(Exception ignored) { }
    }

    private static Font font(int size) {
        Font base=UIManager.getFont("Label.font");
        return (base==null?new Font("Dialog",Font.PLAIN,size):base).deriveFont(Font.PLAIN,(float)size);
    }

    static JButton button(String text,boolean primary,Runnable action) {
        JButton button=new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                boolean pressed=getModel().isPressed(),hover=getModel().isRollover();
                g.setColor(primary?(pressed?new Color(0x005ac2):hover?new Color(0x006ce5):ACCENT):
                        pressed?new Color(0xe8edf2):hover?new Color(0xf2f5f8):Color.WHITE);
                g.fillRoundRect(1,1,getWidth()-3,getHeight()-3,10,10);
                g.setColor(hasFocus()?ACCENT:primary?ACCENT:BORDER);
                g.setStroke(new BasicStroke(hasFocus()?2:1));
                g.drawRoundRect(1,1,getWidth()-3,getHeight()-3,10,10);
                g.setFont(getFont());g.setColor(primary?Color.WHITE:TEXT);
                FontMetrics metrics=g.getFontMetrics();
                g.drawString(getText(),(getWidth()-metrics.stringWidth(getText()))/2,
                        (getHeight()-metrics.getHeight())/2+metrics.getAscent());
                g.dispose();
            }
        };
        button.setFont(font(13));button.setBorder(new EmptyBorder(8,14,8,14));
        button.setContentAreaFilled(false);button.setRolloverEnabled(true);
        button.addActionListener(e->action.run());
        return button;
    }

    private static void styleCombo(JComboBox<String> combo,boolean data) {
        combo.setFont(font(13));combo.setBackground(Color.WHITE);combo.setForeground(TEXT);
        combo.setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton arrow=new JButton() {
                    @Override protected void paintComponent(Graphics graphics) {
                        Graphics2D g=(Graphics2D)graphics.create();
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(MUTED);g.setStroke(new BasicStroke(1.5f));
                        int x=getWidth()/2,y=getHeight()/2;
                        g.drawLine(x-4,y-2,x,y+2);g.drawLine(x,y+2,x+4,y-2);g.dispose();
                    }
                };
                arrow.setPreferredSize(new Dimension(28,28));arrow.setBorder(new EmptyBorder(0,0,0,0));
                arrow.setContentAreaFilled(false);return arrow;
            }
        });
        combo.setBorder(BorderFactory.createLineBorder(BORDER));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus) {
                JLabel label=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
                String text=String.valueOf(value);
                if(data) {
                    if("received".equals(text))text="수신됨";
                    else if("waiting".equals(text))text="수신 대기";
                    else if("invalid".equals(text))text="무효 값";
                    else if("held".equals(text))text="마지막 값";
                    else if("expired".equals(text))text="수신 만료";
                }
                label.setText(text);label.setFont(font(13));label.setBorder(new EmptyBorder(6,10,6,10));
                return label;
            }
        });
        combo.setPreferredSize(new Dimension(180,34));combo.setMinimumSize(new Dimension(110,34));
    }

    private static JPanel field(String title,JComponent control) {
        JPanel group=new JPanel(new BorderLayout(0,6));group.setOpaque(false);
        JLabel label=new JLabel(title);label.setFont(font(12));label.setForeground(MUTED);
        group.add(label,BorderLayout.NORTH);group.add(control,BorderLayout.CENTER);
        return group;
    }

    static JPanel contextBar(JComboBox<String> car,JButton language,JButton theme) {
        styleCombo(car,false);
        JPanel bar=new JPanel(new GridBagLayout());bar.setBackground(BACKGROUND);
        bar.setBorder(new EmptyBorder(12,16,12,16));
        GridBagConstraints layout=new GridBagConstraints();layout.gridy=0;layout.fill=GridBagConstraints.HORIZONTAL;
        layout.gridx=0;layout.weightx=1;layout.insets=new Insets(0,0,0,16);bar.add(field("차량",car),layout);
        layout.gridx=1;layout.weightx=.65;bar.add(field("언어",language),layout);
        layout.gridx=2;layout.weightx=1.2;layout.insets=new Insets(0,0,0,0);bar.add(field("테마",theme),layout);
        return bar;
    }

    static JPanel actionBar(JComboBox<String> state,Runnable refresh,Runnable capture) {
        styleCombo(state,true);state.setPreferredSize(new Dimension(128,34));
        JLabel label=new JLabel("예시 데이터");label.setFont(font(12));label.setForeground(MUTED);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));controls.setOpaque(false);
        controls.add(label);controls.add(state);controls.add(button("새로고침",false,refresh));
        JPanel actions=new JPanel(new BorderLayout(12,0));actions.setBackground(BACKGROUND);
        actions.setBorder(new EmptyBorder(12,6,12,16));
        actions.add(controls,BorderLayout.CENTER);
        JButton captureButton=button("프리뷰 캡처",true,capture);
        captureButton.setToolTipText("현재 대시보드 이미지를 복사하거나 PNG로 저장합니다");
        actions.add(captureButton,BorderLayout.EAST);
        return actions;
    }

    static JPanel shell(JComponent context,JComponent screen,JComponent actions) {
        JPanel viewport=new JPanel(new BorderLayout());viewport.setBackground(new Color(0xe9edf1));
        viewport.setBorder(new EmptyBorder(12,16,12,16));viewport.add(screen,BorderLayout.CENTER);
        JPanel root=new JPanel(new BorderLayout());root.setBackground(BACKGROUND);
        root.add(context,BorderLayout.NORTH);root.add(viewport,BorderLayout.CENTER);root.add(actions,BorderLayout.SOUTH);
        return root;
    }

    static JPanel capturePanel(BufferedImage snapshot,Runnable copy,Runnable save,Runnable close) {
        JPanel panel=new JPanel(new BorderLayout(0,12));panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),new EmptyBorder(12,12,12,12)));
        JPanel heading=new JPanel(new BorderLayout(12,0));heading.setOpaque(false);
        JLabel title=new JLabel("스크린샷 캡처됨");title.setFont(font(13));title.setForeground(TEXT);
        heading.add(title,BorderLayout.CENTER);heading.add(button("닫기",false,close),BorderLayout.EAST);
        panel.add(heading,BorderLayout.NORTH);
        int width=280,height=Math.max(1,Math.round(width*snapshot.getHeight()/(float)snapshot.getWidth()));
        panel.add(new JLabel(new ImageIcon(snapshot.getScaledInstance(width,height,Image.SCALE_SMOOTH))),BorderLayout.CENTER);
        JPanel actions=new JPanel(new GridLayout(1,2,8,0));actions.setOpaque(false);
        actions.add(button("클립보드 복사",false,copy));actions.add(button("저장…",true,save));
        panel.add(actions,BorderLayout.SOUTH);return panel;
    }

    static void showCapture(JFrame owner,BufferedImage snapshot) {
        JWindow toast=new JWindow(owner);toast.setAlwaysOnTop(true);toast.setAutoRequestFocus(false);
        Timer expiry=new Timer(10000,e->toast.dispose());expiry.setRepeats(false);
        Runnable close=()->{expiry.stop();toast.dispose();};
        Runnable save=()->{
            expiry.stop();
            JFileChooser chooser=new JFileChooser();
            chooser.setSelectedFile(new java.io.File("dashboard-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date())+".png"));
            if(chooser.showSaveDialog(owner)==JFileChooser.APPROVE_OPTION) {
                try { javax.imageio.ImageIO.write(snapshot,"png",chooser.getSelectedFile());close.run(); }
                catch(Exception error) { JOptionPane.showMessageDialog(owner,error.toString()); }
            }
            if(toast.isDisplayable())expiry.restart();
        };
        Runnable copy=()->{
            expiry.stop();
            try {
                java.awt.datatransfer.DataFlavor format=java.awt.datatransfer.DataFlavor.imageFlavor;
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.Transferable() {
                    public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors(){return new java.awt.datatransfer.DataFlavor[]{format};}
                    public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor f){return format.equals(f);}
                    public Object getTransferData(java.awt.datatransfer.DataFlavor f)throws java.awt.datatransfer.UnsupportedFlavorException{
                        if(!isDataFlavorSupported(f))throw new java.awt.datatransfer.UnsupportedFlavorException(f);return snapshot;
                    }
                },null);
                close.run();
            } catch(Exception error) { JOptionPane.showMessageDialog(owner,error.toString());expiry.restart(); }
        };
        toast.setContentPane(capturePanel(snapshot,copy,save,close));toast.pack();
        GraphicsConfiguration configuration=owner.getGraphicsConfiguration();Rectangle bounds=configuration.getBounds();
        Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        toast.setLocation(bounds.x+bounds.width-insets.right-toast.getWidth()-18,
                bounds.y+bounds.height-insets.bottom-toast.getHeight()-18);
        toast.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event){expiry.stop();}
        });
        toast.setVisible(true);expiry.start();
    }
}

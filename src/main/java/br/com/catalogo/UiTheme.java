package br.com.catalogo;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.*;

/** Paleta e componentes visuais compartilhados pela janela e pela abertura. */
final class UiTheme {
 static final Color BACKGROUND=new Color(250,236,231),SURFACE=new Color(252,247,243),RAISED=new Color(250,236,231),LINE=new Color(245,196,179),TEXT=new Color(74,27,12),MUTED=new Color(128,98,83),ACCENT=new Color(128,81,63),SELECTED=new Color(250,236,231);
 static void install(){
  FlatLightLaf.setup();
  UIManager.put("defaultFont",new Font("Arial",Font.PLAIN,14));
  for(String key:new String[]{"Panel.background","Viewport.background","ScrollPane.background","SplitPane.background","Tree.background","Table.background","TabbedPane.background"})UIManager.put(key,SURFACE);
  UIManager.put("control",SURFACE);UIManager.put("text",TEXT);
  for(String key:new String[]{"Label.foreground","Table.foreground","Tree.foreground","TextArea.foreground","TextField.foreground","CheckBox.foreground"})UIManager.put(key,TEXT);
  for(String key:new String[]{"TextField.background","TextArea.background","ComboBox.background","Spinner.background","Button.background"})UIManager.put(key,RAISED);
  UIManager.put("Component.borderColor",LINE);UIManager.put("Component.focusColor",ACCENT);UIManager.put("Component.focusedBorderColor",ACCENT);UIManager.put("Component.accentColor",ACCENT);
  UIManager.put("Component.arc",14);UIManager.put("Button.arc",14);UIManager.put("TextComponent.arc",14);UIManager.put("Component.focusWidth",1);UIManager.put("Component.innerFocusWidth",0);
  UIManager.put("Button.margin",new Insets(10,16,10,16));UIManager.put("TextField.margin",new Insets(10,13,10,13));UIManager.put("ComboBox.padding",new Insets(8,10,8,10));
  UIManager.put("Table.selectionBackground",SELECTED);UIManager.put("Table.selectionForeground",TEXT);UIManager.put("Table.selectionInactiveBackground",SELECTED);UIManager.put("Table.gridColor",LINE);
  UIManager.put("TableHeader.background",SURFACE);UIManager.put("TableHeader.foreground",MUTED);UIManager.put("TableHeader.separatorColor",LINE);UIManager.put("TableHeader.bottomSeparatorColor",LINE);
  UIManager.put("Tree.selectionBackground",SELECTED);UIManager.put("Tree.selectionForeground",TEXT);UIManager.put("Tree.selectionInactiveBackground",SELECTED);UIManager.put("Tree.rowHeight",33);UIManager.put("Tree.selectionArc",10);UIManager.put("Tree.showDefaultIcons",false);
  UIManager.put("TabbedPane.underlineColor",ACCENT);UIManager.put("TabbedPane.selectedBackground",SURFACE);UIManager.put("TabbedPane.contentAreaColor",LINE);UIManager.put("TabbedPane.tabHeight",39);
  UIManager.put("ProgressBar.foreground",ACCENT);UIManager.put("ProgressBar.background",RAISED);UIManager.put("ProgressBar.arc",8);
  UIManager.put("SplitPaneDivider.gripDotCount",0);UIManager.put("SplitPaneDivider.style","plain");UIManager.put("ScrollBar.width",9);UIManager.put("ScrollBar.thumbArc",9);
 }
 static JPanel panel(LayoutManager layout){var p=new JPanel(layout);p.setOpaque(false);return p;}
 static JLabel eyebrow(String text){var l=new JLabel(text);l.setForeground(MUTED);l.setFont(l.getFont().deriveFont(Font.BOLD,11));return l;}
 static Border card(){return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),BorderFactory.createEmptyBorder(16,18,16,18));}
 static JScrollPane scroll(Component component){var scroll=new JScrollPane(component);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.getVerticalScrollBar().setUnitIncrement(24);return scroll;}
 static void primary(JButton button){button.setBackground(new Color(153,60,29));button.setForeground(Color.WHITE);button.setFont(button.getFont().deriveFont(Font.BOLD));}
 static Image appIcon(){try(var in=UiTheme.class.getResourceAsStream("/catalogo/praesens-avatar.png")){return javax.imageio.ImageIO.read(in);}catch(Exception e){var im=new java.awt.image.BufferedImage(128,128,java.awt.image.BufferedImage.TYPE_INT_ARGB);var g=im.createGraphics();new BookIcon(128).paintIcon(null,g,0,0);g.dispose();return im;}}
 static final class BookIcon implements Icon {
  final int size;BookIcon(int size){this.size=size;}public int getIconWidth(){return size;}public int getIconHeight(){return size;}
  public void paintIcon(Component c,Graphics graphics,int x,int y){var g=(Graphics2D)graphics.create();g.translate(x,y);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);try(var in=UiTheme.class.getResourceAsStream("/catalogo/praesens-simbolo.png")){var symbol=javax.imageio.ImageIO.read(in);g.drawImage(symbol,0,0,size,size,null);}catch(Exception ignored){}g.dispose();}
 }
}

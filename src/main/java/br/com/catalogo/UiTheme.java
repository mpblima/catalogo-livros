package br.com.catalogo;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.*;

/** Paleta e componentes visuais compartilhados pela janela e pela abertura. */
final class UiTheme {
 static final Color BACKGROUND=new Color(16,23,25),SURFACE=new Color(24,33,35),RAISED=new Color(31,43,45),LINE=new Color(47,64,64),TEXT=new Color(230,239,233),MUTED=new Color(151,173,164),ACCENT=new Color(155,215,182),SELECTED=new Color(38,68,57);
 static void install(){
  FlatDarkLaf.setup();
  UIManager.put("defaultFont",new Font("SansSerif",Font.PLAIN,14));
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
 static void primary(JButton button){button.setBackground(ACCENT);button.setForeground(new Color(18,42,29));button.setFont(button.getFont().deriveFont(Font.BOLD));}
 static Image appIcon(){var im=new java.awt.image.BufferedImage(128,128,java.awt.image.BufferedImage.TYPE_INT_ARGB);var g=im.createGraphics();new BookIcon(128).paintIcon(null,g,0,0);g.dispose();return im;}
 static final class BookIcon implements Icon {
  final int size;BookIcon(int size){this.size=size;}public int getIconWidth(){return size;}public int getIconHeight(){return size;}
  public void paintIcon(Component c,Graphics graphics,int x,int y){var g=(Graphics2D)graphics.create();g.translate(x,y);g.scale(size/64.0,size/64.0);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(new Color(37,63,52));g.fillRoundRect(0,0,64,64,18,18);g.setColor(ACCENT);g.setStroke(new BasicStroke(2.2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));var left=new Path2D.Float();left.moveTo(32,46);left.curveTo(25,41,19,41,12,42);left.lineTo(12,19);left.curveTo(20,17,26,19,32,23);left.curveTo(38,19,44,17,52,19);left.lineTo(52,42);left.curveTo(44,41,39,41,32,46);g.draw(left);g.drawLine(32,23,32,46);g.setColor(new Color(126,170,146));g.drawLine(18,26,25,28);g.drawLine(18,32,25,34);g.drawLine(39,28,46,26);g.drawLine(39,34,46,32);g.dispose();}
 }
}

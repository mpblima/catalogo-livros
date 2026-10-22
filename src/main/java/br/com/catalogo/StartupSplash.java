package br.com.catalogo;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/** Splash sem espera artificial; acompanha o carregamento real da biblioteca. */
final class StartupSplash extends JWindow {
 final JLabel message=new JLabel("Preparando sua biblioteca…");
 final JProgressBar progress=new JProgressBar(0,100);
 StartupSplash(){
  setSize(560,330);setLocationRelativeTo(null);setAlwaysOnTop(true);
  var canvas=new JPanel(new BorderLayout(0,22)){
   protected void paintComponent(Graphics graphics){super.paintComponent(graphics);var g=(Graphics2D)graphics.create();g.setPaint(new GradientPaint(0,0,new Color(26,43,37),560,330,UiTheme.BACKGROUND));g.fillRect(0,0,getWidth(),getHeight());g.setColor(new Color(56,86,68,65));g.setStroke(new BasicStroke(1));for(int i=0;i<4;i++)g.draw(new Ellipse2D.Float(360+i*28,-120+i*20,330,330));g.dispose();}
  };
  canvas.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.LINE),BorderFactory.createEmptyBorder(35,38,30,38)));setContentPane(canvas);
  var heading=UiTheme.panel(new BorderLayout(18,0));heading.add(new JLabel(new UiTheme.BookIcon(66)),BorderLayout.WEST);var names=UiTheme.panel(new GridLayout(2,1,0,5));names.add(UiTheme.eyebrow("CATÁLOGO DE LIVROS"));var title=new JLabel("Biblioteca");title.setForeground(UiTheme.TEXT);title.setFont(new Font("Serif",Font.BOLD,38));names.add(title);heading.add(names);canvas.add(heading,BorderLayout.NORTH);
  var description=new JLabel("Livros, ideias e leituras em um só lugar.");description.setForeground(UiTheme.MUTED);description.setFont(description.getFont().deriveFont(15f));canvas.add(description);
  var bottom=UiTheme.panel(new BorderLayout(0,12));message.setForeground(UiTheme.TEXT);message.setFont(message.getFont().deriveFont(13f));bottom.add(message,BorderLayout.NORTH);progress.setPreferredSize(new Dimension(450,5));progress.setBorderPainted(false);progress.setIndeterminate(true);bottom.add(progress);canvas.add(bottom,BorderLayout.SOUTH);
 }
 void stage(String text,int value){message.setText(text);progress.setIndeterminate(false);progress.setValue(value);}
}

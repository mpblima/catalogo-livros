package br.com.catalogo;
import org.apache.pdfbox.Loader;import org.apache.pdfbox.pdmodel.PDDocument;import org.apache.pdfbox.rendering.PDFRenderer;
import javax.swing.*;import java.awt.*;import java.awt.event.*;

/** Janela de leitura independente, redimensionável e maximizável, com ajuste à largura. */
final class PdfReader extends JFrame {
 private PDDocument document;private final Biblioteca library;private final Biblioteca.Book book;private final Runnable changed;
 private final JLabel image=new JLabel("Abrindo documento…",SwingConstants.CENTER),status=new JLabel(" ");
 private final JSpinner page=new JSpinner(new SpinnerNumberModel(1,1,1000000,1));private final JButton previous=new JButton("← Anterior"),next=new JButton("Próxima →");
 private final JComboBox<String> zoom=new JComboBox<>(new String[]{"Ajustar à largura","100%","125%","150%"});private final JProgressBar progress=new JProgressBar();private final JScrollPane viewport=new JScrollPane(image);
 private boolean rendering=false,closing=false;private int wanted=1;private float wantedDpi=110;private final javax.swing.Timer resizeDelay;
 PdfReader(JFrame owner,Biblioteca lib,Biblioteca.Book b,Runnable changed){
  super(b.display());library=lib;book=b;this.changed=changed;setIconImage(UiTheme.appIcon());setResizable(true);setMinimumSize(new Dimension(640,480));setSize(1000,850);setLocationRelativeTo(owner);setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
  var bar=new JPanel(new FlowLayout(FlowLayout.CENTER,12,10));bar.add(previous);bar.add(new JLabel("Página"));bar.add(page);bar.add(next);bar.add(zoom);progress.setIndeterminate(true);bar.add(progress);add(bar,BorderLayout.NORTH);add(viewport);status.setBorder(BorderFactory.createEmptyBorder(8,14,8,14));add(status,BorderLayout.SOUTH);viewport.getVerticalScrollBar().setUnitIncrement(24);
  previous.addActionListener(e->page.setValue(Math.max(1,(Integer)page.getValue()-1)));next.addActionListener(e->{if(document!=null)page.setValue(Math.min(document.getNumberOfPages(),(Integer)page.getValue()+1));});page.addChangeListener(e->request());zoom.addActionListener(e->request());
  resizeDelay=new javax.swing.Timer(180,e->request());resizeDelay.setRepeats(false);viewport.getViewport().addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){if(!closing)resizeDelay.restart();}});
  getRootPane().registerKeyboardAction(e->previous.doClick(),KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,0),JComponent.WHEN_IN_FOCUSED_WINDOW);getRootPane().registerKeyboardAction(e->next.doClick(),KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
  addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){closing=true;resizeDelay.stop();if(!rendering)finish();}});
  new SwingWorker<PDDocument,Void>(){protected PDDocument doInBackground()throws Exception{return Loader.loadPDF(library.file(book).toFile());}protected void done(){try{document=get();if(closing){finish();return;}page.setModel(new SpinnerNumberModel(Math.min(Math.max(1,book.page),document.getNumberOfPages()),1,document.getNumberOfPages(),1));request();}catch(Exception e){JOptionPane.showMessageDialog(PdfReader.this,"Não foi possível abrir: "+e.getMessage());dispose();}}}.execute();
 }
 private void request(){if(document==null||closing)return;wanted=(Integer)page.getValue();float width=document.getPage(wanted-1).getCropBox().getWidth();int rotation=document.getPage(wanted-1).getRotation();if(rotation%180!=0)width=document.getPage(wanted-1).getCropBox().getHeight();wantedDpi=zoom.getSelectedIndex()==0?Math.max(36,Math.min(240,(Math.max(500,viewport.getViewport().getWidth())-30)*72f/width)):72*(zoom.getSelectedIndex()==1?1:zoom.getSelectedIndex()==2?1.25f:1.5f);if(!rendering)render();}
 private void render(){if(closing){finish();return;}rendering=true;int target=wanted;float dpi=wantedDpi;progress.setVisible(true);
  new SwingWorker<ImageIcon,Void>(){protected ImageIcon doInBackground()throws Exception{return new ImageIcon(new PDFRenderer(document).renderImageWithDPI(target-1,dpi));}protected void done(){try{image.setIcon(get());image.setText("");library.saveProgress(book,target);changed.run();status.setText("Página "+target+" de "+document.getNumberOfPages()+" · progresso salvo automaticamente");}catch(Exception e){status.setText("Falha ao ler ou salvar: "+e.getMessage());}finally{rendering=false;progress.setVisible(false);if(closing)finish();else if(wanted!=target||Math.abs(wantedDpi-dpi)>1)render();}}}.execute();
 }
 private void finish(){resizeDelay.stop();try{if(document!=null){document.close();document=null;}dispose();}catch(Exception e){status.setText(e.getMessage());}}
}

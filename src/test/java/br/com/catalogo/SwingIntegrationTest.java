package br.com.catalogo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import java.awt.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
@EnabledIfSystemProperty(named="catalogo.guiTest",matches="true")
class SwingIntegrationTest {
 @TempDir Path root;Biblioteca lib;CatalogoApp app;PdfReader reader;Path pdf;List<Exception> errors=new ArrayList<>();
 @BeforeEach void setup()throws Exception{
  var f=new BibliotecaTest();f.root=root;f.setup();lib=f.library;pdf=root.resolve("programacao/java/livro.pdf");Files.createDirectories(pdf.getParent());
  try(var doc=new PDDocument()){for(int i=0;i<3;i++){var pg=new PDPage();doc.addPage(pg);try(var cs=new PDPageContentStream(doc,pg)){cs.beginText();cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),24);cs.newLineAtOffset(60,700);cs.showText("Pagina "+(i+1));cs.endText();}}doc.save(pdf.toFile());}
  edt(()->{UiTheme.install();app=new CatalogoApp(lib,false);app.reportError=errors::add;app.setVisible(true);});settled();
 }
 @AfterEach void close()throws Exception{edt(()->{if(reader!=null)reader.dispatchEvent(new java.awt.event.WindowEvent(reader,java.awt.event.WindowEvent.WINDOW_CLOSING));if(app!=null){app.closed=true;app.searchDelay.stop();app.autoSave.stop();if(app.searchWorker!=null)app.searchWorker.cancel(false);app.dispose();}});}
 interface Action {void run()throws Exception;}
 void edt(Action action)throws Exception{var error=new AtomicReference<Throwable>();SwingUtilities.invokeAndWait(()->{try{action.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());}
 void await(BooleanSupplier condition)throws Exception{long until=System.currentTimeMillis()+10000;while(System.currentTimeMillis()<until){boolean[] pass={false};edt(()->pass[0]=condition.getAsBoolean());if(pass[0])return;Thread.sleep(30);}fail("Tempo excedido; erros: "+errors);}
 void settled()throws Exception{await(()->app.appliedSerial==app.searchSerial&&!app.searchDelay.isRunning());}
 javax.swing.tree.TreePath categoryPath(String category){
  var root=(javax.swing.tree.DefaultMutableTreeNode)app.tree.getModel().getRoot();var nodes=root.depthFirstEnumeration();
  while(nodes.hasMoreElements()){var node=(javax.swing.tree.DefaultMutableTreeNode)nodes.nextElement();if(node.getUserObject() instanceof CatalogoApp.Category c&&c.path().equals(category))return new javax.swing.tree.TreePath(node.getPath());}
  throw new AssertionError("Categoria ausente: "+category);
 }
 @Test void selectingCategoryShowsItsBooksAndClearsPreviousFilters()throws Exception{
  try(var c=lib.connect();var s=c.createStatement()){
   s.execute("INSERT INTO books VALUES('id2','cloud/azure/azure.pdf','Azure Essentials','azure.pdf','cloud/azure','PDF','{}',1)");
   s.execute("INSERT INTO categories VALUES('cloud'),('cloud/azure'),('programacao')");
  }
  edt(()->{app.shelf.setSelectedIndex(1);app.search.setText("inexistente");});settled();
  edt(()->app.tree.setSelectionPath(categoryPath("programacao")));settled();
  edt(()->{assertEquals(0,app.shelf.getSelectedIndex());assertEquals("",app.search.getText());assertEquals(1,app.visible.size());assertEquals("id1",app.visible.get(0).id);});
  edt(()->app.tree.setSelectionPath(categoryPath("cloud/azure")));settled();
  edt(()->{assertEquals(1,app.visible.size());assertEquals("id2",app.visible.get(0).id);});
  edt(()->app.tree.setSelectionRow(0));settled();edt(()->assertEquals(2,app.visible.size()));assertTrue(errors.isEmpty());
 }
 @Test void searchesWithoutBlockingAndOpensFolder()throws Exception{
  edt(()->{assertEquals("Livro",app.model.getColumnName(0));assertEquals("Programação Java",app.model.getValueAt(0,0));Path[] opened={null};app.openFolder=p->opened[0]=p;app.table.moveColumn(1,0);var cell=app.table.getCellRect(0,0,true);app.table.dispatchEvent(new java.awt.event.MouseEvent(app.table,java.awt.event.MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,cell.x+10,cell.y+10,1,false,java.awt.event.MouseEvent.BUTTON1));assertEquals(pdf.getParent(),opened[0]);app.table.moveColumn(0,1);app.category="nenhuma";app.shelf.setSelectedIndex(2);app.search.setText("prog jav");});settled();edt(()->assertEquals(1,app.model.getRowCount()));
  // Segurar uma escrita força a busca a esperar pelo SQLite; a thread da interface continua livre.
  try(var c=lib.connect();var s=c.createStatement()){s.execute("BEGIN IMMEDIATE");edt(()->app.search.setText("editora exemplo"));Thread.sleep(300);edt(()->{assertNotNull(app.searchWorker);assertFalse(app.searchWorker.isDone());app.status.setText("Interface continua respondendo");});s.execute("ROLLBACK");}settled();edt(()->assertEquals(1,app.model.getRowCount()));
  edt(()->{app.search.setText("inexistente");app.search.setText("prog");app.search.setText("inexistente");});settled();edt(()->assertEquals(0,app.model.getRowCount()));assertTrue(errors.isEmpty());
 }
 @Test void preservesUnsavedNotesWhenUpdateRefreshFails()throws Exception{
  edt(()->{app.notes.setText("Texto que não pode sumir");app.autoSave.stop();});
  try(var c=lib.connect();var s=c.createStatement()){s.execute("CREATE TRIGGER fail_marks BEFORE UPDATE ON marks BEGIN SELECT RAISE(ABORT,'Falha simulada'); END");}
  edt(()->{assertFalse(app.finishCatalogUpdate());assertEquals("Texto que não pode sumir",app.notes.getText());assertTrue(app.pendingRefresh);});
  try(var c=lib.connect();var s=c.createStatement()){s.execute("DROP TRIGGER fail_marks");}
  edt(()->assertTrue(app.save()));settled();assertEquals("Texto que não pode sumir",lib.books().get(0).notes);
 }
 @Test void groupsFormatsKeepsAnnotationsAndLabelsSamples()throws Exception{
  try(var c=lib.connect();var s=c.createStatement()){s.execute("INSERT INTO books VALUES('id2','programacao/java/livro.epub','Programação Java','livro.epub','programacao/java','EPUB','{}',1)");s.execute("INSERT INTO books VALUES('id3','programacao/java/amostra.pdf','Amostra Java','amostra.pdf','programacao/java','PDF','{}',1)");s.execute("INSERT INTO book_relations VALUES('id1','id2','same_work','teste')");s.execute("INSERT INTO book_relations VALUES('id3','id1','sample_of','teste')");}
  edt(()->app.filter());settled();edt(()->{assertEquals(1,app.model.getRowCount());assertEquals(3,app.versions.getItemCount());assertTrue(app.model.getValueAt(0,1).toString().contains("3 arquivos"));app.notes.setText("Nota do PDF");assertTrue(app.save());app.versions.setSelectedIndex(1);assertEquals("id2",app.selected.id);assertEquals("",app.notes.getText());app.notes.setText("Nota do EPUB");assertTrue(app.save());app.versions.setSelectedIndex(2);assertTrue(app.versionLabel(app.selected).contains("Amostra"));});
  var books=lib.books();assertEquals("Nota do PDF",books.stream().filter(b->b.id.equals("id1")).findFirst().get().notes);assertEquals("Nota do EPUB",books.stream().filter(b->b.id.equals("id2")).findFirst().get().notes);
 }
 @Test void pdfResumePreservesOtherWindowsNotes()throws Exception{
  var stored=lib.books().get(0);stored.page=2;lib.save(stored);var other=lib.books().get(0);other.notes="Anotação da outra janela";lib.save(other);
  edt(()->reader=new PdfReader(app,lib,stored,()->{}));await(()->{for(Component component:((JPanel)reader.getContentPane().getComponent(0)).getComponents())if(component instanceof JSpinner spinner&&Integer.valueOf(3).equals(((SpinnerNumberModel)spinner.getModel()).getMaximum())){spinner.setValue(3);return true;}return false;});
  long deadline=System.currentTimeMillis()+10000;while(lib.books().get(0).page!=3&&System.currentTimeMillis()<deadline)Thread.sleep(50);assertEquals(3,lib.books().get(0).page);assertEquals("Anotação da outra janela",lib.books().get(0).notes);
 }

 @Test void readerResizesAndKeepsMainWindowNotesPending()throws Exception{
  edt(()->{reader=new PdfReader(app,lib,app.selected,()->app.updateReaderProgress(app.selected));reader.setVisible(true);assertTrue(reader.isResizable());reader.setSize(760,600);});
  await(()->reader.getContentPane().getComponentCount()==3);
  edt(()->{assertEquals(760,reader.getWidth());reader.setSize(1100,800);assertEquals(1100,reader.getWidth());app.notes.setText("Anotação durante leitura");app.autoSave.stop();});
  await(()->{for(Component c:((JPanel)reader.getContentPane().getComponent(0)).getComponents())if(c instanceof JSpinner spinner&&Integer.valueOf(3).equals(((SpinnerNumberModel)spinner.getModel()).getMaximum())){spinner.setValue(2);return true;}return false;});
  await(()->app.selected.page==2);edt(()->{assertEquals("Anotação durante leitura",app.notes.getText());assertTrue(app.save());});assertEquals("Anotação durante leitura",lib.books().get(0).notes);assertEquals(2,lib.books().get(0).page);
 }

 @Test void splashTracksStagesAndClosesWhenDataIsReady()throws Exception{
  StartupSplash[] splash={null};
  try{
   edt(()->{splash[0]=new StartupSplash();splash[0].setVisible(true);splash[0].stage("Carregando o acervo",70);assertEquals(70,splash[0].progress.getValue());assertEquals("Carregando o acervo",splash[0].message.getText());app.readyDelivered=false;app.onReady=()->splash[0].dispose();app.filter();});
   settled();edt(()->{assertFalse(splash[0].isDisplayable());assertTrue(app.notes.getHeight()>40,"Área de anotações deve permanecer visível");assertNotNull(app.getIconImage());});
  }finally{edt(()->{if(splash[0]!=null)splash[0].dispose();});}
 }
}

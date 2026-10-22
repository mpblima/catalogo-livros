package br.com.catalogo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import javax.swing.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
@EnabledIfSystemProperty(named="catalogo.guiTest",matches="true")
class StartupIntegrationTest {
 @TempDir Path root;
 @Test void openingDetectsOrganizesAndIndexesNewPdf()throws Exception {
  new Biblioteca(root);
  Path loose=root.resolve("Livro Novo.pdf");try(var doc=new PDDocument()){var pg=new PDPage();doc.addPage(pg);doc.getDocumentInformation().setTitle("Kotlin Essentials");try(var cs=new PDPageContentStream(doc,pg)){cs.beginText();cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),24);cs.newLineAtOffset(60,700);cs.showText("Kotlin Essentials");cs.endText();}doc.save(loose.toFile());}
  var lib=new Biblioteca(root);CatalogoApp[] app={null};var errors=new java.util.ArrayList<Exception>();
  try{
   SwingUtilities.invokeAndWait(()->{try{app[0]=new CatalogoApp(lib);app[0].reportError=errors::add;app[0].setVisible(true);}catch(Exception e){throw new RuntimeException(e);}});
   long deadline=System.currentTimeMillis()+30000;boolean[] finished={false};while(System.currentTimeMillis()<deadline){Thread.sleep(60);SwingUtilities.invokeAndWait(()->finished[0]=!app[0].busy&&app[0].all.size()==1&&app[0].appliedSerial==app[0].searchSerial);if(finished[0])break;}
   assertTrue(finished[0],errors.toString());assertFalse(Files.exists(loose));assertEquals(1,lib.books().size());assertEquals(1,lib.searchIds("kotlin").size());assertTrue(lib.books().get(0).path.contains("kotlin"));
  }finally{SwingUtilities.invokeAndWait(()->{if(app[0]!=null){app[0].closed=true;app[0].searchDelay.stop();app[0].autoSave.stop();if(app[0].searchWorker!=null)app[0].searchWorker.cancel(false);app[0].dispose();}});}
 }
}

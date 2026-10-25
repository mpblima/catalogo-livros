package br.com.catalogo;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.awt.image.BufferedImage;import java.io.*;import javax.imageio.ImageIO;
class CoverQueueTest {
 @TempDir Path root;
 static byte[] image()throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(80,120,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
 String add(Biblioteca lib,String title)throws Exception{var d=new PhysicalBooks.Draft();d.title=title;d.author="Maria";d.notes="Nota preservada";d.summary="Resumo preservado";return PhysicalBooks.save(lib,d);}
 @Test void persistsRetriesAndPreservesBookInformation()throws Exception{
  var lib=new Biblioteca(root);String id=add(lib,"Livro de Go");try(var q=new CoverQueue(lib,(i,t,a,u)->{throw new IOException("Sem conexão");})){assertTrue(q.runNext());}
  try(var c=lib.connect();var s=c.createStatement();var r=s.executeQuery("SELECT attempts,next_attempt,lease_until,last_error FROM cover_jobs")){assertTrue(r.next());assertEquals(1,r.getInt(1));assertTrue(r.getLong(2)>System.currentTimeMillis());assertEquals(0,r.getLong(3));assertEquals("Sem conexão",r.getString(4));}
  try(var q=new CoverQueue(new Biblioteca(root),(i,t,a,u)->image())){assertFalse(q.runNext());try(var c=lib.connect();var s=c.createStatement()){s.execute("UPDATE cover_jobs SET next_attempt=0");}assertTrue(q.runNext());assertFalse(q.runNext());}
  assertNotNull(BookPreview.stored(lib,id).image());assertEquals("Resumo preservado",BookPreview.stored(lib,id).summary());assertEquals("Nota preservada",lib.books().get(0).notes);
 }
 @Test void twoWorkersShareOneSerialQueueAndDeletionCannotResurrectBook()throws Exception{
  var lib=new Biblioteca(root);String first=add(lib,"Primeiro");add(lib,"Segundo");var started=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
  try(var q1=new CoverQueue(lib,(i,t,a,u)->{started.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return image();});var q2=new CoverQueue(lib,(i,t,a,u)->image())){
   var running=pool.submit(q1::runNext);assertTrue(started.await(5,TimeUnit.SECONDS));assertFalse(q2.runNext());lib.deleteBook(first);release.countDown();assertTrue(running.get(5,TimeUnit.SECONDS));assertTrue(q2.runNext());assertEquals(1,lib.books().size());assertNotNull(BookPreview.stored(lib,lib.books().get(0).id).image());assertTrue(lib.searchIds("Primeiro").isEmpty());
  }finally{release.countDown();pool.shutdownNow();}
 }
 @Test void manualCoverClearsPendingJobAndRejectsInvalidImages()throws Exception{
  var lib=new Biblioteca(root);String id=add(lib,"Livro");assertThrows(IOException.class,()->CoverQueue.saveCover(lib,id,new byte[]{1,2,3},true));CoverQueue.saveCover(lib,id,image(),true);
  try(var q=new CoverQueue(lib,(i,t,a,u)->{fail("Não deve procurar uma capa existente");return null;})){assertFalse(q.runNext());}assertNotNull(BookPreview.stored(lib,id).image());
 }
 @Test void fillsMissingInformationAndSummaryWithoutOverwritingExistingValues()throws Exception{
  var lib=new Biblioteca(root);String id=add(lib,"Kotlin Essentials");try(var c=lib.connect();var s=c.createStatement()){s.execute("UPDATE book_previews SET summary=''");}
  try(var q=new CoverQueue(lib,(i,t,a,u)->image(),(i,t,a)->{var d=new PhysicalBooks.Draft();d.author="Outro autor";d.publisher="Editora";d.year="2024";d.language="pt";d.pages="200";d.summary="Resumo documentado";d.source="Fonte verificada";return d;})){assertTrue(q.runNext());assertFalse(q.runNext());}
  var book=lib.books().get(0);assertEquals("Maria",book.meta("author"));assertEquals("200",book.meta("pages"));assertEquals("Editora",book.meta("publisher"));assertEquals("Nota preservada",book.notes);assertEquals("Resumo documentado",BookPreview.stored(lib,id).summary());assertNotNull(BookPreview.stored(lib,id).image());
 }
 @Test void retriesMissingSummaryEvenWhenCoverAlreadyExists()throws Exception{
  var lib=new Biblioteca(root);String id=add(lib,"Livro");CoverQueue.saveCover(lib,id,image(),true);
  try(var q=new CoverQueue(lib,(i,t,a,u)->{fail("Capa existente deve ser preservada");return null;},(i,t,a)->{throw new IOException("Pesquisa indisponível");})){assertTrue(q.runNext());assertNotNull(BookPreview.stored(lib,id).image());try(var c=lib.connect();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM cover_jobs")){r.next();assertEquals(1,r.getInt(1));}}
 }

}

package br.com.catalogo;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;import java.util.*;import java.util.zip.*;import java.io.*;import java.awt.image.BufferedImage;import javax.imageio.ImageIO;
class BookPreviewTest {
 @TempDir Path root;
 @Test void pdfRendersFirstPageAndSummaryWithoutPrefix()throws Exception{Path file=root.resolve("book.pdf");OrganizerTest.pdf(file,"Kotlin Essentials");var preview=BookPreview.load(file);assertNotNull(preview.image());assertTrue(preview.image().getWidth()<=180);assertTrue(preview.image().getHeight()<=220);assertFalse(preview.summary().contains("Trecho inicial:"));assertTrue(preview.summary().contains("Kotlin Essentials"));}
 @Test void catalogingBackfillsAndPreservesStoredPreviews()throws Exception{
  var lib=new Biblioteca(root.resolve("Livros"));Path file=lib.root.resolve("Kotlin.pdf");OrganizerTest.pdf(file,"Kotlin Essentials");var organizer=new LibraryOrganizer(lib);organizer.organize((p,m)->{});var book=lib.books().get(0);var preview=BookPreview.stored(lib,book.id);assertNotNull(preview.image());assertTrue(preview.summary().contains("Kotlin Essentials"));
  try(var c=lib.connect();var s=c.createStatement()){s.execute("UPDATE book_previews SET summary='Resumo preservado'");}
  organizer.organize((p,m)->{});assertEquals("Resumo preservado",BookPreview.stored(lib,book.id).summary());
  try(var c=lib.connect();var s=c.createStatement()){s.execute("UPDATE book_previews SET cover_png=NULL");}
  organizer.organize((p,m)->{});assertNotNull(BookPreview.stored(lib,book.id).image());assertEquals("Resumo preservado",BookPreview.stored(lib,book.id).summary());
 }
 @Test void epubUsesDeclaredCoverAndDescription()throws Exception{
  for(int mode:List.of(0,1,2)){boolean legacy=mode!=0;
  Path file=root.resolve("book"+mode+".epub");var png=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(300,400,BufferedImage.TYPE_INT_RGB),"png",png);
  try(var zip=new ZipOutputStream(Files.newOutputStream(file))){var entries=new LinkedHashMap<String,byte[]>();entries.put("META-INF/container.xml","<container><rootfiles><rootfile full-path='OEBPS/content.opf'/></rootfiles></container>".getBytes(java.nio.charset.StandardCharsets.UTF_8));entries.put("OEBPS/content.opf",("<package xmlns:dc='http://purl.org/dc/elements/1.1/'><metadata><dc:description>Introdução à linguagem Kotlin.</dc:description>"+(mode==1?"<meta name='cover' content='images/front.png'/>":"")+"</metadata><manifest><item id='cover' media-type='image/png' "+(legacy?"":"properties='cover-image'")+" href='images/front.png'/></manifest></package>").getBytes(java.nio.charset.StandardCharsets.UTF_8));entries.put("OEBPS/images/front.png",png.toByteArray());for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}}
  var preview=BookPreview.load(file);assertNotNull(preview.image());assertEquals("Introdução à linguagem Kotlin.",preview.summary());
  }
 }
}

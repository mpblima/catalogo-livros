package br.com.catalogo;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;import java.util.*;import java.util.zip.*;import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.*;import org.apache.pdfbox.pdmodel.font.*;
class OrganizerTest {
 @TempDir Path temp;
 static void pdf(Path path,String title)throws Exception{Files.createDirectories(path.getParent());try(var doc=new PDDocument()){var page=new PDPage();doc.addPage(page);doc.getDocumentInformation().setTitle(title);doc.getDocumentInformation().setAuthor("Autora Exemplo");try(var cs=new PDPageContentStream(doc,page)){cs.beginText();cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),22);cs.newLineAtOffset(50,700);cs.showText(title);cs.newLineAtOffset(0,-50);cs.showText("Published by Manning Publications, Copyright 2025");cs.endText();}doc.save(path.toFile());}}
 @Test void disappearingSqliteSidecarsDoNotAbortDocumentScan()throws Exception{
  var lib=new Biblioteca(temp.resolve("Livros"));var organizer=new LibraryOrganizer(lib);
  Path book=lib.root.resolve("programacao/kotlin.pdf");pdf(book,"Kotlin Essentials");
  var found=new ArrayList<Path>();var visitor=organizer.documentVisitor(found);
  Path wal=lib.root.resolve("catalogo.sqlite-wal");
  assertEquals(FileVisitResult.CONTINUE,visitor.visitFileFailed(wal,new NoSuchFileException(wal.toString())));
  Files.walkFileTree(lib.root,visitor);assertEquals(List.of(book),found);
  assertThrows(AccessDeniedException.class,()->visitor.visitFileFailed(book,new AccessDeniedException(book.toString())));
 }
 @Test void organizesIndexesAndIsIdempotentWhilePreservingMarksAndAliases()throws Exception{
  var lib=new Biblioteca(temp.resolve("Livros"));Path loose=lib.root.resolve("Nome Esquisito.pdf");pdf(loose,"Kotlin Essentials");var organizer=new LibraryOrganizer(lib);var result=organizer.organize((p,m)->{});assertEquals(1,result.get("moved"));var b=lib.books().get(0);assertEquals("Kotlin Essentials",b.title);assertTrue(b.path.contains("kotlin"));assertTrue(b.filename.equals("kotlin_essentials.pdf"));assertEquals("Manning Publications",b.meta("publisher"));assertEquals(Set.of(b.id),lib.searchIds("mann ess"));b.notes="Nota preservada";b.favorite=true;b.page=8;lib.save(b);
  assertEquals(b.id,new HtmlCatalog(lib).resolve("Nome Esquisito.pdf").id);String hash=LibraryOrganizer.sha256(lib.file(b));result=organizer.organize((p,m)->{});assertEquals(0,result.get("moved"));var after=lib.books().get(0);assertEquals(b.id,after.id);assertEquals("Nota preservada",after.notes);assertEquals(8,after.page);assertTrue(after.favorite);assertEquals(hash,LibraryOrganizer.sha256(lib.file(after)));assertTrue(lib.searchIds("preserv").isEmpty());assertTrue(Files.exists(temp.resolve("catalogo_livros.html")));
 }
 @Test void exactDuplicateIsSafelySeparatedAndMalformedFileIsSkipped()throws Exception{
  var lib=new Biblioteca(temp.resolve("Livros"));pdf(lib.root.resolve("Original.pdf"),"Rust Programming");var organizer=new LibraryOrganizer(lib);organizer.organize((p,m)->{});var b=lib.books().get(0);Files.copy(lib.file(b),lib.root.resolve("Copia.pdf"));Files.writeString(lib.root.resolve("invalido.pdf"),"não é pdf");var result=organizer.organize((p,m)->{});assertEquals(1,result.get("duplicates"));assertEquals(1,lib.books().size());assertTrue(Files.exists(lib.root.resolve("organizacao/duplicados/copia.pdf")));assertTrue(Files.exists(lib.root.resolve("invalido.pdf")));assertFalse(((List<?>)result.get("skipped")).isEmpty());
 }
 @Test void transactionFailureRollsFileBack()throws Exception{
  var lib=new Biblioteca(temp.resolve("Livros"));pdf(lib.root.resolve("Rollback.pdf"),"Kotlin Essentials");try(var c=lib.connect();var s=c.createStatement()){s.execute("CREATE TRIGGER fail_move BEFORE UPDATE OF path ON books BEGIN SELECT RAISE(ABORT,'Falha simulada'); END");}var result=new LibraryOrganizer(lib).organize((p,m)->{});assertEquals(0,result.get("moved"));assertTrue(Files.exists(lib.root.resolve("Rollback.pdf")));assertEquals("Rollback.pdf",lib.books().get(0).path);assertFalse(((List<?>)result.get("skipped")).isEmpty());
 }
 @Test void epubExtractsRealTitleAuthorPublisherAndCreatesCategory()throws Exception{
  var lib=new Biblioteca(temp.resolve("Livros"));Path path=lib.root.resolve("123.epub");try(var zip=new ZipOutputStream(Files.newOutputStream(path))){for(var e:Map.of("META-INF/container.xml","<container><rootfiles><rootfile full-path=\"content.opf\"/></rootfiles></container>","content.opf","<package xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><metadata><dc:title>Terraform in Action</dc:title><dc:creator>Autor Exemplo</dc:creator><dc:publisher>Manning</dc:publisher><dc:date>2024</dc:date></metadata></package>").entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}new LibraryOrganizer(lib).organize((p,m)->{});var b=lib.books().get(0);assertEquals("Terraform in Action",b.title);assertEquals("Autor Exemplo",b.meta("author"));assertEquals("Manning",b.meta("publisher"));assertTrue(lib.categories().contains(b.section));assertTrue(b.path.contains("terraform"));assertEquals(Set.of(b.id),lib.searchIds("manning terra"));
 }
 @Test void scannedCoverUsesBundledOcr()throws Exception{
  Path path=temp.resolve("scanned.pdf");var image=new java.awt.image.BufferedImage(1400,1800,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,1400,1800);g.setColor(java.awt.Color.BLACK);g.setFont(new java.awt.Font("SansSerif",java.awt.Font.BOLD,88));g.drawString("Kotlin Essentials",90,300);g.dispose();try(var doc=new PDDocument()){var page=new PDPage();doc.addPage(page);try(var cs=new PDPageContentStream(doc,page)){cs.drawImage(org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc,image),0,0,page.getMediaBox().getWidth(),page.getMediaBox().getHeight());}doc.save(path.toFile());}try(var extractor=new DocumentExtractor()){var result=extractor.extract(path,"scanned.pdf");assertTrue(result.title().toLowerCase().contains("kotlin"),result.toString());assertEquals("pdf_cover_ocr",result.source());}
 }
 @Test void rejectsPathsOutsideLibraryAndSymlinkDestinations()throws Exception{var lib=new Biblioteca(temp.resolve("Livros"));var organizer=new LibraryOrganizer(lib);assertThrows(java.io.IOException.class,()->organizer.safe("../fora"));Files.createSymbolicLink(lib.root.resolve("link"),temp);assertThrows(java.io.IOException.class,()->organizer.safe("link/arquivo.pdf"));}
}

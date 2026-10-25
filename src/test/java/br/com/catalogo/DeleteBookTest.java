package br.com.catalogo;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;import java.nio.file.*;
class DeleteBookTest {
 @TempDir Path root;
 @Test void deletesPhysicalDependenciesAndAllowsRegisteringBarcodeAgain()throws Exception{
  var lib=new Biblioteca(root.resolve("Livros"));var d=new PhysicalBooks.Draft();d.title="Kotlin Essentials";d.isbn="9780132350884";d.barcode=d.isbn;d.notes="Nota";String id=PhysicalBooks.save(lib,d);assertFalse(lib.searchIds("kotlin").isEmpty());new LibraryOrganizer(lib).delete(id);assertTrue(lib.books().isEmpty());assertTrue(lib.searchIds("kotlin").isEmpty());
  try(var c=lib.connect();var s=c.createStatement()){for(String table:new String[]{"physical_books","marks","book_previews","cover_jobs","path_aliases","search_documents","book_relations"})try(var r=s.executeQuery("SELECT count(*) FROM "+table)){r.next();assertEquals(0,r.getInt(1),table);}try(var r=s.executeQuery("PRAGMA foreign_key_check")){assertFalse(r.next());}}
  assertNotNull(PhysicalBooks.save(lib,d));
 }
 @Test void deletingDigitalRegistrationPreservesFileAndDoesNotRecatalogIt()throws Exception{
  var lib=new Biblioteca(root.resolve("Livros"));OrganizerTest.pdf(lib.root.resolve("Kotlin.pdf"),"Kotlin Essentials");var organizer=new LibraryOrganizer(lib);organizer.organize((p,m)->{});var book=lib.books().get(0);Path file=lib.file(book);String hash=LibraryOrganizer.sha256(file);organizer.delete(book.id);assertTrue(Files.isRegularFile(file));organizer.organize((p,m)->{});assertTrue(lib.books().isEmpty());assertEquals(hash,LibraryOrganizer.sha256(file));assertTrue(lib.searchIds("kotlin").isEmpty());
 }
}

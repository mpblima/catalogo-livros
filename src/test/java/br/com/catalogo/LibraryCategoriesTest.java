package br.com.catalogo;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;import java.util.*;
class LibraryCategoriesTest {
 @TempDir Path root;
 @Test void migrationPreservesPathsMarksPreviewsAndCanBeRepeated()throws Exception{
  var f=new BibliotecaTest();f.root=root;f.setup();var lib=f.library;Path file=root.resolve("programacao/java/livro.pdf");OrganizerTest.pdf(file,"Programação Java");var book=lib.books().get(0);book.notes="Minha anotação";book.favorite=true;book.page=3;lib.save(book);BookPreview.ensureStored(lib,file);
  byte[] original;try(var c=lib.connect();var s=c.createStatement()){try(var r=s.executeQuery("SELECT cover_png FROM book_previews")){r.next();original=r.getBytes(1);}s.execute("DELETE FROM catalog_migrations");s.execute("DELETE FROM categories");LibraryCategories.add(c,"programacao/java");s.execute("UPDATE books SET section='programacao/java'");}
  var migrated=new Biblioteca(root);var after=migrated.books().get(0);assertEquals(book.id,after.id);assertEquals(book.path,after.path);assertEquals("livros_digitais/programacao/java",after.section);assertEquals(book.notes,after.notes);assertEquals(3,after.page);assertTrue(after.favorite);assertTrue(Files.exists(migrated.file(after)));
  assertEquals(Set.of("livros_digitais","livros_fisicos"),migrated.categories().stream().filter(p->!p.contains("/")).collect(java.util.stream.Collectors.toSet()));
  try(var c=migrated.connect();var s=c.createStatement();var r=s.executeQuery("SELECT cover_png FROM book_previews")){r.next();assertArrayEquals(original,r.getBytes(1));}
  assertEquals(migrated.categories(),new Biblioteca(root).categories());
 }
 @Test void catalogingStaysDigitalAndMovingUsesOriginalDiskLayout()throws Exception{
  var lib=new Biblioteca(root.resolve("Livros"));OrganizerTest.pdf(lib.root.resolve("Kotlin.pdf"),"Kotlin Essentials");OrganizerTest.pdf(lib.root.resolve("livros_fisicos/Scan.pdf"),"Rust Programming");var organizer=new LibraryOrganizer(lib);organizer.organize((p,m)->{});assertEquals(1,lib.books().size());var b=lib.books().get(0);assertTrue(b.section.startsWith("livros_digitais/"));assertFalse(b.path.startsWith("livros_digitais/"));
  try(var c=lib.connect()){LibraryCategories.add(c,"livros_digitais/revisar");}
  String moved=organizer.move(b.path,"livros_digitais/revisar",(p,m)->{});assertTrue(moved.startsWith("revisar/"));assertEquals("livros_digitais/revisar",lib.books().get(0).section);assertThrows(IllegalArgumentException.class,()->organizer.move(moved,"livros_fisicos",(p,m)->{}));
  organizer.organize((p,m)->{});assertEquals(1,lib.books().size());assertEquals("livros_digitais/revisar",lib.books().get(0).section);
 }
}

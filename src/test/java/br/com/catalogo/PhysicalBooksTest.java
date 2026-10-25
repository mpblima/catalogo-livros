package br.com.catalogo;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;import java.util.*;
class PhysicalBooksTest {
 @TempDir Path root;
 @Test void manualRegistrationWithoutBarcodePersistsAllFieldsAndRemainsSearchable()throws Exception{
  var lib=new Biblioteca(root.resolve("Livros"));var d=new PhysicalBooks.Draft();d.title="Kotlin Essentials";d.author="Autora Exemplo";d.publisher="Editora Teste";d.notes="Exemplar da estante";d.subject="Programação";d.category="livros_fisicos/programacao/kotlin";d.summary="Introdução à linguagem Kotlin";d.location="Estante A";d.favorite=true;d.reading=true;d.page=12;
  String id=PhysicalBooks.save(lib,d);var b=lib.books().get(0);assertEquals(id,b.id);assertTrue(b.physical());assertEquals("livros_fisicos/programacao/kotlin",b.section);assertEquals(d.author,b.meta("author"));assertEquals(d.notes,b.notes);assertEquals(d.location,b.location);assertEquals(12,b.page);assertTrue(b.favorite);assertTrue(b.reading);assertEquals(d.summary,BookPreview.stored(lib,id).summary());assertEquals(Set.of(id),lib.searchIds("kotlin editora teste"));
  new LibraryOrganizer(lib).organize((p,m)->{});assertEquals(1,lib.books().size());assertTrue(lib.books().get(0).physical());assertEquals(id,new Biblioteca(lib.root).books().get(0).id);
 }
 @Test void autoClassifiesEveryManualRegistrationAndPreservesCustomCategory()throws Exception{
  var lib=new Biblioteca(root);var d=new PhysicalBooks.Draft();d.title="Kotlin Essentials";String id=PhysicalBooks.save(lib,d);assertEquals("livros_fisicos/programacao/kotlin",lib.books().get(0).section);assertTrue(lib.categories().contains("livros_fisicos/programacao"));d=new PhysicalBooks.Draft();d.title="Um minuto para sempre";d.subject="Devotional calendars";id=PhysicalBooks.save(lib,d);String devotional=id;assertEquals("livros_fisicos/religiao_e_espiritualidade/devocionais",lib.books().stream().filter(b->b.id.equals(devotional)).findFirst().orElseThrow().section);d=new PhysicalBooks.Draft();d.title="Kotlin personalizado";d.category="livros_fisicos/Meus estudos/Referências";String custom=PhysicalBooks.save(lib,d);assertEquals(d.category,lib.books().stream().filter(b->b.id.equals(custom)).findFirst().orElseThrow().section);
 }
 @Test void repeatedBarcodeOrEquivalentIsbnDoesNotCreateAnotherRecord()throws Exception{
  var lib=new Biblioteca(root.resolve("Livros"));var d=new PhysicalBooks.Draft();d.title="Clean Code";d.isbn="0-13-235088-2";d.barcode="9780132350884";PhysicalBooks.save(lib,d);assertEquals("9780132350884",lib.books().get(0).meta("isbn"));assertThrows(IllegalArgumentException.class,()->PhysicalBooks.save(lib,d));assertEquals(1,lib.books().size());
  assertThrows(IllegalArgumentException.class,()->PhysicalBooks.isbn("9780132350885"));assertEquals("9780132350884",PhysicalBooks.isbn("978013235088451995"));
 }
 @Test void globalSearchFindsPhysicalAndDigitalEditionsSeparately()throws Exception{
  var lib=new Biblioteca(root.resolve("Livros"));OrganizerTest.pdf(lib.root.resolve("Kotlin.pdf"),"Kotlin Essentials");var organizer=new LibraryOrganizer(lib);organizer.organize((p,m)->{});var d=new PhysicalBooks.Draft();d.title="Kotlin Essentials";d.isbn="9780132350884";PhysicalBooks.save(lib,d);organizer.organize((p,m)->{});
  assertEquals(2,lib.books().size());assertEquals(2,lib.searchIds("kotlin essentials").size());assertEquals(2,WorkGroups.group(lib.books(),lib.relations()).size());assertEquals(1,lib.books().stream().filter(Biblioteca.Book::physical).count());
 }
}

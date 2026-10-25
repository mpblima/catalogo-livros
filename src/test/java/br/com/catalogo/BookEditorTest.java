package br.com.catalogo;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;import java.nio.file.*;
class BookEditorTest {
 @TempDir Path root;
 @Test void physicalEditPersistsCategoriesMetadataAndReadingState()throws Exception{
  var lib=new Biblioteca(root);var d=new PhysicalBooks.Draft();d.title="Original";String id=PhysicalBooks.save(lib,d);var b=lib.books().get(0);d=BookEditor.load(lib,b);d.title="Revisado";d.author="Maria";d.edition="3";d.category="livros_fisicos/Estudos/História";d.notes="Notas";d.summary="Resumo";d.page=32;BookEditor.save(lib,b,d);
  b=new Biblioteca(root).books().get(0);assertEquals(id,b.id);assertEquals("Revisado",b.title);assertEquals("Maria",b.meta("author"));assertEquals(d.category,b.section);assertEquals(32,b.page);assertEquals("Notas",b.notes);assertEquals("Resumo",BookPreview.stored(lib,id).summary());assertTrue(lib.categories().contains("livros_fisicos/Estudos"));assertTrue(lib.searchIds("Revisado Maria").contains(id));
 }
 @Test void digitalEditKeepsFileAndCategory()throws Exception{
  var lib=new Biblioteca(root);OrganizerTest.pdf(root.resolve("Kotlin.pdf"),"Kotlin Essentials");new LibraryOrganizer(lib).organize((p,m)->{});var b=lib.books().get(0);var d=BookEditor.load(lib,b);d.title="Meu título";d.category="livros_fisicos/Outro";d.publisher="Editora";BookEditor.save(lib,b,d);var changed=lib.books().get(0);assertEquals(b.path,changed.path);assertEquals(b.section,changed.section);assertEquals("Meu título",changed.title);assertTrue(Files.exists(lib.file(changed)));new LibraryOrganizer(lib).organize((p,m)->{});assertEquals("Meu título",lib.books().get(0).title);
 }
 @Test void metadataOnlyEditPreservesConcurrentProgressAndNotes()throws Exception{
  var lib=new Biblioteca(root);var first=new PhysicalBooks.Draft();first.title="Livro original";PhysicalBooks.save(lib,first);var b=lib.books().get(0);var draft=BookEditor.load(lib,b);var other=lib.books().get(0);lib.saveProgress(other,35);other.notes="Notas de outra janela";lib.save(other);draft.title="Título revisado";BookEditor.save(lib,b,draft);var actual=lib.books().get(0);assertEquals("Título revisado",actual.title);assertEquals(35,actual.page);assertTrue(actual.reading);assertEquals("Notas de outra janela",actual.notes);
 }
 @Test void conflictingTextRollsBackMetadataCategoryAndCoverTogether()throws Exception{
  var lib=new Biblioteca(root);var first=new PhysicalBooks.Draft();first.title="Original";first.notes="Antes";PhysicalBooks.save(lib,first);var b=lib.books().get(0);var draft=BookEditor.load(lib,b);var other=lib.books().get(0);other.notes="Outra janela";lib.save(other);draft.title="Não gravar";draft.notes="Minha edição";draft.category="livros_fisicos/Não persistir";draft.cover=CoverQueueTest.image();assertThrows(java.sql.SQLException.class,()->BookEditor.save(lib,b,draft));var actual=lib.books().get(0);assertEquals("Original",actual.title);assertEquals("Outra janela",actual.notes);assertEquals(b.section,actual.section);assertNull(BookPreview.stored(lib,b.id).image());assertFalse(lib.categories().contains(draft.category));assertEquals("Minha edição",draft.notes);
 }
 @Test void editingOneMarkPreservesOtherConcurrentFieldsAndAllowsIdenticalText()throws Exception{
  var lib=new Biblioteca(root);var first=new PhysicalBooks.Draft();first.title="Livro";PhysicalBooks.save(lib,first);var b=lib.books().get(0);var draft=BookEditor.load(lib,b);var other=lib.books().get(0);lib.saveProgress(other,48);other.notes="Mesma anotação";lib.save(other);draft.notes="Mesma anotação";draft.favorite=true;BookEditor.save(lib,b,draft);var actual=lib.books().get(0);assertEquals(48,actual.page);assertTrue(actual.reading);assertTrue(actual.favorite);assertEquals("Mesma anotação",actual.notes);
 }
 @Test void duplicateIsbnEditRollsBack()throws Exception{
  var lib=new Biblioteca(root);var d=new PhysicalBooks.Draft();d.title="Primeiro";d.isbn="9780132350884";PhysicalBooks.save(lib,d);d=new PhysicalBooks.Draft();d.title="Segundo";String id=PhysicalBooks.save(lib,d);var b=lib.books().stream().filter(x->x.id.equals(id)).findFirst().orElseThrow();var edit=BookEditor.load(lib,b);edit.title="Não salvar";edit.isbn="9780132350884";assertThrows(Exception.class,()->BookEditor.save(lib,b,edit));assertEquals("Segundo",lib.books().stream().filter(x->x.id.equals(id)).findFirst().orElseThrow().title);
 }
}

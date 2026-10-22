package br.com.catalogo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
class BibliotecaTest {
 @TempDir Path root;
 Biblioteca library;
 @BeforeEach void setup()throws Exception {
  try(var c=DriverManager.getConnection("jdbc:sqlite:"+root.resolve("catalogo.sqlite"));var s=c.createStatement()){
   s.execute("CREATE TABLE books(id TEXT PRIMARY KEY,path TEXT,title TEXT,filename TEXT,section TEXT,format TEXT,metadata_json TEXT,active INTEGER)");
   s.execute("CREATE TABLE marks(book_id TEXT PRIMARY KEY REFERENCES books(id),favorite INTEGER,reading INTEGER,page INTEGER,location TEXT,notes TEXT,updated INTEGER)");
   s.execute("CREATE TABLE categories(path TEXT)");s.execute("INSERT INTO categories VALUES('programacao/java')");
   s.execute("INSERT INTO books VALUES('id1','programacao/java/livro.pdf','Programação Java','livro.pdf','programacao/java','PDF','{\"author\":\"Ana\",\"publisher\":\"Editora Exemplo\",\"isbn\":\"9781234567890\"}',1)");
  }library=new Biblioteca(root);
 }
 @Test void preservesAllReadingDataAcrossRestartAndRelocation()throws Exception {
  var b=library.books().get(0);b.favorite=true;b.reading=true;b.page=47;b.notes="Anotação com acentuação\nSegunda linha";b.location="Capítulo 3";library.save(b);
  try(var c=library.connect();var s=c.createStatement()){s.execute("UPDATE books SET path='programacao/java/novo_nome.pdf',filename='novo_nome.pdf' WHERE id='id1'");}
  var again=new Biblioteca(root).books().get(0);assertEquals("id1",again.id);assertEquals(47,again.page);assertTrue(again.favorite);assertTrue(again.reading);assertEquals(b.notes,again.notes);assertEquals(b.location,again.location);assertEquals("novo_nome.pdf",again.filename);
 }
 @Test void searchesPublisherIsbnAccentsFilenameAndNotes()throws Exception {var b=library.books().get(0);b.notes="Rever capítulo de concorrência";assertTrue(Biblioteca.matches(b,"programacao editora exemplo"));assertTrue(Biblioteca.matches(b,"9781234567890"));assertTrue(Biblioteca.matches(b,"livro.pdf concorrencia"));assertFalse(Biblioteca.matches(b,"Editora inexistente"));assertEquals("Programação Java (livro.pdf)",b.display());}
 @Test void preventsEscapingRootAndInvalidPage()throws Exception {var b=library.books().get(0);b.path="../secret.pdf";assertThrows(IllegalArgumentException.class,()->library.file(b));b.page=0;assertThrows(IllegalArgumentException.class,()->library.save(b));}
 @Test void databaseFailureDoesNotDiscardExistingMarks()throws Exception {var b=library.books().get(0);b.notes="Guardar";library.save(b);b.id="unknown";b.notes="Falha simulada";assertThrows(SQLException.class,()->library.save(b));assertEquals("Guardar",library.books().get(0).notes);}

 @Test void twoWindowsUpdateOnlyChangedFields()throws Exception {
  var a=library.books().get(0);var b=new Biblioteca(root).books().get(0);a.notes="Notas novas";a.favorite=true;library.save(a);library.saveProgress(b,12);
  var stored=library.books().get(0);assertEquals("Notas novas",stored.notes);assertTrue(stored.favorite);assertEquals(12,stored.page);
  b.location="Capítulo novo";library.save(b);assertEquals("Notas novas",library.books().get(0).notes);
 }
 @Test void concurrentTextEditsAreNotSilentlyOverwritten()throws Exception {
  var a=library.books().get(0);var b=library.books().get(0);a.notes="Primeira janela";library.save(a);b.notes="Segunda janela";
  assertThrows(SQLException.class,()->library.save(b));assertEquals("Segunda janela",b.notes);assertEquals("Primeira janela",library.books().get(0).notes);
 }
}

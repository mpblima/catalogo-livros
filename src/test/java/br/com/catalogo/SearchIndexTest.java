package br.com.catalogo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
class SearchIndexTest {
 @TempDir Path root;
 Biblioteca library;
 @BeforeEach void setup()throws Exception{var f=new BibliotecaTest();f.root=root;f.setup();library=f.library;}
 @Test void findsWordPartsAcrossTitleCategoriesAndMetadata()throws Exception {
  try(var c=library.connect();var s=c.createStatement()){s.execute("UPDATE books SET title='Apache Camel — Integração Empresarial',section='arquitetura_e_integracao/apache_camel',filename='camel_segunda_edicao.pdf' WHERE id='id1'");}
  for(String query:new String[]{"cam","CAMEL","integra","INTEGRAÇÃO","arquitetura camel","camel arquitetura","tura inte","AP","editora exemplo","978123","segunda edicao","  cam   integra  "})assertEquals(java.util.Set.of("id1"),library.searchIds(query),query);
  assertTrue(library.searchIds("camel inexistente").isEmpty());assertTrue(library.searchIds("camel OR inexistenxyz").isEmpty());
 }
 @Test void persistsIndexAndUpdatesAfterExternalSqlChanges()throws Exception {
  assertEquals(java.util.Set.of("id1"),library.searchIds("java"));
  try(var c=library.connect();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM search_fts")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
  try(var c=library.connect();var s=c.createStatement()){s.execute("UPDATE books SET title='Violão clássico',filename='metodo.pdf',path='musica/violao/metodo.pdf',section='musica/violao',metadata_json='{}' WHERE id='id1'");}
  var reopened=new Biblioteca(root);assertEquals(java.util.Set.of("id1"),reopened.searchIds("viol class"));assertEquals(java.util.Set.of("id1"),reopened.searchIds("musica"));assertTrue(reopened.searchIds("java").isEmpty());
  var b=reopened.books().get(0);b.notes="Rever exercícios de arpejos";reopened.save(b);b.page=3;reopened.save(b);assertTrue(reopened.searchIds("exerc arpej").isEmpty());
  try(var c=library.connect();var s=c.createStatement()){s.execute("UPDATE books SET active=0 WHERE id='id1'");}
  assertTrue(reopened.searchIds("violao").isEmpty());try(var c=library.connect();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM search_documents")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
 }
 @Test void excludesCustomCategoryLabelsAndUsesFtsIndex()throws Exception {
  try(var c=library.connect();var s=c.createStatement()){s.execute("UPDATE categories SET name='Linguagens e integração'");}
  assertTrue(library.searchIds("lingu integra").isEmpty());
  try(var c=library.connect();var s=c.createStatement();var r=s.executeQuery("EXPLAIN QUERY PLAN SELECT rowid FROM search_fts WHERE search_fts MATCH '\"integra\"'")){boolean fts=false;while(r.next())fts|=r.getString("detail").contains("VIRTUAL TABLE INDEX");assertTrue(fts);}
 }
}

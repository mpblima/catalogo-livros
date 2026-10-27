package br.com.catalogo;

import java.sql.*;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;

/** Índice persistente FTS5/trigramas; instr confirma correspondências literais parciais. */
final class SearchIndex {
 static String normalize(String text){
  var out=new StringBuilder();String source="áàâãäåéèêëíìîïóòôõöúùûüçñ",target="aaaaaaeeeeiiiiooooouuuucn";String value=Objects.toString(text,"").toLowerCase(Locale.ROOT);
  value.codePoints().forEach(cp->{int index=source.indexOf(cp);out.appendCodePoint(index<0?cp:target.charAt(index));});
  return out.toString();
 }
 static List<String> terms(String text){var out=new ArrayList<String>();var term=new StringBuilder();normalize(text).codePoints().forEach(cp->{if(Character.isWhitespace(cp)||Character.isSpaceChar(cp)){if(!term.isEmpty()){out.add(term.toString());term.setLength(0);}}else term.appendCodePoint(cp);});if(!term.isEmpty())out.add(term.toString());return out.stream().distinct().toList();}
 static void initialize(Biblioteca library)throws SQLException {
  try(var c=library.connect();var s=c.createStatement()){
   c.setAutoCommit(false);
   try {
    s.execute("CREATE TABLE IF NOT EXISTS search_documents(rowid INTEGER PRIMARY KEY,book_id TEXT UNIQUE NOT NULL,content TEXT NOT NULL)");
    s.execute("CREATE TABLE IF NOT EXISTS search_dirty(book_id TEXT PRIMARY KEY)");
    s.execute("CREATE TABLE IF NOT EXISTS search_index_state(singleton INTEGER PRIMARY KEY CHECK(singleton=1),version INTEGER NOT NULL)");
    s.execute("CREATE VIRTUAL TABLE IF NOT EXISTS search_fts USING fts5(content,content='search_documents',content_rowid='rowid',tokenize='trigram')");
    s.execute("CREATE TRIGGER IF NOT EXISTS search_documents_ai AFTER INSERT ON search_documents BEGIN INSERT INTO search_fts(rowid,content) VALUES(new.rowid,new.content); END");
    s.execute("CREATE TRIGGER IF NOT EXISTS search_documents_ad AFTER DELETE ON search_documents BEGIN INSERT INTO search_fts(search_fts,rowid,content) VALUES('delete',old.rowid,old.content); END");
    s.execute("CREATE TRIGGER IF NOT EXISTS search_documents_au AFTER UPDATE ON search_documents BEGIN INSERT INTO search_fts(search_fts,rowid,content) VALUES('delete',old.rowid,old.content); INSERT INTO search_fts(rowid,content) VALUES(new.rowid,new.content); END");
    for(String event:List.of("INSERT","UPDATE","DELETE")){
     String row=event.equals("DELETE")?"old":"new";
     s.execute("DROP TRIGGER IF EXISTS search_books_"+event);
     s.execute("CREATE TRIGGER search_books_"+event+" AFTER "+event+" ON books BEGIN INSERT INTO search_dirty SELECT "+row+".id WHERE NOT EXISTS(SELECT 1 FROM search_dirty WHERE book_id="+row+".id); END");
     s.execute("DROP TRIGGER IF EXISTS search_marks_"+event);
     s.execute("CREATE TRIGGER search_marks_"+event+" AFTER "+event+" ON marks BEGIN INSERT INTO search_dirty SELECT "+row+".book_id WHERE NOT EXISTS(SELECT 1 FROM search_dirty WHERE book_id="+row+".book_id); END");
     s.execute("DROP TRIGGER IF EXISTS search_categories_"+event);
     s.execute("CREATE TRIGGER search_categories_"+event+" AFTER "+event+" ON categories BEGIN INSERT INTO search_dirty SELECT id FROM books WHERE NOT EXISTS(SELECT 1 FROM search_dirty WHERE book_id=books.id); END");
    }
    s.execute("DROP TRIGGER IF EXISTS search_previews_insert");s.execute("CREATE TRIGGER search_previews_insert AFTER INSERT ON book_previews BEGIN INSERT INTO search_dirty SELECT new.book_id WHERE NOT EXISTS(SELECT 1 FROM search_dirty WHERE book_id=new.book_id); END");
    s.execute("DROP TRIGGER IF EXISTS search_previews_update");s.execute("CREATE TRIGGER search_previews_update AFTER UPDATE ON book_previews BEGIN INSERT INTO search_dirty SELECT new.book_id WHERE NOT EXISTS(SELECT 1 FROM search_dirty WHERE book_id=new.book_id); END");
    s.execute("INSERT OR IGNORE INTO search_dirty SELECT id FROM books WHERE id NOT IN (SELECT book_id FROM search_documents)");
    s.execute("INSERT OR IGNORE INTO search_dirty SELECT book_id FROM search_documents WHERE book_id NOT IN (SELECT id FROM books WHERE active=1)");
    int searchVersion=0;try(var r=s.executeQuery("SELECT version FROM search_index_state WHERE singleton=1")){if(r.next())searchVersion=r.getInt(1);}if(searchVersion!=2){s.execute("INSERT OR IGNORE INTO search_dirty SELECT id FROM books WHERE active=1");s.execute("INSERT INTO search_index_state(singleton,version)VALUES(1,2)ON CONFLICT(singleton)DO UPDATE SET version=excluded.version");}
    c.commit();
   }catch(SQLException e){c.rollback();throw e;}
  }
  refresh(library);
 }
 static void appendValues(JsonNode node,StringBuilder text){if(node.isValueNode())text.append(' ').append(node.asText());else node.elements().forEachRemaining(n->appendValues(n,text));}
 static void refresh(Biblioteca library)throws SQLException {
  try(var c=library.connect()){
   try(var s=c.createStatement()){
    s.execute("BEGIN IMMEDIATE");
    try{
    // Reservar a escrita antes de ler evita SQLITE_BUSY_SNAPSHOT com a atualização em paralelo.
    var ids=new ArrayList<String>();try(var r=s.executeQuery("SELECT book_id FROM search_dirty")){while(r.next())ids.add(r.getString(1));}
    if(ids.isEmpty()){s.execute("COMMIT");return;}
    try(var get=c.prepareStatement("SELECT b.*,p.summary FROM books b LEFT JOIN book_previews p ON b.id=p.book_id WHERE b.id=? AND b.active=1");var put=c.prepareStatement("INSERT INTO search_documents(book_id,content) VALUES(?,?) ON CONFLICT(book_id) DO UPDATE SET content=excluded.content");var del=c.prepareStatement("DELETE FROM search_documents WHERE book_id=?");var done=c.prepareStatement("DELETE FROM search_dirty WHERE book_id=?")){
     for(String id:ids){
      get.setString(1,id);try(var r=get.executeQuery()){
       if(r.next()){
        var text=new StringBuilder();for(String key:List.of("title","filename","path","section","summary"))text.append(' ').append(Objects.toString(r.getString(key),""));
        try{appendValues(Biblioteca.JSON.readTree(r.getString("metadata_json")),text);}catch(Exception ignored){}
        put.setString(1,id);put.setString(2,normalize(text.toString()));put.executeUpdate();
       }else{del.setString(1,id);del.executeUpdate();}
      }
      done.setString(1,id);done.executeUpdate();
     }
    }
    s.execute("COMMIT");
   }catch(SQLException e){s.execute("ROLLBACK");throw e;}
   }
  }
 }
 static Set<String> search(Biblioteca library,String query)throws SQLException {
  refresh(library);var terms=terms(query);var longTerms=terms.stream().filter(t->t.codePointCount(0,t.length())>=3).toList();
  var sql=new StringBuilder("SELECT d.book_id FROM search_documents d JOIN books b ON b.id=d.book_id WHERE b.active=1");
  if(!longTerms.isEmpty())sql.append(" AND d.rowid IN (SELECT rowid FROM search_fts WHERE search_fts MATCH ?)");
  for(String term:terms)sql.append(" AND instr(d.content,?)>0");
  try(var c=library.connect();var s=c.prepareStatement(sql.toString())){
   int i=1;if(!longTerms.isEmpty())s.setString(i++,String.join(" AND ",longTerms.stream().map(t->"\""+t+"\"").toList()));for(String term:terms)s.setString(i++,term);
   var ids=new HashSet<String>();try(var r=s.executeQuery()){while(r.next())ids.add(r.getString(1));}return ids;
  }
 }
}

package br.com.catalogo;

import java.nio.file.*;
import java.sql.*;
import java.text.Normalizer;
import java.util.*;
import com.fasterxml.jackson.databind.*;

/** Persistência nativa JDBC. Mantém IDs e o esquema da biblioteca anterior. */
public final class Biblioteca {
 public final Path root;
 public static final ObjectMapper JSON = new ObjectMapper();
 public Biblioteca(Path root) throws Exception {
  this.root=root.toAbsolutePath().normalize();
  Files.createDirectories(this.root);
  initializeSchema();
  try(var c=connect();var s=c.createStatement()){s.execute("PRAGMA journal_mode=WAL");}
  SearchIndex.initialize(this);
 }
 private void initializeSchema()throws SQLException {
  try(var c=connect();var s=c.createStatement()){
   s.execute("CREATE TABLE IF NOT EXISTS books(id TEXT PRIMARY KEY,path TEXT UNIQUE NOT NULL,title TEXT,filename TEXT NOT NULL,topic TEXT NOT NULL,section TEXT NOT NULL,format TEXT NOT NULL,size INTEGER NOT NULL,mtime_ns INTEGER NOT NULL,sha256 TEXT,metadata_json TEXT NOT NULL DEFAULT '{}',title_source TEXT,active INTEGER NOT NULL DEFAULT 1,updated INTEGER NOT NULL)");
   s.execute("CREATE TABLE IF NOT EXISTS marks(book_id TEXT PRIMARY KEY REFERENCES books(id),favorite INTEGER NOT NULL DEFAULT 0,reading INTEGER NOT NULL DEFAULT 0,page INTEGER,location TEXT NOT NULL DEFAULT '',notes TEXT NOT NULL DEFAULT '',updated INTEGER NOT NULL)");
   s.execute("CREATE TABLE IF NOT EXISTS categories(path TEXT PRIMARY KEY,name TEXT NOT NULL,parent TEXT NOT NULL DEFAULT '')");
   s.execute("CREATE TABLE IF NOT EXISTS path_aliases(path TEXT PRIMARY KEY,book_id TEXT NOT NULL REFERENCES books(id))");
   s.execute("CREATE TABLE IF NOT EXISTS operations(id INTEGER PRIMARY KEY,operation TEXT,source TEXT,destination TEXT,sha256 TEXT,created INTEGER,detail TEXT)");
  }
 }
 Connection connect() throws SQLException {var c=DriverManager.getConnection("jdbc:sqlite:"+root.resolve("catalogo.sqlite"));try(var s=c.createStatement()){s.execute("PRAGMA busy_timeout=30000");s.execute("PRAGMA foreign_keys=ON");}return c;}
 public static String normalize(String s){return SearchIndex.normalize(s);}
 public Set<String> searchIds(String query)throws SQLException{return SearchIndex.search(this,query);}
 public void refreshSearchIndex()throws SQLException{SearchIndex.refresh(this);}
 public static final class Book {
  public String id,path,title,filename,section,format,metadata,notes,location;public boolean favorite,reading;public int page;public long updated;
  final Map<String,Object> original=new HashMap<>();
  Map<String,Object> fields(){var m=new LinkedHashMap<String,Object>();m.put("favorite",favorite?1:0);m.put("reading",reading?1:0);m.put("page",page);m.put("location",location);m.put("notes",notes);return m;}
  void remember(){original.clear();original.putAll(fields());}
  public String display(){return (title==null||title.isBlank()?"Título não identificado":title)+" ("+filename+")";}
  public String searchable(){return normalize(display()+" "+path+" "+section+" "+metadata+" "+notes);}
  public String meta(String name){try{return JSON.readTree(metadata).path(name).asText("");}catch(Exception e){return "";}}
  public String toString(){return display();}
 }
 public List<Book> books() throws Exception {
  var list=new ArrayList<Book>();
  try(var c=connect();var s=c.createStatement();var r=s.executeQuery("SELECT b.*,m.favorite,m.reading,m.page,m.location,m.notes,m.updated AS mark_updated FROM books b LEFT JOIN marks m ON b.id=m.book_id WHERE b.active=1 ORDER BY COALESCE(b.title,b.filename) COLLATE NOCASE")){
   while(r.next()){var b=new Book();b.id=r.getString("id");b.path=r.getString("path");b.title=r.getString("title");b.filename=r.getString("filename");b.section=r.getString("section");b.format=r.getString("format");b.metadata=r.getString("metadata_json");b.favorite=r.getInt("favorite")==1;b.reading=r.getInt("reading")==1;b.page=Math.max(1,r.getInt("page"));b.location=Objects.toString(r.getString("location"),"");b.notes=Objects.toString(r.getString("notes"),"");b.updated=r.getLong("mark_updated");b.remember();list.add(b);}
  }return list;
 }
 /** Atualiza somente campos editados; conflitos no texto mantêm a edição no formulário. */
 public void save(Book b) throws SQLException {
  if(b.page<1)throw new IllegalArgumentException("Página inválida");
  var changes=new LinkedHashMap<String,Object>();b.fields().forEach((k,v)->{if(!Objects.equals(v,b.original.get(k)))changes.put(k,v);});
  if(changes.isEmpty())return;writeChanges(b,changes);
 }
 public void saveProgress(Book b,int page)throws SQLException {
  if(page<1)throw new IllegalArgumentException("Página inválida");
  writeChanges(b,new LinkedHashMap<>(Map.of("page",page,"reading",1)));b.page=page;b.reading=true;
 }
 private void writeChanges(Book b,LinkedHashMap<String,Object> changes)throws SQLException {
  try(var c=connect()){
   c.setAutoCommit(false);
   try {
    try(var insert=c.prepareStatement("INSERT INTO marks(book_id,favorite,reading,page,location,notes,updated) VALUES(?,0,0,1,'','',0) ON CONFLICT(book_id) DO NOTHING")){insert.setString(1,b.id);insert.executeUpdate();}
    var sql=new StringBuilder("UPDATE marks SET ");sql.append(String.join(",",changes.keySet().stream().map(k->k+"=?").toList()));sql.append(",updated=? WHERE book_id=?");
    for(String k:List.of("notes","location"))if(changes.containsKey(k))sql.append(" AND (COALESCE("+k+",'') IS ? OR COALESCE("+k+",'') IS ?)");
    try(var update=c.prepareStatement(sql.toString())){
     int i=1;for(Object value:changes.values())update.setObject(i++,value);long stamp=System.currentTimeMillis();update.setLong(i++,stamp);update.setString(i++,b.id);
     for(String k:List.of("notes","location"))if(changes.containsKey(k)){update.setObject(i++,b.original.getOrDefault(k,""));update.setObject(i++,changes.get(k));}
     if(update.executeUpdate()!=1)throw new SQLException("Este texto foi alterado em outra janela. Sua edição foi preservada. Copie o texto antes de recarregar o catálogo.");
     c.commit();b.updated=stamp;changes.forEach(b.original::put);
    }
   }catch(SQLException e){c.rollback();throw e;}
  }
 }
 public List<WorkGroups.Relation> relations()throws SQLException {
  var list=new ArrayList<WorkGroups.Relation>();try(var c=connect();var s=c.createStatement()){
   s.execute("CREATE TABLE IF NOT EXISTS book_relations(book_id TEXT NOT NULL REFERENCES books(id) ON DELETE CASCADE,related_book_id TEXT NOT NULL REFERENCES books(id) ON DELETE CASCADE,kind TEXT NOT NULL,source TEXT NOT NULL,PRIMARY KEY(book_id,related_book_id,kind))");
   try(var r=s.executeQuery("SELECT book_id,related_book_id,kind FROM book_relations")){while(r.next())list.add(new WorkGroups.Relation(r.getString(1),r.getString(2),r.getString(3)));}
  }return list;
 }
 public Path file(Book b) {var p=root.resolve(b.path).normalize();if(!p.startsWith(root))throw new IllegalArgumentException("Caminho inválido");return p;}
 public List<String> categories() throws Exception {try(var c=connect();var s=c.createStatement();var r=s.executeQuery("SELECT path FROM categories ORDER BY path")){var a=new ArrayList<String>();while(r.next())a.add(r.getString(1));return a;}}
 public static boolean matches(Book b,String query){String haystack=b.searchable();for(String term:SearchIndex.terms(query))if(!haystack.contains(term))return false;return true;}
}

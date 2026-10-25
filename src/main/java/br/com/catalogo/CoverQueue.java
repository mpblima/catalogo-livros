package br.com.catalogo;
import java.sql.*;import java.util.*;import java.util.concurrent.*;import java.nio.file.*;import java.io.*;import javax.imageio.ImageIO;

/** Fila persistente. A concessão no SQLite permite somente uma busca por biblioteca. */
final class CoverQueue implements AutoCloseable {
 interface Search {byte[] find(String isbn,String title,String author,List<String> urls)throws Exception;}
 interface Details {PhysicalBooks.Draft find(String isbn,String title,String author)throws Exception;}
 private final Biblioteca library;private final Search search;private final Details details;
 private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"capas-fisicas");t.setDaemon(true);return t;});
 private static final Map<Path,CoverQueue> queues=new ConcurrentHashMap<>();
 CoverQueue(Biblioteca library,Search search){this(library,search,null);}
 CoverQueue(Biblioteca library,Search search,Details details){this.library=library;this.search=search;this.details=details;}
 static CoverQueue start(Biblioteca library){return queues.computeIfAbsent(library.root,p->{var lookup=new BookLookup();var q=new CoverQueue(library,lookup::findCover,lookup::findDetails);q.executor.scheduleWithFixedDelay(()->{try{q.runNext();}catch(Exception e){System.err.println("Fila de capas: "+e.getMessage());}},0,5,TimeUnit.SECONDS);return q;});}
 static void enqueue(Connection c,String id)throws SQLException{try(var q=c.prepareStatement("INSERT OR IGNORE INTO cover_jobs(book_id) VALUES(?)")){q.setString(1,id);q.executeUpdate();}}
 private String incompleteSql(){return details==null?"v.cover_png IS NULL":"(v.cover_png IS NULL OR trim(COALESCE(v.summary,''))='' OR "+String.join(" OR ",List.of("author","publisher","year","language","pages").stream().map(k->"trim(COALESCE(json_extract(b.metadata_json,'$."+k+"'),''))=''").toList())+")";}
 void enqueueMissing()throws SQLException{
  try(var c=library.connect();var s=c.createStatement()){
   s.executeUpdate("INSERT OR IGNORE INTO book_previews(book_id,summary) SELECT id,'' FROM books WHERE active=1 AND section LIKE 'livros_fisicos%'");
   s.executeUpdate("INSERT OR IGNORE INTO cover_jobs(book_id) SELECT b.id FROM books b JOIN book_previews v ON v.book_id=b.id WHERE b.active=1 AND b.section LIKE 'livros_fisicos%' AND "+incompleteSql()+"");
  }
 }
 record Job(String id,String title,String isbn,String metadata,int attempt,long lease,boolean coverMissing){}
 private Job claim()throws SQLException{
  long now=System.currentTimeMillis();try(var c=library.connect();var s=c.createStatement()){
   s.execute("BEGIN IMMEDIATE");try{
    Job job=null;boolean working;try(var r=s.executeQuery("SELECT 1 FROM cover_jobs WHERE lease_until>"+now+" LIMIT 1")){working=r.next();}
    if(!working)try(var q=c.prepareStatement("SELECT b.id,b.title,p.isbn,b.metadata_json,j.attempts,v.cover_png IS NULL FROM cover_jobs j JOIN books b ON b.id=j.book_id JOIN physical_books p ON p.book_id=b.id JOIN book_previews v ON v.book_id=b.id WHERE b.active=1 AND "+incompleteSql()+" AND j.next_attempt<=? AND j.lease_until<=? ORDER BY j.next_attempt,b.rowid LIMIT 1")){q.setLong(1,now);q.setLong(2,now);try(var r=q.executeQuery()){if(r.next())job=new Job(r.getString(1),r.getString(2),Objects.toString(r.getString(3),""),r.getString(4),r.getInt(5)+1,now+300000,r.getBoolean(6));}}
    if(job!=null)try(var q=c.prepareStatement("UPDATE cover_jobs SET attempts=?,lease_until=? WHERE book_id=?")){q.setInt(1,job.attempt);q.setLong(2,job.lease);q.setString(3,job.id);q.executeUpdate();}
    s.execute("COMMIT");return job;
   }catch(SQLException e){s.execute("ROLLBACK");throw e;}
  }
 }
 boolean runNext()throws Exception{
  enqueueMissing();Job job=claim();if(job==null)return false;
  String failure="";var metadata=Biblioteca.JSON.readTree(job.metadata);var urls=new ArrayList<String>();for(var url:metadata.path("cover_urls"))urls.add(url.asText());
  if(job.coverMissing)try{byte[] image=search.find(job.isbn,job.title,metadata.path("author").asText(),urls);if(image==null)throw new IOException("Capa ainda não encontrada.");putCover(library,job.id,image,false,true);}catch(Exception e){failure=Objects.toString(e.getMessage(),"Capa pendente.");}
  if(details!=null)try{mergeDetails(library,job.id,details.find(job.isbn,job.title,metadata.path("author").asText()));}catch(Exception e){failure=Objects.toString(e.getMessage(),"Informações e resumo pendentes.");}
  boolean missing;try(var c=library.connect();var q=c.prepareStatement("SELECT 1 FROM books b JOIN book_previews v ON v.book_id=b.id WHERE b.id=? AND "+incompleteSql())){q.setString(1,job.id);try(var r=q.executeQuery()){missing=r.next();}}
  if(!missing){try(var c=library.connect();var q=c.prepareStatement("DELETE FROM cover_jobs WHERE book_id=?")){q.setString(1,job.id);q.executeUpdate();}}
  else{
   long delay=Math.min(1800000L,60000L*(1L<<Math.min(5,job.attempt-1)));String error=failure.isBlank()?"Informações bibliográficas ou resumo ainda pendentes.":failure;if(error.length()>500)error=error.substring(0,500);
   try(var c=library.connect();var q=c.prepareStatement("UPDATE cover_jobs SET lease_until=0,next_attempt=?,last_error=? WHERE book_id=? AND lease_until=?")){q.setLong(1,System.currentTimeMillis()+delay);q.setString(2,error);q.setString(3,job.id);q.setLong(4,job.lease);q.executeUpdate();}
  }
  return true;
 }
 static void saveCover(Biblioteca library,String id,byte[] bytes,boolean replace)throws Exception{
  putCover(library,id,bytes,replace,false);
 }
 private static void putCover(Biblioteca library,String id,byte[] bytes,boolean replace,boolean keepJob)throws Exception{
  var image=ImageIO.read(new ByteArrayInputStream(bytes));if(image==null||image.getWidth()<20||image.getHeight()<20)throw new IOException("A fonte não retornou uma capa válida.");var out=new ByteArrayOutputStream();ImageIO.write(BookPreview.fit(image),"png",out);
  try(var c=library.connect()){
   c.setAutoCommit(false);try{
    try(var q=c.prepareStatement("UPDATE book_previews SET cover_png=? WHERE book_id=?"+(replace?"":" AND cover_png IS NULL"))){q.setBytes(1,out.toByteArray());q.setString(2,id);q.executeUpdate();}
    if(!keepJob)try(var q=c.prepareStatement("DELETE FROM cover_jobs WHERE book_id=?")){q.setString(1,id);q.executeUpdate();}c.commit();
   }catch(Exception e){c.rollback();throw e;}
  }
 }
 static void mergeDetails(Biblioteca library,String id,PhysicalBooks.Draft draft)throws Exception{
  try(var c=library.connect();var s=c.createStatement()){
   s.execute("BEGIN IMMEDIATE");try{
    com.fasterxml.jackson.databind.node.ObjectNode current=null;String summary="";
    try(var q=c.prepareStatement("SELECT b.metadata_json,v.summary FROM books b JOIN book_previews v ON v.book_id=b.id WHERE b.id=? AND b.active=1")){q.setString(1,id);try(var r=q.executeQuery()){if(r.next()){current=(com.fasterxml.jackson.databind.node.ObjectNode)Biblioteca.JSON.readTree(r.getString(1));summary=Objects.toString(r.getString(2),"");}}}
    if(current!=null){
     for(var entry:draft.metadata().entrySet())if(entry.getValue() instanceof String value&&!value.isBlank()&&current.path(entry.getKey()).asText().isBlank())current.put(entry.getKey(),value);
     if(!draft.source.isBlank())current.put("supplement_source",draft.source);
     try(var q=c.prepareStatement("UPDATE books SET metadata_json=? WHERE id=?")){q.setString(1,Biblioteca.JSON.writeValueAsString(current));q.setString(2,id);q.executeUpdate();}
     if(summary.isBlank()&&!draft.summary.isBlank())try(var q=c.prepareStatement("UPDATE book_previews SET summary=? WHERE book_id=?")){q.setString(1,draft.summary);q.setString(2,id);q.executeUpdate();}
    }
    s.execute("COMMIT");
   }catch(Exception e){s.execute("ROLLBACK");throw e;}
  }
 }
 @Override public void close(){executor.shutdownNow();queues.remove(library.root,this);}
}

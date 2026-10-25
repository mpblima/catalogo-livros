package br.com.catalogo;
import java.nio.file.*;import java.nio.file.attribute.*;import java.nio.channels.*;import java.util.*;import java.util.concurrent.*;import java.sql.*;import java.io.*;import java.security.*;

/** Organização e indexação locais. Mantém IDs, aliases, marcações e diário SQLite. */
final class LibraryOrganizer {
 interface Progress{void update(int percent,String message);}
 private final Biblioteca lib;private final CategoryClassifier classifier;
 LibraryOrganizer(Biblioteca lib)throws Exception{this.lib=lib;classifier=new CategoryClassifier();}
 static String sha256(Path p)throws Exception{var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(p)){byte[] buffer=new byte[1024*1024];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());}
 static long modified(Path p)throws IOException{return Files.getLastModifiedTime(p).to(TimeUnit.NANOSECONDS);}
 Path safe(String relative)throws IOException{
  if(relative==null||relative.isBlank()||relative.contains("\\"))throw new IOException("Caminho inválido");Path p=lib.root.resolve(relative).normalize();if(!p.startsWith(lib.root)||p.equals(lib.root)||p.startsWith(lib.root.resolve("organizacao")))throw new IOException("Caminho fora da biblioteca");
  for(Path part=p;part!=null&&part.startsWith(lib.root);part=part.getParent())if(Files.isSymbolicLink(part))throw new IOException("Links simbólicos não são destinos de organização");return p;
 }
 static boolean extension(Path p){String n=p.getFileName().toString().toLowerCase();return n.endsWith(".pdf")||n.endsWith(".epub");}
 private String relative(Path p){return lib.root.relativize(p).toString().replace(File.separatorChar,'/');}
 private static String suffix(Path p){String n=p.getFileName().toString();return n.substring(n.lastIndexOf('.')).toLowerCase();}
 private static Path unique(Path desired){Path p=desired;int n=2;String name=desired.getFileName().toString();int dot=name.lastIndexOf('.');while(Files.exists(p,LinkOption.NOFOLLOW_LINKS)){p=desired.resolveSibling(name.substring(0,dot)+"_"+n+++name.substring(dot));}return p;}
 private interface Operation<T>{T run()throws Exception;}
 private <T>T locked(Progress progress,Operation<T> task)throws Exception{
  Path support=lib.root.resolve("organizacao");Files.createDirectories(support);try(var channel=FileChannel.open(support.resolve("catalogo_operacao_java.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
   FileLock lock=null;boolean notified=false;while(lock==null){try{lock=channel.tryLock();}catch(OverlappingFileLockException ignored){}if(lock==null){if(!notified){progress.update(0,"Aguardando a atualização iniciada em outra janela");notified=true;}Thread.sleep(100);}}
   try{return task.run();}finally{lock.release();}
  }
 }
 Map<String,Object> organize(Progress progress)throws Exception{return locked(progress,()->{
  int moved=0,duplicates=0;var skipped=new ArrayList<String>();var changes=new ArrayList<Map<String,Object>>();
  Map<String,Map<String,Object>> cache=cache();
  try(var extractor=new DocumentExtractor()){
   List<Path> loose;try(var stream=Files.list(lib.root)){loose=stream.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&extension(p)).sorted().toList();}
   for(int i=0;i<loose.size();i++){Path file=loose.get(i);if(lib.excluded(relative(file)))continue;progress.update(i*35/Math.max(1,loose.size()),"Lendo documento: "+file.getFileName());if(!DocumentExtractor.recognizable(file)){skipped.add(file.getFileName()+": não é um PDF/EPUB legível");continue;}
    try{
     var extracted=extractor.extract(file,relative(file));String folder=classifier.destination(extracted.title(),file.getFileName().toString(),extracted.metadata());String hash=sha256(file);String duplicate=null;
     try(var c=lib.connect();var q=c.prepareStatement("SELECT path,sha256 FROM books WHERE active=1 AND size=?")){q.setLong(1,Files.size(file));try(var r=q.executeQuery()){while(r.next()){Path other=lib.root.resolve(r.getString(1));if(other.equals(file)||!Files.isRegularFile(other))continue;String otherHash=r.getString(2);if(otherHash==null)otherHash=sha256(other);if(hash.equals(otherHash)&&Files.mismatch(file,other)==-1){duplicate=r.getString(1);break;}}}}
     indexOne(file,extracted,cache,extractor);
     if(duplicate!=null){Path dest=lib.root.resolve("organizacao/duplicados");Files.createDirectories(dest);Path wanted=unique(dest.resolve(CategoryClassifier.snake(file.getFileName().toString().replaceFirst("\\.[^.]+$",""))+suffix(file)));String before=relative(file);relocateFile(file,wanted,"duplicate_preserved",hash,cache,false);duplicates++;changes.add(Map.of("source",before,"destination",relative(wanted),"action","duplicate_preserved"));}
     else{Path dir=safe(folder);Files.createDirectories(dir);String stem=extracted.title()==null?file.getFileName().toString().replaceFirst("\\.[^.]+$",""):extracted.title();Path wanted=unique(dir.resolve(CategoryClassifier.snake(stem)+suffix(file)));String before=relative(file);relocateFile(file,wanted,"move",hash,cache,true);moved++;changes.add(Map.of("source",before,"destination",relative(wanted),"action","move"));}
    }catch(Exception e){skipped.add(file.getFileName()+": "+Objects.toString(e.getMessage(),e.getClass().getSimpleName()));}
   }
   var files=paths();var present=new HashSet<String>();for(int i=0;i<files.size();i++){var p=files.get(i);present.add(relative(p));progress.update(35+i*50/Math.max(1,files.size()),"Atualizando metadados: "+p.getFileName());try{indexOne(p,null,cache,extractor);progress.update(35+i*50/Math.max(1,files.size()),"Criando capa e apresentação: "+p.getFileName());BookPreview.ensureStored(lib,p);}catch(Exception e){skipped.add(relative(p)+": "+e.getMessage());}}
   try(var c=lib.connect();var q=c.prepareStatement("UPDATE books SET active=0 WHERE id=?");var s=c.createStatement();var rows=s.executeQuery("SELECT id,path FROM books WHERE active=1 AND section NOT LIKE 'livros_fisicos%'")){while(rows.next())if(!present.contains(rows.getString(2))){q.setString(1,rows.getString(1));q.addBatch();}q.executeBatch();}
   progress.update(86,"Conferindo nomes e categorias");correctMismatches(cache);saveCache(cache);progress.update(90,"Agrupando formatos e amostras");int relations=BookGrouper.refresh(lib);progress.update(95,"Atualizando índice de pesquisa");lib.refreshSearchIndex();progress.update(98,"Atualizando catálogo HTML");new HtmlCatalog(lib).export();progress.update(100,"Catálogo e índice atualizados");
   var result=new LinkedHashMap<String,Object>();result.put("moved",moved);result.put("duplicates",duplicates);result.put("files",changes);result.put("skipped",skipped);result.put("total",lib.books().size());result.put("relations",relations);result.put("unclassified",lib.books().stream().filter(b->b.section.equals(LibraryCategories.digital("a_classificar"))).count());return result;
  }
 });}
 private List<Path> paths()throws Exception{var result=new ArrayList<Path>();Files.walkFileTree(lib.root,documentVisitor(result));var excluded=new HashSet<String>();try(var c=lib.connect();var s=c.createStatement();var rows=s.executeQuery("SELECT path FROM excluded_documents")){while(rows.next())excluded.add(rows.getString(1));}result.removeIf(p->excluded.contains(relative(p)));result.sort(Comparator.naturalOrder());return result;}
 SimpleFileVisitor<Path> documentVisitor(List<Path> result){return new SimpleFileVisitor<>(){
  public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs){String n=dir.getFileName().toString();return !dir.equals(lib.root)&&(n.equals("organizacao")||n.equals(LibraryCategories.PHYSICAL)||n.startsWith(".")||n.contains("wso2is_analytics"))?FileVisitResult.SKIP_SUBTREE:FileVisitResult.CONTINUE;}
  public FileVisitResult visitFile(Path file,BasicFileAttributes attrs){if(attrs.isRegularFile()&&extension(file)&&DocumentExtractor.recognizable(file))result.add(file);return FileVisitResult.CONTINUE;}
  public FileVisitResult visitFileFailed(Path file,IOException error)throws IOException{
   // SQLite sidecars may disappear between directory listing and attribute lookup.
   if(error instanceof NoSuchFileException)return FileVisitResult.CONTINUE;
   throw error;
  }
 };}
 @SuppressWarnings("unchecked") private Map<String,Map<String,Object>> cache()throws IOException{Path p=lib.root.resolve("organizacao/titulos_documentos.json");return Files.exists(p)?Biblioteca.JSON.readValue(Files.readString(p),LinkedHashMap.class):new LinkedHashMap<>();}
 private void saveCache(Map<String,Map<String,Object>> cache)throws Exception{Path dir=lib.root.resolve("organizacao");Files.createDirectories(dir);Path tmp=Files.createTempFile(dir,"titulos_",".tmp");Files.writeString(tmp,Biblioteca.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(cache));Files.move(tmp,dir.resolve("titulos_documentos.json"),StandardCopyOption.REPLACE_EXISTING);}
 private void indexOne(Path file,DocumentExtractor.Result extracted,Map<String,Map<String,Object>> cache,DocumentExtractor extractor)throws Exception{
  String path=relative(file);long size=Files.size(file),time=modified(file);String id=null;long oldSize=-1,oldTime=-1;int active=0;
  try(var c=lib.connect();var s=c.prepareStatement("SELECT id,size,mtime_ns,active FROM books WHERE path=?")){s.setString(1,path);try(var r=s.executeQuery()){if(r.next()){id=r.getString(1);oldSize=r.getLong(2);oldTime=r.getLong(3);active=r.getInt(4);}}}
  if(id!=null&&size==oldSize&&time==oldTime){if(active==0)try(var c=lib.connect();var s=c.prepareStatement("UPDATE books SET active=1 WHERE id=?")){s.setString(1,id);s.executeUpdate();}return;}
  var previous=cache.get(path);if(extracted==null)extracted=extractor.extract(file,path);
  String title=extracted.title(),source=extracted.source();
  if(previous!=null&&Objects.equals(((Number)previous.getOrDefault("size",-1)).longValue(),size)&&Objects.equals(((Number)previous.getOrDefault("mtime_ns",-1)).longValue(),time)&&previous.get("title")!=null){title=previous.get("title").toString();source=Objects.toString(previous.get("source"),"document_review");}
  if(id==null)id=UUID.randomUUID().toString().replace("-","");String section=LibraryCategories.digital(relative(file.getParent()));
  try(var c=lib.connect()){
   c.setAutoCommit(false);try{category(c,section);try(var s=c.prepareStatement("INSERT INTO books(id,path,title,filename,topic,section,format,size,mtime_ns,sha256,metadata_json,title_source,active,updated) VALUES(?,?,?,?,?,?,?,?,?,NULL,?,?,1,?) ON CONFLICT(path) DO UPDATE SET title=CASE WHEN books.title_source='manual' THEN books.title ELSE excluded.title END,filename=excluded.filename,topic=excluded.topic,section=excluded.section,format=excluded.format,size=excluded.size,mtime_ns=excluded.mtime_ns,sha256=NULL,metadata_json=CASE WHEN books.title_source='manual' THEN books.metadata_json ELSE excluded.metadata_json END,title_source=CASE WHEN books.title_source='manual' THEN books.title_source ELSE excluded.title_source END,active=1,updated=excluded.updated")){int i=1;s.setString(i++,id);s.setString(i++,path);s.setString(i++,title);s.setString(i++,file.getFileName().toString());s.setString(i++,section.split("/")[0]);s.setString(i++,section);s.setString(i++,suffix(file).substring(1).toUpperCase());s.setLong(i++,size);s.setLong(i++,time);s.setString(i++,Biblioteca.JSON.writeValueAsString(extracted.metadata()));s.setString(i++,source);s.setLong(i++,System.currentTimeMillis());s.executeUpdate();}c.commit();}catch(Exception e){c.rollback();throw e;}
  }
  var entry=new LinkedHashMap<String,Object>();entry.put("path",path);entry.put("size",size);entry.put("mtime_ns",time);entry.put("title",title);entry.put("source",source);entry.put("confidence",title==null?"unidentified":"document_extracted");entry.put("evidence",extracted.evidence());entry.put("extraction_version",3);cache.put(path,entry);
 }
 static void category(Connection c,String path)throws SQLException{String[] parts=path.split("/");for(int i=0;i<parts.length;i++){String part=String.join("/",Arrays.copyOfRange(parts,0,i+1));try(var s=c.prepareStatement("INSERT INTO categories(path,name,parent) VALUES(?,?,?) ON CONFLICT(path) DO NOTHING")){s.setString(1,part);s.setString(2,parts[i].replace('_',' '));s.setString(3,String.join("/",Arrays.copyOfRange(parts,0,i)));s.executeUpdate();}}}
 void delete(String id)throws Exception{locked((p,m)->{},()->{lib.deleteBook(id);return null;});}
 String move(String path,String category,Progress progress)throws Exception{return locked(progress,()->{try(var c=lib.connect();var s=c.prepareStatement("SELECT 1 FROM categories WHERE path=?")){s.setString(1,category);try(var r=s.executeQuery()){if(!r.next())throw new IOException("Categoria não encontrada");}}var cache=cache();Path source=safe(path),folder=safe(LibraryCategories.disk(category));Files.createDirectories(folder);Path target=unique(folder.resolve(source.getFileName()));if(source.equals(folder.resolve(source.getFileName())))return path;relocateFile(source,target,"manual_move",sha256(source),cache,true);saveCache(cache);BookGrouper.refresh(lib);lib.refreshSearchIndex();new HtmlCatalog(lib).export();return relative(target);});}
 private void relocateFile(Path from,Path to,String operation,String hash,Map<String,Map<String,Object>> cache,boolean active)throws Exception{
  String old=relative(from),next=relative(to);Files.move(from,to);boolean committed=false;
  try{
   if(!sha256(to).equals(hash))throw new IOException("A integridade do arquivo não foi confirmada");
   try(var c=lib.connect()){
    c.setAutoCommit(false);try{String id;Map<String,Object> metadata;
     try(var get=c.prepareStatement("SELECT id,metadata_json FROM books WHERE path=?")){get.setString(1,old);try(var r=get.executeQuery()){if(!r.next())throw new IOException("Livro não encontrado no banco");id=r.getString(1);metadata=Biblioteca.JSON.readValue(r.getString(2),Map.class);}}
     String section=active?LibraryCategories.digital(relative(to.getParent())):"organizacao/duplicados";if(active)category(c,section);
     try(var s=c.prepareStatement("INSERT INTO path_aliases(path,book_id) VALUES(?,?) ON CONFLICT(path) DO UPDATE SET book_id=excluded.book_id")){s.setString(1,old);s.setString(2,id);s.executeUpdate();}
     metadata.putIfAbsent("original_filename",from.getFileName().toString());metadata.put("filename",to.getFileName().toString());
     try(var s=c.prepareStatement("UPDATE books SET path=?,filename=?,section=?,topic=?,sha256=?,metadata_json=?,active=?,updated=? WHERE id=?")){s.setString(1,next);s.setString(2,to.getFileName().toString());s.setString(3,section);s.setString(4,section.split("/")[0]);s.setString(5,hash);s.setString(6,Biblioteca.JSON.writeValueAsString(metadata));s.setInt(7,active?1:0);s.setLong(8,System.currentTimeMillis());s.setString(9,id);s.executeUpdate();}
     try(var s=c.prepareStatement("INSERT INTO operations(operation,source,destination,sha256,created,detail) VALUES(?,?,?,?,?,?)")){s.setString(1,operation);s.setString(2,old);s.setString(3,next);s.setString(4,hash);s.setLong(5,System.currentTimeMillis());s.setString(6,"");s.executeUpdate();}c.commit();committed=true;
    }catch(Exception e){c.rollback();throw e;}
   }
   if(cache.containsKey(old)){var entry=cache.remove(old);entry.put("path",next);cache.put(next,entry);}
   if(lib.root.equals(Path.of(System.getProperty("user.home"),"Desktop/Livros"))){try(var links=Files.list(lib.root.getParent())){for(Path link:links.filter(Files::isSymbolicLink).toList())if(link.getParent().resolve(Files.readSymbolicLink(link)).normalize().equals(from)){Path temp=link.resolveSibling(".catalogo_link_"+UUID.randomUUID());Files.createSymbolicLink(temp,to);Files.move(temp,link,StandardCopyOption.REPLACE_EXISTING);}}}
  }catch(Exception e){if(!committed&&Files.exists(to))Files.move(to,from);throw e;}
 }
 private void correctMismatches(Map<String,Map<String,Object>> cache)throws Exception{
  Map<String,String> signals=Map.ofEntries(Map.entry("python","\\bpython\\b|wxpython"),Map.entry("rust","\\brust\\b"),Map.entry("java","\\bjava\\b(?![ _-]?script)"),Map.entry("javascript","java[ _-]?script"),Map.entry("cassandra","\\bcassandra\\b"),Map.entry("ruby","\\bruby\\b"),Map.entry("php","\\bphp\\b"),Map.entry("kotlin","\\bkotlin\\b"));
  for(var b:lib.books()){if(LibraryCategories.physical(b.section))continue;var name=new HashSet<String>();var title=new HashSet<String>();for(var e:signals.entrySet()){if(java.util.regex.Pattern.compile(e.getValue()).matcher(CategoryClassifier.fold(b.filename.replace('_',' '))).find())name.add(e.getKey());if(java.util.regex.Pattern.compile(e.getValue()).matcher(CategoryClassifier.fold(b.title)).find())title.add(e.getKey());}if(name.isEmpty()||title.isEmpty()||!Collections.disjoint(name,title))continue;String category=classifier.destination(b.title,"",Biblioteca.JSON.readValue(b.metadata,Map.class));if(category.equals("a_classificar"))continue;Path from=lib.file(b),dir=safe(category);Files.createDirectories(dir);Path target=unique(dir.resolve(CategoryClassifier.snake(b.title)+suffix(from)));relocateFile(from,target,"content_name_correction",sha256(from),cache,true);}
 }
}

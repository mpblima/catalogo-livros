package br.com.catalogo;
import java.nio.file.*;import java.nio.file.attribute.*;import java.net.*;import java.util.*;import java.sql.*;import java.security.*;

/** Compatibilidade com o catálogo HTML existente, servida e atualizada pelo Java. */
final class HtmlCatalog {
 final Biblioteca lib;
 record Config(int port,String token){}
 HtmlCatalog(Biblioteca lib){this.lib=lib;}
 static Config configuration()throws Exception{
  Path file=Path.of(System.getProperty("catalogo.config",Path.of(System.getProperty("user.home"),".local/share/catalogo_livros/config.json").toString()));Files.createDirectories(file.getParent());
  if(Files.exists(file)){var data=Biblioteca.JSON.readTree(Files.readString(file));return new Config(data.path("port").asInt(8774),data.path("token").asText());}
  byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);var config=new Config(8774,Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));try{Files.writeString(file,Biblioteca.JSON.writeValueAsString(Map.of("port",config.port,"token",config.token)),StandardOpenOption.CREATE_NEW);Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));}catch(FileAlreadyExistsException e){return configuration();}return config;
 }
 static String encode(String value){return URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20").replace("%2F","/");}
 Map<String,Object> catalog()throws Exception{
  var books=new ArrayList<Map<String,Object>>();var marks=new LinkedHashMap<String,Object>();for(var b:lib.books()){
   var row=new LinkedHashMap<String,Object>();row.put("id",b.id);row.put("documentTitle",b.title);row.put("filename",b.filename);row.put("name",b.filename.replaceFirst("\\.[^.]+$",""));row.put("path",b.path);row.put("topic",b.section.split("/")[0]);row.put("section",b.section);row.put("format",b.format);row.put("size",Files.exists(lib.file(b))?Files.size(lib.file(b)):0);row.put("url",encode("Livros/"+b.path));row.put("metadata",Biblioteca.JSON.readTree(b.metadata));books.add(row);
   if(b.updated>0){var mark=new LinkedHashMap<String,Object>();mark.put("favorite",b.favorite);mark.put("reading",b.reading);mark.put("page",b.page);mark.put("location",b.location);mark.put("notes",b.notes);mark.put("updated",b.updated);marks.put(b.path,mark);}
  }
  var categories=new ArrayList<Map<String,String>>();for(String path:lib.categories())categories.add(Map.of("path",path,"name",Path.of(path).getFileName().toString().replace('_',' '),"parent",path.contains("/")?path.substring(0,path.lastIndexOf('/')):""));
  return Map.of("books",books,"marks",marks,"categories",categories,"database",lib.root.resolve("catalogo.sqlite").toString());
 }
 String render(Config config)throws Exception{return Resources.text("catalogo_template.html").replace("__CATALOG_DATA__",Biblioteca.JSON.writeValueAsString(catalog().get("books")).replace("<","\\u003c")).replace("__API_CONFIG__",Biblioteca.JSON.writeValueAsString(Map.of("base","http://127.0.0.1:"+config.port,"token",config.token))).replace("__UPDATED__",java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")));}
 void export()throws Exception{Config config=configuration();Path target=lib.root.getParent().resolve("catalogo_livros.html"),tmp=Files.createTempFile(target.getParent(),".catalogo_",".html.tmp");Files.writeString(tmp,render(config));Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
 Biblioteca.Book resolve(String path)throws Exception{for(var b:lib.books())if(b.path.equals(path))return b;try(var c=lib.connect();var s=c.prepareStatement("SELECT b.path FROM path_aliases a JOIN books b ON b.id=a.book_id WHERE a.path=? AND b.active=1")){s.setString(1,path);try(var r=s.executeQuery()){if(r.next()){String actual=r.getString(1);return lib.books().stream().filter(b->b.path.equals(actual)).findFirst().orElseThrow();}}}throw new IllegalArgumentException("Livro não encontrado");}
 void saveMark(String path,com.fasterxml.jackson.databind.JsonNode patch,boolean importing)throws Exception{
  var b=resolve(path);if(importing&&patch.path("updated").asLong()<=b.updated)return;
  for(String key:List.of("favorite","reading"))if(patch.has(key)&&!patch.get(key).isBoolean())throw new IllegalArgumentException("Marcação inválida");
  if(patch.has("favorite"))b.favorite=patch.path("favorite").asBoolean();if(patch.has("reading"))b.reading=patch.path("reading").asBoolean();
  if(patch.hasNonNull("page")){if(!patch.get("page").isIntegralNumber()||patch.path("page").asInt()<1||patch.path("page").asLong()>1000000)throw new IllegalArgumentException("Página inválida");b.page=patch.path("page").asInt();}
  if(patch.has("notes")){if(!patch.get("notes").isTextual()||patch.path("notes").asText().length()>200000)throw new IllegalArgumentException("Anotação inválida");b.notes=patch.path("notes").asText();}if(patch.has("location")){if(!patch.get("location").isTextual()||patch.path("location").asText().length()>500)throw new IllegalArgumentException("Local inválido");b.location=patch.path("location").asText();}lib.save(b);
 }
}

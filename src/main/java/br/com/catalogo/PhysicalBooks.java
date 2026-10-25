package br.com.catalogo;
import java.util.*;import java.sql.*;

/** Cadastro físico usa os mesmos livros, marcações, prévias e índice do acervo digital. */
final class PhysicalBooks {
 static final class Draft {
  String category="",barcode="",isbn="",title="",subtitle="",author="",publisher="",year="",edition="",language="",pages="",subject="",keywords="",summary="",notes="",location="",source="";
  Map<String,Object> extraMetadata=new LinkedHashMap<>(),originalMarks=new LinkedHashMap<>();
  boolean favorite,reading;int page=1;byte[] cover;List<String> coverUrls=new ArrayList<>();
  Map<String,Object> metadata(){var m=new LinkedHashMap<String,Object>();m.put("title",title);m.put("subtitle",subtitle);m.put("author",author);m.put("publisher",publisher);m.put("year",year);m.put("edition",edition);m.put("language",language);m.put("pages",pages);m.put("subject",subject);m.put("keywords",keywords);m.put("isbn",isbn);m.put("barcode",barcode);if(!coverUrls.isEmpty())m.put("cover_urls",coverUrls);if(!source.isBlank())m.put("source",source);return m;}
 }
 static String isbn(String value){String n=value.replaceAll("[^0-9Xx]","").toUpperCase(Locale.ROOT);if(n.length()==18)n=n.substring(0,13);if(!BookGrouper.validIsbn(n)||n.length()==13&&!n.startsWith("978")&&!n.startsWith("979"))throw new IllegalArgumentException("Informe um ISBN-10 ou ISBN-13 válido para pesquisar o livro.");if(n.length()==10){n="978"+n.substring(0,9);int sum=0;for(int i=0;i<12;i++)sum+=(n.charAt(i)-'0')*(i%2==0?1:3);n+=(10-sum%10)%10;}return n;}
 static String classify(Draft d)throws Exception{
  var metadata=new LinkedHashMap<String,Object>(d.extraMetadata);metadata.putAll(d.metadata());metadata.put("subject",d.subject+" "+d.keywords+" "+d.summary);
  return LibraryCategories.PHYSICAL+"/"+new CategoryClassifier().destination((d.title+" "+d.subtitle).strip(),d.subject+" "+d.keywords,metadata);
 }
 static String save(Biblioteca lib,Draft d)throws Exception{
  d.title=d.title.strip();if(d.title.isBlank())throw new IllegalArgumentException("Informe o título do livro.");if(d.page<1)throw new IllegalArgumentException("Página inválida.");if(!d.pages.isBlank()&&(!d.pages.matches("[0-9]{1,7}")||Integer.parseInt(d.pages)<1))throw new IllegalArgumentException("Número de páginas inválido.");if(d.isbn.isBlank()&&!d.barcode.isBlank())try{d.isbn=isbn(d.barcode);}catch(IllegalArgumentException ignored){}if(!d.isbn.isBlank())d.isbn=isbn(d.isbn);
  String category=d.category.isBlank()||d.category.equals(LibraryCategories.PHYSICAL)?classify(d):d.category;if(!LibraryCategories.physical(category))throw new IllegalArgumentException("Escolha uma categoria de Livros físicos.");String id=UUID.randomUUID().toString().replace("-","");
  try(var c=lib.connect()){
   c.setAutoCommit(false);try{
    LibraryCategories.add(c,category);
    try(var q=c.prepareStatement("INSERT INTO books(id,path,title,filename,topic,section,format,size,mtime_ns,metadata_json,active,updated) VALUES(?,?,?,?,?,?,'FISICO',0,0,?,1,?)")){q.setString(1,id);q.setString(2,"fisicos/"+id);q.setString(3,d.title);q.setString(4,d.isbn.isBlank()?"Livro físico":"ISBN "+d.isbn);q.setString(5,LibraryCategories.PHYSICAL);q.setString(6,category);q.setString(7,Biblioteca.JSON.writeValueAsString(d.metadata()));q.setLong(8,System.currentTimeMillis());q.executeUpdate();}
    try(var q=c.prepareStatement("INSERT INTO physical_books(book_id,barcode,isbn) VALUES(?,NULLIF(?,''),NULLIF(?,''))")){q.setString(1,id);q.setString(2,d.barcode.strip());q.setString(3,d.isbn);q.executeUpdate();}
    try(var q=c.prepareStatement("INSERT INTO book_previews(book_id,cover_png,summary) VALUES(?,?,?)")){q.setString(1,id);q.setBytes(2,d.cover);q.setString(3,d.summary);q.executeUpdate();}
    try(var q=c.prepareStatement("INSERT INTO marks(book_id,favorite,reading,page,location,notes,updated) VALUES(?,?,?,?,?,?,?)")){q.setString(1,id);q.setBoolean(2,d.favorite);q.setBoolean(3,d.reading);q.setInt(4,d.page);q.setString(5,d.location);q.setString(6,d.notes);q.setLong(7,System.currentTimeMillis());q.executeUpdate();}
    if(d.cover==null)CoverQueue.enqueue(c,id);
    c.commit();return id;
   }catch(SQLException e){c.rollback();if(e.getErrorCode()==19)throw new IllegalArgumentException("Este ISBN ou código de barras já está cadastrado em Livros físicos.",e);throw e;}catch(Exception e){c.rollback();throw e;}
  }
 }
}

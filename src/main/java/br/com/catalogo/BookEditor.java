package br.com.catalogo;
import java.util.*;
final class BookEditor {
 static PhysicalBooks.Draft load(Biblioteca lib,Biblioteca.Book b)throws Exception{
  var d=new PhysicalBooks.Draft();var m=Biblioteca.JSON.readTree(b.metadata);for(var f:PhysicalBooks.Draft.class.getDeclaredFields())if(f.getType()==String.class&&m.has(f.getName()))f.set(d,m.path(f.getName()).asText());d.extraMetadata=Biblioteca.JSON.convertValue(m,new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String,Object>>(){});for(String key:d.metadata().keySet())d.extraMetadata.remove(key);for(var url:m.path("cover_urls"))d.coverUrls.add(url.asText());d.title=b.title;d.category=b.section;d.favorite=b.favorite;d.reading=b.reading;d.page=b.page;d.location=b.location;d.notes=b.notes;d.originalMarks.putAll(b.fields());
  try(var c=lib.connect();var q=c.prepareStatement("SELECT cover_png,summary FROM book_previews WHERE book_id=?")){q.setString(1,b.id);try(var r=q.executeQuery()){if(r.next()){d.cover=r.getBytes(1);d.summary=Objects.toString(r.getString(2),"");}}}return d;
 }
 static void save(Biblioteca lib,Biblioteca.Book b,PhysicalBooks.Draft d)throws Exception{
  if(d.title.isBlank())throw new IllegalArgumentException("Informe o título do livro.");if(!d.pages.isBlank()&&(!d.pages.matches("[0-9]{1,7}")||Integer.parseInt(d.pages)<1))throw new IllegalArgumentException("Número de páginas inválido.");if(!d.isbn.isBlank())d.isbn=PhysicalBooks.isbn(d.isbn);if(b.physical()&&(d.category.isBlank()||d.category.equals(LibraryCategories.PHYSICAL)))d.category=PhysicalBooks.classify(d);if(b.physical()&&!LibraryCategories.physical(d.category))throw new IllegalArgumentException("Categoria física inválida.");
  if(d.page<1)throw new IllegalArgumentException("Página inválida.");
  var marks=new Biblioteca.Book();marks.id=b.id;marks.favorite=d.favorite;marks.reading=d.reading;marks.page=d.page;marks.location=d.location;marks.notes=d.notes;marks.original.putAll(d.originalMarks.isEmpty()?b.original:d.originalMarks);var changes=new LinkedHashMap<String,Object>();marks.fields().forEach((key,value)->{if(!Objects.equals(value,marks.original.get(key)))changes.put(key,value);});
  var metadata=Biblioteca.JSON.createObjectNode();for(var entry:d.extraMetadata.entrySet())metadata.set(entry.getKey(),Biblioteca.JSON.valueToTree(entry.getValue()));for(var entry:d.metadata().entrySet())metadata.set(entry.getKey(),Biblioteca.JSON.valueToTree(entry.getValue()));
  try(var c=lib.connect()){c.setAutoCommit(false);try{
   String category=b.physical()?d.category:b.section;if(b.physical())LibraryCategories.add(c,category);
   try(var q=c.prepareStatement("UPDATE books SET title=?,metadata_json=?,section=?,title_source='manual',updated=? WHERE id=? AND active=1")){q.setString(1,d.title.strip());q.setString(2,metadata.toString());q.setString(3,category);q.setLong(4,System.currentTimeMillis());q.setString(5,b.id);if(q.executeUpdate()!=1)throw new IllegalArgumentException("Livro não encontrado.");}
   if(b.physical())try(var q=c.prepareStatement("UPDATE physical_books SET isbn=NULLIF(?,''),barcode=NULLIF(?,'') WHERE book_id=?")){q.setString(1,d.isbn);q.setString(2,d.barcode.strip());q.setString(3,b.id);q.executeUpdate();}
   try(var q=c.prepareStatement("INSERT INTO book_previews(book_id,cover_png,summary) VALUES(?,?,?) ON CONFLICT(book_id) DO UPDATE SET cover_png=excluded.cover_png,summary=excluded.summary")){q.setString(1,b.id);q.setBytes(2,d.cover);q.setString(3,d.summary);q.executeUpdate();}
   if(!changes.isEmpty())Biblioteca.writeChanges(c,marks,changes);
   c.commit();changes.forEach(d.originalMarks::put);
  }catch(Exception e){c.rollback();throw e;}}
 }
}

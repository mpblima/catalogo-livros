package br.com.catalogo;
import java.util.*;import java.nio.file.*;import java.sql.*;import java.util.regex.*;
final class BookGrouper {
 record Entry(String id,String path,String title,long size,String hash,Map<String,Object> meta){}
 static boolean validIsbn(String n){if(n.matches("[0-9]{13}")){int sum=0;for(int i=0;i<13;i++)sum+=(n.charAt(i)-'0')*(i%2==0?1:3);return sum%10==0;}if(n.matches("[0-9]{9}[0-9X]")){int sum=0;for(int i=0;i<10;i++)sum+=(10-i)*(n.charAt(i)=='X'?10:n.charAt(i)-'0');return sum%11==0;}return false;}
 static String compact(String s){return CategoryClassifier.fold(s).replaceAll("[^a-z0-9]","");}
 @SuppressWarnings("unchecked") static int refresh(Biblioteca lib)throws Exception {
  lib.relations();try(var c=lib.connect()){
   var books=new ArrayList<Entry>();try(var s=c.createStatement();var r=s.executeQuery("SELECT id,path,title,size,sha256,metadata_json FROM books WHERE active=1 AND section NOT LIKE 'livros_fisicos%'")){while(r.next())books.add(new Entry(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),r.getString(5),Biblioteca.JSON.readValue(r.getString(6),Map.class)));}
   var byPath=new HashMap<String,Entry>();var byId=new HashMap<String,Entry>();for(var b:books){byPath.put(b.path,b);byId.put(b.id,b);}try(var s=c.createStatement();var r=s.executeQuery("SELECT path,book_id FROM path_aliases")){while(r.next()){var b=byId.get(r.getString(2));if(b!=null)byPath.putIfAbsent(r.getString(1),b);}}
   c.setAutoCommit(false);try{
    try(var s=c.createStatement()){s.executeUpdate("DELETE FROM book_relations WHERE source IN ('isbn_e_titulo','sha256')");}
    Path audit=lib.root.resolve("organizacao/auditoria_duplicatas/resultado.json");Path snapshot=lib.root.resolve("organizacao/auditoria_duplicatas/candidatos.json");var sizes=new HashMap<String,Long>();
    if(Files.exists(snapshot))for(var n:Biblioteca.JSON.readTree(Files.readString(snapshot)).path("books"))sizes.put(n.path("path").asText(),n.path("size").asLong());
    var verified=new HashMap<>(byPath);for(var e:sizes.entrySet()){var b=verified.get(e.getKey());if(b!=null&&b.size!=e.getValue()){verified.remove(e.getKey());try(var s=c.prepareStatement("DELETE FROM book_relations WHERE source='auditoria_conteudo' AND (book_id=? OR related_book_id=?)")){s.setString(1,b.id);s.setString(2,b.id);s.executeUpdate();}}}
    if(Files.exists(audit)){var data=Biblioteca.JSON.readTree(Files.readString(audit));for(var group:data.path("grupos")){Entry first=null;for(var n:group){Entry b=verified.get(n.path("path").asText());if(b==null)continue;if(first==null)first=b;else relate(c,first,b,"same_work","auditoria_conteudo");}}for(var pair:data.path("trechos_ou_amostras")){Entry a=verified.get(pair.path("a").asText()),b=verified.get(pair.path("b").asText());if(pair.path("chars_a").asLong()>pair.path("chars_b").asLong()){Entry t=a;a=b;b=t;}relate(c,a,b,"sample_of","auditoria_conteudo");}}
    var isbns=new HashMap<String,List<Entry>>();var hashes=new HashMap<String,List<Entry>>();var sizesGroups=new HashMap<Long,List<Entry>>();for(var b:books){var matcher=Pattern.compile("(?<!\\d)(?:97[89][\\d -]{10,20}|\\d[\\d -]{7,15}[\\dXx])(?!\\d)").matcher(Objects.toString(b.meta.get("isbn"),""));while(matcher.find()){String isbn=matcher.group().replaceAll("[^0-9Xx]","").toUpperCase();if(validIsbn(isbn)&&b.title!=null)isbns.computeIfAbsent(isbn+"/"+compact(b.title),k->new ArrayList<>()).add(b);}sizesGroups.computeIfAbsent(b.size,k->new ArrayList<>()).add(b);}
    for(var group:sizesGroups.values())if(group.size()>1)for(var b:group){Path p=lib.root.resolve(b.path);if(!Files.isRegularFile(p))continue;String hash=b.hash;if(hash==null){hash=LibraryOrganizer.sha256(p);try(var update=c.prepareStatement("UPDATE books SET sha256=? WHERE id=?")){update.setString(1,hash);update.setString(2,b.id);update.executeUpdate();}}hashes.computeIfAbsent(hash,k->new ArrayList<>()).add(b);}
    for(var group:isbns.values())for(int i=1;i<group.size();i++)relate(c,group.get(0),group.get(i),"same_work","isbn_e_titulo");for(var group:hashes.values())for(int i=1;i<group.size();i++)relate(c,group.get(0),group.get(i),"same_work","sha256");
    c.commit();try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM book_relations")){return r.next()?r.getInt(1):0;}
   }catch(Exception e){c.rollback();throw e;}
  }
 }
 static void relate(Connection c,Entry a,Entry b,String kind,String source)throws SQLException{if(a==null||b==null||a.id.equals(b.id))return;if(kind.equals("same_work")&&a.id.compareTo(b.id)>0){Entry swap=a;a=b;b=swap;}try(var s=c.prepareStatement("INSERT INTO book_relations VALUES(?,?,?,?) ON CONFLICT(book_id,related_book_id,kind) DO NOTHING")){s.setString(1,a.id);s.setString(2,b.id);s.setString(3,kind);s.setString(4,source);s.executeUpdate();}}
}

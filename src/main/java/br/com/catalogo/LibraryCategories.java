package br.com.catalogo;
import java.sql.*;import java.util.*;

/** Separa o tipo de acervo da localização dos arquivos digitais. */
final class LibraryCategories {
 static final String DIGITAL="livros_digitais",PHYSICAL="livros_fisicos";
 static boolean physical(String category){return category.equals(PHYSICAL)||category.startsWith(PHYSICAL+"/");}
 static String digital(String category){return category.equals(DIGITAL)||category.startsWith(DIGITAL+"/")?category:DIGITAL+"/"+(category.isBlank()?"a_classificar":category);}
 static String disk(String category){if(physical(category))throw new IllegalArgumentException("Escolha uma categoria de Livros digitais para este arquivo.");if(category.equals(DIGITAL))return "a_classificar";return category.startsWith(DIGITAL+"/")?category.substring(DIGITAL.length()+1):category;}
 static String label(String path){return path.equals(DIGITAL)?"Livros digitais":path.equals(PHYSICAL)?"Livros físicos":path.substring(path.lastIndexOf('/')+1).replace('_',' ');}
 static void initialize(Connection c)throws SQLException{
  c.setAutoCommit(false);
  try(var s=c.createStatement()){
   s.execute("CREATE TABLE IF NOT EXISTS catalog_migrations(name TEXT PRIMARY KEY)");
   boolean migrated;try(var r=s.executeQuery("SELECT 1 FROM catalog_migrations WHERE name='collection_roots_v1'")){migrated=r.next();}
   if(!migrated){
    var old=new ArrayList<String[]>();try(var r=s.executeQuery("SELECT path,name FROM categories")){while(r.next())if(!physical(r.getString(1))&&!r.getString(1).equals(DIGITAL)&&!r.getString(1).startsWith(DIGITAL+"/"))old.add(new String[]{r.getString(1),r.getString(2)});}
    for(var row:old){String path=digital(row[0]);add(c,path);try(var q=c.prepareStatement("UPDATE categories SET name=? WHERE path=?")){q.setString(1,row[1]);q.setString(2,path);q.executeUpdate();}try(var q=c.prepareStatement("DELETE FROM categories WHERE path=?")){q.setString(1,row[0]);q.executeUpdate();}}
    var books=new ArrayList<String[]>();try(var r=s.executeQuery("SELECT id,section FROM books WHERE active=1")){while(r.next())if(!physical(r.getString(2)))books.add(new String[]{r.getString(1),digital(r.getString(2))});}
    for(var row:books){add(c,row[1]);try(var q=c.prepareStatement("UPDATE books SET section=? WHERE id=?")){q.setString(1,row[1]);q.setString(2,row[0]);q.executeUpdate();}}
    s.execute("INSERT INTO catalog_migrations VALUES('collection_roots_v1')");
   }
   add(c,DIGITAL);add(c,PHYSICAL);c.commit();
  }catch(SQLException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
 }
 static void add(Connection c,String path)throws SQLException{
  String[] parts=path.split("/");for(int i=0;i<parts.length;i++){String part=String.join("/",Arrays.copyOfRange(parts,0,i+1));try(var q=c.prepareStatement("INSERT INTO categories(path,name,parent) VALUES(?,?,?) ON CONFLICT(path) DO NOTHING")){q.setString(1,part);q.setString(2,label(part));q.setString(3,i==0?"":String.join("/",Arrays.copyOfRange(parts,0,i)));q.executeUpdate();}}
 }
}

package br.com.catalogo;
import java.nio.file.*;import java.util.*;

/** Perfis locais independentes; trocar o perfil não move livros nem bancos. */
final class LibrarySettings {
 record Profile(Path directory,String database){
  Profile{directory=directory.toAbsolutePath().normalize();if(database==null||database.isBlank()||database.contains("/")||database.contains("\\")||database.equals(".")||database.equals("..")||!database.matches("(?i).+\\.(sqlite|sqlite3|db)"))throw new IllegalArgumentException("Informe somente o nome do banco, com extensão .sqlite, .sqlite3 ou .db.");}
  Path file(){return directory.resolve(database);}
  Biblioteca open()throws Exception{return new Biblioteca(directory,database);}
  public String toString(){return directory+" · "+database;}
 }
 record State(Profile active,List<Profile> profiles){}
 static Profile legacy(){return new Profile(Path.of(System.getProperty("user.home"),"Desktop","Livros"),"catalogo.sqlite");}
 static Path file(){return Path.of(System.getProperty("catalogo.libraries",Path.of(System.getProperty("user.home"),".local","share","catalogo_livros","libraries.json").toString()));}
 static State read()throws Exception{
  if(!Files.exists(file())){var old=legacy();return new State(Files.isRegularFile(old.file())?old:null,List.of());}
  var node=Biblioteca.JSON.readTree(Files.readString(file()));var profiles=new ArrayList<Profile>();for(var item:node.path("profiles"))profiles.add(new Profile(Path.of(item.path("directory").asText()),item.path("database").asText()));var active=node.path("active");var selected=new Profile(Path.of(active.path("directory").asText()),active.path("database").asText());if(!profiles.contains(selected))profiles.add(selected);return new State(selected,List.copyOf(profiles));
 }
 static void activate(Profile profile)throws Exception{
  var state=read();var profiles=new ArrayList<>(state.profiles());if(state.active()!=null&&!profiles.contains(state.active()))profiles.add(state.active());if(!profiles.contains(profile))profiles.add(profile);var data=new LinkedHashMap<String,Object>();data.put("active",row(profile));data.put("profiles",profiles.stream().map(LibrarySettings::row).toList());Path config=file().toAbsolutePath();Files.createDirectories(config.getParent());Path temp=Files.createTempFile(config.getParent(),"libraries-",".tmp");try{HtmlCatalog.restrictPermissions(temp);Files.writeString(temp,Biblioteca.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data));try{Files.move(temp,config,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,config,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
 }
 private static Map<String,String> row(Profile p){return Map.of("directory",p.directory().toString(),"database",p.database());}
 static Profile explicit(String[] args){String directory=System.getProperty("catalogo.root"),database=System.getProperty("catalogo.database");for(int i=0;i<args.length;i++){if(args[i].equals("--library")){if(++i>=args.length)throw new IllegalArgumentException("Informe o diretório após --library.");directory=args[i];}else if(args[i].equals("--database")){if(++i>=args.length)throw new IllegalArgumentException("Informe o nome do banco após --database.");database=args[i];}}if(directory==null&&database==null)return null;return new Profile(directory==null?legacy().directory():Path.of(directory),database==null?"catalogo.sqlite":database);}
}

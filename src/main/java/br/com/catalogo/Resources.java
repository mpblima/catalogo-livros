package br.com.catalogo;
import java.nio.file.*;import java.io.*;import java.security.*;import java.util.*;
final class Resources {
 static byte[] bytes(String name)throws IOException{try(var in=Resources.class.getResourceAsStream("/catalogo/"+name)){if(in==null)throw new FileNotFoundException("Recurso da aplicação ausente: "+name);return in.readAllBytes();}}
 static String text(String name)throws IOException{return new String(bytes(name),java.nio.charset.StandardCharsets.UTF_8);}
 static Path materialize(String name)throws Exception{
  byte[] data=bytes(name);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)).substring(0,16);
  Path directory=Path.of(System.getProperty("user.home"),".cache/catalogo_livros/recursos",hash);Files.createDirectories(directory);Path target=directory.resolve(Path.of(name).getFileName());
  if(!Files.exists(target)||Files.size(target)!=data.length){Path tmp=Files.createTempFile(directory,"recurso_",".tmp");Files.write(tmp,data);Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
  return target;
 }
 static Path tessdata()throws Exception{
  Path dir=Path.of(System.getProperty("user.home"),".cache/catalogo_livros/tessdata");Files.createDirectories(dir);
  for(String name:List.of("eng","por")){Path model=materialize("ocr/tessdata/"+name+".traineddata");Path target=dir.resolve(model.getFileName());if(!Files.exists(target)||Files.size(target)!=Files.size(model))Files.copy(model,target,StandardCopyOption.REPLACE_EXISTING);}
  return dir;
 }
}

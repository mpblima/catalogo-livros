package br.com.catalogo;
import java.nio.file.*;import java.io.*;import java.security.*;import java.util.*;import com.sun.jna.Library;
final class Resources {
 record OcrNative(Path tesseract,Path leptonica,Path openmp,Map<String,Object> flags){}
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
 static String ocrPlatform(){String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT),arch=System.getProperty("os.arch","").toLowerCase(Locale.ROOT);boolean x64=arch.equals("amd64")||arch.equals("x86_64");if(os.contains("linux")&&x64)return "linux-x86_64";if(os.contains("windows")&&x64)return "windows-x86_64";if(os.contains("mac")&&arch.equals("aarch64"))return "macos-aarch64";throw new UnsupportedOperationException("OCR incluído indisponível para esta plataforma: "+os+" / "+arch);}
 static OcrNative ocrNative()throws Exception{
  String platform=ocrPlatform();if(platform.equals("linux-x86_64")){var flags=Map.<String,Object>of(Library.OPTION_OPEN_FLAGS,0x101);return new OcrNative(materialize("ocr/native/libtesseract.so.5"),materialize("ocr/native/libleptonica.so"),materialize("ocr/native/libgomp.so.1"),flags);}
  String base="ocr/native/"+platform+"/",manifest=base+"manifest.properties";Properties props=new Properties();props.load(new ByteArrayInputStream(bytes(manifest)));String[] files=props.getProperty("files","").split(",");String tess=props.getProperty("tesseract","").strip(),lept=props.getProperty("leptonica","").strip(),openmp=props.getProperty("openmp","").strip();if(tess.isBlank()||lept.isBlank()||files.length==0)throw new IOException("Manifesto OCR inválido para "+platform);
  var contents=new LinkedHashMap<String,byte[]>();var digest=MessageDigest.getInstance("SHA-256");digest.update(bytes(manifest));for(String raw:files){String name=raw.strip();if(!name.matches("[A-Za-z0-9._-]+"))throw new IOException("Nome inválido no manifesto OCR.");byte[] data=bytes(base+name);contents.put(name,data);digest.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update(data);}String hash=HexFormat.of().formatHex(digest.digest()).substring(0,20);Path dir=Path.of(System.getProperty("user.home"),".cache","catalogo_livros","ocr-native",platform,hash);Files.createDirectories(dir);for(var entry:contents.entrySet()){Path file=dir.resolve(entry.getKey());if(!Files.exists(file)||Files.size(file)!=entry.getValue().length){Path tmp=Files.createTempFile(dir,"ocr-",".tmp");try{Files.write(tmp,entry.getValue());try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}}
  int flags=platform.startsWith("windows-")?0x8:0;Map<String,Object> options=flags==0?Map.of():Map.of(Library.OPTION_OPEN_FLAGS,flags);return new OcrNative(dir.resolve(tess),dir.resolve(lept),openmp.isBlank()?null:dir.resolve(openmp),options);
 }
}

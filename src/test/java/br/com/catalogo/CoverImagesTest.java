package br.com.catalogo;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;import java.nio.file.*;import java.net.*;import java.io.*;import javax.imageio.ImageIO;import com.sun.net.httpserver.HttpServer;
class CoverImagesTest {
 @TempDir Path root;
 @Test void imageUrlIsDownloadedAndPersistedAsBlob()throws Exception{
  var out=new ByteArrayOutputStream();ImageIO.write(new java.awt.image.BufferedImage(100,150,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);byte[] png=out.toByteArray();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/cover",e->{e.sendResponseHeaders(200,png.length);try(var body=e.getResponseBody()){body.write(png);}});server.createContext("/invalid",e->{byte[] text="<html>Não é uma imagem</html>".getBytes();e.sendResponseHeaders(200,text.length);try(var body=e.getResponseBody()){body.write(text);}});server.start();try{String base="http://127.0.0.1:"+server.getAddress().getPort();var lib=new Biblioteca(root);var d=new PhysicalBooks.Draft();d.title="Capa por URL";d.cover=CoverImages.download(base+"/cover");String id=PhysicalBooks.save(lib,d);assertNotNull(BookPreview.stored(new Biblioteca(root),id).image());assertThrows(IOException.class,()->CoverImages.download(base+"/invalid"));assertThrows(IOException.class,()->CoverImages.download("file:///tmp/cover.png"));}finally{server.stop(0);}
 }
}

package br.com.catalogo;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;import java.nio.file.*;import java.net.*;import java.net.http.*;
class LibraryServerTest {
 @TempDir Path root;
 @Test void serverFollowsSelectedBankAndWaitsWithoutCreatingDefaultFiles()throws Exception{
  try(var service=new LibraryServer(new HtmlCatalog.Config(0,"test-token"))){service.select(null);assertEquals(-1,service.port());var first=new LibrarySettings.Profile(root.resolve("books"),"first.db");service.select(first);assertEquals(first.file().toString(),catalog(service.port()));var second=new LibrarySettings.Profile(first.directory(),"second.db");service.select(second);assertEquals(second.file().toString(),catalog(service.port()));var stale=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+service.port()+"/api/catalog")).header("X-Catalogo-Token","test-token").header("X-Catalogo-Library",first.file().toUri().toASCIIString()).build(),HttpResponse.BodyHandlers.ofString());assertEquals(409,stale.statusCode());service.select(first);assertEquals(first.file().toString(),catalog(service.port()));assertFalse(Files.exists(first.directory().resolve("catalogo.sqlite")));}
 }
 private String catalog(int port)throws Exception{var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/catalog")).header("X-Catalogo-Token","test-token").build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());return Biblioteca.JSON.readTree(response.body()).path("database").asText();}
}

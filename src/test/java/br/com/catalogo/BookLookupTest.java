package br.com.catalogo;
import com.sun.net.httpserver.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.awt.image.BufferedImage;import javax.imageio.ImageIO;import java.io.*;
class BookLookupTest {
 HttpServer server;String base;
 @BeforeEach void start()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);base="http://127.0.0.1:"+server.getAddress().getPort();server.start();}
 @AfterEach void stop(){server.stop(0);}
 void response(String path,String data){server.createContext(path,x->{byte[] b=data.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(200,b.length);try(var out=x.getResponseBody()){out.write(b);}});}
 @Test void readsOpenLibraryMetadataDescriptionAndCover()throws Exception{
  var png=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(100,150,BufferedImage.TYPE_INT_RGB),"png",png);server.createContext("/cover",x->{byte[] b=png.toByteArray();x.sendResponseHeaders(200,b.length);try(var out=x.getResponseBody()){out.write(b);}});
  response("/api/books","{\"ISBN:9780132350884\":{\"key\":\"/books/OL1M\",\"title\":\"Clean Code\",\"authors\":[{\"name\":\"Robert Martin\"}],\"number_of_pages\":431,\"subjects\":[{\"name\":\"Software engineering\"}],\"cover\":{\"medium\":\""+base+"/cover\"}}}");response("/books/OL1M.json","{\"languages\":[{\"key\":\"/languages/eng\"}],\"works\":[{\"key\":\"/works/OL2W\"}]}");response("/works/OL2W.json","{\"description\":{\"value\":\"Práticas de código limpo.\"}}");
  var d=new BookLookup(base,base).find("9780132350884");assertEquals("Clean Code",d.title);assertEquals("Robert Martin",d.author);assertEquals("431",d.pages);assertEquals("eng",d.language);assertEquals("Práticas de código limpo.",d.summary);assertNotNull(d.cover);assertNotNull(ImageIO.read(new ByteArrayInputStream(d.cover)));
 }
 @Test void googleFallbackRequiresExactIsbn()throws Exception{
  response("/api/books","{}");response("/volumes","{\"items\":[{\"volumeInfo\":{\"title\":\"Clean Code\",\"industryIdentifiers\":[{\"identifier\":\"0132350882\"}],\"authors\":[\"Robert Martin\"],\"description\":\"<p>Boas práticas.</p>\"}}]}");var d=new BookLookup(base,base).find("9780132350884");assertEquals("Google Books",d.source);assertEquals("Boas práticas.",d.summary);
 }
 @Test void unknownBarcodeDoesNotInventBookData()throws Exception{response("/api/books","{}");response("/volumes","{}");assertThrows(IllegalArgumentException.class,()->new BookLookup(base,base).find("9780132350884"));}
 @Test void metadataLookupDoesNotDownloadCoverAndQueueUsesAlternateSource()throws Exception{
  var count=new java.util.concurrent.atomic.AtomicInteger();byte[] png=CoverQueueTest.image();server.createContext("/primary",x->{count.incrementAndGet();x.sendResponseHeaders(503,-1);x.close();});
  response("/api/books","{\"ISBN:9780132350884\":{\"title\":\"Clean Code\",\"cover\":{\"medium\":\""+base+"/primary\"}}}");
  server.createContext("/images/9780132350884/lrg.jpg",x->{x.sendResponseHeaders(200,png.length);try(var out=x.getResponseBody()){out.write(png);}});
  var lookup=new BookLookup(base,base);var d=lookup.findMetadata("9780132350884");assertNull(d.cover);assertEquals(0,count.get());assertEquals(1,d.coverUrls.size());
  assertNotNull(lookup.findCover(d.isbn,d.title,d.author,d.coverUrls));assertEquals(1,count.get());
 }

 @Test void interactiveLookupFetchesAlternativeCoverBeforeReturningMetadata()throws Exception{
  byte[] png=CoverQueueTest.image();response("/api/books","{\"ISBN:9780132350884\":{\"title\":\"Clean Code\",\"cover\":{\"medium\":\""+base+"/missing\"}}}");server.createContext("/images/9780132350884/lrg.jpg",x->{x.sendResponseHeaders(200,png.length);try(var out=x.getResponseBody()){out.write(png);}});var d=new BookLookup(base,base).findWithCover("9780132350884");assertEquals("Clean Code",d.title);assertNotNull(d.cover);assertNotNull(ImageIO.read(new ByteArrayInputStream(d.cover)));
 }
 @Test void interactiveLookupKeepsMetadataWhenAllCoverSourcesFail()throws Exception{
  response("/api/books","{\"ISBN:9780132350884\":{\"title\":\"Clean Code\"}}");response("/volumes","{}");var d=new BookLookup(base,base).findWithCover("9780132350884");assertEquals("Clean Code",d.title);assertNull(d.cover);
 }
 @Test void webProductCoverIsUsedWhenBibliographicImagesFail()throws Exception{
  response("/api/books","{\"ISBN:9788534531238\":{\"title\":\"Um minuto para sempre\"}}");server.createContext("/volumes",x->{x.sendResponseHeaders(503,-1);x.close();});response("/busca","<a href='/produto/um-minuto-para-sempre'>Livro</a>");response("/produto/um-minuto-para-sempre","<meta property='og:image' content='"+base+"/web-cover'><p>ISBN: 9788534531238</p>");byte[] png=CoverQueueTest.image();server.createContext("/web-cover",x->{x.sendResponseHeaders(200,png.length);try(var out=x.getResponseBody()){out.write(png);}});var stages=new java.util.ArrayList<BookLookup.Progress>();var d=new BookLookup(base,base).findWithCover("9788534531238",stages::add);assertNotNull(d.cover);assertEquals("Um minuto para sempre",d.title);assertTrue(stages.stream().anyMatch(p->p.message().contains("livrarias")));assertEquals(100,stages.get(stages.size()-1).value());
 }
 @Test void complementsOpenLibraryMetadataWithMissingGoogleDescription()throws Exception{
  response("/api/books","{\"ISBN:9780132350884\":{\"title\":\"Clean Code\",\"authors\":[{\"name\":\"Robert Martin\"}]}}");
  response("/volumes","{\"items\":[{\"volumeInfo\":{\"title\":\"Clean Code\",\"industryIdentifiers\":[{\"identifier\":\"9780132350884\"}],\"description\":\"Resumo encontrado\",\"pageCount\":431,\"language\":\"en\",\"publisher\":\"Prentice Hall\"}}]}");
  var d=new BookLookup(base,base).findDetails("9780132350884","Clean Code","Robert Martin");assertEquals("Robert Martin",d.author);assertEquals("Resumo encontrado",d.summary);assertEquals("431",d.pages);assertEquals("Prentice Hall",d.publisher);
 }

 @Test void googleDynamicLinksProvideCoverAndMissingBibliographicData()throws Exception{
  byte[] png=CoverQueueTest.image();server.createContext("/google-cover",x->{x.sendResponseHeaders(200,png.length);try(var out=x.getResponseBody()){out.write(png);}});
  response("/books","bookInfo({\"ISBN:9780132350884\":{\"bib_key\":\"ISBN:9780132350884\",\"thumbnail_url\":\""+base+"/google-cover\",\"info_url\":\""+base+"/info?id=1\"}});");
  response("/info","<html><head><meta name='description' content='Resumo &amp; programação.'></head><body><table><tr><td class='metadata_label'>Title</td><td class='metadata_value'>Clean Code</td></tr><tr><td class='metadata_label'>Author</td><td class='metadata_value'>Robert Martin</td></tr><tr><td class='metadata_label'>Publisher</td><td class='metadata_value'>Prentice Hall, 2008</td></tr><tr><td class='metadata_label'>Length</td><td class='metadata_value'>431 pages</td></tr><tr><td class='metadata_label'>Edition</td><td class='metadata_value'>1</td></tr></table></body></html>");
  response("/api/books","{}");response("/volumes","{}");var lookup=new BookLookup(base,base);assertNotNull(lookup.findCover("9780132350884","Clean Code","Robert Martin",java.util.List.of()));var d=lookup.findDetails("9780132350884","Clean Code","Robert Martin");assertEquals("Resumo & programação.",d.summary);assertEquals("431",d.pages);assertEquals("2008",d.year);assertEquals("1",d.edition);
 }

}

package br.com.catalogo;
import java.net.*;import java.net.http.*;import java.time.Duration;import java.io.*;import java.util.*;import javax.imageio.ImageIO;import com.fasterxml.jackson.databind.JsonNode;

/** Consulta pública por ISBN; nenhuma conta ou chave é necessária. */
final class BookLookup {
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();
 record Progress(int value,String message){}
 private java.util.function.Consumer<Progress> progress=p->{};
 private void stage(int value,String message){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();progress.accept(new Progress(value,message));}
 private final String openLibrary,google,covers,publisherCovers,publisherLibrary,googleLinks;
 BookLookup(){this("https://openlibrary.org","https://www.googleapis.com/books/v1","https://covers.openlibrary.org","https://covers.oreillystatic.com","https://learning.oreilly.com","https://books.google.com/books");}
 BookLookup(String openLibrary,String google){this(openLibrary,google,openLibrary,openLibrary);}
 BookLookup(String openLibrary,String google,String covers,String publisherCovers){this(openLibrary,google,covers,publisherCovers,publisherCovers);}
 BookLookup(String openLibrary,String google,String covers,String publisherCovers,String publisherLibrary){this(openLibrary,google,covers,publisherCovers,publisherLibrary,google+"/books");}
 BookLookup(String openLibrary,String google,String covers,String publisherCovers,String publisherLibrary,String googleLinks){this.googleLinks=googleLinks;this.publisherLibrary=publisherLibrary;this.openLibrary=openLibrary;this.google=google;this.covers=covers;this.publisherCovers=publisherCovers;}
 private byte[] get(String url,int max)throws Exception{return get(url,max,null);}
 private byte[] get(String url,int max,String[] charset)throws Exception{var request=HttpRequest.newBuilder(URI.create(url.replace("http://books.google.com","https://books.google.com"))).timeout(Duration.ofSeconds(12)).header("User-Agent","CatalogoLivros/2.0 (personal ISBN lookup)").GET().build();var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());try(var in=response.body()){if(charset!=null){var matcher=java.util.regex.Pattern.compile("(?i)charset=([^; ]+)").matcher(response.headers().firstValue("Content-Type").orElse(""));if(matcher.find())charset[0]=matcher.group(1).replace("\"","");}if(response.statusCode()!=200)throw new IOException("Serviço bibliográfico indisponível (HTTP "+response.statusCode()+").");byte[] data=in.readNBytes(max+1);if(data.length>max)throw new IOException("Resposta bibliográfica muito grande.");return data;}}
 private JsonNode json(String url)throws Exception{return Biblioteca.JSON.readTree(get(url,2_000_000));}
 PhysicalBooks.Draft find(String barcode)throws Exception{return find(barcode,true);}
 PhysicalBooks.Draft findWithCover(String barcode,java.util.function.Consumer<Progress> listener)throws Exception{
  progress=listener;try{return findWithCover(barcode);}finally{progress=p->{};}
 }
 PhysicalBooks.Draft findWithCover(String barcode)throws Exception{
  stage(5,"Consultando dados do livro…");var draft=findMetadata(barcode);
  try{draft.cover=findCover(draft.isbn,draft.title,draft.author,draft.coverUrls);}catch(IOException ignored){/* Dados bibliográficos continuam disponíveis quando nenhuma capa é encontrada. */}
  stage(100,draft.cover==null?"Pesquisa concluída; capa não encontrada.":"Dados e capa encontrados.");return draft;
 }
 PhysicalBooks.Draft findMetadata(String barcode)throws Exception{return find(barcode,false);}
 private PhysicalBooks.Draft find(String barcode,boolean images)throws Exception{
  String isbn,ean="";try{isbn=PhysicalBooks.isbn(barcode);}catch(IllegalArgumentException invalidIsbn){ean=ean(barcode);isbn=ean;}
  String storedIsbn=ean.isBlank()?isbn:"";
  PhysicalBooks.Draft d=null;Exception failure=null;
  stage(10,"Pesquisando dados na Open Library…");try{var item=json(openLibrary+"/api/books?bibkeys=ISBN:"+isbn+"&jscmd=data&format=json").path("ISBN:"+isbn);if(!item.path("title").asText().isBlank())d=open(item,isbn,images);}catch(Exception e){failure=e;}
  if(d!=null&&!ean.isBlank())d.isbn="";
  if(d==null)try{stage(20,"Pesquisando dados no Google Books…");var items=json(google+"/volumes?q=isbn:"+isbn+"&maxResults=5").path("items");for(var item:items){var info=item.path("volumeInfo");boolean exact=false;for(var identifier:info.path("industryIdentifiers")){String candidate=identifier.path("identifier").asText();if(!ean.isBlank()?candidate.replaceAll("\\D","").equals(ean):matchesIsbn(candidate,isbn))exact=true;}if(exact&&!info.path("title").asText().isBlank()){d=google(info,storedIsbn,images);break;}}}catch(Exception e){failure=e;}
  if(d==null)try{stage(25,"Consultando a página do livro…");var g=googlePage(isbn);if(!g.title.isBlank()){d=g;if(!ean.isBlank())d.isbn="";if(images)d.cover=firstCover(d.coverUrls);}}catch(Exception ignored){}
  if(d==null&&!ean.isBlank())try{stage(30,"Consultando catálogo brasileiro…");d=findRetailer(ean,images);}catch(Exception ignored){}
  if(d==null){if(!ean.isBlank())throw new IllegalArgumentException("Livro não encontrado para este código. Você pode preencher o formulário manualmente.");if(failure!=null)throw new IOException("Não foi possível consultar o livro agora. Preencha os dados manualmente ou tente novamente.",failure);throw new IllegalArgumentException("Livro não encontrado para este ISBN. Você pode preencher o formulário manualmente.");}
  d.barcode=barcode.strip();return d;
 }
 private static boolean matchesIsbn(String candidate,String expected){try{return PhysicalBooks.isbn(candidate).equals(expected);}catch(IllegalArgumentException ignored){return false;}}
 private static String ean(String value){String code=value.replaceAll("\\D","");if(!code.matches("789[0-9]{10}"))throw new IllegalArgumentException("Código não reconhecido como ISBN ou código de barras brasileiro.");return code;}
 private PhysicalBooks.Draft findRetailer(String ean,boolean images){
  String search="https://www.livrarialoyola.com.br/busca?busca="+ean;
  try{String results=new String(get(search,2_000_000),java.nio.charset.StandardCharsets.UTF_8);
   for(String page:WebCoverSearch.links(results,search,""))try{
    String html=new String(get(page,2_000_000),java.nio.charset.StandardCharsets.UTF_8);
    if(!java.util.regex.Pattern.compile("(?is)(?:isbn(?:-1[03])?|gtin1[34])\\s*[:=\\\" ]{0,30}[^0-9]{0,30}"+ean+"(?![0-9])|attribute-value=[\\\"']"+ean+"[\\\"']").matcher(html).find())continue;
    var draft=new PhysicalBooks.Draft();draft.barcode=ean;draft.source="Livraria Loyola (edição identificada pelo EAN)";
    var title=java.util.regex.Pattern.compile("(?is)<meta\\s+property=[\\\"']?og:title[\\\"']?\\s+content=[\\\"']([^\\\"']+)").matcher(html);
    if(title.find())draft.title=title.group(1).replaceAll("(?i)\\s*[-|]\\s*Livraria Loyola.*$","").replaceAll("\\s+"," ").strip();
    String image=WebCoverSearch.image(html,page,ean);if(!image.isBlank())draft.coverUrls.add(image);
    if(images)draft.cover=firstCover(draft.coverUrls);return draft;
   }catch(Exception ignored){}
  }catch(Exception ignored){}return null;
 }
 PhysicalBooks.Draft findDetails(String rawIsbn,String title,String author)throws Exception{
  PhysicalBooks.Draft d;
  if(!rawIsbn.isBlank())try{d=findMetadata(rawIsbn);}catch(Exception e){d=new PhysicalBooks.Draft();d.title=title;d.author=author;d.isbn=PhysicalBooks.isbn(rawIsbn);}else{d=new PhysicalBooks.Draft();d.title=title;d.author=author;}
  if(d.summary.isBlank()||d.pages.isBlank()||d.publisher.isBlank()||d.language.isBlank())try{
   String query=rawIsbn.isBlank()?"intitle:"+title+(author.isBlank()?"":" inauthor:"+author):"isbn:"+PhysicalBooks.isbn(rawIsbn);
   for(var item:json(google+"/volumes?q="+URLEncoder.encode(query,java.nio.charset.StandardCharsets.UTF_8)+"&maxResults=5").path("items")){
    var info=item.path("volumeInfo");boolean exact=rawIsbn.isBlank()&&BookGrouper.compact(title).equals(BookGrouper.compact(info.path("title").asText()))&&(author.isBlank()||BookGrouper.compact(names(info.path("authors"))).contains(BookGrouper.compact(author)));
    if(!rawIsbn.isBlank())for(var identifier:info.path("industryIdentifiers"))try{if(PhysicalBooks.isbn(identifier.path("identifier").asText()).equals(PhysicalBooks.isbn(rawIsbn)))exact=true;}catch(IllegalArgumentException ignored){}
    if(!exact)continue;var g=google(info,d.isbn,false);
    if(d.author.isBlank())d.author=g.author;if(d.subtitle.isBlank())d.subtitle=g.subtitle;if(d.publisher.isBlank())d.publisher=g.publisher;if(d.year.isBlank())d.year=g.year;if(d.language.isBlank())d.language=g.language;if(d.pages.isBlank())d.pages=g.pages;if(d.subject.isBlank())d.subject=g.subject;if(d.summary.isBlank())d.summary=g.summary;d.source=d.source.isBlank()?g.source:d.source+"; "+g.source;d.coverUrls.addAll(g.coverUrls);break;
   }
  }catch(Exception ignored){}
  if(!rawIsbn.isBlank()&&(d.summary.isBlank()||d.pages.isBlank()||d.edition.isBlank()))try{
   var supplement=googlePage(PhysicalBooks.isbn(rawIsbn));if(d.summary.isBlank())d.summary=supplement.summary;if(d.pages.isBlank())d.pages=supplement.pages;if(d.edition.isBlank())d.edition=supplement.edition;if(d.publisher.isBlank())d.publisher=supplement.publisher;if(d.year.isBlank())d.year=supplement.year;if(d.author.isBlank())d.author=supplement.author;if(d.language.isBlank())d.language=supplement.language;if(d.subject.isBlank())d.subject=supplement.subject;if(d.keywords.isBlank())d.keywords=supplement.keywords;if(d.subtitle.isBlank()&&supplement.title.startsWith(d.title+":"))d.subtitle=supplement.title.substring(d.title.length()+1).strip();if(!supplement.source.isBlank())d.source=d.source.isBlank()?supplement.source:d.source+"; "+supplement.source;
  }catch(Exception ignored){}
  return d;
 }
 private PhysicalBooks.Draft open(JsonNode n,String isbn,boolean images){var d=new PhysicalBooks.Draft();d.isbn=isbn;d.title=n.path("title").asText();d.subtitle=n.path("subtitle").asText();d.author=names(n.path("authors"));d.publisher=names(n.path("publishers"));d.year=n.path("publish_date").asText();d.pages=positive(n.path("number_of_pages"));d.subject=names(n.path("subjects"));d.keywords=d.subject;d.summary=text(n.path("notes"));enrich(d,n);d.source="Open Library";for(String size:List.of("medium","large","small")){String url=n.path("cover").path(size).asText();if(!url.isBlank())d.coverUrls.add(url);}if(images)d.cover=firstCover(d.coverUrls);return d;}
 private PhysicalBooks.Draft google(JsonNode n,String isbn,boolean images){var d=new PhysicalBooks.Draft();d.isbn=isbn;d.title=n.path("title").asText();d.subtitle=n.path("subtitle").asText();d.author=names(n.path("authors"));d.publisher=n.path("publisher").asText();d.year=n.path("publishedDate").asText();d.pages=positive(n.path("pageCount"));d.language=n.path("language").asText();d.subject=names(n.path("categories"));d.keywords=d.subject;d.summary=clean(n.path("description").asText());d.source="Google Books";for(String size:List.of("thumbnail","smallThumbnail","small","medium")){String url=n.path("imageLinks").path(size).asText();if(!url.isBlank())d.coverUrls.add(url);}if(images)d.cover=firstCover(d.coverUrls);return d;}
 private void enrich(PhysicalBooks.Draft d,JsonNode book){
  String key=book.path("key").asText();if(!key.matches("/books/OL[0-9]+M"))return;
  try{var edition=json(openLibrary+key+".json");d.edition=edition.path("edition_name").asText();var langs=new ArrayList<String>();for(var lang:edition.path("languages")){String code=lang.path("key").asText();langs.add(code.substring(code.lastIndexOf('/')+1));}d.language=String.join("; ",langs);String description=text(edition.path("description"));if(description.isBlank())for(var work:edition.path("works")){String workKey=work.path("key").asText();if(workKey.matches("/works/OL[0-9]+W")){description=text(json(openLibrary+workKey+".json").path("description"));if(!description.isBlank())break;}}if(!description.isBlank())d.summary=description;}catch(Exception ignored){}
 }
 byte[] findCover(String rawIsbn,String title,String author,List<String> known)throws Exception{
  stage(35,"Baixando a capa indicada nos metadados…");byte[] image=firstCover(known);if(image!=null)return image;String isbn="";try{isbn=PhysicalBooks.isbn(rawIsbn);}catch(IllegalArgumentException ignored){}
  if(!isbn.isBlank()){
   stage(45,"Pesquisando capa no Google Books…");try{image=cover(googleLink(isbn).path("thumbnail_url").asText());if(image!=null)return image;}catch(Exception ignored){}
   stage(55,"Pesquisando capa na Open Library…");image=cover(covers+"/b/isbn/"+isbn+"-M.jpg?default=false");if(image!=null)return image;
   stage(65,"Consultando capas de editoras…");image=cover(publisherCovers+"/images/"+isbn+"/lrg.jpg");if(image!=null)return image;
   image=cover(publisherLibrary+"/library/cover/"+isbn+"/250w/");if(image!=null)return image;
   try{var n=json(openLibrary+"/api/books?bibkeys=ISBN:"+isbn+"&jscmd=data&format=json").path("ISBN:"+isbn);var urls=new ArrayList<String>();for(var url:n.path("cover"))urls.add(url.asText());image=firstCover(urls);if(image!=null)return image;}catch(Exception ignored){}
  }
  String query=isbn.isBlank()?"intitle:"+title+(author.isBlank()?"":" inauthor:"+author):"isbn:"+isbn;
  try{for(var item:json(google+"/volumes?q="+URLEncoder.encode(query,java.nio.charset.StandardCharsets.UTF_8)+"&maxResults=5").path("items")){
   var info=item.path("volumeInfo");boolean exact=false;
   if(!isbn.isBlank())for(var identifier:info.path("industryIdentifiers"))try{if(PhysicalBooks.isbn(identifier.path("identifier").asText()).equals(isbn))exact=true;}catch(IllegalArgumentException ignored){}
   else exact=BookGrouper.compact(title).equals(BookGrouper.compact(info.path("title").asText()))&&(author.isBlank()||BookGrouper.compact(names(info.path("authors"))).contains(BookGrouper.compact(author)));
   if(exact){image=firstCover(google(info,isbn,false).coverUrls);if(image!=null)return image;}
  }
  }catch(IOException ignored){}
  if(!isbn.isBlank()){
   var shops=openLibrary.equals("https://openlibrary.org")?List.of("https://www.livrarialoyola.com.br/busca?busca=","https://www.editoraluzdomundo.com.br/buscar?q="):List.of(openLibrary+"/busca?busca=",openLibrary+"/buscar?q=");
   for(int index=0;index<shops.size();index++)try{
    stage(80+index*8,"Pesquisando capa em páginas de livrarias ("+(index+1)+"/"+shops.size()+")…");
    for(var candidate:WebCoverSearch.find(shops.get(index)+isbn,isbn,title,url->new String(get(url,2_000_000),java.nio.charset.StandardCharsets.UTF_8))){stage(95,"Baixando capa da edição confirmada pelo ISBN…");image=cover(candidate.image());if(image!=null)return image;}
   }catch(IOException ignored){}
  }
  throw new IOException("Capa não encontrada nas fontes consultadas; nova tentativa será feita.");
 }
 private JsonNode googleLink(String isbn)throws Exception{
  String data=new String(get(googleLinks+"?jscmd=viewapi&bibkeys=ISBN:"+isbn+"&callback=bookInfo",1_000_000),java.nio.charset.StandardCharsets.UTF_8).strip();
  if(!data.startsWith("bookInfo("))throw new IOException("Resposta de capa do Google inválida.");int end=data.lastIndexOf(')');if(end<9)throw new IOException("Resposta do Google incompleta.");var root=Biblioteca.JSON.readTree(data.substring(9,end));var book=root.path("ISBN:"+isbn);if(book.isMissingNode())book=root.path("books").path("ISBN:"+isbn);if(!book.path("bib_key").asText("ISBN:"+isbn).equals("ISBN:"+isbn))throw new IOException("ISBN diferente na resposta do Google.");return book;
 }
 private PhysicalBooks.Draft googlePage(String isbn)throws Exception{
  var d=new PhysicalBooks.Draft();var link=googleLink(isbn);String url=link.path("info_url").asText();if(url.isBlank())return d;String[] charset={"UTF-8"};byte[] data=get(url+(url.contains("?")?"&hl=en":"?hl=en"),2_000_000,charset);String html=new String(data,java.nio.charset.Charset.forName(charset[0]));var fields=new HashMap<String,String>();
  new javax.swing.text.html.parser.ParserDelegator().parse(new StringReader(html),new javax.swing.text.html.HTMLEditorKit.ParserCallback(){String cell="",key="";StringBuilder value=new StringBuilder();
   public void handleSimpleTag(javax.swing.text.html.HTML.Tag tag,javax.swing.text.MutableAttributeSet a,int p){if(tag==javax.swing.text.html.HTML.Tag.META&&"description".equalsIgnoreCase(Objects.toString(a.getAttribute(javax.swing.text.html.HTML.Attribute.NAME),"")))fields.put("description",Objects.toString(a.getAttribute(javax.swing.text.html.HTML.Attribute.CONTENT),""));}
   public void handleStartTag(javax.swing.text.html.HTML.Tag tag,javax.swing.text.MutableAttributeSet a,int p){if(tag==javax.swing.text.html.HTML.Tag.TD){cell=Objects.toString(a.getAttribute(javax.swing.text.html.HTML.Attribute.CLASS),"");value.setLength(0);}}
   public void handleText(char[] text,int p){if(!cell.isBlank())value.append(text).append(' ');}
   public void handleEndTag(javax.swing.text.html.HTML.Tag tag,int p){if(tag==javax.swing.text.html.HTML.Tag.TD){if(cell.contains("metadata_label"))key=CategoryClassifier.fold(value.toString().strip());else if(cell.contains("metadata_value"))fields.put(key,value.toString().strip());cell="";}}
  },true);
  d.isbn=isbn;d.title=fields.getOrDefault("title",fields.getOrDefault("titulo",""));d.language=fields.getOrDefault("language",fields.getOrDefault("idioma",""));String thumbnail=link.path("thumbnail_url").asText();if(!thumbnail.isBlank())d.coverUrls.add(thumbnail);d.summary=clean(fields.getOrDefault("description",""));if(d.summary.length()>1100){int boundary=d.summary.lastIndexOf(". ",1100);d.summary=boundary>100?d.summary.substring(0,boundary+1):d.summary.substring(0,1100)+"…";}d.subject=fields.getOrDefault("subjects",fields.getOrDefault("assuntos",""));d.keywords=d.subject;d.edition=fields.getOrDefault("edition",fields.getOrDefault("edicao",""));d.author=fields.getOrDefault("author",fields.getOrDefault("authors",""));d.publisher=fields.getOrDefault("publisher",fields.getOrDefault("editora",""));String length=fields.getOrDefault("length",fields.getOrDefault("num. paginas",""));var pagesMatcher=java.util.regex.Pattern.compile("([0-9]+)").matcher(length);if(pagesMatcher.find())d.pages=pagesMatcher.group(1);var yearMatcher=java.util.regex.Pattern.compile("\\b(?:19|20)[0-9]{2}\\b").matcher(d.publisher);if(yearMatcher.find())d.year=yearMatcher.group();d.source=url;return d;
 }
 private byte[] firstCover(List<String> urls){for(String url:urls){byte[] data=cover(url);if(data!=null)return data;}return null;}
 private byte[] cover(String url){if(url.isBlank())return null;try{byte[] bytes=get(url,5_000_000);String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));if(hash.equals("523952c3a7d70adf72c6058bb18e80b89a440e368278a7195af09bd3136de4fe"))return null;var image=ImageIO.read(new ByteArrayInputStream(bytes));if(image==null||image.getWidth()<20||image.getHeight()<20)return null;var scaled=BookPreview.fit(image);var out=new ByteArrayOutputStream();ImageIO.write(scaled,"png",out);return out.toByteArray();}catch(Exception ignored){return null;}}
 private static String positive(JsonNode value){return value.asInt(0)>0?String.valueOf(value.asInt()):"";}
 private static String names(JsonNode nodes){var names=new ArrayList<String>();for(var n:nodes){String name=n.isTextual()?n.asText():n.path("name").asText();if(!name.isBlank())names.add(name);}return String.join("; ",names);}
 private static String text(JsonNode n){return clean(n.isObject()?n.path("value").asText():n.asText());}
 private static String clean(String s){return s.replaceAll("(?s)<[^>]*>"," ").replaceAll("\\s+"," ").strip();}
}

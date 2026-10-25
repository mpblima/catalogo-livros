package br.com.catalogo;
import com.sun.net.httpserver.*;import java.net.*;import java.nio.file.*;import java.nio.charset.*;import java.util.*;import java.util.concurrent.*;import java.io.*;import java.security.*;
final class CatalogHttpServer implements AutoCloseable {
 final HttpServer server;final Biblioteca library;final HtmlCatalog html;final String token;final ConcurrentMap<String,Map<String,Object>> jobs=new ConcurrentHashMap<>();
 private final ExecutorService executor=Executors.newCachedThreadPool(r->{var t=new Thread(r,"catalogo-http");t.setDaemon(true);return t;});
 CatalogHttpServer(Biblioteca lib,int port,String token)throws Exception{library=lib;html=new HtmlCatalog(lib);this.token=token;server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);server.createContext("/",this::handle);server.setExecutor(executor);server.start();}
 int port(){return server.getAddress().getPort();}
 private boolean host(HttpExchange x){String value=x.getRequestHeaders().getFirst("Host");return value!=null&&Set.of("127.0.0.1:"+port(),"localhost:"+port()).contains(value);}
 private boolean authorized(HttpExchange x){return host(x)&&MessageDigest.isEqual(Objects.toString(x.getRequestHeaders().getFirst("X-Catalogo-Token"),"").getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8));}
 private void cors(HttpExchange x){String origin=x.getRequestHeaders().getFirst("Origin");if(origin!=null&&Set.of("null","http://127.0.0.1:"+port(),"http://localhost:"+port()).contains(origin)){x.getResponseHeaders().set("Access-Control-Allow-Origin",origin);x.getResponseHeaders().set("Vary","Origin");}x.getResponseHeaders().set("Access-Control-Allow-Methods","GET, POST, OPTIONS");x.getResponseHeaders().set("Access-Control-Allow-Headers","Content-Type, X-Catalogo-Token");x.getResponseHeaders().set("Access-Control-Allow-Private-Network","true");}
 private void json(HttpExchange x,int code,Object data)throws IOException{byte[] body=Biblioteca.JSON.writeValueAsBytes(data);cors(x);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(code,body.length);try(var out=x.getResponseBody()){out.write(body);}}
 private synchronized String startJob(){for(var e:jobs.entrySet())if(e.getValue().get("state").equals("running"))return e.getKey();String id=UUID.randomUUID().toString();var state=new ConcurrentHashMap<String,Object>();state.put("state","running");state.put("progress",0);state.put("message","Procurando documentos");jobs.put(id,state);executor.submit(()->{try{var result=new LibraryOrganizer(library).organize((p,m)->{state.put("progress",p);state.put("message",m);});state.put("result",result);state.put("progress",100);state.put("state","done");}catch(Exception e){state.put("message",Objects.toString(e.getMessage(),"Falha na atualização"));state.put("state","error");}});return id;}
 private void handle(HttpExchange x)throws IOException{
  try{
   if(!host(x)){json(x,403,Map.of("error","Host não autorizado"));return;}cors(x);
   if(x.getRequestMethod().equals("OPTIONS")){x.sendResponseHeaders(204,-1);x.close();return;}
   String path=x.getRequestURI().getPath();
   if(path.startsWith("/api/")){
    if(!authorized(x)){json(x,403,Map.of("error","Acesso não autorizado"));return;}
    if(x.getRequestMethod().equals("GET")){
     if(path.equals("/api/catalog")){json(x,200,html.catalog());return;}
     if(path.startsWith("/api/jobs/")){var job=jobs.get(path.substring("/api/jobs/".length()));json(x,job==null?404:200,job==null?Map.of("error","Operação não encontrada"):job);return;}
    }else if(x.getRequestMethod().equals("POST")){
     byte[] bytes=x.getRequestBody().readNBytes(2_000_001);if(bytes.length>2_000_000)throw new IllegalArgumentException("Requisição muito grande");var data=Biblioteca.JSON.readTree(bytes.length==0?"{}".getBytes():bytes);if(!data.isObject())throw new IllegalArgumentException("Dados inválidos");
     if(path.equals("/api/marks")){html.saveMark(data.path("path").asText(),data.path("patch"),false);json(x,200,Map.of("ok",true));return;}
     if(path.equals("/api/import-marks")){var fields=data.path("marks").fields();while(fields.hasNext()){var field=fields.next();try{html.saveMark(field.getKey(),field.getValue(),true);}catch(IllegalArgumentException ignored){}}json(x,200,Map.of("ok",true));return;}
     if(path.equals("/api/organize")){json(x,200,Map.of("job",startJob()));return;}
     if(path.equals("/api/move")){String moved=new LibraryOrganizer(library).move(data.path("path").asText(),data.path("category").asText(),(p,m)->{});json(x,200,Map.of("path",moved));return;}
    }
    json(x,404,Map.of("error","Recurso não encontrado"));return;
   }
   if(path.equals("/")||path.equals("/catalogo_livros.html")){byte[] bytes=html.render(new HtmlCatalog.Config(port(),token)).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");x.sendResponseHeaders(200,bytes.length);try(var out=x.getResponseBody()){out.write(bytes);}return;}
   if(path.startsWith("/Livros/")){String relative=path.substring(8);var book=html.resolve(relative);if(book.physical())throw new IllegalArgumentException("Livro físico não possui arquivo digital.");Path file=new LibraryOrganizer(library).safe(book.path).toRealPath();if(!file.startsWith(library.root.toRealPath())||!LibraryOrganizer.extension(file))throw new IllegalArgumentException("Arquivo não encontrado");long size=Files.size(file),start=0,end=size-1;int code=200;String range=x.getRequestHeaders().getFirst("Range");if(range!=null){var match=java.util.regex.Pattern.compile("bytes=(\\d+)-(\\d*)").matcher(range);if(!match.matches()){json(x,416,Map.of("error","Intervalo inválido"));return;}start=Long.parseLong(match.group(1));if(!match.group(2).isBlank())end=Math.min(Long.parseLong(match.group(2)),end);if(start>end||start>=size){json(x,416,Map.of("error","Intervalo inválido"));return;}code=206;x.getResponseHeaders().set("Content-Range","bytes "+start+"-"+end+"/"+size);}
    x.getResponseHeaders().set("Content-Type",book.format.equals("PDF")?"application/pdf":"application/epub+zip");x.getResponseHeaders().set("Accept-Ranges","bytes");x.getResponseHeaders().set("Content-Disposition","inline; filename*=UTF-8''"+HtmlCatalog.encode(book.filename));x.sendResponseHeaders(code,end-start+1);try(var in=Files.newInputStream(file);var out=x.getResponseBody()){in.skipNBytes(start);byte[] buffer=new byte[262144];long remaining=end-start+1;while(remaining>0){int n=in.read(buffer,0,(int)Math.min(remaining,buffer.length));if(n<0)break;out.write(buffer,0,n);remaining-=n;}}return;
   }
   json(x,404,Map.of("error","Recurso não encontrado"));
  }catch(IllegalArgumentException e){json(x,400,Map.of("error",Objects.toString(e.getMessage(),"Dados inválidos")));}catch(Exception e){json(x,500,Map.of("error",Objects.toString(e.getMessage(),"Não foi possível concluir")));}
 }
 public void close(){server.stop(0);executor.shutdownNow();}
}

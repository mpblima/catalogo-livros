package br.com.catalogo;
/** O serviço HTTP acompanha a seleção persistida sem criar bibliotecas na instalação limpa. */
final class LibraryServer implements AutoCloseable {
 private final HtmlCatalog.Config config;private LibrarySettings.Profile current;private CatalogHttpServer server;private CoverQueue queue;
 LibraryServer(HtmlCatalog.Config config){this.config=config;}
 synchronized void select(LibrarySettings.Profile profile)throws Exception{
  if(java.util.Objects.equals(profile,current))return;Biblioteca next=profile==null?null:profile.open();if(server!=null){server.close();server=null;}if(queue!=null){queue.close();queue=null;}current=null;if(next!=null){server=new CatalogHttpServer(next,config.port(),config.token());queue=CoverQueue.start(next);current=profile;}
 }
 synchronized int port(){return server==null?-1:server.port();}
 @Override public synchronized void close(){if(server!=null)server.close();if(queue!=null)queue.close();server=null;queue=null;current=null;}
}

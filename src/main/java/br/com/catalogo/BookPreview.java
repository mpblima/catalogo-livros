package br.com.catalogo;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.w3c.dom.Element;

/** Prévia obtida apenas do próprio documento, sem acesso à rede. */
final class BookPreview {
 record Preview(BufferedImage image,String summary){}
 static Preview stored(Biblioteca library,String id)throws Exception{
  try(var c=library.connect();var q=c.prepareStatement("SELECT cover_png,summary FROM book_previews WHERE book_id=?")){
   q.setString(1,id);try(var r=q.executeQuery()){if(!r.next())return new Preview(null,"Prévia ainda não catalogada. Use Organizar e atualizar.");byte[] bytes=r.getBytes(1);return new Preview(bytes==null?null:ImageIO.read(new ByteArrayInputStream(bytes)),r.getString(2)==null?"Resumo indisponível neste documento.":r.getString(2));}
  }
 }
 static void ensureStored(Biblioteca library,Path file)throws Exception{
  String id;
  try(var c=library.connect();var q=c.prepareStatement("SELECT b.id,p.cover_png IS NOT NULL AND p.summary IS NOT NULL FROM books b LEFT JOIN book_previews p ON p.book_id=b.id WHERE b.path=?")){
   q.setString(1,library.root.relativize(file).toString().replace(File.separatorChar,'/'));try(var r=q.executeQuery()){if(!r.next()||r.getBoolean(2))return;id=r.getString(1);}
  }
  var preview=load(file);byte[] png=null;if(preview.image()!=null){var out=new ByteArrayOutputStream();ImageIO.write(preview.image(),"png",out);png=out.toByteArray();}
  try(var c=library.connect();var q=c.prepareStatement("INSERT INTO book_previews(book_id,cover_png,summary) VALUES(?,?,?) ON CONFLICT(book_id) DO UPDATE SET cover_png=COALESCE(book_previews.cover_png,excluded.cover_png),summary=COALESCE(book_previews.summary,excluded.summary)")){
   q.setString(1,id);q.setBytes(2,png);q.setString(3,preview.summary());q.executeUpdate();
  }
 }
 static Preview load(Path file)throws Exception{
  if(file.toString().toLowerCase().endsWith(".pdf")){
   try(var doc=Loader.loadPDF(file.toFile())){
    BufferedImage image=null;
    if(doc.getNumberOfPages()>0){var box=doc.getPage(0).getCropBox();float scale=Math.min(180f/box.getWidth(),220f/box.getHeight());image=fit(new PDFRenderer(doc).renderImage(0,Math.max(.01f,scale)));}
    String summary=doc.getDocumentInformation().getSubject();
    if(summary==null||summary.isBlank()){
     var text=new PDFTextStripper();text.setStartPage(1);text.setEndPage(Math.min(3,doc.getNumberOfPages()));String excerpt=clean(text.getText(doc));
     summary=excerpt.isBlank()?"Resumo indisponível neste documento.":limit(excerpt);
    }
    return new Preview(image,limit(clean(summary)));
   }
  }
  try(var zip=new ZipFile(file.toFile())){
   var container=DocumentExtractor.xml(read(zip,"META-INF/container.xml"));
   var roots=container.getElementsByTagNameNS("*","rootfile");if(roots.getLength()==0)throw new IOException("EPUB sem metadados");
   String opf=((Element)roots.item(0)).getAttribute("full-path");var doc=DocumentExtractor.xml(read(zip,opf));String coverId="",href="";
   var metas=doc.getElementsByTagNameNS("*","meta");for(int i=0;i<metas.getLength();i++){var e=(Element)metas.item(i);if(e.getAttribute("name").equals("cover"))coverId=e.getAttribute("content");}
   var items=doc.getElementsByTagNameNS("*","item");for(int i=0;i<items.getLength();i++){var e=(Element)items.item(i);if(java.util.Arrays.asList(e.getAttribute("properties").split("\\s+")).contains("cover-image")||!coverId.isBlank()&&coverId.equals(e.getAttribute("id"))){href=e.getAttribute("href");break;}}
   if(href.isBlank()&&!coverId.isBlank()){String candidate=java.net.URI.create(opf).resolve(coverId).getPath();if(zip.getEntry(candidate)!=null)href=coverId;}
   if(href.isBlank())for(int i=0;i<items.getLength();i++){var e=(Element)items.item(i);if(e.getAttribute("media-type").startsWith("image/")&&(e.getAttribute("id").equalsIgnoreCase("cover")||e.getAttribute("href").matches("(?i)(?:.*/)?cover\\.(?:png|jpe?g|gif)"))){href=e.getAttribute("href");break;}}
   BufferedImage image=null;if(!href.isBlank()){String resolved=java.net.URI.create(opf).resolve(href).getPath();try(var in=new ByteArrayInputStream(read(zip,resolved))){image=fit(ImageIO.read(in));}}
   var descriptions=doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/","description");String summary=descriptions.getLength()>0?clean(descriptions.item(0).getTextContent()):"";
   return new Preview(image,summary.isBlank()?"Resumo indisponível neste documento.":limit(summary));
  }
 }
 private static byte[] read(ZipFile zip,String name)throws IOException{var entry=zip.getEntry(name);if(entry==null)throw new IOException("Arquivo ausente no EPUB");try(var in=zip.getInputStream(entry)){byte[] bytes=in.readNBytes(12_000_001);if(bytes.length>12_000_000)throw new IOException("Recurso EPUB muito grande");return bytes;}}
 private static String clean(String text){return text.replaceAll("(?s)<[^>]+>"," ").replaceAll("\\s+"," ").strip();}
 private static String limit(String text){return text.length()>1200?text.substring(0,1200)+"…":text;}
 static BufferedImage fit(BufferedImage source){if(source==null)return null;double scale=Math.min(140d/source.getWidth(),160d/source.getHeight());int w=Math.max(1,(int)(source.getWidth()*scale)),h=Math.max(1,(int)(source.getHeight()*scale));var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,w,h);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(source,0,0,w,h,null);g.dispose();return image;}
}

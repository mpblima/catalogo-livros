package br.com.catalogo;
import org.apache.pdfbox.Loader;import org.apache.pdfbox.pdmodel.*;import org.apache.pdfbox.text.*;import org.apache.pdfbox.rendering.*;
import java.nio.file.*;import java.io.*;import java.util.*;import java.util.regex.*;import java.util.zip.*;import javax.xml.parsers.*;import org.w3c.dom.*;

/** PDFBox, ZIP/XML e OCR nativo: títulos e metadados extraídos do próprio documento. */
final class DocumentExtractor implements AutoCloseable {
 record Line(String text,double size,double y){}
 record Result(String title,String source,Map<String,Object> metadata,List<Map<String,Object>> evidence){}
 private NativeOcr ocr;private final Map<String,Object> reviewed;
 @SuppressWarnings("unchecked") DocumentExtractor()throws Exception{reviewed=Biblioteca.JSON.readValue(Resources.text("titulos_revisados.json"),Map.class);}
 static boolean validTitle(String text){
  if(text==null)return false;String t=text.strip();return t.length()>=3&&t.length()<=240&&t.chars().filter(Character::isLetter).count()>=3&&!t.matches("(?i)^(?:contents|table of contents|sumário|índice|copyright|about the author|preface|prefácio|dedication|acknowledg.*|packt|apress|manning|springer|pearson|wiley|cover|title|untitled|document\\d*|aula\\d+|microsoft (?:word|powerpoint).*|powerpoint presentation|slide.*|www\\..*)$")&&!Pattern.compile("(?i)https?://|\\.(?:indd|indb|docx?|pptx?|dvi|tex|qxd)\\b|^ISBN|allitebooks|ebooksworld").matcher(t).find()&&!t.contains("_");
 }
 private static String clean(String text){return Objects.toString(text,"").replace('\u00ad',' ').replace('\ufffd',' ').replaceAll("[\\p{Cntrl}\\s]+"," ").strip();}
 static org.w3c.dom.Document xml(byte[] bytes)throws Exception{var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setXIncludeAware(false);return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));}
 static boolean recognizable(Path p){try{if(p.getFileName().toString().toLowerCase().endsWith(".pdf")){try(var in=Files.newInputStream(p)){return new String(in.readNBytes(1024),java.nio.charset.StandardCharsets.ISO_8859_1).contains("%PDF-");}}try(var z=new ZipFile(p.toFile())){return z.getEntry("META-INF/container.xml")!=null;}}catch(Exception e){return false;}}
 Result extract(Path file,String relative)throws Exception {
  var meta=new LinkedHashMap<String,Object>();meta.put("filename",file.getFileName().toString());meta.put("file_size",Files.size(file));meta.put("format",file.toString().toLowerCase().endsWith(".epub")?"EPUB":"PDF");
  var evidence=new ArrayList<Map<String,Object>>();String title=null,source=null;
  if(file.toString().toLowerCase().endsWith(".epub")){
   try(var z=new ZipFile(file.toFile())){var container=xml(z.getInputStream(z.getEntry("META-INF/container.xml")).readAllBytes());var roots=container.getElementsByTagNameNS("*","rootfile");if(roots.getLength()==0)throw new IOException("EPUB sem arquivo de metadados");String opf=((Element)roots.item(0)).getAttribute("full-path");var entry=z.getEntry(opf);if(entry==null||entry.getSize()>4_000_000)throw new IOException("Metadados EPUB inválidos");var doc=xml(z.getInputStream(entry).readAllBytes());var nodes=doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/","*");for(int i=0;i<nodes.getLength();i++){var n=nodes.item(i);String key=n.getLocalName(),value=clean(n.getTextContent());if(!value.isBlank())meta.merge(key,value,(a,b)->a+"; "+b);}meta.put("author",meta.getOrDefault("creator",""));meta.put("isbn",meta.getOrDefault("identifier",""));meta.put("year",first("\\b(?:19|20)\\d{2}\\b",Objects.toString(meta.get("date"),"")));String t=Objects.toString(meta.get("title"),"");if(validTitle(t)){title=t;source="epub_dc_title";}}
  }else{
   try(var doc=Loader.loadPDF(file.toFile())){
    if(doc.getNumberOfPages()==0)throw new IOException("PDF sem páginas legíveis");var info=doc.getDocumentInformation();put(meta,"title",info.getTitle());put(meta,"author",info.getAuthor());put(meta,"subject",info.getSubject());put(meta,"keywords",info.getKeywords());put(meta,"creator",info.getCreator());put(meta,"producer",info.getProducer());meta.put("pages",doc.getNumberOfPages());
    try{var xmp=doc.getDocumentCatalog().getMetadata();if(xmp!=null){var xml=xml(xmp.toByteArray());var nodes=xml.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/","*");for(int i=0;i<nodes.getLength();i++){var n=nodes.item(i);put(meta,"xmp_"+n.getLocalName(),clean(n.getTextContent()));}}}catch(Exception ignored){}
    var allLines=new ArrayList<List<Line>>();var text=new StringBuilder();for(int i=0;i<Math.min(5,doc.getNumberOfPages());i++){final var lines=new ArrayList<Line>();var stripper=new PDFTextStripper(){protected void writeString(String value,List<TextPosition> positions)throws IOException{String t=clean(value);if(!t.isBlank()&&!positions.isEmpty())lines.add(new Line(t,positions.stream().mapToDouble(TextPosition::getFontSizeInPt).max().orElse(12),positions.get(0).getYDirAdj()));super.writeString(value,positions);}};stripper.setStartPage(i+1);stripper.setEndPage(i+1);stripper.setSortByPosition(true);text.append(stripper.getText(doc)).append('\n');allLines.add(lines);}
    String metadataTitle=Objects.toString(meta.get("xmp_title"),Objects.toString(meta.get("title"),""));
    if(validTitle(metadataTitle)){title=clean(metadataTitle);source="pdf_metadata";}
    if(title==null){double best=-1;for(int i=0;i<allLines.size();i++){var lines=allLines.get(i);boolean usedOcr=false;if(lines.stream().mapToInt(l->l.text.length()).sum()<70&&i<2){if(ocr==null)ocr=new NativeOcr();var recognized=ocr.lines(new PDFRenderer(doc).renderImageWithDPI(i,150));if(!recognized.isEmpty()){usedOcr=true;lines=recognized;allLines.set(i,lines);text.append('\n').append(String.join("\n",lines.stream().map(Line::text).toList()));}}
      double max=lines.stream().filter(l->validTitle(l.text)).mapToDouble(Line::size).max().orElse(12);var candidates=new ArrayList<Line>(lines);var cluster=new ArrayList<Line>();for(var line:lines){if(!validTitle(line.text)||line.size<max*.72)continue;if(!cluster.isEmpty()&&line.y-cluster.get(cluster.size()-1).y>max*2){candidates.add(combine(cluster));cluster.clear();}cluster.add(line);}if(!cluster.isEmpty())candidates.add(combine(cluster));
      for(var line:candidates){if(!validTitle(line.text)||line.size<max*.6)continue;double score=Math.min(line.size,65)-i*3+Math.min(line.text.split(" ").length,7);if(Pattern.compile("(?i)copyright|reserved|published|\\bISBN\\b|http|author|rights|birmingham|mumbai").matcher(line.text).find())score-=80;if(line.text.length()>150)score-=30;if(score>best&&score>13){best=score;title=line.text;source=usedOcr?"pdf_cover_ocr":"pdf_text";}}
     }
    }
    for(int i=0;i<allLines.size();i++)evidence.add(Map.of("page",i+1,"lines",allLines.get(i).stream().map(Line::text).limit(50).toList()));
    String content=text.toString();put(meta,"publisher",meta.get("xmp_publisher"));if(!meta.containsKey("publisher")){String[][] publishers={{"Casa do Código","casa do c[oó]digo"},{"O’Reilly Media","o[’'`\\s]?reilly"},{"Packt Publishing","\\bpackt\\b"},{"Apress","\\bapress\\b"},{"Manning Publications","\\bmanning\\b"},{"Wiley","\\bwiley\\b"},{"Pearson","\\bpearson\\b"},{"Springer","\\bspringer\\b"},{"Addison-Wesley","addison.wesley"},{"Novatec","\\bnovatec\\b"},{"No Starch Press","no starch press"},{"Microsoft Press","microsoft press"},{"MIT Press","mit press"}};int offset=Integer.MAX_VALUE;for(var p:publishers){var m=Pattern.compile(p[1],Pattern.CASE_INSENSITIVE).matcher(content);if(m.find()&&m.start()<offset){meta.put("publisher",p[0]);offset=m.start();}}}
    var year=Pattern.compile("(?i)(?:copyright|©)[^\\n]{0,80}?\\b((?:19|20)\\d{2})\\b").matcher(content);if(year.find())meta.put("year",year.group(1));
    var isbn=new LinkedHashSet<String>();var matcher=Pattern.compile("97[89][\\d \\-]{10,22}").matcher(content);while(matcher.find()){String number=matcher.group().replaceAll("\\D","");if(number.length()>=13){number=number.substring(0,13);if(BookGrouper.validIsbn(number))isbn.add(number);}}meta.put("isbn",String.join("; ",isbn));put(meta,"language",meta.get("xmp_language"));
   }
  }
  String key=reviewed.containsKey(relative)?relative:file.getFileName().toString();if(reviewed.containsKey(key)){title=(String)reviewed.get(key);source="document_review";}
  return new Result(title,source,meta,evidence);
 }
 private static Line combine(List<Line> lines){return new Line(String.join(" ",lines.stream().map(Line::text).toList()),lines.stream().mapToDouble(Line::size).average().orElse(12),lines.get(0).y);}
 private static void put(Map<String,Object> map,String key,Object value){if(value!=null&&!value.toString().isBlank())map.put(key,value);}
 private static String first(String pattern,String text){var m=Pattern.compile(pattern).matcher(text);return m.find()?m.group():"";}
 public void close(){if(ocr!=null)ocr.close();}
}

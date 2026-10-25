package br.com.catalogo;
import java.net.*;import java.io.*;import java.util.*;import java.util.regex.*;import javax.swing.text.*;import javax.swing.text.html.*;import javax.swing.text.html.parser.ParserDelegator;

/** Capa da página de produto, somente depois de confirmar o ISBN da edição. */
final class WebCoverSearch {
 interface Fetch {String get(String url)throws Exception;}
 record Candidate(String image,String page){}
 private static String attribute(MutableAttributeSet attrs,String name){for(var keys=attrs.getAttributeNames();keys.hasMoreElements();){Object key=keys.nextElement();if(key.toString().equalsIgnoreCase(name))return Objects.toString(attrs.getAttribute(key),"");}return "";}
 static List<String> links(String html,String base,String title)throws Exception{
  var result=new LinkedHashSet<String>();var origin=URI.create(base);var words=Arrays.stream(CategoryClassifier.fold(title).split("[^a-z0-9]+" )).filter(w->w.length()>3).toList();
  new ParserDelegator().parse(new StringReader(html),new HTMLEditorKit.ParserCallback(){public void handleStartTag(HTML.Tag tag,MutableAttributeSet a,int pos){if(tag!=HTML.Tag.A)return;try{var uri=origin.resolve(attribute(a,"href"));String path=CategoryClassifier.fold(Objects.toString(uri.getPath(),""));boolean product=path.contains("/produto/")||words.stream().filter(path::contains).count()>=Math.min(2,Math.max(1,words.size()));if(product&&Objects.equals(origin.getHost(),uri.getHost())&&List.of("http","https").contains(uri.getScheme()))result.add(uri.toString());}catch(IllegalArgumentException ignored){}}},true);return result.stream().limit(5).toList();
 }
 static String image(String html,String base,String isbn)throws Exception{
  if(!Pattern.compile("(?is)(?:isbn(?:-1[03])?|gtin1[34])[\\s\\p{Punct}]{0,80}"+Pattern.quote(isbn)+"(?![0-9])").matcher(html).find())return "";
  var images=new LinkedHashMap<String,String>();var metaTags=new StringBuilder("<html><head>");var tags=Pattern.compile("(?is)<meta\\b[^>]*>").matcher(html);while(tags.find())metaTags.append(tags.group());metaTags.append("</head><body></body></html>");new ParserDelegator().parse(new StringReader(metaTags.toString()),new HTMLEditorKit.ParserCallback(){public void handleSimpleTag(HTML.Tag tag,MutableAttributeSet a,int pos){if(tag==HTML.Tag.META){String key=attribute(a,"property");if(key.isBlank())key=attribute(a,"name");if(List.of("og:image","og:image:secure_url","twitter:image").contains(key))images.put(key,attribute(a,"content"));}}},true);
  for(String key:List.of("og:image:secure_url","og:image","twitter:image")){String value=images.getOrDefault(key,"");if(!value.isBlank()){var uri=URI.create(base).resolve(value);if(List.of("http","https").contains(Objects.toString(uri.getScheme(),"")))return uri.toString();}}return "";
 }
 static List<Candidate> find(String searchUrl,String isbn,String title,Fetch fetch)throws Exception{
  var results=new ArrayList<Candidate>();String html=fetch.get(searchUrl);for(String page:links(html,searchUrl,title)){if(Thread.currentThread().isInterrupted())throw new InterruptedException();try{String image=image(fetch.get(page),page,isbn);if(!image.isBlank())results.add(new Candidate(image,page));}catch(IOException|IllegalArgumentException ignored){}if(results.size()>=2)break;}return results;
 }
}

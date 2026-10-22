package br.com.catalogo;
import java.util.*;
/** Relações verificadas; títulos iguais sozinhos não unem edições. */
final class WorkGroups {
 record Relation(String book,String related,String kind){}
 record Group(List<Biblioteca.Book> books,Set<String> samples){
  Biblioteca.Book primary(){return books.stream().filter(b->!samples.contains(b.id)).findFirst().orElse(books.get(0));}
  Biblioteca.Book resume(){return books.stream().filter(b->b.reading).max(Comparator.comparingLong(b->b.updated)).orElseGet(()->books.stream().filter(b->b.favorite).findFirst().orElse(primary()));}
  String files(){return books.size()==1?books.get(0).filename:books.size()+" arquivos · "+String.join(", ",books.stream().map(b->b.format).distinct().sorted().toList());}
 }
 static List<Group> group(List<Biblioteca.Book> all,List<Relation> relations){
  var parent=new HashMap<String,String>();for(var b:all)parent.put(b.id,b.id);var samples=new HashSet<String>();
  for(var r:relations){if(!parent.containsKey(r.book)||!parent.containsKey(r.related))continue;if(r.kind.equals("sample_of"))samples.add(r.book);parent.put(find(parent,r.book),find(parent,r.related));}
  var groups=new LinkedHashMap<String,List<Biblioteca.Book>>();for(var b:all)groups.computeIfAbsent(find(parent,b.id),k->new ArrayList<>()).add(b);
  var result=new ArrayList<Group>();for(var books:groups.values()){books.sort(Comparator.<Biblioteca.Book>comparingInt(b->samples.contains(b.id)?2:b.format.equals("PDF")?0:1).thenComparing(b->b.filename));result.add(new Group(books,samples));}return result;
 }
 private static String find(Map<String,String> parent,String id){String p=parent.get(id);if(!p.equals(id)){p=find(parent,p);parent.put(id,p);}return p;}
}

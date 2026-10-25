package br.com.catalogo;
import java.util.*;import java.util.regex.*;import java.text.*;
final class CategoryClassifier {
 record Rule(Pattern pattern,String destination){}
 private final List<Rule> rules=new ArrayList<>();
 CategoryClassifier()throws Exception{for(var row:Biblioteca.JSON.readTree(Resources.text("classificacao.json")))rules.add(new Rule(Pattern.compile(row.get(0).asText()),row.get(1).asText()));}
 static String fold(String text){return Normalizer.normalize(Objects.toString(text,""),Normalizer.Form.NFKD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
 static String snake(String text){String result=fold(text).replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$","");return result.isBlank()?"sem_nome":result;}
 static final Map<String,String> ALIASES=Map.ofEntries(Map.entry("JavaScript","java_script"),Map.entry("TypeScript","type_script"),Map.entry("DevOps e Infraestrutura","dev_ops_e_infraestrutura"),Map.entry("APIs","ap_is"),Map.entry("GraphQL","graph_ql"),Map.entry("NoSQL","no_sql"),Map.entry("MongoDB","mongo_db"),Map.entry("MySQL","my_sql"),Map.entry("PostgreSQL","postgre_sql"),Map.entry("OpenStack","open_stack"),Map.entry("OpenShift","open_shift"),Map.entry("Git e GitHub","git_e_git_hub"),Map.entry("C# e .NET","csharp_e_dotnet"),Map.entry("C e C++","c_e_cpp"),Map.entry("iOS e Swift","i_os_e_swift"),Map.entry("Eletrônica e IoT","eletronica_e_io_t"),Map.entry("OCaml","o_caml"));
 String destination(String title,String filename,Map<String,Object> metadata){
  String actual=fold(title);String[][] extra={{"\\brust\\b","programacao/rust"},{"\\bkotlin\\b","programacao/kotlin"},{"\\btauri\\b","desenvolvimento_desktop/tauri"},{"\\babap\\b","programacao/abap"},{"\\bterraform\\b","dev_ops_e_infraestrutura/infraestrutura_como_codigo_e_automacao/terraform"},{"\\bguitarra?\\b|\\bguitar\\b","musica/guitarra"},{"\\bviolao\\b","musica/violao"},{"\\barchimate\\b|\\btogaf\\b","arquitetura_e_integracao/arquitetura_empresarial"}};
  for(var item:extra)if(Pattern.compile(item[0]).matcher(actual).find())return item[1];
  if(actual.contains("cassandra"))return "dados_e_bancos_de_dados/no_sql/cassandra";
  if(actual.contains("text adventure")&&actual.contains("python"))return "desenvolvimento_de_jogos/python";
  String category=null;for(String text:List.of(Objects.toString(title,""),filename.replace('_',' '))){for(var rule:rules)if(rule.pattern.matcher(fold(text)).find()){category=rule.destination;break;}if(category!=null)break;}
  if(category==null){String subject=Objects.toString(metadata.getOrDefault("xmp_subject",metadata.getOrDefault("subject","")),"").strip();String text=actual+" "+fold(filename)+" "+fold(subject);
   String[][] sciences={{"devotional|devociona|reflexoes diarias|meditacoes diarias","Religião e Espiritualidade/Devocionais"},{"biblia|biblical|bible|teologia|theolog|christian|cristian|espiritualidade","Religião e Espiritualidade/Estudos bíblicos e teologia"},{"astronom|astrophys|cosmolog","Ciências/Astronomia"},{"quantum|quantica|quantico","Ciências/Computação quântica"},{"fisica|physics","Ciências/Física"},{"biolog|genetic|bioinform","Ciências/Biologia"},{"quimica|chemistry","Ciências/Química"},{"economics|economia","Gestão e Negócios/Economia"},{"financ|investiment","Gestão e Negócios/Finanças"},{"culinaria|cozinha|cooking","Conhecimentos Gerais/Culinária"},{"dostoi|tolstoi|machado de assis|jane austen|literatura|romance|novel","Literatura/Ficção e poesia"}};
   for(var item:sciences)if(Pattern.compile(item[0]).matcher(text).find()){category=item[1];break;}
   if(category==null){if(subject.length()>=3&&subject.length()<=90&&subject.matches("[\\p{L}\\p{N}\\s,;&()\\-]+")&&!fold(subject).matches(".*(www|http|allitebooks|ebooksworld|microsoft|powerpoint|untitled).*"))return "conhecimentos_gerais/"+snake(subject.split("[;,]")[0]);return "a_classificar";}
  }
  if(category.equals("Programação/Python")||category.equals("Programação/Java")){
   String text=fold(title==null?filename:title);String sub="Fundamentos e referência";String[][] topics={{"concurr|parallel|performance|clustering|distributed","Concorrência e desempenho"},{"data.struct|algorithm|algoritmo","Algoritmos e estruturas de dados"},{"design.pattern|object.oriented|orientacao|clean|best.practices","Orientação a objetos e boas práticas"},{"django|requests|scraping|scrapping|web","Web e extração de dados"},{"gui|wxpython|graphics|javafx","Interfaces gráficas"}};
   for(var item:topics)if(Pattern.compile(item[0]).matcher(text).find()){sub=item[1];break;}category+="/"+sub;
  }
  return String.join("/",Arrays.stream(category.split("/")).map(p->ALIASES.getOrDefault(p,snake(p))).toList());
 }
}

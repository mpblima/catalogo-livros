package br.com.catalogo;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class WebCoverSearchTest {
 @Test void requiresExactIsbnAndDecodesImageUrl()throws Exception{
  String html="<meta property='og:image' content='/cover.jpg?w=800&amp;h=800'><p>ISBN: 9788534531238</p>";
  assertEquals("https://book.test/cover.jpg?w=800&h=800",WebCoverSearch.image(html,"https://book.test/product","9788534531238"));assertEquals("",WebCoverSearch.image(html,"https://book.test/product","9780132350884"));
 }
 @Test void searchFiltersProductLinksAndDeduplicates()throws Exception{
  String html="<a href='/conta'>Conta</a><a href='/produto/livro-123'>Livro</a><a href='/produto/livro-123'>Capa</a><a href='https://other.test/produto/book'>Outro</a>";
  assertEquals(java.util.List.of("https://book.test/produto/livro-123"),WebCoverSearch.links(html,"https://book.test/busca?q=isbn","Livro"));
 }
}

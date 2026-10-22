package br.com.catalogo;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WorkGroupsTest {
 Biblioteca.Book book(String id,String format){var b=new Biblioteca.Book();b.id=id;b.format=format;b.filename=id+"."+format.toLowerCase();b.title="Mesmo título";b.section="assunto";return b;}
 @Test void titleAloneDoesNotMergeEditions(){assertEquals(2,WorkGroups.group(List.of(book("ed1","PDF"),book("ed2","PDF")),List.of()).size());}
 @Test void groupsVerifiedFormatsAndPreservesIndependentData(){var a=book("pdf","PDF");a.notes="Nota PDF";var b=book("epub","EPUB");b.notes="Nota EPUB";b.reading=true;b.updated=10;var group=WorkGroups.group(List.of(a,b),List.of(new WorkGroups.Relation(a.id,b.id,"same_work"))).get(0);assertEquals(2,group.books().size());assertEquals(b,group.resume());assertEquals("Nota PDF",a.notes);assertEquals("Nota EPUB",b.notes);assertTrue(group.files().contains("2 arquivos"));}
 @Test void sampleDoesNotReplaceCompleteBook(){var sample=book("a_amostra","PDF");var full=book("z_completo","PDF");var group=WorkGroups.group(List.of(sample,full),List.of(new WorkGroups.Relation(sample.id,full.id,"sample_of"))).get(0);assertEquals(full,group.primary());assertTrue(group.samples().contains(sample.id));}
}

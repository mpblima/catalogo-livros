package br.com.catalogo;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;import java.nio.file.*;import java.nio.file.attribute.*;
class HtmlCatalogConfigTest {
 @TempDir Path root;
 @Test void createsPrivateConfigurationUsingSupportedPermissions()throws Exception{
  String previous=System.getProperty("catalogo.config");Path file=root.resolve("config.json");try{System.setProperty("catalogo.config",file.toString());var config=HtmlCatalog.configuration();assertFalse(config.token().isBlank());assertEquals(config,HtmlCatalog.configuration());var posix=Files.getFileAttributeView(file,PosixFileAttributeView.class);if(posix!=null)assertEquals(PosixFilePermissions.fromString("rw-------"),posix.readAttributes().permissions());else{var acl=Files.getFileAttributeView(file,AclFileAttributeView.class);assertNotNull(acl);assertTrue(acl.getAcl().stream().anyMatch(entry->entry.type()==AclEntryType.ALLOW&&entry.principal().equals(owner(acl))));assertFalse(acl.getAcl().stream().anyMatch(entry->entry.type()==AclEntryType.ALLOW&&!entry.principal().equals(owner(acl))));}}finally{if(previous==null)System.clearProperty("catalogo.config");else System.setProperty("catalogo.config",previous);}
 }
 private static UserPrincipal owner(AclFileAttributeView view){try{return view.getOwner();}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
}

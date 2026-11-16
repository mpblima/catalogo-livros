package br.com.catalogo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;

/** Updates are accepted only when the release manifest has a valid Ed25519 signature. */
final class AutoUpdater {
 private static final String FEED="https://github.com/mpblima/catalogo-livros/releases/latest/download/latest.json";
 private static final String PUBLIC_KEY="MCowBQYDK2VwAyEAs020iYs/8Xdm9o43bvSEaBhf1FT4R3dj/6MJQ7nA+hY=";
 private static final HttpClient HTTP=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(java.time.Duration.ofSeconds(15)).build();
 private static final ObjectMapper JSON=new ObjectMapper();
 private static final java.util.prefs.Preferences PREFS=java.util.prefs.Preferences.userNodeForPackage(AutoUpdater.class);
 private AutoUpdater(){}
 static void check(JFrame parent,boolean manual){
  if(!packaged()){if(manual)JOptionPane.showMessageDialog(parent,"A atualização automática está disponível na versão instalada do aplicativo.","Atualizações",JOptionPane.INFORMATION_MESSAGE);return;}
  new SwingWorker<JsonNode,Void>(){
   protected JsonNode doInBackground()throws Exception{return latest();}
   protected void done(){try{JsonNode manifest=get();String current=version();String next=manifest.path("version").asText();if(compare(next,current)<=0){if(manual)JOptionPane.showMessageDialog(parent,"O catálogo já está atualizado.","Atualizações",JOptionPane.INFORMATION_MESSAGE);return;}if(next.equals(PREFS.get("skipVersion",""))&&!manual)return;offer(parent,manifest,manual);}
    catch(Exception e){if(manual)JOptionPane.showMessageDialog(parent,"Não foi possível verificar atualizações: "+message(e),"Atualizações",JOptionPane.ERROR_MESSAGE);}}
  }.execute();
 }
 private static boolean packaged(){return System.getProperty("catalogo.app-dir")!=null||System.getProperty("jpackage.app-path")!=null;}
 private static String version(){String v=AutoUpdater.class.getPackage().getImplementationVersion();return v==null?"0.0.0":v;}
 private static JsonNode latest()throws Exception{
  var response=HTTP.send(HttpRequest.newBuilder(URI.create(FEED)).timeout(java.time.Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
  if(response.statusCode()!=200)throw new IOException("Servidor respondeu HTTP "+response.statusCode());byte[] body=response.body();
  var sigResponse=HTTP.send(HttpRequest.newBuilder(URI.create(FEED.replace("latest.json","latest.sig"))).timeout(java.time.Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofString());
  if(sigResponse.statusCode()!=200)throw new IOException("Assinatura da versão ausente");
  byte[] keyBytes=Base64.getDecoder().decode(PUBLIC_KEY);PublicKey key=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(keyBytes));Signature verifier=Signature.getInstance("Ed25519");verifier.initVerify(key);verifier.update(body);
  if(!verifier.verify(Base64.getDecoder().decode(sigResponse.body().trim())))throw new SecurityException("A assinatura do catálogo de atualização é inválida.");
  JsonNode node=JSON.readTree(body);if(!node.path("version").asText().matches("[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IOException("Versão inválida no catálogo");return node;
 }
 private static int compare(String a,String b){try{String[] x=a.split("\\."),y=b.split("\\.");for(int i=0;i<3;i++){int d=Integer.compare(Integer.parseInt(x[i]),Integer.parseInt(y[i]));if(d!=0)return d;}return 0;}catch(Exception e){return -1;}}
 private static void offer(JFrame parent,JsonNode manifest,boolean manual){String version=manifest.path("version").asText();Object[] options={"Atualizar agora","Depois","Ignorar esta versão"};int selected=JOptionPane.showOptionDialog(parent,"A versão "+version+" está disponível. Deseja baixar e instalar agora?","Atualização disponível",JOptionPane.DEFAULT_OPTION,JOptionPane.INFORMATION_MESSAGE,null,options,options[0]);
  if(selected==0)download(parent,manifest);else if(selected==2)PREFS.put("skipVersion",version);
 }
 private static void download(JFrame parent,JsonNode manifest){
  String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);String platform=os.contains("win")?"windows":os.contains("mac")?"macos":"linux";JsonNode asset=manifest.path("assets").path(platform);
  if(asset.isMissingNode()){JOptionPane.showMessageDialog(parent,"Não há instalador publicado para este sistema.","Atualizações",JOptionPane.WARNING_MESSAGE);return;}
  new SwingWorker<Path,Void>(){protected Path doInBackground()throws Exception{String name=asset.path("name").asText();if(name.isBlank()||name.contains("/")||name.contains("\\"))throw new IOException("Nome de pacote inválido");var r=HTTP.send(HttpRequest.newBuilder(URI.create(asset.path("url").asText())).timeout(java.time.Duration.ofMinutes(10)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());if(r.statusCode()!=200)throw new IOException("Download respondeu HTTP "+r.statusCode());byte[] data=r.body();String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));if(!MessageDigest.isEqual(hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),asset.path("sha256").asText().toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new SecurityException("O checksum do instalador não confere.");Path file=Files.createTempFile("catalogo-update-",platform.equals("windows")?".msi":platform.equals("macos")?".dmg":".run");Files.write(file,data);return file;}
   protected void done(){try{Path file=get();if(platform.equals("macos")){if(Desktop.isDesktopSupported())Desktop.getDesktop().open(file.toFile());else new ProcessBuilder("open",file.toString()).start();JOptionPane.showMessageDialog(parent,"O instalador foi aberto. Arraste o aplicativo para Aplicativos para concluir.","Atualização",JOptionPane.INFORMATION_MESSAGE);return;}if(JOptionPane.showConfirmDialog(parent,"O instalador foi verificado. O catálogo será fechado para aplicar a atualização.","Atualização",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;launchInstaller(file,platform);parent.dispose();System.exit(0);}catch(Exception e){JOptionPane.showMessageDialog(parent,"Falha ao preparar a atualização: "+message(e),"Atualização",JOptionPane.ERROR_MESSAGE);}}
  }.execute();
 }
 private static void launchInstaller(Path file,String platform)throws Exception{
  if(platform.equals("windows")){String exe=System.getProperty("jpackage.app-path");String msi=file.toString().replace("'","''"),launch=exe.replace("'","''");String script="$p=Get-Process CatalogoLivros -ErrorAction SilentlyContinue; if($p){$p|Wait-Process}; $i=Start-Process msiexec.exe -ArgumentList @('/i',\""+msi+"\",'/passive','/norestart') -Wait -PassThru; if($i.ExitCode -eq 0){Start-Process \""+launch+"\"}";new ProcessBuilder("powershell.exe","-NoProfile","-WindowStyle","Hidden","-Command",script).start();}
  else {Path app=Path.of(System.getProperty("catalogo.app-dir")).toAbsolutePath().normalize();Path prefix=app.getParent();long pid=ProcessHandle.current().pid();String qfile=file.toString().replace("'","'\\''"),qprefix=prefix.toString().replace("'","'\\''"),qlauncher=prefix.resolve("catalogo").toString().replace("'","'\\''");String script="#!/bin/sh\nwhile kill -0 "+pid+" 2>/dev/null; do sleep 1; done\n'"+qfile+"' --prefix '"+qprefix+"'\nrm -f '"+qfile+"'\n'"+qlauncher+"'\nrm -f \"$0\"\n";Path helper=Files.createTempFile("catalogo-update-",".sh");Files.writeString(helper,script);helper.toFile().setExecutable(true);new ProcessBuilder("sh","-c","nohup \""+helper+"\" >/dev/null 2>&1 &").start();}
 }
 private static String message(Throwable t){while(t.getCause()!=null)t=t.getCause();return Objects.toString(t.getMessage(),t.getClass().getSimpleName());}
}

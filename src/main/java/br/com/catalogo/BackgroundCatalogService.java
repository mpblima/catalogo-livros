package br.com.catalogo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;
import java.util.concurrent.TimeUnit;

/** Registra o servidor HTML como processo de usuário iniciado no login. */
final class BackgroundCatalogService {
 private static final String TASK_NAME="CatalogoLivros-HTML";
 private static final String LAUNCH_AGENT="br.com.praesens.catalogo-livros.html";

 private BackgroundCatalogService(){}

 static boolean supported(){String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);return (os.contains("windows")||os.contains("mac"))&&System.getProperty("jpackage.app-path")!=null;}
 static boolean automatic(){return preferences().getBoolean("htmlServerAutomatic",true);}
 static void automatic(boolean value){preferences().putBoolean("htmlServerAutomatic",value);}
 private static Preferences preferences(){return Preferences.userNodeForPackage(BackgroundCatalogService.class);}

 static void ensureInstalled()throws Exception {
  String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
  if(!os.contains("windows")&&!os.contains("mac"))return;
  if(!automatic()){disable();return;}
  String appPath=System.getProperty("jpackage.app-path");
  if(appPath==null||appPath.isBlank())return;
  if(os.contains("mac")&&(appPath.startsWith("/Volumes/")||appPath.contains("/AppTranslocation/")))throw new IOException("Mova Catálogo de Livros para Aplicativos antes de abrir; o servidor local precisa de um caminho permanente.");
  Path app=Path.of(appPath).toAbsolutePath().normalize();
  Path server=app.resolveSibling(os.contains("windows")?"CatalogoServer.exe":"CatalogoServer");
  if(!Files.isRegularFile(server))throw new IOException("Inicializador do servidor HTML ausente: "+server);
  if(os.contains("windows"))ensureWindows(server);else ensureMac(server);
 }

 static void disable()throws Exception {
  String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
  if(os.contains("windows")){
   ProcessResult query=run(List.of("schtasks.exe","/Query","/TN",TASK_NAME),Duration.ofSeconds(15),false);
   if(query.exitCode()==0){run(List.of("schtasks.exe","/End","/TN",TASK_NAME),Duration.ofSeconds(15),false);run(List.of("schtasks.exe","/Delete","/F","/TN",TASK_NAME),Duration.ofSeconds(15),true);}
  }else if(os.contains("mac")){
   Path plist=Path.of(System.getProperty("user.home"),"Library","LaunchAgents",LAUNCH_AGENT+".plist");
   String target="gui/"+uid()+"/"+LAUNCH_AGENT;
   if(run(List.of("launchctl","print",target),Duration.ofSeconds(10),false).exitCode()==0)run(List.of("launchctl","bootout",target),Duration.ofSeconds(15),true);
   Files.deleteIfExists(plist);
  }
 }

 private static void ensureWindows(Path server)throws Exception {
  ProcessResult query=run(List.of("schtasks.exe","/Query","/TN",TASK_NAME,"/XML"),Duration.ofSeconds(15),false);
  if(query.exitCode()==0&&query.output().toLowerCase(Locale.ROOT).contains(server.toString().toLowerCase(Locale.ROOT)))return;
  if(query.exitCode()==0)run(List.of("schtasks.exe","/End","/TN",TASK_NAME),Duration.ofSeconds(15),false);
  run(List.of("schtasks.exe","/Create","/F","/SC","ONLOGON","/TN",TASK_NAME,"/TR","\""+server+"\""),Duration.ofSeconds(30),true);
  run(List.of("schtasks.exe","/Run","/TN",TASK_NAME),Duration.ofSeconds(15),true);
 }

 private static void ensureMac(Path server)throws Exception {
  Path agents=Path.of(System.getProperty("user.home"),"Library","LaunchAgents");
  Path plist=agents.resolve(LAUNCH_AGENT+".plist");
  String contents=launchAgent(server);
  String domain="gui/"+uid();
  boolean loaded=run(List.of("launchctl","print",domain+"/"+LAUNCH_AGENT),Duration.ofSeconds(10),false).exitCode()==0;
  if(Files.isRegularFile(plist)&&Files.readString(plist).equals(contents)&&loaded)return;
  Files.createDirectories(agents);
  Files.createDirectories(Path.of(System.getProperty("user.home"),"Library","Logs","CatalogoLivros"));
  if(loaded)run(List.of("launchctl","bootout",domain+"/"+LAUNCH_AGENT),Duration.ofSeconds(15),true);
  Path temporary=Files.createTempFile(agents,LAUNCH_AGENT,".tmp");
  try {
   Files.writeString(temporary,contents,StandardCharsets.UTF_8);
   try{Files.move(temporary,plist,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
   catch(AtomicMoveNotSupportedException e){Files.move(temporary,plist,StandardCopyOption.REPLACE_EXISTING);}
  } finally {Files.deleteIfExists(temporary);}
  run(List.of("launchctl","bootstrap",domain,plist.toString()),Duration.ofSeconds(15),true);
 }

 private static String launchAgent(Path server) {
  Path log=Path.of(System.getProperty("user.home"),"Library","Logs","CatalogoLivros","servidor-html.log");
  return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
   "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"+
   "<plist version=\"1.0\"><dict>\n"+
   "<key>Label</key><string>"+xml(LAUNCH_AGENT)+"</string>\n"+
   "<key>ProgramArguments</key><array><string>"+xml(server.toString())+"</string></array>\n"+
   "<key>RunAtLoad</key><true/><key>KeepAlive</key><true/>\n"+
   "<key>StandardOutPath</key><string>"+xml(log.toString())+"</string>\n"+
   "<key>StandardErrorPath</key><string>"+xml(log.toString())+"</string>\n"+
   "</dict></plist>\n";
 }

 private static String xml(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
 private static String uid()throws Exception{return run(List.of("id","-u"),Duration.ofSeconds(5),true).output().strip();}

 private record ProcessResult(int exitCode,String output){}
 private static ProcessResult run(List<String> command,Duration timeout,boolean required)throws Exception {
  Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
  boolean finished=process.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS);
  if(!finished){process.destroyForcibly();throw new IOException("Tempo esgotado ao configurar o servidor HTML.");}
  String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
  int code=process.exitValue();
  if(required&&code!=0)throw new IOException("Não foi possível configurar o servidor HTML: "+output.strip());
  return new ProcessResult(code,output);
 }
}

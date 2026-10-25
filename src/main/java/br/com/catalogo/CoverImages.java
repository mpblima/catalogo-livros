package br.com.catalogo;
import java.net.*;import java.net.http.*;import java.time.Duration;import java.io.*;import javax.imageio.ImageIO;
final class CoverImages {
 static byte[] download(String url)throws Exception{
  var uri=URI.create(url.strip());if(!"https".equalsIgnoreCase(uri.getScheme())&&!"http".equalsIgnoreCase(uri.getScheme()))throw new IOException("Informe uma URL HTTP ou HTTPS da imagem.");
  var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();var response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
  try(var in=response.body()){if(response.statusCode()!=200)throw new IOException("Não foi possível baixar a imagem (HTTP "+response.statusCode()+").");byte[] bytes=in.readNBytes(12000001);if(bytes.length>12000000)throw new IOException("Imagem excede 12 MB.");var image=ImageIO.read(new ByteArrayInputStream(bytes));if(image==null||image.getWidth()<20||image.getHeight()<20)throw new IOException("URL não contém uma capa válida.");var out=new ByteArrayOutputStream();ImageIO.write(BookPreview.fit(image),"png",out);return out.toByteArray();}
 }
}

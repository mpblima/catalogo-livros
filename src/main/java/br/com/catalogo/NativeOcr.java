package br.com.catalogo;
import com.sun.jna.*;import java.awt.*;import java.awt.image.*;import java.util.*;

/** Ponte Java para o motor Tesseract incluído nos recursos da aplicação. */
final class NativeOcr implements AutoCloseable {
 interface Api extends Library {
  Pointer TessBaseAPICreate();int TessBaseAPIInit3(Pointer api,String path,String languages);void TessBaseAPISetImage(Pointer api,Pointer data,int width,int height,int bytesPerPixel,int stride);
  Pointer TessBaseAPIGetTsvText(Pointer api,int page);void TessDeleteText(Pointer text);void TessBaseAPIDelete(Pointer api);void TessBaseAPISetSourceResolution(Pointer api,int dpi);int TessBaseAPISetVariable(Pointer api,String name,String value);
 }
 private final Api api;private Pointer handle;
 NativeOcr()throws Exception {
  var bundle=Resources.ocrNative();var flags=bundle.flags();
  if(bundle.openmp()!=null)NativeLibrary.getInstance(bundle.openmp().toString(),flags).getFunction("omp_set_num_threads").invokeVoid(new Object[]{1});
  NativeLibrary.getInstance(bundle.leptonica().toString(),flags);
  api=Native.load(bundle.tesseract().toString(),Api.class,flags);handle=api.TessBaseAPICreate();
  if(handle==null||api.TessBaseAPIInit3(handle,Resources.tessdata().toString(),"eng+por")!=0){close();throw new IllegalStateException("Não foi possível iniciar o OCR incluído na aplicação");}
  api.TessBaseAPISetVariable(handle,"debug_file",System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("windows")?"NUL":"/dev/null");
 }
 java.util.List<DocumentExtractor.Line> lines(BufferedImage image)throws Exception {
  var gray=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_BYTE_GRAY);var graphics=gray.createGraphics();graphics.setColor(Color.WHITE);graphics.fillRect(0,0,gray.getWidth(),gray.getHeight());graphics.drawImage(image,0,0,null);graphics.dispose();byte[] data=((DataBufferByte)gray.getRaster().getDataBuffer()).getData();
  try(var memory=new Memory(data.length)){memory.write(0,data,0,data.length);api.TessBaseAPISetImage(handle,memory,image.getWidth(),image.getHeight(),1,image.getWidth());api.TessBaseAPISetSourceResolution(handle,150);Pointer text=api.TessBaseAPIGetTsvText(handle,0);if(text==null)return java.util.List.of();String tsv;try{tsv=text.getString(0,"UTF-8");}finally{api.TessDeleteText(text);}
   var groups=new LinkedHashMap<String,java.util.List<String[]>>();for(String row:tsv.split("\n")){String[] columns=row.split("\t",12);if(columns.length==12&&columns[0].equals("5")){try{if(Double.parseDouble(columns[10])<35)continue;}catch(NumberFormatException e){continue;}String key=String.join("/",Arrays.copyOfRange(columns,1,5));groups.computeIfAbsent(key,k->new ArrayList<>()).add(columns);}}
   var result=new ArrayList<DocumentExtractor.Line>();for(var group:groups.values()){var words=new ArrayList<String>();double height=0,y=Double.MAX_VALUE;for(var word:group){words.add(word[11]);height=Math.max(height,Integer.parseInt(word[9])/2.0833);y=Math.min(y,Integer.parseInt(word[7])/2.0833);}result.add(new DocumentExtractor.Line(String.join(" ",words),height,y));}return result;
  }
 }
 public void close(){if(handle!=null){api.TessBaseAPIDelete(handle);handle=null;}}
}

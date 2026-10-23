package br.com.catalogo;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.List;

/** Catálogo desktop Swing: não usa navegador nem servidor para a interface. */
public final class CatalogoApp extends JFrame {
 final Biblioteca library;
 List<Biblioteca.Book> all=new ArrayList<>(),visible=new ArrayList<>();
 Biblioteca.Book selected;
 List<WorkGroups.Group> visibleGroups=new ArrayList<>();
 final JComboBox<Biblioteca.Book> versions=new JComboBox<>();
 final javax.swing.Timer searchDelay;
 SwingWorker<Snapshot,Void> searchWorker;
 long searchSerial=0,appliedSerial=-1,formVersion=0;boolean initialLoad=true,pendingRefresh=false,closed=false;
 java.util.function.Consumer<Exception> reportError=this::showError;
 record Snapshot(List<Biblioteca.Book> books,List<String> categories,List<WorkGroups.Relation> relations,Set<String> hits){}

 final JTextField search=new JTextField(),location=new JTextField();
 final JTextArea notes=new JTextArea(),metadata=new JTextArea();
 final JLabel collectionStat=new JLabel("—"),readingStat=new JLabel("—"),favoriteStat=new JLabel("—");
 Runnable onReady=()->{};boolean readyDelivered=false;
 final JLabel title=new JLabel("Escolha um livro"),subtitle=new JLabel(" "),status=new JLabel("Carregando biblioteca…"),saved=new JLabel(" ");
 final JCheckBox favorite=new JCheckBox("Favorito"),reading=new JCheckBox("Estou lendo");
 final JSpinner page=new JSpinner(new SpinnerNumberModel(1,1,1000000,1));
 final JComboBox<String> shelf=new JComboBox<>(new String[]{"Toda a biblioteca","Estou lendo","Favoritos","Com anotações","Título a revisar"});
 final DefaultTableModel model=new DefaultTableModel(new String[]{"Livro","Arquivo","Autor","Categoria/Subcategoria"},0){public boolean isCellEditable(int r,int c){return false;}};
 final JTable table=new JTable(model);
 final JTree tree=new JTree(new DefaultMutableTreeNode("Todos os assuntos"));
 final JProgressBar progress=new JProgressBar(0,100);
 final JButton update=new JButton("Organizar e atualizar"),open=new JButton("Abrir e continuar"),move=new JButton("Alterar categoria");
 final javax.swing.Timer autoSave;
 java.util.function.Consumer<Path> openFolder=this::external;
 String category="";boolean loading=false,busy=false;String lastSaved="";
 public CatalogoApp(Biblioteca lib)throws Exception {this(lib,true);}
 CatalogoApp(Biblioteca lib,boolean automaticStartup)throws Exception {
  super("Catálogo de Livros");library=lib;
  setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);setMinimumSize(new Dimension(1050,700));setSize(1480,960);setLocationRelativeTo(null);setIconImage(UiTheme.appIcon());
  autoSave=new javax.swing.Timer(650,e->save());autoSave.setRepeats(false);
  searchDelay=new javax.swing.Timer(180,e->runSearch());searchDelay.setRepeats(false);
  var main=new JPanel(new BorderLayout(22,22));main.setBackground(UiTheme.BACKGROUND);main.setBorder(BorderFactory.createEmptyBorder(26,28,18,28));setContentPane(main);
  var header=UiTheme.panel(new BorderLayout(0,20));var hero=UiTheme.panel(new BorderLayout(20,0));
  var identity=UiTheme.panel(new BorderLayout(16,0));identity.add(new JLabel(new UiTheme.BookIcon(54)),BorderLayout.WEST);var brandText=UiTheme.panel(new BorderLayout(0,3));brandText.add(UiTheme.eyebrow("SEU ACERVO PESSOAL"),BorderLayout.NORTH);var brand=new JLabel("Biblioteca");brand.setFont(new Font("Serif",Font.BOLD,34));brandText.add(brand);identity.add(brandText);hero.add(identity,BorderLayout.WEST);
  var metrics=UiTheme.panel(new FlowLayout(FlowLayout.RIGHT,28,4));metrics.add(metric("ARQUIVOS",collectionStat));metrics.add(metric("EM LEITURA",readingStat));metrics.add(metric("FAVORITOS",favoriteStat));hero.add(metrics,BorderLayout.EAST);header.add(hero,BorderLayout.NORTH);
  search.putClientProperty("JTextField.placeholderText","Pesquisar por título, autor, editora ou assunto…");search.setPreferredSize(new Dimension(400,46));search.setToolTipText("Pesquise palavras ou partes de palavras em todo o acervo");
  var commandBar=UiTheme.panel(new BorderLayout(16,0));commandBar.add(search);var actions=UiTheme.panel(new FlowLayout(FlowLayout.RIGHT,10,0));UiTheme.primary(update);var arbitrary=new JButton("Abrir arquivo…");actions.add(arbitrary);actions.add(update);commandBar.add(actions,BorderLayout.EAST);header.add(commandBar,BorderLayout.SOUTH);main.add(header,BorderLayout.NORTH);
  var sidebar=new JPanel(new BorderLayout(0,18));sidebar.setBorder(UiTheme.card());sidebar.setPreferredSize(new Dimension(235,400));
  var navigation=UiTheme.panel(new BorderLayout(0,10));navigation.add(UiTheme.eyebrow("EXPLORAR"),BorderLayout.NORTH);navigation.add(shelf);sidebar.add(navigation,BorderLayout.NORTH);tree.setRootVisible(true);tree.setBorder(BorderFactory.createEmptyBorder(4,0,4,0));sidebar.add(UiTheme.scroll(tree));
  table.setRowHeight(48);table.setIntercellSpacing(new Dimension(0,0));table.getTableHeader().setPreferredSize(new Dimension(0,40));table.setFillsViewportHeight(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setAutoCreateRowSorter(true);table.setShowGrid(false);table.getColumnModel().getColumn(0).setPreferredWidth(480);table.getColumnModel().getColumn(1).setPreferredWidth(280);table.getColumnModel().getColumn(2).setPreferredWidth(145);table.getColumnModel().getColumn(3).setPreferredWidth(240);
  table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,boolean focus,int row,int col){var c=super.getTableCellRendererComponent(t,v,sel,focus,row,col);setBorder(BorderFactory.createEmptyBorder(0,14,0,14));setFont(t.getFont().deriveFont(t.convertColumnIndexToModel(col)==0?Font.BOLD:Font.PLAIN,t.convertColumnIndexToModel(col)==0?14f:13f));setBackground(sel?t.getSelectionBackground():row%2==0?UiTheme.SURFACE:new Color(26,36,38));setToolTipText(Objects.toString(v,""));setForeground(sel?t.getSelectionForeground():t.convertColumnIndexToModel(col)==1?UiTheme.ACCENT:t.convertColumnIndexToModel(col)==3?UiTheme.MUTED:t.getForeground());if(t.convertColumnIndexToModel(col)==1){setToolTipText("Clique para abrir a pasta de um dos arquivos desta obra");}return c;}});
  var details=new JPanel(new BorderLayout(12,14));details.setBorder(UiTheme.card());
  var detailHead=UiTheme.panel(new BorderLayout(0,12));var titlePanel=UiTheme.panel(new BorderLayout(0,7));titlePanel.add(UiTheme.eyebrow("SUA LEITURA"),BorderLayout.NORTH);title.setFont(title.getFont().deriveFont(Font.BOLD,20));titlePanel.add(title);detailHead.add(titlePanel,BorderLayout.NORTH);var versionPanel=UiTheme.panel(new BorderLayout(10,5));subtitle.setVisible(false);subtitle.setForeground(UiTheme.MUTED);subtitle.setFont(subtitle.getFont().deriveFont(12f));versionPanel.add(subtitle,BorderLayout.NORTH);versionPanel.add(versions);detailHead.add(versionPanel,BorderLayout.CENTER);
  versions.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){super.getListCellRendererComponent(list,value,index,selected,focus);if(value instanceof Biblioteca.Book b)setText(versionLabel(b));return this;}});
  versions.addActionListener(e->{if(loading)return;var b=(Biblioteca.Book)versions.getSelectedItem();if(b!=null){if(save())fill(b);else{loading=true;versions.setSelectedItem(selected);loading=false;}}});
  var controls=UiTheme.panel(new FlowLayout(FlowLayout.LEFT,10,0));UiTheme.primary(open);controls.add(open);controls.add(favorite);controls.add(reading);controls.add(new JLabel("Página:"));controls.add(page);controls.add(move);detailHead.add(controls,BorderLayout.SOUTH);details.add(detailHead,BorderLayout.NORTH);
  notes.setLineWrap(true);notes.setWrapStyleWord(true);notes.setMargin(new Insets(16,16,16,16));notes.putClientProperty("JTextArea.placeholderText","Suas anotações sobre este livro. O texto é salvo automaticamente.");
  metadata.setEditable(false);metadata.setLineWrap(true);metadata.setWrapStyleWord(true);metadata.setMargin(new Insets(16,16,16,16));
  var tabs=new JTabbedPane();tabs.setBorder(BorderFactory.createEmptyBorder());tabs.addTab("Anotações",UiTheme.scroll(notes));tabs.addTab("Metadados",UiTheme.scroll(metadata));details.add(tabs,BorderLayout.CENTER);
  var detailFoot=UiTheme.panel(new BorderLayout(10,0));var where=UiTheme.panel(new BorderLayout(8,0));var locationLabel=new JLabel("Capítulo / local (EPUB)");locationLabel.setForeground(UiTheme.MUTED);where.add(locationLabel,BorderLayout.WEST);saved.setForeground(UiTheme.MUTED);saved.setFont(saved.getFont().deriveFont(12f));where.add(location);detailFoot.add(where,BorderLayout.CENTER);detailFoot.add(saved,BorderLayout.EAST);details.add(detailFoot,BorderLayout.SOUTH);
  var tableCard=new JPanel(new BorderLayout());tableCard.setBorder(BorderFactory.createLineBorder(UiTheme.LINE));tableCard.add(UiTheme.scroll(table));var vertical=new JSplitPane(JSplitPane.VERTICAL_SPLIT,tableCard,details);vertical.setResizeWeight(.44);vertical.setDividerLocation(300);vertical.setBorder(BorderFactory.createEmptyBorder());vertical.setDividerSize(18);
  var horizontal=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,sidebar,vertical);horizontal.setResizeWeight(.16);horizontal.setDividerLocation(255);horizontal.setBorder(BorderFactory.createEmptyBorder());horizontal.setDividerSize(20);main.add(horizontal);
  var footer=UiTheme.panel(new BorderLayout(14,0));status.setForeground(UiTheme.MUTED);status.setFont(status.getFont().deriveFont(12f));footer.add(status);progress.setPreferredSize(new Dimension(270,18));progress.setStringPainted(true);progress.setVisible(false);footer.add(progress,BorderLayout.EAST);main.add(footer,BorderLayout.SOUTH);
  search.getDocument().addDocumentListener(listener(this::filter));shelf.addActionListener(e->filter());
  tree.addTreeSelectionListener(e->{if(!loading)browseCategory(tree.getSelectionPath());});
  tree.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e))browseCategory(tree.getPathForLocation(e.getX(),e.getY()));}});
  table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!loading)choose();});
  table.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(!SwingUtilities.isLeftMouseButton(e))return;int row=table.rowAtPoint(e.getPoint()),col=table.columnAtPoint(e.getPoint());if(row<0||col<0)return;if(table.convertColumnIndexToModel(col)==1){if(e.getClickCount()==1)openGroupFolder(visibleGroups.get(table.convertRowIndexToModel(row)));}else if(e.getClickCount()==2)open();}});
  table.addMouseMotionListener(new MouseMotionAdapter(){public void mouseMoved(MouseEvent e){int col=table.columnAtPoint(e.getPoint());table.setCursor(Cursor.getPredefinedCursor(col>=0&&table.rowAtPoint(e.getPoint())>=0&&table.convertColumnIndexToModel(col)==1?Cursor.HAND_CURSOR:Cursor.DEFAULT_CURSOR));}});
  notes.getDocument().addDocumentListener(listener(this::dirty));location.getDocument().addDocumentListener(listener(this::dirty));favorite.addActionListener(e->dirty());reading.addActionListener(e->dirty());page.addChangeListener(e->dirty());
  open.addActionListener(e->open());update.addActionListener(e->organize());move.addActionListener(e->move());
  arbitrary.addActionListener(e->{var chooser=new JFileChooser(library.root.toFile());if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION&&save()){var p=chooser.getSelectedFile().toPath();var b=all.stream().filter(x->library.file(x).equals(p)).findFirst().orElse(null);if(b!=null)read(b);else external(p);}});
  addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){if(save()){closed=true;searchDelay.stop();autoSave.stop();if(searchWorker!=null)searchWorker.cancel(false);dispose();}}});
  reload();if(automaticStartup)SwingUtilities.invokeLater(()->worker(List.of(),false));
 }
 void refreshCounters(){collectionStat.setText(String.format(java.util.Locale.forLanguageTag("pt-BR"),"%,d",all.size()));readingStat.setText(String.valueOf(all.stream().filter(b->b.reading).count()));favoriteStat.setText(String.valueOf(all.stream().filter(b->b.favorite).count()));}
 static JPanel metric(String label,JLabel value){var panel=UiTheme.panel(new BorderLayout(0,5));value.setFont(value.getFont().deriveFont(Font.BOLD,23));panel.add(value,BorderLayout.NORTH);panel.add(UiTheme.eyebrow(label));return panel;}
 static DocumentListener listener(Runnable run){return new DocumentListener(){public void insertUpdate(DocumentEvent e){run.run();}public void removeUpdate(DocumentEvent e){run.run();}public void changedUpdate(DocumentEvent e){run.run();}};}
 record Category(String path){public String toString(){String[] p=path.split("/");return p[p.length-1].replace('_',' ');}}
 void browseCategory(TreePath path){
  if(loading||path==null)return;
  var node=(DefaultMutableTreeNode)path.getLastPathComponent();
  category=node.getUserObject() instanceof Category c?c.path:"";
  loading=true;try{shelf.setSelectedIndex(0);search.setText("");}finally{loading=false;}
  filter();
 }
 void reload(){filter();}
 void filter(){if(loading||closed)return;searchSerial++;searchDelay.restart();}
 void runSearch(){
  if(closed||!save())return;
  if(searchWorker!=null)searchWorker.cancel(false);
  final long serial=searchSerial,version=formVersion;final String query=search.getText(),chosenCategory=category,chosenId=selected==null?null:selected.id;final int mode=shelf.getSelectedIndex();
  status.setText("Pesquisando…");
  searchWorker=new SwingWorker<Snapshot,Void>(){
   protected Snapshot doInBackground()throws Exception{var hits=library.searchIds(query);return new Snapshot(library.books(),library.categories(),library.relations(),hits);}
   protected void done(){
    if(isCancelled()||closed||serial!=searchSerial)return;
    try{var snapshot=get();if(!save())return;if(version!=formVersion){filter();return;}applySnapshot(snapshot,query,chosenCategory,mode,chosenId);appliedSerial=serial;}
    catch(Exception e){error(e);}
   }
  };searchWorker.execute();
 }
 void applySnapshot(Snapshot snapshot,String query,String chosenCategory,int mode,String chosenId){
  boolean searching=!query.isBlank();loading=true;all=snapshot.books();refreshCounters();
  if(initialLoad){initialLoad=false;if(all.stream().anyMatch(b->b.reading)){shelf.setSelectedIndex(1);mode=1;}}
  List<String> current=new ArrayList<>();var oldRoot=(DefaultMutableTreeNode)tree.getModel().getRoot();var nodesEnumeration=oldRoot.depthFirstEnumeration();while(nodesEnumeration.hasMoreElements()){var n=(DefaultMutableTreeNode)nodesEnumeration.nextElement();if(n.getUserObject() instanceof Category c)current.add(c.path());}Collections.sort(current);
  if(!current.equals(snapshot.categories())){
   var root=new DefaultMutableTreeNode("Todos os assuntos");var nodes=new HashMap<String,DefaultMutableTreeNode>();nodes.put("",root);
   for(String path:snapshot.categories()){var node=new DefaultMutableTreeNode(new Category(path));nodes.put(path,node);String parent=path.contains("/")?path.substring(0,path.lastIndexOf('/')):"";nodes.getOrDefault(parent,root).add(node);}
   tree.setModel(new DefaultTreeModel(root));tree.expandRow(0);var node=nodes.get(chosenCategory);if(node!=null)tree.setSelectionPath(new TreePath(node.getPath()));
  }
  visible=new ArrayList<>();visibleGroups=new ArrayList<>();var data=new ArrayList<Object[]>();
  for(var group:WorkGroups.group(all,snapshot.relations())){
   final int filterMode=searching?0:mode;
   boolean matches=group.books().stream().anyMatch(b->snapshot.hits().contains(b.id)&&(searching||chosenCategory.isBlank()||b.section.equals(chosenCategory)||b.section.startsWith(chosenCategory+"/"))&&(filterMode==0||filterMode==1&&b.reading||filterMode==2&&b.favorite||filterMode==3&&!b.notes.isBlank()||filterMode==4&&(b.title==null||b.title.isBlank())));
   if(!matches)continue;var b=group.primary();visible.add(b);visibleGroups.add(group);
   String categories=String.join("; ",group.books().stream().map(x->x.section.replace('_',' ').replace("/"," / ")).distinct().toList());
   data.add(new Object[]{b.title==null||b.title.isBlank()?"Título não identificado":b.title,group.files(),b.meta("author"),categories});
  }
  selected=null;model.setDataVector(data.toArray(Object[][]::new),new String[]{"Livro","Arquivo","Autor","Categoria/Subcategoria"});
  table.getColumnModel().getColumn(0).setPreferredWidth(480);table.getColumnModel().getColumn(1).setPreferredWidth(280);table.getColumnModel().getColumn(2).setPreferredWidth(145);table.getColumnModel().getColumn(3).setPreferredWidth(240);
  clear();loading=false;status.setText(visible.size()+" obras exibidas · "+all.size()+" arquivos na biblioteca"+(searching?" · busca em todo o acervo":""));
  int row=0;if(chosenId!=null)for(int i=0;i<visibleGroups.size();i++)if(visibleGroups.get(i).books().stream().anyMatch(b->b.id.equals(chosenId))){row=i;break;}
  if(!visible.isEmpty()){table.setRowSelectionInterval(table.convertRowIndexToView(row),table.convertRowIndexToView(row));if(chosenId!=null){var book=visibleGroups.get(row).books().stream().filter(b->b.id.equals(chosenId)).findFirst().orElse(null);if(book!=null){loading=true;versions.setSelectedItem(book);loading=false;fill(book);}}}
  if(!readyDelivered){readyDelivered=true;onReady.run();}
 }
 String versionLabel(Biblioteca.Book book){boolean sample=visibleGroups.stream().anyMatch(g->g.samples().contains(book.id));return (sample?"Amostra / parcial · ":"")+book.format+" · "+book.filename;}
 void openGroupFolder(WorkGroups.Group group){
  var b=group.books().size()==1?group.books().get(0):(Biblioteca.Book)JOptionPane.showInputDialog(this,"Escolha o arquivo para abrir sua pasta:","Arquivos desta obra",JOptionPane.PLAIN_MESSAGE,null,group.books().toArray(),group.resume());
  if(b!=null)openFolder.accept(library.file(b).getParent());
 }
 void clear(){loading=true;versions.removeAllItems();versions.setEnabled(false);title.setText("Escolha um livro");subtitle.setText(" ");notes.setText("");metadata.setText("");location.setText("");location.setEnabled(false);favorite.setSelected(false);reading.setSelected(false);page.setValue(1);open.setEnabled(false);move.setEnabled(false);saved.setText(" ");loading=false;}
 void choose(){
  if(!save()){restoreSelection();return;}int row=table.getSelectedRow();if(row<0){selected=null;clear();return;}
  var group=visibleGroups.get(table.convertRowIndexToModel(row));loading=true;versions.removeAllItems();for(var b:group.books())versions.addItem(b);versions.setEnabled(group.books().size()>1);var book=group.resume();versions.setSelectedItem(book);loading=false;fill(book);
 }
 void restoreSelection(){if(selected==null)return;for(int i=0;i<visibleGroups.size();i++){final String id=selected.id;if(visibleGroups.get(i).books().stream().anyMatch(b->b.id.equals(id))){loading=true;table.setRowSelectionInterval(table.convertRowIndexToView(i),table.convertRowIndexToView(i));loading=false;return;}}}
 void fill(Biblioteca.Book book){selected=book;loading=true;title.setText(selected.title==null?"Título não identificado":selected.title);title.setToolTipText(title.getText());subtitle.setText("("+selected.filename+")");subtitle.setToolTipText(selected.path);versions.setToolTipText(selected.path);favorite.setSelected(selected.favorite);reading.setSelected(selected.reading);page.setValue(selected.page);notes.setText(selected.notes);notes.setCaretPosition(0);location.setEnabled(!selected.format.equalsIgnoreCase("PDF"));location.setText(selected.format.equalsIgnoreCase("PDF")?"":selected.location);try{var labels=Map.of("title","Título nos metadados","author","Autor","publisher","Editora","year","Ano","isbn","ISBN","language","Idioma","pages","Páginas","keywords","Palavras-chave","subject","Assunto","creator","Criado com");var text=new StringBuilder();var fields=Biblioteca.JSON.readTree(selected.metadata).fields();while(fields.hasNext()){var f=fields.next();String value=f.getValue().isValueNode()?f.getValue().asText():f.getValue().toString();if(!value.isBlank())text.append(labels.getOrDefault(f.getKey(),f.getKey())).append(": ").append(value).append("\n\n");}metadata.setText(text+"Arquivo: "+selected.path);}catch(Exception e){metadata.setText(selected.metadata);}metadata.setCaretPosition(0);saved.setText("Salvo no catálogo");open.setEnabled(true);move.setEnabled(!busy);loading=false;lastSaved=state();}
 String state(){try{return Biblioteca.JSON.writeValueAsString(List.of(favorite.isSelected(),reading.isSelected(),page.getValue(),location.getText(),notes.getText()));}catch(Exception e){throw new IllegalStateException(e);}}
 void dirty(){if(!loading&&selected!=null){formVersion++;saved.setText("Salvando…");autoSave.restart();}}
 boolean save(){if(selected==null||loading)return true;try{page.commitEdit();if(state().equals(lastSaved))return true;autoSave.stop();selected.favorite=favorite.isSelected();selected.reading=reading.isSelected();selected.page=(Integer)page.getValue();selected.notes=notes.getText();if(!selected.format.equalsIgnoreCase("PDF"))selected.location=location.getText();library.save(selected);refreshCounters();lastSaved=state();saved.setText("Salvo no catálogo");if(pendingRefresh){pendingRefresh=false;SwingUtilities.invokeLater(this::filter);}return true;}catch(Exception e){saved.setText("Falha ao salvar");error(e);return false;}}
 void open(){if(selected!=null&&save())read(selected);}
 void read(Biblioteca.Book b){if(!save())return;try{if(b.format.equalsIgnoreCase("PDF")){new PdfReader(this,library,b,()->{if(selected!=null&&selected.id.equals(b.id)){updateReaderProgress(b);}}).setVisible(true);}else{b.reading=true;library.save(b);external(library.file(b));if(!b.location.isBlank())JOptionPane.showMessageDialog(this,"Retome no local salvo: "+b.location);}}catch(Exception e){error(e);}}
 void updateReaderProgress(Biblioteca.Book book){
  loading=true;selected.page=book.page;selected.reading=book.reading;selected.original.put("page",book.page);selected.original.put("reading",book.reading?1:0);page.setValue(book.page);reading.setSelected(book.reading);refreshCounters();loading=false;
  // Atualiza só o progresso na referência de salvamento; notas ainda em edição continuam pendentes.
  try{var previous=(com.fasterxml.jackson.databind.node.ArrayNode)Biblioteca.JSON.readTree(lastSaved);previous.set(1,Biblioteca.JSON.getNodeFactory().booleanNode(book.reading));previous.set(2,Biblioteca.JSON.getNodeFactory().numberNode(book.page));lastSaved=previous.toString();}catch(Exception ignored){}
 }
 void external(Path path){try{Desktop.getDesktop().open(path.toFile());}catch(Exception e){error(e);}}
 void move(){if(selected==null||!save())return;try{var cats=library.categories();Object target=JOptionPane.showInputDialog(this,"Escolha uma categoria existente:","Mover livro",JOptionPane.PLAIN_MESSAGE,null,cats.toArray(),selected.section);if(target!=null)worker(List.of("move",selected.path,target.toString()));}catch(Exception e){error(e);}}
 void organize(){if(save())worker(List.of());}
 void worker(List<String> args){worker(args,true);}
 boolean finishCatalogUpdate(){if(!save()){pendingRefresh=true;status.setText("Catálogo atualizado; suas anotações precisam ser salvas antes de recarregar.");return false;}filter();return true;}
 void worker(List<String> args,boolean showSummary){if(busy||closed)return;busy=true;update.setEnabled(false);move.setEnabled(false);progress.setVisible(true);progress.setValue(0);status.setText("Procurando livros soltos e atualizando metadados…");
  new SwingWorker<String,String>(){
   protected String doInBackground()throws Exception{
    var organizer=new LibraryOrganizer(library);LibraryOrganizer.Progress callback=(p,m)->{try{publish(Biblioteca.JSON.writeValueAsString(Map.of("progress",p,"message",m)));}catch(Exception e){throw new IllegalStateException(e);}};
    if(args.isEmpty())return Biblioteca.JSON.writeValueAsString(Map.of("result",organizer.organize(callback)));
    if(args.size()==3&&args.get(0).equals("move"))return Biblioteca.JSON.writeValueAsString(Map.of("moved",organizer.move(args.get(1),args.get(2),callback)));
    throw new IllegalArgumentException("Operação inválida");
   }
   protected void process(List<String> lines){for(var line:lines)try{var j=Biblioteca.JSON.readTree(line);if(j.has("progress"))progress.setValue(Math.max(progress.getValue(),Math.min(97,j.path("progress").asInt())));if(j.has("message"))status.setText(j.path("message").asText());}catch(Exception ignored){}}
   protected void done(){try{String result=get();if(closed)return;if(!finishCatalogUpdate())return;progress.setValue(100);status.setText("Catálogo e índice atualizados");var j=Biblioteca.JSON.readTree(result);if(showSummary&&j.has("result")){var r=j.path("result");JOptionPane.showMessageDialog(CatalogoApp.this,"Movidos: "+r.path("moved").asInt()+"\nDuplicados separados para revisão: "+r.path("duplicates").asInt()+"\nA classificar: "+r.path("unclassified").asInt(),"Atualização concluída",JOptionPane.INFORMATION_MESSAGE);}}catch(Exception e){error(e);}finally{busy=false;update.setEnabled(true);move.setEnabled(selected!=null);progress.setVisible(false);}}
  }.execute();
 }
 void error(Exception e){reportError.accept(e);}
 void showError(Exception e){JOptionPane.showMessageDialog(this,Objects.toString(e.getMessage(),e.toString()),"Não foi possível concluir",JOptionPane.ERROR_MESSAGE);}
 public static void main(String[] args)throws Exception {
  Path requested=Path.of(System.getProperty("catalogo.root",System.getProperty("user.home")+"/Desktop/Livros"));for(int i=0;i<args.length-1;i++)if(args[i].equals("--library"))requested=Path.of(args[i+1]);final Path root=requested;
  if(Arrays.asList(args).contains("--serve")){var library=new Biblioteca(root);var config=HtmlCatalog.configuration();var server=new CatalogHttpServer(library,config.port(),config.token());Runtime.getRuntime().addShutdownHook(new Thread(server::close));new java.util.concurrent.CountDownLatch(1).await();return;}
  if(Arrays.asList(args).contains("--update")){var library=new Biblioteca(root);System.out.println(Biblioteca.JSON.writeValueAsString(new LibraryOrganizer(library).organize((p,m)->System.err.println(p+"% "+m))));return;}
  if(Arrays.asList(args).contains("--verify-ocr")){var image=new java.awt.image.BufferedImage(1200,250,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,1200,250);g.setColor(Color.BLACK);g.setFont(new Font("SansSerif",Font.BOLD,64));g.drawString("Kotlin Essentials",60,140);g.dispose();try(var ocr=new NativeOcr()){String text=String.join(" ",ocr.lines(image).stream().map(DocumentExtractor.Line::text).toList());if(!text.toLowerCase().contains("kotlin"))throw new IllegalStateException("OCR não reconheceu o texto de teste");System.out.println("OCR incluído: OK (eng+por)");}return;}
  UiTheme.install();
  if(Arrays.asList(args).contains("--verify")){var library=new Biblioteca(root);var list=library.books();System.out.println("SQLite OK: "+list.size()+" documentos; "+list.stream().filter(b->b.reading).count()+" em leitura; "+list.stream().filter(b->b.favorite).count()+" favoritos.");return;}
  SwingUtilities.invokeLater(()->{
   var splash=new StartupSplash();splash.setVisible(true);splash.stage("Abrindo catálogo e índice de pesquisa…",20);
   new SwingWorker<Biblioteca,Void>(){
    protected Biblioteca doInBackground()throws Exception{return new Biblioteca(root);}
    protected void done(){try{
     splash.stage("Preparando suas leituras e os formatos disponíveis…",70);var app=new CatalogoApp(get());
     app.onReady=()->{splash.stage("Biblioteca pronta",100);app.setVisible(true);splash.dispose();};
     app.reportError=e->{splash.dispose();app.setVisible(true);app.showError(e);};
    }catch(Exception e){splash.dispose();JOptionPane.showMessageDialog(null,"Não foi possível abrir a biblioteca: "+e.getMessage(),"Catálogo de Livros",JOptionPane.ERROR_MESSAGE);}}
   }.execute();
  });
 }
}

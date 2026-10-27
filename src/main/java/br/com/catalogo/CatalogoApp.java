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
 final CloudSync cloudSync;
 CoverQueue ownedCoverQueue;
 final JButton switchLibrary=new JButton("Biblioteca / banco…");
 final JButton cloud=new JButton("Conta / sincronização…");
 List<Biblioteca.Book> all=new ArrayList<>(),visible=new ArrayList<>();
 Biblioteca.Book selected;
 List<WorkGroups.Group> visibleGroups=new ArrayList<>();
 final JComboBox<Biblioteca.Book> versions=new JComboBox<>();
 final javax.swing.Timer searchDelay;
 SwingWorker<Snapshot,Void> searchWorker;
 long searchSerial=0,appliedSerial=-1,formVersion=0;boolean initialLoad=true,pendingRefresh=false,closed=false;
 java.util.function.Consumer<Exception> reportError=this::showError;
 record Snapshot(List<Biblioteca.Book> books,List<String> categories,List<WorkGroups.Relation> relations,Set<String> hits){}

 final JLabel locationLabel=new JLabel("Capítulo / local (EPUB)");
 final JTextField search=new JTextField(),location=new JTextField();
 final JLabel cover=new JLabel("Selecione um livro",SwingConstants.CENTER);
 final JTextArea summary=new JTextArea("Selecione um livro para ver a apresentação.");
 SwingWorker<BookPreview.Preview,Void> previewWorker;
 long previewSerial;
 final JTextArea notes=new JTextArea(),metadata=new JTextArea();
 final JLabel collectionStat=new JLabel("—"),readingStat=new JLabel("—"),favoriteStat=new JLabel("—");
 Runnable onReady=()->{};boolean readyDelivered=false;
 final JLabel title=new JLabel("Escolha um livro"),status=new JLabel("Carregando biblioteca…"),saved=new JLabel(" ");
 final JCheckBox favorite=new JCheckBox("Favorito"),reading=new JCheckBox("Estou lendo");
 final JSpinner page=new JSpinner(new SpinnerNumberModel(1,1,1000000,1));
 final JComboBox<String> shelf=new JComboBox<>(new String[]{"Toda a biblioteca","Estou lendo","Favoritos","Com anotações","Título a revisar"});
 final DefaultTableModel model=new DefaultTableModel(new String[]{"Livro","Arquivo / ISBN","Autor","Categoria/Subcategoria"},0){public boolean isCellEditable(int r,int c){return false;}};
 final JTextArea subtitle=new JTextArea(2,1);
 final JTable table=new JTable(model);
 final JSplitPane bookSplit=new JSplitPane(JSplitPane.VERTICAL_SPLIT);
 final JTree tree=new JTree(new DefaultMutableTreeNode("Todos os assuntos"));
 final JProgressBar progress=new JProgressBar(0,100);
 final JButton saveChanges=new JButton("Salvar alterações");
 final JButton editButton=new JButton("Editar livro…");
 final JButton deleteButton=new JButton("Excluir livro físico"),setCover=new JButton("Definir capa…");
 java.util.function.Predicate<Biblioteca.Book> confirmDelete=this::confirmDeletion;
 final javax.swing.Timer coverRefresh=new javax.swing.Timer(3000,e->pollCover());
 final JButton newPhysical=new JButton("Novo livro físico");
 final JButton update=new JButton("Catalogar livros digitais"),open=new JButton("Abrir e continuar"),move=new JButton("Alterar categoria");
 final javax.swing.Timer autoSave;
 final javax.swing.Timer cloudSyncTimer;long lastCloudSync=0;boolean cloudSyncBusy=false;
 java.util.function.Consumer<Path> openFolder=this::external;
 String category="";boolean loading=false,busy=false;String lastSaved="";
 public CatalogoApp(Biblioteca lib)throws Exception {this(lib,true);}
 CatalogoApp(Biblioteca lib,boolean automaticStartup)throws Exception {
  super("Catálogo de Livros");library=lib;cloudSync=new CloudSync(lib);setTitle("Catálogo de Livros · "+lib.root+" · "+lib.database.getFileName());if(BackgroundCatalogService.supported())setJMenuBar(serviceMenu());
  setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);setMinimumSize(new Dimension(1050,700));setSize(1480,960);setLocationRelativeTo(null);setIconImage(UiTheme.appIcon());
  autoSave=new javax.swing.Timer(650,e->save());autoSave.setRepeats(false);
  cloudSyncTimer=new javax.swing.Timer(10000,e->automaticCloudSync());cloudSyncTimer.start();
  searchDelay=new javax.swing.Timer(180,e->runSearch());searchDelay.setRepeats(false);
  var main=new JPanel(new BorderLayout(22,22));main.setBackground(UiTheme.BACKGROUND);main.setBorder(BorderFactory.createEmptyBorder(26,28,18,28));setContentPane(main);
  var header=UiTheme.panel(new BorderLayout(0,20));var hero=UiTheme.panel(new BorderLayout(20,0));
  var identity=UiTheme.panel(new BorderLayout(16,0));identity.add(new JLabel(new UiTheme.BookIcon(54)),BorderLayout.WEST);var brandText=UiTheme.panel(new BorderLayout(0,3));brandText.add(UiTheme.eyebrow("SEU ACERVO PESSOAL"),BorderLayout.NORTH);var brand=new JLabel("Biblioteca");brand.setFont(new Font("Serif",Font.BOLD,34));brandText.add(brand);identity.add(brandText);hero.add(identity,BorderLayout.WEST);
  var metrics=UiTheme.panel(new FlowLayout(FlowLayout.RIGHT,28,4));metrics.add(metric("LIVROS",collectionStat));metrics.add(metric("EM LEITURA",readingStat));metrics.add(metric("FAVORITOS",favoriteStat));hero.add(metrics,BorderLayout.EAST);header.add(hero,BorderLayout.NORTH);
  search.putClientProperty("JTextField.placeholderText","Pesquisar por título, autor, editora ou assunto…");search.setPreferredSize(new Dimension(400,46));search.setToolTipText("Pesquise palavras ou partes de palavras usando o filtro selecionado em Explorar");shelf.setToolTipText("Filtre os livros, inclusive nos resultados da busca");
  var commandBar=UiTheme.panel(new BorderLayout(16,0));commandBar.add(search);var actions=UiTheme.panel(new FlowLayout(FlowLayout.RIGHT,10,0));UiTheme.primary(update);var arbitrary=new JButton("Abrir arquivo…");actions.add(switchLibrary);switchLibrary.setToolTipText(library.root+" · "+library.database.getFileName());switchLibrary.addActionListener(e->chooseLibrary());actions.add(arbitrary);actions.add(newPhysical);actions.add(update);commandBar.add(actions,BorderLayout.EAST);header.add(commandBar,BorderLayout.SOUTH);main.add(header,BorderLayout.NORTH);
  var sidebar=new JPanel(new BorderLayout(0,18));sidebar.setBorder(UiTheme.card());sidebar.setPreferredSize(new Dimension(235,400));
  var navigation=UiTheme.panel(new BorderLayout(0,10));navigation.add(UiTheme.eyebrow("EXPLORAR"),BorderLayout.NORTH);navigation.add(shelf);sidebar.add(navigation,BorderLayout.NORTH);tree.setRootVisible(true);tree.setBorder(BorderFactory.createEmptyBorder(4,0,4,0));sidebar.add(UiTheme.scroll(tree));sidebar.add(cloud,BorderLayout.SOUTH);cloud.addActionListener(e->{if(busy)return;if(selected!=null&&selected.physical()&&!state().equals(lastSaved)){JOptionPane.showMessageDialog(this,"Salve as alterações do livro físico antes de sincronizar.");return;}if(!save())return;try{new CloudDialog(this,library,this::filter).setVisible(true);}catch(Exception ex){error(ex);}});
  table.setRowHeight(48);table.setIntercellSpacing(new Dimension(0,0));table.getTableHeader().setPreferredSize(new Dimension(0,40));table.setFillsViewportHeight(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setAutoCreateRowSorter(true);table.setShowGrid(false);table.getColumnModel().getColumn(0).setPreferredWidth(480);table.getColumnModel().getColumn(1).setPreferredWidth(280);table.getColumnModel().getColumn(2).setPreferredWidth(145);table.getColumnModel().getColumn(3).setPreferredWidth(240);
  table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,boolean focus,int row,int col){var c=super.getTableCellRendererComponent(t,v,sel,focus,row,col);setBorder(BorderFactory.createEmptyBorder(0,14,0,14));setFont(t.getFont().deriveFont(t.convertColumnIndexToModel(col)==0?Font.BOLD:Font.PLAIN,t.convertColumnIndexToModel(col)==0?14f:13f));setBackground(sel?t.getSelectionBackground():row%2==0?UiTheme.SURFACE:new Color(26,36,38));setToolTipText(Objects.toString(v,""));setForeground(sel?t.getSelectionForeground():t.convertColumnIndexToModel(col)==1?UiTheme.ACCENT:t.convertColumnIndexToModel(col)==3?UiTheme.MUTED:t.getForeground());if(t.convertColumnIndexToModel(col)==1){setToolTipText("Clique para abrir a pasta de um dos arquivos desta obra");}return c;}});
  var details=new DetailsPanel();details.setBorder(UiTheme.card());
  var detailHead=UiTheme.panel(new BorderLayout(0,12));var titlePanel=UiTheme.panel(new BorderLayout(0,7));titlePanel.add(UiTheme.eyebrow("SUA LEITURA"),BorderLayout.NORTH);title.setFont(title.getFont().deriveFont(Font.BOLD,20));titlePanel.add(title);detailHead.add(titlePanel,BorderLayout.NORTH);var versionPanel=UiTheme.panel(new BorderLayout(10,5));subtitle.setEditable(false);subtitle.setLineWrap(true);subtitle.setWrapStyleWord(false);subtitle.setOpaque(false);subtitle.setBorder(BorderFactory.createEmptyBorder());subtitle.setVisible(false);subtitle.setForeground(UiTheme.MUTED);subtitle.setFont(subtitle.getFont().deriveFont(12f));versionPanel.add(subtitle,BorderLayout.NORTH);versionPanel.add(versions);detailHead.add(versionPanel,BorderLayout.CENTER);
  versions.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){super.getListCellRendererComponent(list,value,index,selected,focus);if(value instanceof Biblioteca.Book b)setText(versionLabel(b));return this;}});
  versions.addActionListener(e->{if(loading)return;var b=(Biblioteca.Book)versions.getSelectedItem();if(b!=null){if(save())fill(b);else{loading=true;versions.setSelectedItem(selected);loading=false;}}});
  var controls=UiTheme.panel(new FlowLayout(FlowLayout.LEFT,10,0));UiTheme.primary(open);controls.add(open);saveChanges.setVisible(false);saveChanges.addActionListener(e->save(true));controls.add(favorite);controls.add(reading);controls.add(new JLabel("Página:"));controls.add(page);var bookActions=UiTheme.panel(new GridLayout(0,3,8,6));bookActions.add(editButton);bookActions.add(deleteButton);bookActions.add(setCover);bookActions.add(move);bookActions.add(saveChanges);detailHead.add(controls,BorderLayout.SOUTH);details.add(detailHead,BorderLayout.NORTH);
  notes.setLineWrap(true);notes.setWrapStyleWord(true);notes.setMargin(new Insets(16,16,16,16));notes.putClientProperty("JTextArea.placeholderText","Suas anotações sobre este livro. O texto é salvo automaticamente.");
  metadata.setEditable(false);metadata.setLineWrap(true);metadata.setWrapStyleWord(true);metadata.setMargin(new Insets(16,16,16,16));
  var tabs=new JTabbedPane();tabs.setBorder(BorderFactory.createEmptyBorder());tabs.addTab("Anotações",UiTheme.scroll(notes));tabs.addTab("Metadados",UiTheme.scroll(metadata));var preview=UiTheme.panel(new BorderLayout(16,0));cover.setPreferredSize(new Dimension(150,170));cover.setForeground(UiTheme.MUTED);preview.add(cover,BorderLayout.WEST);
  summary.setEditable(false);summary.setLineWrap(true);summary.setWrapStyleWord(true);summary.setMargin(new Insets(10,12,10,12));var presentation=UiTheme.panel(new BorderLayout(0,6));presentation.add(UiTheme.eyebrow("SOBRE O LIVRO"),BorderLayout.NORTH);presentation.add(UiTheme.scroll(summary));preview.add(presentation);preview.setPreferredSize(new Dimension(0,180));
  var detailBody=UiTheme.panel(new BorderLayout(0,12));detailBody.add(preview,BorderLayout.NORTH);detailBody.add(tabs);details.add(detailBody,BorderLayout.CENTER);
  var detailFoot=UiTheme.panel(new BorderLayout(10,0));var where=UiTheme.panel(new BorderLayout(8,0));locationLabel.setForeground(UiTheme.MUTED);where.add(locationLabel,BorderLayout.WEST);saved.setForeground(UiTheme.MUTED);saved.setFont(saved.getFont().deriveFont(12f));where.add(location);detailFoot.add(where,BorderLayout.CENTER);detailFoot.add(saved,BorderLayout.EAST);details.add(detailFoot,BorderLayout.SOUTH);
  var tableCard=new JPanel(new BorderLayout());tableCard.setBorder(BorderFactory.createLineBorder(UiTheme.LINE));tableCard.add(bookActions,BorderLayout.NORTH);tableCard.add(UiTheme.scroll(table));tableCard.setMinimumSize(new Dimension(0,180));var detailsScroll=UiTheme.scroll(details);detailsScroll.setMinimumSize(new Dimension(0,140));detailsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);var vertical=bookSplit;vertical.setTopComponent(tableCard);vertical.setBottomComponent(detailsScroll);vertical.setContinuousLayout(true);vertical.setOneTouchExpandable(true);vertical.setResizeWeight(.65);SwingUtilities.invokeLater(()->vertical.setDividerLocation(.65));vertical.setBorder(BorderFactory.createEmptyBorder());vertical.setDividerSize(18);vertical.setToolTipText("Arraste a divisória para ajustar a altura da lista e dos detalhes.");
  var horizontal=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,sidebar,vertical);horizontal.setResizeWeight(.16);horizontal.setDividerLocation(255);horizontal.setBorder(BorderFactory.createEmptyBorder());horizontal.setDividerSize(20);main.add(horizontal);
  var footer=UiTheme.panel(new BorderLayout(14,0));status.setForeground(UiTheme.MUTED);status.setFont(status.getFont().deriveFont(12f));footer.add(status);progress.setPreferredSize(new Dimension(270,18));progress.setStringPainted(true);progress.setVisible(false);footer.add(progress,BorderLayout.EAST);main.add(footer,BorderLayout.SOUTH);
  search.getDocument().addDocumentListener(listener(this::filter));shelf.addActionListener(e->filter());
  tree.addTreeSelectionListener(e->{if(!loading)browseCategory(tree.getSelectionPath());});
  tree.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(!SwingUtilities.isLeftMouseButton(e))return;var path=tree.getClosestPathForLocation(e.getX(),e.getY());if(path==null)return;var bounds=tree.getPathBounds(path);if(bounds!=null&&e.getY()>=bounds.y&&e.getY()<bounds.y+bounds.height)browseCategory(path);}});
  table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!loading)choose();});
  table.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(!SwingUtilities.isLeftMouseButton(e))return;int row=table.rowAtPoint(e.getPoint()),col=table.columnAtPoint(e.getPoint());if(row<0||col<0)return;if(table.convertColumnIndexToModel(col)==1){if(e.getClickCount()==1)openGroupFolder(visibleGroups.get(table.convertRowIndexToModel(row)));}else if(e.getClickCount()==2)open();}});
  table.addMouseMotionListener(new MouseMotionAdapter(){public void mouseMoved(MouseEvent e){int col=table.columnAtPoint(e.getPoint());table.setCursor(Cursor.getPredefinedCursor(col>=0&&table.rowAtPoint(e.getPoint())>=0&&table.convertColumnIndexToModel(col)==1?Cursor.HAND_CURSOR:Cursor.DEFAULT_CURSOR));}});
  notes.getDocument().addDocumentListener(listener(this::dirty));location.getDocument().addDocumentListener(listener(this::dirty));favorite.addActionListener(e->markChanged());reading.addActionListener(e->markChanged());page.addChangeListener(e->dirty());
  editButton.addActionListener(e->{if(selected==null||busy||!save())return;try{var form=new PhysicalBookForm(this,library,this::filter);form.edit(selected);form.setVisible(true);}catch(Exception ex){error(ex);}});
  deleteButton.addActionListener(e->deleteSelected());setCover.addActionListener(e->selectCover());
  newPhysical.addActionListener(e->{if(save())new PhysicalBookForm(this,library,()->{category=LibraryCategories.PHYSICAL;loading=true;try{shelf.setSelectedIndex(0);search.setText("");}finally{loading=false;}filter();}).setVisible(true);});
  open.addActionListener(e->open());update.addActionListener(e->organize());move.addActionListener(e->move());
  arbitrary.addActionListener(e->{var chooser=new JFileChooser(library.root.toFile());if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION&&save()){var p=chooser.getSelectedFile().toPath();var b=all.stream().filter(x->library.file(x).equals(p)).findFirst().orElse(null);if(b!=null)read(b);else external(p);}});
  addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){if(save()){closed=true;resetPreview();searchDelay.stop();autoSave.stop();if(searchWorker!=null)searchWorker.cancel(false);dispose();}}});
  coverRefresh.start();reload();if(automaticStartup)SwingUtilities.invokeLater(()->worker(List.of(),false));
 }
 void refreshCounters(){collectionStat.setText(String.format(java.util.Locale.forLanguageTag("pt-BR"),"%,d",all.size()));readingStat.setText(String.valueOf(all.stream().filter(b->b.reading).count()));favoriteStat.setText(String.valueOf(all.stream().filter(b->b.favorite).count()));}
 static JPanel metric(String label,JLabel value){var panel=UiTheme.panel(new BorderLayout(0,5));value.setFont(value.getFont().deriveFont(Font.BOLD,23));panel.add(value,BorderLayout.NORTH);panel.add(UiTheme.eyebrow(label));return panel;}
 JMenuBar serviceMenu(){var bar=new JMenuBar();var menu=new JMenu("Catálogo local");var automatic=new JCheckBoxMenuItem("Iniciar servidor HTML no login",BackgroundCatalogService.automatic());automatic.addActionListener(e->{boolean enabled=automatic.isSelected();automatic.setEnabled(false);new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{BackgroundCatalogService.automatic(enabled);if(enabled)BackgroundCatalogService.ensureInstalled();else BackgroundCatalogService.disable();return null;}protected void done(){automatic.setEnabled(true);try{get();}catch(Exception ex){BackgroundCatalogService.automatic(!enabled);automatic.setSelected(!enabled);Throwable cause=ex.getCause()==null?ex:ex.getCause();showError(new Exception("Não foi possível atualizar a inicialização do servidor HTML: "+Objects.toString(cause.getMessage(),"erro desconhecido")));}}}.execute();});menu.add(automatic);bar.add(menu);return bar;}
 static DocumentListener listener(Runnable run){return new DocumentListener(){public void insertUpdate(DocumentEvent e){run.run();}public void removeUpdate(DocumentEvent e){run.run();}public void changedUpdate(DocumentEvent e){run.run();}};}
 static final class DetailsPanel extends JPanel implements Scrollable {
  DetailsPanel(){super(new BorderLayout(12,14));}
  @Override public Dimension getPreferredSize(){var size=super.getPreferredSize();return new Dimension(size.width,Math.max(560,size.height));}
  public Dimension getPreferredScrollableViewportSize(){return new Dimension(0,560);}
  public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 24;}
  public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(24,visible.height-48);}
  public boolean getScrollableTracksViewportWidth(){return true;}
  public boolean getScrollableTracksViewportHeight(){return getParent() instanceof JViewport viewport&&viewport.getHeight()>=getPreferredSize().height;}
 }
 record Category(String path){public String toString(){return LibraryCategories.label(path);}}
 void browseCategory(TreePath path){
  if(loading||path==null)return;
  var node=(DefaultMutableTreeNode)path.getLastPathComponent();
  category=node.getUserObject() instanceof Category c?c.path:"";
  loading=true;try{tree.setSelectionPath(path);shelf.setSelectedIndex(0);search.setText("");}finally{loading=false;}
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
   final int filterMode=mode;
   boolean matches=group.books().stream().anyMatch(b->snapshot.hits().contains(b.id)&&(searching||chosenCategory.isBlank()||b.section.equals(chosenCategory)||b.section.startsWith(chosenCategory+"/"))&&(filterMode==0||filterMode==1&&b.reading||filterMode==2&&b.favorite||filterMode==3&&!b.notes.isBlank()||filterMode==4&&(b.title==null||b.title.isBlank())));
   if(!matches)continue;var b=group.primary();visible.add(b);visibleGroups.add(group);
   String categories=String.join("; ",group.books().stream().map(x->x.section.replace('_',' ').replace("/"," / ")).distinct().toList());
   data.add(new Object[]{b.title==null||b.title.isBlank()?"Título não identificado":b.title,group.files(),b.meta("author"),categories});
  }
  selected=null;model.setDataVector(data.toArray(Object[][]::new),new String[]{"Livro","Arquivo / ISBN","Autor","Categoria/Subcategoria"});
  table.getColumnModel().getColumn(0).setPreferredWidth(480);table.getColumnModel().getColumn(1).setPreferredWidth(280);table.getColumnModel().getColumn(2).setPreferredWidth(145);table.getColumnModel().getColumn(3).setPreferredWidth(240);
  clear();loading=false;status.setText(visible.size()+" obras exibidas · "+all.size()+" livros na biblioteca"+(searching?" · busca"+(mode==0?" em todo o acervo":" · "+shelf.getItemAt(mode)):""));
  int row=0;if(chosenId!=null)for(int i=0;i<visibleGroups.size();i++)if(visibleGroups.get(i).books().stream().anyMatch(b->b.id.equals(chosenId))){row=i;break;}
  if(!visible.isEmpty()){table.setRowSelectionInterval(table.convertRowIndexToView(row),table.convertRowIndexToView(row));if(chosenId!=null){var book=visibleGroups.get(row).books().stream().filter(b->b.id.equals(chosenId)).findFirst().orElse(null);if(book!=null){loading=true;versions.setSelectedItem(book);loading=false;fill(book);}}}
  if(!readyDelivered){readyDelivered=true;onReady.run();}
 }
 String versionLabel(Biblioteca.Book book){if(book.physical())return "Livro físico"+(book.meta("isbn").isBlank()?"":" · ISBN "+book.meta("isbn"));boolean sample=visibleGroups.stream().anyMatch(g->g.samples().contains(book.id));return (sample?"Amostra / parcial · ":"")+book.format+" · "+book.filename;}
 void openGroupFolder(WorkGroups.Group group){
  var b=group.books().size()==1?group.books().get(0):(Biblioteca.Book)JOptionPane.showInputDialog(this,"Escolha o arquivo para abrir sua pasta:","Arquivos desta obra",JOptionPane.PLAIN_MESSAGE,null,group.books().toArray(),group.resume());
  if(b!=null&&!b.physical())openFolder.accept(library.file(b).getParent());
 }
 void clear(){resetPreview();loading=true;versions.removeAllItems();versions.setEnabled(false);title.setText("Escolha um livro");subtitle.setText("");subtitle.setToolTipText(null);subtitle.setVisible(false);notes.setText("");metadata.setText("");location.setText("");location.setEnabled(false);favorite.setSelected(false);reading.setSelected(false);page.setValue(1);open.setEnabled(false);move.setEnabled(false);saveChanges.setVisible(false);editButton.setEnabled(false);deleteButton.setEnabled(false);setCover.setEnabled(false);saved.setText(" ");loading=false;}
 void choose(){
  if(!save()){restoreSelection();return;}int row=table.getSelectedRow();if(row<0){selected=null;clear();return;}
  var group=visibleGroups.get(table.convertRowIndexToModel(row));loading=true;versions.removeAllItems();for(var b:group.books())versions.addItem(b);versions.setEnabled(group.books().size()>1);var book=group.resume();versions.setSelectedItem(book);loading=false;fill(book);
 }
 void restoreSelection(){if(selected==null)return;for(int i=0;i<visibleGroups.size();i++){final String id=selected.id;if(visibleGroups.get(i).books().stream().anyMatch(b->b.id.equals(id))){loading=true;table.setRowSelectionInterval(table.convertRowIndexToView(i),table.convertRowIndexToView(i));loading=false;return;}}}
 void fill(Biblioteca.Book book){selected=book;loading=true;title.setText(selected.title==null?"Título não identificado":selected.title);title.setToolTipText(title.getText());subtitle.setVisible(!book.physical());subtitle.setText(book.physical()?"":"Arquivo: "+library.file(book).toAbsolutePath().normalize());subtitle.setCaretPosition(0);subtitle.setToolTipText(book.physical()?null:library.file(book).toAbsolutePath().normalize().toString());versions.setToolTipText(selected.path);favorite.setSelected(selected.favorite);reading.setSelected(selected.reading);page.setValue(selected.page);notes.setText(selected.notes);notes.setCaretPosition(0);location.setEnabled(!selected.format.equalsIgnoreCase("PDF"));location.setText(selected.format.equalsIgnoreCase("PDF")?"":selected.location);locationLabel.setText(selected.physical()?"Local / estante":"Capítulo / local (EPUB)");location.setToolTipText(selected.physical()?"Local / estante do livro físico":"Local de leitura");try{var labels=Map.ofEntries(Map.entry("title","Título nos metadados"),Map.entry("subtitle","Subtítulo"),Map.entry("author","Autor"),Map.entry("publisher","Editora"),Map.entry("year","Ano"),Map.entry("isbn","ISBN"),Map.entry("barcode","Código de barras"),Map.entry("edition","Edição"),Map.entry("language","Idioma"),Map.entry("pages","Páginas"),Map.entry("keywords","Palavras-chave"),Map.entry("subject","Assunto"),Map.entry("source","Fonte"),Map.entry("supplement_source","Fonte complementar"),Map.entry("creator","Criado com"));var text=new StringBuilder();var fields=Biblioteca.JSON.readTree(selected.metadata).fields();while(fields.hasNext()){var f=fields.next();if(f.getKey().equals("cover_urls"))continue;String value=f.getValue().isValueNode()?f.getValue().asText():f.getValue().toString();if(!value.isBlank())text.append(labels.getOrDefault(f.getKey(),f.getKey())).append(": ").append(value).append("\n\n");}metadata.setText(text+(selected.physical()?"Acervo: Livros físicos":"Arquivo: "+selected.path));}catch(Exception e){metadata.setText(selected.metadata);}metadata.setCaretPosition(0);saved.setText("Salvo no catálogo");open.setEnabled(!book.physical()&&Files.isRegularFile(library.file(book)));open.setToolTipText(book.meta("_cloud_missing").equals("true")?"Cadastro recebido da nuvem; o arquivo precisa estar disponível neste computador.":"Abrir o arquivo local e continuar a leitura");open.setVisible(!book.physical());saveChanges.setVisible(book.physical());saveChanges.setEnabled(false);notes.putClientProperty("JTextArea.placeholderText",book.physical()?"Suas anotações. Clique em Salvar alterações para gravar.":"Suas anotações. O texto é salvo automaticamente.");move.setEnabled(!busy&&!book.physical()&&Files.isRegularFile(library.file(book)));editButton.setEnabled(!busy);deleteButton.setEnabled(!busy&&book.physical());setCover.setEnabled(!busy&&book.physical());loading=false;lastSaved=state();loadPreview(book);}
 void resetPreview(){previewSerial++;if(previewWorker!=null)previewWorker.cancel(false);cover.setIcon(null);cover.setText("Selecione um livro");summary.setText("Selecione um livro para ver a apresentação.");}
 void loadPreview(Biblioteca.Book book){loadPreview(book,true);}
 void loadPreview(Biblioteca.Book book,boolean fresh){
  if(fresh){resetPreview();cover.setText("Carregando capa…");summary.setText("Carregando apresentação…");}long serial=previewSerial;
  previewWorker=new SwingWorker<>(){
   protected BookPreview.Preview doInBackground()throws Exception{return BookPreview.stored(library,book.id);}
   protected void done(){if(isCancelled()||closed||serial!=previewSerial)return;try{var preview=get();if(fresh||cover.getIcon()==null)cover.setIcon(preview.image()==null?null:new ImageIcon(preview.image()));cover.setText(preview.image()==null?(book.physical()?"Capa em busca…":"Capa indisponível"):"");cover.setToolTipText(book.physical()&&preview.image()==null?"Busca enfileirada em segundo plano. Use Definir capa para selecionar uma imagem.":null);String presentation=book.physical()&&preview.summary().isBlank()?"Resumo em pesquisa…":preview.summary();if(fresh||!Objects.equals(summary.getText(),presentation)){summary.setText(presentation);summary.setCaretPosition(0);}}catch(Exception e){if(fresh){cover.setText("Capa indisponível");summary.setText("Não foi possível obter a apresentação deste arquivo.");}}}
  };previewWorker.execute();
 }
 void pollCover(){if(!closed&&isShowing()&&selected!=null&&selected.physical()&&(previewWorker==null||previewWorker.isDone()))loadPreview(selected,false);}
 void automaticCloudSync(){if(closed||cloudSyncBusy)return;try{int minutes=cloudSync.syncIntervalMinutes();if(minutes<=0||System.currentTimeMillis()-lastCloudSync<minutes*60000L)return;var info=cloudSync.connection();if(info==null||!save())return;cloudSyncBusy=true;lastCloudSync=System.currentTimeMillis();new SwingWorker<CloudSync.Result,Void>(){protected CloudSync.Result doInBackground()throws Exception{return cloudSync.sync(false,s->{});}protected void done(){cloudSyncBusy=false;try{var result=get();if(result.uploaded()+result.downloaded()>0){status.setText("Sincronizado com a nuvem · "+new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date()));filter();}}catch(Exception e){lastCloudSync=System.currentTimeMillis()-Math.max(0,minutes*60000L-60000L);Throwable cause=e;while(cause.getCause()!=null&&cause instanceof java.util.concurrent.ExecutionException)cause=cause.getCause();status.setText("Falha na sincronização automática: "+Objects.toString(cause.getMessage(),"serviço indisponível"));}}}.execute();}catch(Exception e){status.setText("Sincronização automática indisponível: "+e.getMessage());}}
 @Override public void dispose(){cloudSyncTimer.stop();coverRefresh.stop();if(ownedCoverQueue!=null)ownedCoverQueue.close();super.dispose();}
 boolean confirmDeletion(Biblioteca.Book book){return JOptionPane.showConfirmDialog(this,"Excluir o cadastro de "+book.title+"?\nAs marcações deste cadastro serão removidas."+(book.physical()?"":"\nO arquivo digital será preservado e ignorado nas próximas catalogagens."),"Excluir cadastro",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION;}
 void deleteSelected(){
  if(selected==null||!selected.physical()||busy||!save())return;var book=selected;if(!confirmDelete.test(book))return;
  busy=true;deleteButton.setEnabled(false);update.setEnabled(false);setCover.setEnabled(false);move.setEnabled(false);status.setText("Excluindo cadastro…");
  new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{new LibraryOrganizer(library).delete(book.id);return null;}protected void done(){try{get();selected=null;clear();filter();status.setText("Cadastro excluído.");}catch(Exception e){error(e);}finally{busy=false;update.setEnabled(true);deleteButton.setEnabled(selected!=null&&selected.physical());setCover.setEnabled(selected!=null&&selected.physical());move.setEnabled(selected!=null&&!selected.physical()&&Files.isRegularFile(library.file(selected)));}}}.execute();
 }
 void selectCover(){if(selected==null||!selected.physical()||busy)return;try{var form=new PhysicalBookForm(this,library,this::filter);form.edit(selected);SwingUtilities.invokeLater(form::selectCover);form.setVisible(true);}catch(Exception e){error(e);}}
 String state(){try{return Biblioteca.JSON.writeValueAsString(List.of(favorite.isSelected(),reading.isSelected(),page.getValue(),location.getText(),notes.getText()));}catch(Exception e){throw new IllegalStateException(e);}}
 void markChanged(){if(loading||selected==null)return;formVersion++;if(save(true))filter();}
 void dirty(){if(!loading&&selected!=null){formVersion++;if(selected.physical()){autoSave.stop();saveChanges.setEnabled(true);saved.setText("Alterações não salvas");}else{saved.setText("Salvando…");autoSave.restart();}}}
 boolean save(){return save(false);}
 boolean save(boolean explicit){if(selected==null||loading)return true;if(selected.physical()&&!explicit)return true;try{page.commitEdit();if(state().equals(lastSaved))return true;autoSave.stop();selected.favorite=favorite.isSelected();selected.reading=reading.isSelected();selected.page=(Integer)page.getValue();selected.notes=notes.getText();if(!selected.format.equalsIgnoreCase("PDF"))selected.location=location.getText();library.save(selected);refreshCounters();lastSaved=state();saveChanges.setEnabled(false);saved.setText("Salvo no catálogo");if(pendingRefresh){pendingRefresh=false;SwingUtilities.invokeLater(this::filter);}return true;}catch(Exception e){saved.setText("Falha ao salvar");error(e);return false;}}
 void open(){if(selected==null||selected.physical())return;if(!Files.isRegularFile(library.file(selected))){JOptionPane.showMessageDialog(this,"O cadastro está disponível, mas o arquivo digital não existe neste computador.");return;}if(save())read(selected);}
 void read(Biblioteca.Book b){if(b.physical()||!save())return;try{if(b.format.equalsIgnoreCase("PDF")){new PdfReader(this,library,b,()->{if(selected!=null&&selected.id.equals(b.id)){updateReaderProgress(b);}}).setVisible(true);}else{b.reading=true;library.save(b);external(library.file(b));if(!b.location.isBlank())JOptionPane.showMessageDialog(this,"Retome no local salvo: "+b.location);}}catch(Exception e){error(e);}}
 void updateReaderProgress(Biblioteca.Book book){
  loading=true;selected.page=book.page;selected.reading=book.reading;selected.original.put("page",book.page);selected.original.put("reading",book.reading?1:0);page.setValue(book.page);reading.setSelected(book.reading);refreshCounters();loading=false;
  // Atualiza só o progresso na referência de salvamento; notas ainda em edição continuam pendentes.
  try{var previous=(com.fasterxml.jackson.databind.node.ArrayNode)Biblioteca.JSON.readTree(lastSaved);previous.set(1,Biblioteca.JSON.getNodeFactory().booleanNode(book.reading));previous.set(2,Biblioteca.JSON.getNodeFactory().numberNode(book.page));lastSaved=previous.toString();}catch(Exception ignored){}
 }
 void external(Path path){try{Desktop.getDesktop().open(path.toFile());}catch(Exception e){error(e);}}
 void move(){if(selected==null||selected.physical()||!Files.isRegularFile(library.file(selected))||!save())return;try{String target=CategoryPicker.choose(this,library,selected.section);if(target!=null&&!target.equals(selected.section)){try(var c=library.connect()){LibraryCategories.add(c,target);}worker(List.of("move",selected.path,target));}}catch(Exception e){error(e);}}
 void organize(){if(save())worker(List.of());}
 void worker(List<String> args){worker(args,true);}
 boolean finishCatalogUpdate(){if(!save()){pendingRefresh=true;status.setText("Catálogo atualizado; suas anotações precisam ser salvas antes de recarregar.");return false;}filter();return true;}
 void worker(List<String> args,boolean showSummary){if(busy||closed)return;busy=true;update.setEnabled(false);move.setEnabled(false);deleteButton.setEnabled(false);setCover.setEnabled(false);progress.setVisible(true);progress.setValue(0);status.setText("Procurando livros soltos e atualizando metadados…");
  new SwingWorker<String,String>(){
   protected String doInBackground()throws Exception{
    var organizer=new LibraryOrganizer(library);LibraryOrganizer.Progress callback=(p,m)->{try{publish(Biblioteca.JSON.writeValueAsString(Map.of("progress",p,"message",m)));}catch(Exception e){throw new IllegalStateException(e);}};
    if(args.isEmpty())return Biblioteca.JSON.writeValueAsString(Map.of("result",organizer.organize(callback)));
    if(args.size()==3&&args.get(0).equals("move"))return Biblioteca.JSON.writeValueAsString(Map.of("moved",organizer.move(args.get(1),args.get(2),callback)));
    throw new IllegalArgumentException("Operação inválida");
   }
   protected void process(List<String> lines){for(var line:lines)try{var j=Biblioteca.JSON.readTree(line);if(j.has("progress"))progress.setValue(Math.max(progress.getValue(),Math.min(97,j.path("progress").asInt())));if(j.has("message"))status.setText(j.path("message").asText());}catch(Exception ignored){}}
   protected void done(){try{String result=get();if(closed)return;if(!finishCatalogUpdate())return;progress.setValue(100);status.setText("Catálogo e índice atualizados");var j=Biblioteca.JSON.readTree(result);if(showSummary&&j.has("result")){var r=j.path("result");JOptionPane.showMessageDialog(CatalogoApp.this,"Movidos: "+r.path("moved").asInt()+"\nDuplicados separados para revisão: "+r.path("duplicates").asInt()+"\nA classificar: "+r.path("unclassified").asInt(),"Atualização concluída",JOptionPane.INFORMATION_MESSAGE);}}catch(Exception e){error(e);}finally{busy=false;update.setEnabled(true);move.setEnabled(selected!=null&&!selected.physical()&&Files.isRegularFile(library.file(selected)));deleteButton.setEnabled(selected!=null&&selected.physical());setCover.setEnabled(selected!=null&&selected.physical());progress.setVisible(false);}}
  }.execute();
 }
 void chooseLibrary(){if(busy)return;try{var profile=LibraryPicker.choose(this,new LibrarySettings.Profile(library.root,library.database.getFileName().toString()));if(profile!=null)switchTo(profile);}catch(Exception e){error(e);}}
 void switchTo(LibrarySettings.Profile profile){
  if(busy||closed||profile.file().equals(library.database)||!save())return;busy=true;switchLibrary.setEnabled(false);status.setText("Abrindo biblioteca: "+profile.directory()+" · "+profile.database());
  new SwingWorker<Biblioteca,Void>(){protected Biblioteca doInBackground()throws Exception{var next=profile.open();LibrarySettings.activate(profile);return next;}protected void done(){try{var next=new CatalogoApp(get());next.ownedCoverQueue=CoverQueue.start(next.library);next.onReady=()->{next.setVisible(true);closed=true;searchDelay.stop();autoSave.stop();if(searchWorker!=null)searchWorker.cancel(false);dispose();};next.reportError=e->{next.setVisible(true);next.showError(e);};}catch(Exception e){busy=false;switchLibrary.setEnabled(true);error(e);}}}.execute();
 }
 void error(Exception e){reportError.accept(e);}
 void showError(Exception e){JOptionPane.showMessageDialog(this,Objects.toString(e.getMessage(),e.toString()),"Não foi possível concluir",JOptionPane.ERROR_MESSAGE);}
 public static void main(String[] args)throws Exception {
  var explicit=LibrarySettings.explicit(args);var active=explicit!=null?explicit:LibrarySettings.read().active();
  if(Arrays.asList(args).contains("--serve")){var server=new LibraryServer(HtmlCatalog.configuration());Runtime.getRuntime().addShutdownHook(new Thread(server::close));while(true){try{server.select(explicit!=null?explicit:LibrarySettings.read().active());}catch(Exception e){System.err.println("Biblioteca do serviço: "+e.getMessage());}Thread.sleep(1000);}}
  if(Arrays.asList(args).contains("--update")){var library=(active!=null?active:LibrarySettings.legacy()).open();System.out.println(Biblioteca.JSON.writeValueAsString(new LibraryOrganizer(library).organize((p,m)->System.err.println(p+"% "+m))));return;}
  if(Arrays.asList(args).contains("--verify-ocr")){var image=new java.awt.image.BufferedImage(1200,250,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,1200,250);g.setColor(Color.BLACK);g.setFont(new Font("SansSerif",Font.BOLD,64));g.drawString("Kotlin Essentials",60,140);g.dispose();try(var ocr=new NativeOcr()){String text=String.join(" ",ocr.lines(image).stream().map(DocumentExtractor.Line::text).toList());if(!text.toLowerCase().contains("kotlin"))throw new IllegalStateException("OCR não reconheceu o texto de teste");System.out.println("OCR incluído: OK (eng+por)");}return;}
  UiTheme.install();
  if(Arrays.asList(args).contains("--verify")){var library=(active!=null?active:LibrarySettings.legacy()).open();var list=library.books();System.out.println("SQLite OK: "+list.size()+" documentos; "+list.stream().filter(b->b.reading).count()+" em leitura; "+list.stream().filter(b->b.favorite).count()+" favoritos.");return;}
  SwingUtilities.invokeLater(()->{
   final LibrarySettings.Profile profile;try{profile=active!=null?active:LibraryPicker.choose(null,null);if(profile==null)return;}catch(Exception e){JOptionPane.showMessageDialog(null,e.getMessage(),"Biblioteca",JOptionPane.ERROR_MESSAGE);return;}
   var splash=new StartupSplash();splash.setVisible(true);splash.stage("Abrindo catálogo e índice de pesquisa…",20);
   String[] serviceWarning={null};
   new SwingWorker<Biblioteca,Void>(){
    protected Biblioteca doInBackground()throws Exception{var library=profile.open();if(explicit==null)LibrarySettings.activate(profile);try{BackgroundCatalogService.ensureInstalled();}catch(Exception e){serviceWarning[0]=e.getMessage();System.err.println("Servidor HTML em segundo plano: "+e.getMessage());}return library;}
    protected void done(){try{
     splash.stage("Preparando suas leituras e os formatos disponíveis…",70);var loaded=get();var queue=CoverQueue.start(loaded);Runtime.getRuntime().addShutdownHook(new Thread(queue::close));var app=new CatalogoApp(loaded);app.ownedCoverQueue=queue;
     app.onReady=()->{splash.stage("Biblioteca pronta",100);app.setVisible(true);if(serviceWarning[0]!=null)app.status.setText("Servidor HTML não foi iniciado automaticamente: "+serviceWarning[0]);splash.dispose();};
     app.reportError=e->{splash.dispose();app.setVisible(true);app.showError(e);};
    }catch(Exception e){splash.dispose();JOptionPane.showMessageDialog(null,"Não foi possível abrir a biblioteca: "+e.getMessage(),"Catálogo de Livros",JOptionPane.ERROR_MESSAGE);}}
   }.execute();
  });
 }
}

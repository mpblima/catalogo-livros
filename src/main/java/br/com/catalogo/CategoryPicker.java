package br.com.catalogo;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.util.*;
final class CategoryPicker {
 static String choose(Component owner,Biblioteca library,String current){
  String collection=LibraryCategories.physical(current)?LibraryCategories.PHYSICAL:LibraryCategories.DIGITAL;var root=new DefaultMutableTreeNode(collection);var nodes=new LinkedHashMap<String,DefaultMutableTreeNode>();nodes.put(collection,root);
  try{for(String path:library.categories())if((path.startsWith(collection+"/"))){var node=new DefaultMutableTreeNode(path);nodes.put(path,node);nodes.getOrDefault(path.substring(0,path.lastIndexOf('/')),root).add(node);}}catch(Exception e){JOptionPane.showMessageDialog(owner,e.getMessage());return null;}
  var tree=new JTree(root);tree.setCellRenderer(new DefaultTreeCellRenderer(){public Component getTreeCellRendererComponent(JTree t,Object v,boolean s,boolean x,boolean leaf,int row,boolean focus){super.getTreeCellRendererComponent(t,v,s,x,leaf,row,focus);setText(LibraryCategories.label(((DefaultMutableTreeNode)v).getUserObject().toString()));return this;}});
  tree.setSelectionPath(new TreePath(nodes.getOrDefault(current,root).getPath()));var panel=new JPanel(new BorderLayout());var scroll=new JScrollPane(tree);scroll.setPreferredSize(new Dimension(460,380));panel.add(scroll);var add=new JButton("Nova categoria / subcategoria…");panel.add(add,BorderLayout.SOUTH);add.addActionListener(e->{var parent=(DefaultMutableTreeNode)tree.getLastSelectedPathComponent();if(parent==null)return;String name=JOptionPane.showInputDialog(owner,"Nome da categoria dentro de "+LibraryCategories.label(parent.toString())+":");if(name==null||name.isBlank())return;name=name.strip();if(name.contains("/")||name.contains("\\")||name.equals(".")||name.equals("..")){JOptionPane.showMessageDialog(owner,"Informe um nome sem barras.");return;}String path=parent+"/"+name;if(nodes.containsKey(path)){tree.setSelectionPath(new TreePath(nodes.get(path).getPath()));return;}var node=new DefaultMutableTreeNode(path);nodes.put(path,node);((DefaultTreeModel)tree.getModel()).insertNodeInto(node,parent,parent.getChildCount());tree.setSelectionPath(new TreePath(node.getPath()));});
  if(JOptionPane.showConfirmDialog(owner,panel,"Categorias · "+LibraryCategories.label(collection),JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return null;var selected=(DefaultMutableTreeNode)tree.getLastSelectedPathComponent();return selected==null?null:selected.toString();
 }
}

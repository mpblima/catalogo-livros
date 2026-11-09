package br.com.catalogo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Objects;

/** Formulário modal para registrar empréstimos e cópias digitais enviadas. */
final class LoanRegisterDialog extends JDialog {
 record Recipient(String name,String email){}
 private final JTextField name=new JTextField(30),email=new JTextField(30);
 private final JLabel validation=new JLabel(" ");
 private Recipient recipient;

 private LoanRegisterDialog(CatalogoApp owner,Biblioteca.Book book){
  super(owner,book.physical()?"Registrar empréstimo":"Registrar cópia digital",Dialog.ModalityType.APPLICATION_MODAL);
  setDefaultCloseOperation(DISPOSE_ON_CLOSE);setResizable(false);
  var root=new JPanel(new BorderLayout());root.setBackground(UiTheme.BACKGROUND);root.setBorder(new EmptyBorder(22,24,18,24));setContentPane(root);

  var content=new JPanel();content.setOpaque(false);content.setLayout(new BoxLayout(content,BoxLayout.Y_AXIS));
  var heading=UiTheme.panel(new BorderLayout(16,0));heading.setAlignmentX(Component.LEFT_ALIGNMENT);
  var mark=new JLabel(book.physical()?"↔":"↗",SwingConstants.CENTER);mark.setOpaque(true);mark.setBackground(new Color(245,196,179));mark.setForeground(UiTheme.ACCENT);mark.setFont(mark.getFont().deriveFont(Font.BOLD,25f));mark.setPreferredSize(new Dimension(54,54));
  var intro=UiTheme.panel(new GridLayout(0,1,0,4));intro.add(UiTheme.eyebrow(book.physical()?"LIVRO FÍSICO":"LIVRO DIGITAL"));var headingText=new JLabel(book.physical()?"Registrar empréstimo":"Registrar envio da cópia");headingText.setFont(headingText.getFont().deriveFont(Font.BOLD,22f));intro.add(headingText);heading.add(mark,BorderLayout.WEST);heading.add(intro,BorderLayout.CENTER);content.add(heading);
  content.add(Box.createVerticalStrut(19));

  var bookCard=new JPanel(new BorderLayout(13,0));bookCard.setBackground(UiTheme.SURFACE);bookCard.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.LINE),new EmptyBorder(14,15,14,15)));bookCard.setAlignmentX(Component.LEFT_ALIGNMENT);
  var icon=new JLabel(new UiTheme.BookIcon(34));bookCard.add(icon,BorderLayout.WEST);
  var bookInfo=UiTheme.panel(new GridLayout(0,1,0,4));bookInfo.add(UiTheme.eyebrow("LIVRO SELECIONADO"));var bookTitle=new JTextArea(Objects.toString(book.title,"Título não identificado"));bookTitle.setFont(bookTitle.getFont().deriveFont(Font.BOLD,15f));bookTitle.setForeground(UiTheme.TEXT);bookTitle.setOpaque(false);bookTitle.setEditable(false);bookTitle.setFocusable(false);bookTitle.setLineWrap(true);bookTitle.setWrapStyleWord(true);bookTitle.setRows(2);bookInfo.add(bookTitle);String author=book.meta("author");bookInfo.add(new JLabel(author.isBlank()?Objects.toString(book.format,""):author));bookCard.add(bookInfo,BorderLayout.CENTER);content.add(bookCard);
  content.add(Box.createVerticalStrut(18));var section=UiTheme.eyebrow("QUEM VAI RECEBER");section.setAlignmentX(Component.LEFT_ALIGNMENT);content.add(section);content.add(Box.createVerticalStrut(10));
  addField(content,"Nome completo",name,"Ex.: Marina Oliveira");content.add(Box.createVerticalStrut(12));addField(content,"E-mail",email,"marina@exemplo.com");
  content.add(Box.createVerticalStrut(14));var explanation=new JTextArea(book.physical()?"O livro ficará marcado como emprestado até você registrar a devolução.":"A cópia digital será registrada como enviada. Não é necessário acompanhar devolução.");explanation.setFont(explanation.getFont().deriveFont(12f));explanation.setForeground(UiTheme.MUTED);explanation.setOpaque(false);explanation.setEditable(false);explanation.setFocusable(false);explanation.setLineWrap(true);explanation.setWrapStyleWord(true);explanation.setBorder(new EmptyBorder(0,2,0,2));explanation.setAlignmentX(Component.LEFT_ALIGNMENT);content.add(explanation);
  root.add(content,BorderLayout.CENTER);

  validation.setForeground(new Color(153,60,29));validation.setFont(validation.getFont().deriveFont(12f));var footer=new JPanel(new BorderLayout(10,0));footer.setOpaque(false);footer.setBorder(new EmptyBorder(18,0,0,0));footer.add(validation,BorderLayout.CENTER);var actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));actions.setOpaque(false);var cancel=new JButton("Cancelar");var submit=new JButton(book.physical()?"Registrar empréstimo":"Registrar cópia enviada");UiTheme.primary(submit);actions.add(cancel);actions.add(submit);footer.add(actions,BorderLayout.EAST);root.add(footer,BorderLayout.SOUTH);
  cancel.addActionListener(e->dispose());submit.addActionListener(e->save());getRootPane().setDefaultButton(submit);getRootPane().registerKeyboardAction(e->dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
  name.addActionListener(e->email.requestFocusInWindow());email.addActionListener(e->save());pack();setSize(Math.max(getWidth(),520),getHeight());setLocationRelativeTo(owner);
 }
 private static void addField(JPanel parent,String label,JTextField field,String placeholder){
  var group=UiTheme.panel(new BorderLayout(0,6));group.setAlignmentX(Component.LEFT_ALIGNMENT);var caption=new JLabel(label);caption.setFont(caption.getFont().deriveFont(Font.BOLD,12f));group.add(caption,BorderLayout.NORTH);field.putClientProperty("JTextField.placeholderText",placeholder);field.setPreferredSize(new Dimension(440,43));group.add(field,BorderLayout.CENTER);parent.add(group);
 }
 private void save(){
  String recipientName=name.getText().strip(),recipientEmail=email.getText().strip();
  if(recipientName.isBlank()){validation.setText("Informe o nome de quem vai receber o livro.");name.requestFocusInWindow();return;}
  if(!recipientEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")){validation.setText("Confira o endereço de e-mail informado.");email.requestFocusInWindow();return;}
  recipient=new Recipient(recipientName,recipientEmail);dispose();
 }
 Recipient showDialog(){setVisible(true);return recipient;}
 static Recipient show(CatalogoApp owner,Biblioteca.Book book){return new LoanRegisterDialog(owner,book).showDialog();}
}

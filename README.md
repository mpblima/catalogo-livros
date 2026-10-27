# Catálogo de Livros

Aplicação Java 17/Swing para uma biblioteca pessoal de PDFs e EPUBs, com tema escuro, splash screen e banco SQLite. Interface, organização, extração de metadados, pesquisa, servidor local e catálogo HTML são executados pelo Java. Não depende de Python nem da pasta do código-fonte.

## Instalar e abrir

Baixe o arquivo `catalogo-livros-VERSAO-linux-x86_64.run` na página de Releases e execute:

```bash
chmod +x catalogo-livros-*-linux-x86_64.run
./catalogo-livros-VERSAO-linux-x86_64.run
```

O instalador pessoal inclui o Java, instala em `~/Apps/Catalogo`, cria um atalho no Desktop e ativa um serviço local para o catálogo HTML. A biblioteca padrão é `~/Desktop/Livros`; o SQLite fica dentro dela. Livros, favoritos, páginas e anotações são mantidos ao atualizar. A instalação anterior fica em uma pasta de backup ao lado de `Catalogo`. Feche as janelas da versão anterior antes de reabrir pelo atalho.

Também são distribuídos um `.deb` para instalação no sistema e um `.tar.gz` com a aplicação portátil. O `.run` é a opção recomendada para o diretório pessoal; o `.deb` oferece lançamento pelo menu do sistema. Linux x86_64; as releases de CI são compiladas no Ubuntu 24.04. As versões Windows x64 e macOS ARM64 também incluem Tesseract, Leptonica, os modelos português/inglês e as bibliotecas nativas necessárias; OCR e catalogação de PDFs digitalizados funcionam nas três plataformas. Na primeira abertura após instalar Windows ou macOS, o aplicativo registra o serviço do catálogo HTML para iniciar no login, sem privilégios administrativos. O menu **Catálogo local** permite desligar e reativar essa inicialização.

## Usar

- **Livro:** título extraído do conteúdo, com o nome do arquivo entre parênteses. Autor, editora e categoria/subcategoria vêm dos metadados.
- **Pesquisar:** palavras ou partes de palavras, combinadas em título, arquivo, categoria, autor, editora, outros metadados e anotações. O índice SQLite FTS5 é atualizado automaticamente.
- **Arquivo:** clique para abrir a pasta onde ele está. Formatos e amostras da mesma obra ficam agrupados, preservando as anotações por arquivo.
- **Abrir e continuar:** PDF em janela independente, redimensionável e maximizável, com ajuste à largura e página salva automaticamente. EPUB abre no leitor padrão; o campo de localização registra onde continuar.
- **Favorito / Em leitura:** mantém as escolhas no SQLite. A tela permite retomar as leituras.
- **Anotações:** salvamento automático; alterações de outra janela são protegidas contra sobrescrita silenciosa.
- **Acervos:** a árvore tem as categorias principais Livros digitais e Livros físicos. O acervo existente e seus assuntos ficam em Livros digitais; Livros físicos recebe os cadastros manuais ou por código de barras. Essa hierarquia é mantida no banco, preservando os caminhos dos arquivos e as marcações. A catalogação automática atua somente no acervo digital.
- **Novo livro físico:** formulário com código de barras opcional e foco inicial para leitores USB/Bluetooth em modo teclado. Um ISBN válido inicia a consulta automática (Enter também funciona); Open Library e, como alternativa, Google Books fornecem os dados disponíveis e os links de capa. O livro é salvo numa categoria física criada pelo classificador, a confirmação aparece e o formulário limpa e retorna ao código. A tela agrupa os dados em duas colunas, com capa e localização na lateral e aba de resumo/anotações. Sem consulta disponível, é possível cadastrar manualmente, inclusive capa, resumo, localização, anotações e estado de leitura. ISBN/código repetido não cria outro registro físico. A busca geral encontra os dois acervos. A consulta requer internet; cadastro manual e pesquisa local funcionam sem ela.
- **Capas, informações e resumos em segundo plano:** o cadastro enfileira a busca da imagem e o preenchimento dos dados bibliográficos e do resumo ausentes sem bloquear os próximos livros. A fila fica no SQLite, permite uma pesquisa por vez por biblioteca, usa Google Books (incluindo Dynamic Links), Open Library e fontes alternativas por ISBN e agenda novas tentativas após falhas. Reabre com a aplicação ou continua no serviço local. Capas existentes são preservadas; a prévia atualiza sozinha quando a imagem chega. Definir capa permite selecionar uma imagem manualmente. O diálogo de cadastro tem Fechar, inclusive durante a consulta de metadados.
- **Excluir cadastro:** exige confirmação para o livro selecionado e remove suas marcações, prévia e buscas pendentes. Para digitais, mantém o arquivo no disco e registra a exclusão para que o próximo catálogo não o adicione novamente no mesmo caminho.
- **Catalogar livros digitais:** procura documentos soltos na raiz de Livros, extrai título e metadados, cria categorias automaticamente, renomeia em snake_case e move para o assunto correspondente. Atualiza agrupamentos, SQLite, índice e HTML, com barra de progresso. Também ocorre em segundo plano ao iniciar.
- **Capa e apresentação:** ao selecionar um livro, exibe uma miniatura da primeira página do PDF ou da capa declarada do EPUB, com a descrição do documento ao lado. Na ausência de descrição, o PDF mostra o texto inicial, sem prefixo. Capas e textos ficam no SQLite; a catalogação automática e o botão Catalogar livros digitais preenchem apenas os dados ausentes, preservando os existentes. Documentos sem capa ou texto disponíveis são indicados na interface.

Arquivos sem identificação suficiente vão para `a_classificar`. PDFs inválidos são informados e mantidos. Cópias byte a byte idênticas são separadas em `organizacao/duplicados` para revisão; versões diferentes, EPUBs e amostras são preservados. IDs, aliases de caminhos e diário de operações mantêm a continuidade das marcações. Movimentações verificam SHA-256 e revertem o arquivo se a transação do banco falhar. Livros em outras pastas, incluindo material original do MBA, não são varridos automaticamente.

O HTML é gerado ao lado da pasta Livros. Seu serviço aceita conexões somente em `127.0.0.1`, com token local para alterações. Configuração em `~/.local/share/catalogo_livros/config.json`; nunca deve ser versionada.

## Código e compilação

```
src/main/java/br/com/catalogo/    aplicação Java
src/main/resources/catalogo/    regras, títulos revisados, HTML e recursos
src/test/java/br/com/catalogo/   testes Java e Swing
packaging/                      montagem do OCR e instalação
.github/workflows/release.yml   testes, instaladores e releases
```

Requisitos de compilação Linux: JDK 17, Maven, `jpackage`, `fakeroot`, bibliotecas Tesseract/Leptonica/OpenMP e modelos OCR português e inglês. No Ubuntu:

```bash
sudo apt-get install libtesseract5 libleptonica-dev libgomp1 tesseract-ocr-eng tesseract-ocr-por xvfb fakeroot
./packaging/preparar_ocr.sh
xvfb-run -a mvn -Dcatalogo.guiTest=true verify
java -jar target/catalogo-livros.jar --verify-ocr
./empacotar_java.sh 2.0.0
```

O workflow compila Tesseract e Leptonica para Windows x64 e macOS ARM64 usando vcpkg nos runners nativos, reúne as dependências e avisos de licença e inclui esses recursos no instalador. Usuários não precisam instalar Tesseract, Maven ou o código-fonte. Os instaladores nativos também incluem um inicializador separado para o serviço HTML, registrado por usuário no primeiro uso.

Comandos opcionais da aplicação:

```bash
~/Apps/Catalogo/catalogo --verify
~/Apps/Catalogo/catalogo --update
~/Apps/Catalogo/catalogo --serve --no-browser
~/Apps/Catalogo/catalogo --library /caminho/para/Livros
```

## Releases automáticas

A branch principal é `master`. Todo push/merge nela executa testes Java e Swing, valida OCR, gera instaladores `.run`, `.deb`, `.tar.gz`, checksums SHA-256 e publica uma release `v2.0.NUMERO_DA_EXECUCAO`. Pull requests executam os testes sem publicar. O workflow também pode ser acionado manualmente. O repositório contém o código e recursos necessários para reconstruir a aplicação; livros, banco pessoal, credenciais e binários gerados ficam fora do Git.

## Componentes de terceiros

PDFBox (Apache 2.0), Jackson (Apache 2.0), SQLite JDBC (Apache 2.0), FlatLaf (Apache 2.0), JNA (Apache 2.0/LGPL), OpenJDK (GPLv2 com Classpath Exception), [Tesseract](https://github.com/tesseract-ocr/tesseract/blob/main/LICENSE) e [modelos de idioma](https://github.com/tesseract-ocr/tessdata_fast/blob/main/LICENSE) (Apache 2.0), [Leptonica](https://github.com/DanBloomberg/leptonica/blob/master/leptonica-license.txt) (BSD) e GNU OpenMP (GPLv3 com GCC Runtime Library Exception). Os avisos do OCR acompanham os recursos e os avisos Java são mantidos nos artefatos.

### Edição e cadastro físico

Use **Editar informações…** no livro selecionado para revisar os dados bibliográficos,
resumo, capa, anotações e progresso. Metadados adicionais ficam disponíveis na aba
**Outros metadados (JSON)**. Alterações são gravadas no catálogo; os arquivos digitais
não são reescritos. Categorias digitais acompanham a organização dos arquivos; **Alterar categoria**
permite personalizá-las na árvore, movendo e conferindo o arquivo.
Todos os novos livros recebem classificação automática. Nos físicos, a categoria
é sugerida após a consulta por ISBN e calculada também no cadastro manual. Você
pode substituir a sugestão na árvore; a escolha vale só para aquele livro.
Para livros físicos, personalize a categoria na árvore; **Nova categoria / subcategoria…**
cria um nível dentro do nó selecionado, persistido ao salvar o livro.

A leitura do ISBN preenche o formulário sem gravar. **Salvar alterações** é a
única ação que cadastra ou atualiza o livro físico. Após salvar, o formulário
permanece no mesmo livro; salvar novamente atualiza esse cadastro.
**Cadastrar novo livro** limpa os campos e descarta alterações pendentes, sem
gravação, inclusive cancelando uma consulta em andamento. Não há cadastro
automático. Anotações e progresso dos físicos na tela principal também exigem
**Salvar alterações**.
A consulta por ISBN busca automaticamente os dados e a capa, tentando fontes
alternativas quando necessário, incluindo páginas de livrarias com o ISBN da
edição confirmado. Durante a consulta, uma barra animada mostra a etapa em
andamento até concluir a busca da capa. **Trocar capa…** permite substituir uma capa que
não agrade. A busca manual no Google e **Colar URL da capa…** ficam como último
recurso, quando nenhuma capa é encontrada ou você decide trocá-la. A imagem é
baixada e armazenada no SQLite ao salvar.

O pipeline gera instaladores Linux (`.run`, `.deb` e arquivo portátil), Windows
(`.msi`, x64) e macOS (`.dmg`, ARM64), com Java incluído. A publicação aguarda os
três jobs; os dois runners nativos preparam as bibliotecas OCR próprias da
plataforma antes de empacotar o aplicativo.

## Página do projeto

A página de apresentação e downloads fica em https://mpblima.github.io/catalogo-livros/.
O site está em `docs/`, com layout responsivo e sem dependências de frontend.
`python3 scripts/build_pages.py` gera `_site/` consultando as releases com o GitHub
CLI autenticado. O contador soma downloads dos instaladores e pacotes portáteis
nas releases estáveis; checksums, drafts e pré-releases ficam de fora. Cada
plataforma aponta para a versão estável mais recente que contém seu instalador.

O workflow `pages.yml` publica o site em alterações da página, conclusão da release,
eventos de release, execução manual e a cada seis horas para atualizar contadores.
Nenhum token de acesso é incluído nos arquivos publicados.


## Bibliotecas e bancos independentes

Na primeira abertura de uma instalação limpa, escolha o diretório dos livros
digitais e o nome do arquivo SQLite (`.sqlite`, `.sqlite3` ou `.db`). Use
**Escolher banco existente…** para reabrir uma biblioteca após reinstalar. Se o
arquivo não existir, o aplicativo cria a pasta, o banco e a estrutura do catálogo.
O banco fica dentro do diretório escolhido para a biblioteca.

O botão **Biblioteca / banco…** permite alternar entre pastas e bancos, inclusive
entre bancos diferentes na mesma pasta. As escolhas ficam no histórico e a última
biblioteca é restaurada ao abrir. O serviço HTML acompanha a seleção. A biblioteca
antiga em `~/Desktop/Livros/catalogo.sqlite` é reconhecida automaticamente, sem
mover seus arquivos. Mantenha livros e banco juntos no backup.

Também é possível abrir uma combinação específica sem alterar a seleção salva:

```bash
~/Apps/Catalogo/catalogo --library /caminho/dos/livros --database minha-biblioteca.sqlite
```

## Conta e sincronização com o catálogo web

O botão **Conta / sincronização…** conecta o perfil SQLite aberto a um catálogo próprio na web. A autorização ocorre no navegador, com um código temporário; nenhuma senha é solicitada pelo desktop. Use **Sincronizar catálogo** para enviar e receber livros físicos e digitais, seus campos bibliográficos e metadados extras, capas, categorias, relações entre edições/amostras, favoritos e anotações com autoria. A conexão pode ser revogada na web ou no aplicativo.

Os arquivos PDF/EPUB e o estado privado do leitor (página/posição) permanecem neste computador. Um cadastro recebido da nuvem sem arquivo local continua visível, mas não pode abrir ou movimentar um arquivo remoto. A sincronização preserva os arquivos existentes e nunca os exclui. Exclusões de cadastros físicos locais só são propagadas com confirmação; exclusões remotas desativam o cadastro local, preservando os arquivos.

Alterações concorrentes aparecem na aba **Conflitos** para decisão explícita. Lotes interrompidos são retomados sem duplicar cadastros/anotações. As credenciais do dispositivo ficam fora do SQLite, em arquivo privado por perfil, e expiram em até 30 dias. Conectar não inicia um envio automaticamente. Antes da primeira sincronização, confira a conta e o catálogo escolhidos.

O protocolo real Java/Go é validado no CI do repositório web contra PostgreSQL descartável, além dos testes locais Java e Swing. Não use uma base pessoal nos testes de integração.

# Catálogo de Livros

Aplicação Java 17/Swing para uma biblioteca pessoal de PDFs e EPUBs, com tema escuro, splash screen e banco SQLite. Interface, organização, extração de metadados, pesquisa, servidor local e catálogo HTML são executados pelo Java. Não depende de Python nem da pasta do código-fonte.

## Instalar e abrir

Baixe o arquivo `catalogo-livros-VERSAO-linux-x86_64.run` na página de Releases e execute:

```bash
chmod +x catalogo-livros-*-linux-x86_64.run
./catalogo-livros-VERSAO-linux-x86_64.run
```

O instalador pessoal inclui o Java, instala em `~/Apps/Catalogo`, cria um atalho no Desktop e ativa um serviço local para o catálogo HTML. A biblioteca padrão é `~/Desktop/Livros`; o SQLite fica dentro dela. Livros, favoritos, páginas e anotações são mantidos ao atualizar. A instalação anterior fica em uma pasta de backup ao lado de `Catalogo`. Feche as janelas da versão anterior antes de reabrir pelo atalho.

Também são distribuídos um `.deb` para instalação no sistema e um `.tar.gz` com a aplicação portátil. O `.run` é a opção recomendada para o diretório pessoal; o `.deb` oferece lançamento pelo menu do sistema. Linux x86_64; as releases de CI são compiladas no Ubuntu 24.04. OCR usa Tesseract e Leptonica incluídos, além de bibliotecas comuns do sistema operacional.

## Usar

- **Livro:** título extraído do conteúdo, com o nome do arquivo entre parênteses. Autor, editora e categoria/subcategoria vêm dos metadados.
- **Pesquisar:** palavras ou partes de palavras, combinadas em título, arquivo, categoria, autor, editora, outros metadados e anotações. O índice SQLite FTS5 é atualizado automaticamente.
- **Arquivo:** clique para abrir a pasta onde ele está. Formatos e amostras da mesma obra ficam agrupados, preservando as anotações por arquivo.
- **Abrir e continuar:** PDF em janela independente, redimensionável e maximizável, com ajuste à largura e página salva automaticamente. EPUB abre no leitor padrão; o campo de localização registra onde continuar.
- **Favorito / Em leitura:** mantém as escolhas no SQLite. A tela permite retomar as leituras.
- **Anotações:** salvamento automático; alterações de outra janela são protegidas contra sobrescrita silenciosa.
- **Organizar e atualizar:** procura documentos soltos na raiz de Livros, extrai título e metadados, cria categorias automaticamente, renomeia em snake_case e move para o assunto correspondente. Atualiza agrupamentos, SQLite, índice e HTML, com barra de progresso. Também ocorre em segundo plano ao iniciar.

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

Requisitos de compilação: JDK 17, Maven, `jpackage`, `fakeroot`, bibliotecas Tesseract/Leptonica/OpenMP, modelos OCR português e inglês. No Ubuntu:

```bash
sudo apt-get install libtesseract5 libleptonica-dev libgomp1 tesseract-ocr-eng tesseract-ocr-por xvfb fakeroot
./packaging/preparar_ocr.sh
xvfb-run -a mvn -Dcatalogo.guiTest=true verify
java -jar target/catalogo-livros.jar --verify-ocr
./empacotar_java.sh 2.0.0
```

Os três scripts shell são ferramentas de compilação/instalação, não processadores de livros. Os recursos OCR são obtidos do sistema de compilação e incluídos no instalador; não é necessário instalar Maven ou o código-fonte na máquina de uso.

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

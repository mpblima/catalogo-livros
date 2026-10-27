#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
version="${1:-2.0.0}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Versão deve ser X.Y.Z' >&2; exit 2; }
packaging/preparar_ocr.sh
mvn -q -Drevision="$version" package
jpackage_bin="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}/bin/jpackage"
[[ -x "$jpackage_bin" ]] || { echo 'Defina JAVA_HOME para um JDK 17 com jpackage' >&2; exit 1; }
work="target/empacotamento_${version}_$$"
mkdir -p "$work/input" "$work/image" "$work/payload" dist
cp target/catalogo-livros.jar "$work/input/"
"$jpackage_bin" --type app-image --name catalogo --app-version "$version" \
 --input "$work/input" --main-jar catalogo-livros.jar --dest "$work/image" \
 --icon packaging/catalogo.png --java-options '-Dfile.encoding=UTF-8' --description 'Biblioteca pessoal Java/Swing'
cp -a "$work/image/catalogo" "$work/payload/"
cp packaging/instalar.sh packaging/catalogo.svg packaging/catalogo.png "$work/payload/"
tar -czf "$work/payload.tar.gz" -C "$work/payload" .
installer="dist/catalogo-livros-${version}-linux-x86_64.run"
cat > "$installer" <<'HEADER'
#!/usr/bin/env bash
set -euo pipefail
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT
line=$(awk '/^__CATALOGO_PAYLOAD__$/ {print NR+1; exit}' "$0")
tail -n +"$line" "$0" | tar -xzf - -C "$temporary"
bash "$temporary/instalar.sh" "$@"
exit 0
__CATALOGO_PAYLOAD__
HEADER
cat "$work/payload.tar.gz" >> "$installer"
chmod +x "$installer"
cp "$work/payload.tar.gz" "dist/catalogo-livros-${version}-linux-x86_64.tar.gz"
# O .deb instala em /opt; o .run oferece instalação pessoal sem sudo.
"$jpackage_bin" --type deb --name catalogo-livros --app-version "$version" \
 --app-image "$work/image/catalogo" --dest dist --icon packaging/catalogo.png --linux-package-name catalogo-livros \
 --linux-shortcut --linux-menu-group Office --vendor 'Catálogo de Livros' \
 --description 'Biblioteca pessoal Java/Swing com SQLite e OCR'
(cd dist && sha256sum "catalogo-livros-${version}-linux-x86_64.run" "catalogo-livros-${version}-linux-x86_64.tar.gz" "catalogo-livros_${version}-1_amd64.deb" > "SHA256SUMS-${version}.txt")
printf 'Instaladores gerados em %s/dist\n' "$PWD"

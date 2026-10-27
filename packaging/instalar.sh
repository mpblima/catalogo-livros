#!/usr/bin/env bash
# Executado dentro do payload extraído pelo instalador .run.
set -euo pipefail
prefix="${HOME}/Apps/Catalogo"
integrate=true
while [[ $# -gt 0 ]]; do
 case "$1" in --prefix) prefix="$2"; shift 2;; --no-integrate) integrate=false; shift;; *) echo "Uso: $0 [--prefix DIRETORIO] [--no-integrate]" >&2; exit 2;; esac
done
[[ "$prefix" = /* && "$prefix" != / && "$prefix" != "$HOME" ]] || { echo 'Destino inválido' >&2; exit 2; }
source_dir=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$prefix"
stamp=$(date +%Y%m%d_%H%M%S)
backup="${prefix%/*}/catalogo_backup_${stamp}_$$"
if $integrate; then systemctl --user stop catalogo_livros.service 2>/dev/null || true; fi
rollback() {
 status=$?
 if [[ $status -ne 0 && -d "$backup" ]]; then
   [[ ! -e "$prefix/java" ]] || mv "$prefix/java" "$prefix/java_instalacao_incompleta_${stamp}"
   for old in "$backup"/*; do [[ -e "$old" || -L "$old" ]] && mv "$old" "$prefix/"; done
   $integrate && systemctl --user start catalogo_livros.service 2>/dev/null || true
 fi
 exit "$status"
}
trap rollback EXIT
for old in java catalogo catalogo_web _internal; do
 if [[ -e "$prefix/$old" || -L "$prefix/$old" ]]; then mkdir -p "$backup"; mv "$prefix/$old" "$backup/"; fi
done
cp -a "$source_dir/catalogo" "$prefix/java"
ln -s java/bin/catalogo "$prefix/catalogo"
cp "$source_dir/catalogo.svg" "$prefix/catalogo.svg"
cp "$source_dir/catalogo.png" "$prefix/catalogo.png"
"$prefix/catalogo" --verify-ocr
if $integrate; then
 mkdir -p "$HOME/.local/share/applications" "$HOME/.config/systemd/user"
 desktop=$(xdg-user-dir DESKTOP 2>/dev/null || printf '%s/Desktop' "$HOME")
 mkdir -p "$desktop"
 cat > "$HOME/.local/share/applications/catalogo_livros.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Catálogo de Livros
Comment=Biblioteca pessoal, leituras e anotações
Exec="$prefix/catalogo"
Icon=$prefix/catalogo.png
Terminal=false
Categories=Office;Education;
StartupNotify=true
DESKTOP
 chmod +x "$HOME/.local/share/applications/catalogo_livros.desktop"
 rm -f "$desktop/catalogo.desktop"
 cp "$HOME/.local/share/applications/catalogo_livros.desktop" "$desktop/catalogo.desktop"
 chmod +x "$desktop/catalogo.desktop"
 if command -v gio >/dev/null 2>&1; then gio set "$desktop/catalogo.desktop" metadata::trusted true 2>/dev/null || true; fi
 cat > "$HOME/.config/systemd/user/catalogo_livros.service" <<SERVICE
[Unit]
Description=Catálogo local da biblioteca (Java)
After=default.target
[Service]
Type=simple
ExecStart="$prefix/catalogo" --serve --no-browser
WorkingDirectory=$prefix
Restart=on-failure
RestartSec=3
UMask=0077
[Install]
WantedBy=default.target
SERVICE
 systemctl --user daemon-reload
 systemctl --user enable --now catalogo_livros.service
fi
printf 'Instalado em %s\n' "$prefix"
[[ ! -d "$backup" ]] || printf 'Versão anterior preservada em %s\n' "$backup"

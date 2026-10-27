$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$vcpkgRef = '2026.10.16'
$toolRoot = Join-Path $repo 'target/vcpkg-windows'
$overlay = Join-Path $repo 'target/vcpkg-overlay/tesseract'
$triplet = 'x64-windows-static-crt-dynamic'
$installed = Join-Path $toolRoot "installed/$triplet"
$native = Join-Path $repo 'src/main/resources/catalogo/ocr/native/windows-x86_64'
$licenses = Join-Path $repo 'src/main/resources/catalogo/ocr/licencas/windows'

if (Test-Path $toolRoot) { Remove-Item -Recurse -Force $toolRoot }
if (Test-Path $native) { Remove-Item -Recurse -Force $native }
if (Test-Path $licenses) { Remove-Item -Recurse -Force $licenses }
New-Item -ItemType Directory -Force $native, $licenses, (Split-Path $toolRoot) | Out-Null
git clone --depth 1 --branch $vcpkgRef https://github.com/microsoft/vcpkg.git $toolRoot
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível obter o vcpkg.' }
& (Join-Path $toolRoot 'bootstrap-vcpkg.bat') -disableMetrics
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível preparar o vcpkg.' }
New-Item -ItemType Directory -Force $overlay | Out-Null
Copy-Item (Join-Path $toolRoot 'ports/tesseract/*') $overlay -Recurse -Force
python (Join-Path $PSScriptRoot 'disable_tesseract_openmp.py') (Join-Path $overlay 'portfile.cmake')
& (Join-Path $toolRoot 'vcpkg.exe') install "tesseract:$triplet" --overlay-triplets (Join-Path $repo 'packaging/vcpkg-triplets') --overlay-ports (Join-Path $repo 'target/vcpkg-overlay')
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível compilar o OCR para Windows.' }

$bin = Join-Path $installed 'bin'
$dlls = @(Get-ChildItem $bin -Filter '*.dll' -File | Sort-Object Name)
$tesseract = @($dlls | Where-Object Name -Like '*tesseract*.dll')
$leptonica = @($dlls | Where-Object Name -Like '*lept*.dll')
if ($tesseract.Count -ne 1 -or $leptonica.Count -ne 1) { throw "DLLs OCR inesperadas: tesseract=$($tesseract.Count), leptonica=$($leptonica.Count)." }
foreach ($dll in $dlls) { Copy-Item $dll.FullName $native }
$files = @($dlls | ForEach-Object Name)
@("tesseract=$($tesseract[0].Name)", "leptonica=$($leptonica[0].Name)", "files=$($files -join ',')") | Set-Content (Join-Path $native 'manifest.properties') -Encoding ascii

$share = Join-Path $installed 'share'
Get-ChildItem $share -Directory | Sort-Object Name | ForEach-Object {
  $copyright = Join-Path $_.FullName 'copyright'
  if (Test-Path $copyright -PathType Leaf) { Copy-Item $copyright (Join-Path $licenses ($_.Name + '.txt')) }
}
if (-not (Test-Path (Join-Path $licenses 'tesseract.txt'))) { throw 'Aviso de licença do Tesseract ausente.' }
Write-Host "OCR Windows preparado: $($dlls.Count) DLLs, Tesseract $($tesseract[0].Name)."

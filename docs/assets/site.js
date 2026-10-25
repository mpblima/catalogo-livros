'use strict';
const platformNames = { windows: 'Windows', linux: 'Linux', macos: 'macOS' };
const number = new Intl.NumberFormat('pt-BR');
function formatSize(bytes) { return bytes ? `${number.format(Math.round(bytes / 1048576))} MB` : ''; }
function renderDownloads(data) {
  if (!data || !data.platforms || !Number.isFinite(data.downloads)) throw new Error('Dados de download inválidos');
  document.querySelector('#download-total').textContent = number.format(data.downloads);
  for (const [key, name] of Object.entries(platformNames)) {
    const card = document.querySelector(`[data-platform="${key}"]`);
    const platform = data.platforms[key];
    const button = card.querySelector('.platform-button');
    const alternatives = card.querySelector('.alternatives');
    alternatives.replaceChildren();
    if (!platform || !platform.assets.length) {
      card.querySelector('.release-version').textContent = 'Ainda sem instalador publicado';
      button.textContent = 'Em breve';
      button.removeAttribute('href');
      button.setAttribute('aria-disabled', 'true');
      continue;
    }
    const [primary, ...others] = platform.assets;
    const url = new URL(primary.url);
    if (url.protocol !== 'https:' || url.hostname !== 'github.com' || !url.pathname.includes('/releases/download/')) throw new Error('Link de instalador inválido');
    card.querySelector('.release-version').textContent = `${platform.version} · ${formatSize(primary.size)}`;
    button.href = primary.url;
    button.textContent = `Baixar para ${name} ↓`;
    button.removeAttribute('aria-disabled');
    button.setAttribute('aria-label', `Baixar ${name}, ${platform.version}, ${primary.name}`);
    for (const asset of others) {
      const assetUrl = new URL(asset.url);
      if (assetUrl.protocol !== 'https:' || assetUrl.hostname !== 'github.com' || !assetUrl.pathname.includes('/releases/download/')) continue;
      const link = document.createElement('a');
      link.href = asset.url;
      link.textContent = asset.name.endsWith('.tar.gz') ? 'Versão portátil' : `Pacote ${asset.name.split('.').pop().toUpperCase()}`;
      alternatives.append(link);
    }
    card.querySelector('.platform-count').textContent = `${number.format(platform.downloads)} downloads nesta plataforma`;
  }
  const updated = new Date(data.updated);
  document.querySelector('#download-status').textContent = `Último instalador estável disponível para cada plataforma. Dados atualizados em ${updated.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}. Contagem de arquivos baixados, não de usuários únicos.`;
}
async function loadDownloads() {
  try {
    const response = await fetch('downloads.json', { cache: 'no-cache' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    renderDownloads(await response.json());
  } catch (error) {
    document.querySelector('#download-total').textContent = 'Indisponível';
    for (const card of document.querySelectorAll('.platform')) {
      card.querySelector('.release-version').textContent = 'Não foi possível consultar os arquivos';
      card.querySelector('.platform-button').textContent = 'Tente novamente mais tarde';
    }
    document.querySelector('#download-status').textContent = 'Os dados de download estão temporariamente indisponíveis. Recarregue a página em instantes.';
  }
}
loadDownloads();

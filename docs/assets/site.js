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
      link.textContent = asset.name.endsWith('.tar.gz') ? 'Versão sem instalador' : `Outra opção (.${asset.name.split('.').pop()})`;
      alternatives.append(link);
    }
    card.querySelector('.platform-count').textContent = `${number.format(platform.downloads)} downloads para este sistema`;
  }
  const updated = new Date(data.updated);
  document.querySelector('#download-status').textContent = `Versão mais recente disponível para cada sistema. Dados atualizados em ${updated.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}. O contador mostra arquivos baixados, não a quantidade de pessoas.`;
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

function selectScenario(name, focus = false) {
  const selected = document.querySelector(`[role="tab"][data-tab="${name}"]`);
  if (!selected) return;
  for (const tab of document.querySelectorAll('[data-tab]')) {
    const active = tab === selected;
    tab.setAttribute('aria-selected', String(active));
    tab.tabIndex = active ? 0 : -1;
    document.getElementById(tab.getAttribute('aria-controls')).hidden = !active;
  }
  if (focus) selected.focus();
}
for (const tab of document.querySelectorAll('[data-tab]')) {
  tab.addEventListener('click', () => selectScenario(tab.dataset.tab));
  tab.addEventListener('keydown', event => {
    const tabs = [...document.querySelectorAll('[data-tab]')];
    const index = tabs.indexOf(tab);
    const offset = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (offset || event.key === 'Home' || event.key === 'End') {
      event.preventDefault();
      const target = event.key === 'Home' ? tabs[0] : event.key === 'End' ? tabs[tabs.length - 1] : tabs[(index + offset + tabs.length) % tabs.length];
      selectScenario(target.dataset.tab, true);
    }
  });
}
for (const link of document.querySelectorAll('[data-select-tab]')) link.addEventListener('click', () => selectScenario(link.dataset.selectTab));

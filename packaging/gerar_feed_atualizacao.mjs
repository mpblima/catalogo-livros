import { createPrivateKey, sign, createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { basename, join } from 'node:path';

const version = process.env.VERSION;
const dist = process.env.DIST ?? 'dist';
if (!/^\d+\.\d+\.\d+$/.test(version ?? '')) throw new Error('VERSION inválida');
if (!process.env.CATALOGO_UPDATER_PRIVATE_KEY) throw new Error('Secret CATALOGO_UPDATER_PRIVATE_KEY não configurado');
const key = createPrivateKey(Buffer.from(process.env.CATALOGO_UPDATER_PRIVATE_KEY, 'base64').toString('utf8'));
const files = {
  linux: `catalogo-livros-${version}-linux-x86_64.run`,
  windows: `CatalogoLivros-${version}.msi`,
  macos: `CatalogoLivros-${version}.dmg`,
};
const assets = {};
for (const [platform, name] of Object.entries(files)) {
  const data = await readFile(join(dist, name));
  assets[platform] = {
    name,
    url: `https://github.com/mpblima/catalogo-livros/releases/latest/download/${name}`,
    sha256: createHash('sha256').update(data).digest('hex'),
  };
}
const body = Buffer.from(JSON.stringify({ version, assets }, null, 2) + '\n');
await writeFile(join(dist, 'latest.json'), body);
await writeFile(join(dist, 'latest.sig'), sign(null, body, key).toString('base64') + '\n');
console.log(`Feed assinado para ${version}`);

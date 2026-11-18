// Export only into this workbench. Nothing here writes Android app assets.
import {mkdir, readFile, writeFile, copyFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const slug = process.argv[2] || 'f1-blue';
if (!['f1-blue', 'f1-2'].includes(slug)) throw new Error(`Unknown draft: ${slug}`);
const built = await import(`./themes/${slug}/${slug}.js`);
const theme = Object.values(built).find(value => value?.__built && value?.tokens);
if (!theme) throw new Error(`No built theme in ${slug}`);
const tokens = theme.tokens;
function lightColor(name) {
  let value = tokens[name];
  if (typeof value !== 'string') throw new Error(`Missing token: ${name}`);
  if (value.startsWith('light-dark(')) value = value.slice(11, -1).split(',')[0].trim();
  if (!/^#[\da-f]{6}([\da-f]{2})?$/i.test(value)) throw new Error(`Unsupported color ${name}: ${value}`);
  // CSS #RRGGBBAA -> Android #AARRGGBB.
  return ('#' + (value.length === 9 ? value.slice(7) : 'ff') + value.slice(1, 7)).toUpperCase();
}
const mapping = {
  background: '--color-background-body',
  panel: '--color-background-surface',
  panelRaised: '--color-background-card',
  stroke: '--color-border',
  textPrimary: '--color-text-primary',
  textSecondary: '--color-text-secondary',
  accent: '--color-accent',
  warning: '--color-warning',
  critical: '--color-error',
  unavailable: '--color-text-secondary',
};
const dashboard = JSON.parse(await readFile(new URL(`./themes/${slug}/dashboard.json`, import.meta.url), 'utf8'));
const manifest = {
  schemaVersion: 3,
  id: slug.replaceAll('-', '_') + '_draft',
  displayName: slug === 'f1-2' ? 'Liquid Glass / Draft' : 'F1 Blue / Draft',
  files: {colors: 'colors.json', fonts: 'fonts.json', components: 'components.json',
    overview: 'layouts/overview.json',
    ...(slug === 'f1-2' ? {shell: 'layouts/shell.json', f1: 'layouts/f1.json'} : {})},
};
const output = new URL(slug === 'f1-blue' ? './artifacts/' : `./artifacts/${slug}/`, import.meta.url);
await mkdir(output, {recursive: true});
const writeJson = async (name, value) => writeFile(new URL(name, output), JSON.stringify(value, null, 2) + '\n');
await writeFile(new URL('manifest.json', output), JSON.stringify(manifest, null, 2) + '\n');
await writeJson('colors.json', {tokens: {...Object.fromEntries(Object.entries(mapping).map(([key, value]) => [key, lightColor(value)])), cutPanels: false}, palette: dashboard.palette, backdrop: dashboard.backdrop});
await writeJson('fonts.json', {fonts: dashboard.fonts, typography: dashboard.typography});
await writeJson('components.json', {spacing: dashboard.spacing, material: dashboard.material, surfaceStyles: dashboard.surfaceStyles,
  mediaControls: dashboard.mediaControls, mediaFooter: dashboard.mediaFooter,
  decorations: dashboard.decorations, fills: dashboard.fills, assets: {background: ''}});
await mkdir(new URL('layouts/', output), {recursive: true});
const layout = {columns: 5, rows: 4};
if (slug === 'f1-2') {
  const overview = JSON.parse(await readFile(new URL(`./themes/${slug}/layouts/overview.json`, import.meta.url), 'utf8'));
  await writeJson('layouts/overview.json', {layout, ...overview});
  for (const name of ['shell.json', 'f1.json'])
    await copyFile(new URL(`./themes/${slug}/layouts/${name}`, import.meta.url), new URL(`layouts/${name}`, output));
} else {
  await writeJson('layouts/overview.json', {layout});
}
await writeFile(new URL('token_mapping.json', output), JSON.stringify({
  source: `themes/${slug}/${slug}.js`,
  mode: 'light',
  mapping,
  dashboardSource: `themes/${slug}/dashboard.json`,
  limitations: ['Canvas uses the production theme engine and the exported layout files; Astryx web component geometry is not imported.', ...(slug === 'f1-2' ? ['Glass depth and light are approximated with Canvas gradients and transparency. Native Apple blur/refraction and optical motion are not implemented.'] : [])],
}, null, 2) + '\n');
console.log(`Exported draft: ${fileURLToPath(new URL('manifest.json', output))}`);

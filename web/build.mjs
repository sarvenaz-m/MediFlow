import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const [template, styles, engine, app, font] = await Promise.all([
  readFile(root+'web/src/template.html','utf8'), readFile(root+'web/src/styles.css','utf8'),
  readFile(root+'web/src/engine.js','utf8'), readFile(root+'web/src/app.js','utf8'), readFile(root+'assets/manrope.ttf'),
]);
const html = template.replace('/* MEDIFLOW_STYLES */',styles.replace('MEDIFLOW_FONT',font.toString('base64')))
  .replace('/* MEDIFLOW_ENGINE */',engine).replace('/* MEDIFLOW_APP */',app);
await writeFile(root+'index.html',html);
console.log(`Built standalone preview: ${Buffer.byteLength(html).toLocaleString()} bytes`);

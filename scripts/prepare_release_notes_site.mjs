// Stage an unpublished candidate in a disposable sleepy-site checkout only.
// Asset metadata below is a contract fixture, never a published release.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const [notesPath, sitePath] = process.argv.slice(2);
assert(notesPath && sitePath, 'Usage: node prepare_release_notes_site.mjs NOTES SITE_CHECKOUT');
const site = resolve(sitePath);
const body = readFileSync(notesPath, 'utf8');
const tag = body.match(/^# Sleepy (v\d+\.\d+\.\d+)$/m)?.[1];
assert(tag, 'Missing release version');
assert.deepEqual(body.match(/^# .+$/gm), [`# Sleepy ${tag}`, `# Sleepy ${tag}`]);
assert(!/^(?:>|---\s*$|```|\|)/m.test(body), 'Use Markdown supported by both consumers');

const { releaseFromApi, validate } = await import(pathToFileURL(resolve(site, 'scripts/fetch-release.mjs')));
const { buildReleaseSurfaces } = await import(pathToFileURL(resolve(site, 'scripts/build-release-surfaces.mjs')));
const { marked } = await import(pathToFileURL(resolve(site, 'node_modules/marked/lib/marked.esm.js')));
const download = `https://github.com/lingion/sleepy/releases/download/${tag}/`;
const sourceSha = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
const date = new Date().toISOString();
const release = releaseFromApi({
  tag_name: tag, name: `Sleepy ${tag}`, body,
  html_url: `https://github.com/lingion/sleepy/releases/tag/${tag}`, published_at: date,
  assets: ['arm64-v8a', 'armeabi-v7a', 'x86_64', 'universal'].map(abi => ({
    name: `app-${abi}-release.apk`, size: 1,
    browser_download_url: `${download}app-${abi}-release.apk`,
  })).concat([{ name: 'sha256sums.txt', browser_download_url: `${download}sha256sums.txt` }]),
});
assert.deepEqual(validate(release), []);
assert.equal(release.body, body);
release.sourceSha = sourceSha;
const read = path => JSON.parse(readFileSync(resolve(site, path), 'utf8'));
const write = (path, value) => writeFileSync(resolve(site, path), JSON.stringify(value, null, 2) + '\n');
const product = {
  tag, commit: sourceSha, publishedAt: date, snapshotRefreshedAt: date.slice(0, 10),
  sourceUrl: `https://github.com/lingion/sleepy/tree/${sourceSha}`,
};
const catalog = { ...read('src/data/schools.snapshot.json'), ref: tag, sourceSha, releasedAt: date.slice(0, 10) };
write('src/data/release.json', release);
write('src/data/product-release.json', product);
write('src/data/schools.snapshot.json', catalog);
const surfaces = buildReleaseSurfaces({
  release, product, catalog, baseline: read('src/data/content-baseline.json'), faq: read('src/data/faq.json'),
});
writeFileSync(resolve(site, 'public/release.json'), surfaces.releaseJson);
writeFileSync(resolve(site, 'public/llms.txt'), surfaces.llms);
// Feed the same rendered body through the App's mirror fallback in the CI test.
writeFileSync(resolve(site, 'candidate-mirror.html'), `<div class="markdown-body">${marked.parse(body)}</div>`);
console.log(`Staged ${tag}: bilingual notes preserved; four ABI contract fixtures; no deployment`);

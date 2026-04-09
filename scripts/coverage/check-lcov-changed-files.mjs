import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

function parseArgs(argv) {
  const out = { base: null, lcov: null, min: 85 };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--base') out.base = argv[++i];
    else if (a === '--lcov') out.lcov = argv[++i];
    else if (a === '--min') out.min = Number(argv[++i]);
  }
  if (!out.base || !out.lcov || !Number.isFinite(out.min)) {
    throw new Error('Usage: node check-lcov-changed-files.mjs --base <ref> --lcov <glob> --min <percent>');
  }
  return out;
}

function sh(cmd, args, opts = {}) {
  return execFileSync(cmd, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], ...opts });
}

function listFilesByGlob(glob) {
  // Minimal globbing for patterns like "coverage/**/lcov.info"
  const parts = glob.split('/**/');
  if (parts.length !== 2) return [glob].filter(p => fs.existsSync(p));
  const [root, tail] = parts;
  const rootAbs = path.resolve(root);
  const results = [];
  function walk(dir) {
    for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, ent.name);
      if (ent.isDirectory()) walk(p);
      else if (ent.isFile() && ent.name === tail) results.push(p);
    }
  }
  if (fs.existsSync(rootAbs)) walk(rootAbs);
  return results;
}

function parseLcov(content) {
  // Map: source file -> { found, hit }
  const files = new Map();
  let current = null;
  for (const line of content.split('\n')) {
    if (line.startsWith('SF:')) {
      current = line.slice(3).trim();
      files.set(current, { found: 0, hit: 0 });
    } else if (current && line.startsWith('DA:')) {
      const [, hitRaw] = line.slice(3).split(',');
      const hit = Number(hitRaw);
      const v = files.get(current);
      v.found += 1;
      if (hit > 0) v.hit += 1;
    } else if (line.startsWith('end_of_record')) {
      current = null;
    }
  }
  return files;
}

function toPosix(p) {
  return p.split(path.sep).join('/');
}

const { base, lcov, min } = parseArgs(process.argv.slice(2));

// Run from frontend/ (workflow working-directory), normalize paths accordingly.
const repoRoot = path.resolve('..');
const changed = sh('git', ['diff', '--name-only', `${base}...HEAD`]).trim().split('\n').filter(Boolean);
const changedFrontend = changed
  .filter(f => f.startsWith('frontend/src/') && f.endsWith('.ts') && !f.endsWith('.spec.ts'))
  .map(f => path.resolve(repoRoot, f));

if (changedFrontend.length === 0) {
  console.log('No changed frontend source files detected; skipping changed-files coverage gate.');
  process.exit(0);
}

const lcovFiles = listFilesByGlob(lcov);
if (lcovFiles.length === 0) {
  throw new Error(`No lcov files found for pattern: ${lcov}`);
}

const coverageByFile = new Map(); // abs -> {found, hit}
for (const lf of lcovFiles) {
  const parsed = parseLcov(fs.readFileSync(lf, 'utf8'));
  for (const [sf, v] of parsed.entries()) {
    const abs = path.isAbsolute(sf) ? sf : path.resolve(path.dirname(lf), sf);
    const prev = coverageByFile.get(abs) ?? { found: 0, hit: 0 };
    coverageByFile.set(abs, { found: prev.found + v.found, hit: prev.hit + v.hit });
  }
}

let totalFound = 0;
let totalHit = 0;
const missing = [];

for (const fileAbs of changedFrontend) {
  const v = coverageByFile.get(fileAbs);
  if (!v) {
    missing.push(toPosix(path.relative(repoRoot, fileAbs)));
    continue;
  }
  totalFound += v.found;
  totalHit += v.hit;
}

if (missing.length) {
  console.log('Changed files missing from LCOV (not instrumented or not executed):');
  for (const f of missing) console.log(`- ${f}`);
}

if (totalFound === 0) {
  throw new Error('No executable lines found for changed files (LCOV had no DA entries).');
}

const pct = (totalHit / totalFound) * 100;
const pctStr = pct.toFixed(2);
console.log(`Changed-files line coverage: ${pctStr}% (min ${min}%)`);

if (pct + 1e-9 < min) {
  process.exitCode = 1;
}

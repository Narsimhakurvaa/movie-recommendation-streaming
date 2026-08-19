#!/usr/bin/env node
/**
 * Offline Java syntax + structural verification.
 *
 * Parses every backend source file with a complete Java grammar (java-parser,
 * built on Chevrotain) and reports genuine syntax errors. This exists because
 * the environment the project was assembled in had no route to Maven Central,
 * so `javac` could not resolve Spring/JPA symbols; a grammar-level parse is
 * dependency-free and still catches every malformed construct.
 *
 * Beyond parsing it applies project conventions that a compiler would not:
 *   - package declaration must match the directory path
 *   - public type name must match the file name
 *   - no wildcard imports (explicit imports only)
 *   - no unused imports
 *   - no System.out/err printing in production code (use a logger)
 *   - no obvious hardcoded credentials
 *
 * Usage: node check-java-syntax.js <dir> [<dir>...]
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { parse } = require('java-parser');

const GREEN = '\x1b[32m';
const RED = '\x1b[31m';
const YELLOW = '\x1b[33m';
const DIM = '\x1b[2m';
const RESET = '\x1b[0m';

/** Recursively collect .java files. */
function collect(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) collect(full, out);
    else if (entry.name.endsWith('.java')) out.push(full);
  }
  return out;
}

/** Strip comments and string/char literals so text scans don't match inside them. */
function stripLiteralsAndComments(source) {
  let out = '';
  let i = 0;
  const n = source.length;
  while (i < n) {
    const c = source[i];
    const next = source[i + 1];
    if (c === '/' && next === '/') {
      while (i < n && source[i] !== '\n') i++;
    } else if (c === '/' && next === '*') {
      i += 2;
      while (i < n && !(source[i] === '*' && source[i + 1] === '/')) i++;
      i += 2;
    } else if (c === '"' && source.slice(i, i + 3) === '"""') {
      i += 3;
      while (i < n && source.slice(i, i + 3) !== '"""') i++;
      i += 3;
      out += '""';
    } else if (c === '"') {
      i++;
      while (i < n && source[i] !== '"') {
        if (source[i] === '\\') i++;
        i++;
      }
      i++;
      out += '""';
    } else if (c === "'") {
      i++;
      while (i < n && source[i] !== "'") {
        if (source[i] === '\\') i++;
        i++;
      }
      i++;
      out += "''";
    } else {
      out += c;
      i++;
    }
  }
  return out;
}

const findings = [];
function record(level, file, message, line) {
  findings.push({ level, file, message, line });
}

function checkConventions(file, source, rootDir) {
  const code = stripLiteralsAndComments(source);
  const rel = path.relative(rootDir, file);

  // --- package must mirror directory structure ---
  const pkgMatch = code.match(/^\s*package\s+([\w.]+)\s*;/m);
  if (!pkgMatch) {
    record('error', rel, 'missing package declaration');
  } else {
    const expected = path.dirname(rel).split(path.sep).join('.');
    if (pkgMatch[1] !== expected) {
      record('error', rel, `package "${pkgMatch[1]}" does not match directory "${expected}"`);
    }
  }

  // --- public type name must match file name ---
  const base = path.basename(file, '.java');
  const typeRe = new RegExp(`\\b(?:public\\s+)?(?:final\\s+|abstract\\s+)?(?:class|interface|enum|record|@interface)\\s+${base}\\b`);
  if (!typeRe.test(code)) {
    record('error', rel, `no type named "${base}" declared in this file`);
  }

  // --- imports ---
  const imports = [...code.matchAll(/^\s*import\s+(static\s+)?([\w.]+(?:\.\*)?)\s*;/gm)];
  for (const imp of imports) {
    const name = imp[2];
    if (name.endsWith('.*')) {
      record('error', rel, `wildcard import "${name}" (use explicit imports)`);
    }
  }
  // unused-import detection: strip the import block, then look for the symbol
  const withoutImports = code.replace(/^\s*import\s+.*?;\s*$/gm, '');
  for (const imp of imports) {
    if (imp[1]) continue; // static imports: symbol may be used bare
    const simple = imp[2].split('.').pop();
    if (simple === '*') continue;
    const used = new RegExp(`\\b${simple}\\b`).test(withoutImports);
    if (!used) record('error', rel, `unused import "${imp[2]}"`);
  }

  // --- no console printing in production code ---
  const printMatch = code.match(/System\s*\.\s*(out|err)\s*\.\s*print/);
  if (printMatch) {
    const line = code.slice(0, printMatch.index).split('\n').length;
    record('error', rel, 'System.out/err printing in production code (use a logger)', line);
  }

  // --- hardcoded credential heuristics (literals are stripped, so scan raw) ---
  const secretPatterns = [
    { re: /(?:password|passwd|secret|apiKey|api_key|token)\s*=\s*"(?!\s*")[^"]{8,}"/i, msg: 'possible hardcoded secret' },
    { re: /jdbc:[a-z]+:\/\/[^"\s]*:[^"@\s]+@/i, msg: 'credentials embedded in a JDBC URL' },
  ];
  for (const { re, msg } of secretPatterns) {
    const m = source.match(re);
    if (m) {
      // Allow obvious placeholders and the documented BCrypt probe value.
      if (/REPLACE|EXAMPLE|changeme|placeholder|\$2a\$|\{noop\}|example\.com/i.test(m[0])) continue;
      const line = source.slice(0, m.index).split('\n').length;
      record('error', rel, `${msg}: ${m[0].slice(0, 60)}`, line);
    }
  }
}

function main() {
  const dirs = process.argv.slice(2);
  if (dirs.length === 0) {
    console.error('usage: check-java-syntax.js <dir> [<dir>...]');
    process.exit(2);
  }
  const repoRoot = path.resolve(__dirname, '../../..');

  let files = [];
  for (const d of dirs) {
    const abs = path.resolve(repoRoot, d);
    if (!fs.existsSync(abs)) {
      console.error(`no such directory: ${d}`);
      process.exit(2);
    }
    // Convention checks are relative to the source root (…/java).
    files.push(...collect(abs).map((f) => ({ file: f, root: abs })));
  }

  let parsed = 0;
  let parseFailures = 0;

  for (const { file, root } of files) {
    const source = fs.readFileSync(file, 'utf8');
    const rel = path.relative(repoRoot, file);
    try {
      parse(source);
      parsed++;
    } catch (err) {
      parseFailures++;
      const line = err.token && err.token.startLine ? err.token.startLine : '?';
      record('error', rel, `SYNTAX: ${String(err.message).split('\n')[0]}`, line);
      continue;
    }
    checkConventions(file, source, root);
  }

  const errors = findings.filter((f) => f.level === 'error');

  console.log(`\n${DIM}parsed ${parsed}/${files.length} Java sources with a full Java grammar${RESET}`);
  if (errors.length === 0) {
    console.log(`${GREEN}PASS${RESET}  no syntax errors, no convention violations`);
    return 0;
  }

  console.log(`\n${RED}${errors.length} problem(s):${RESET}`);
  for (const f of errors) {
    const at = f.line ? `:${f.line}` : '';
    console.log(`  ${RED}error${RESET} ${f.file}${at}\n         ${f.message}`);
  }
  return 1;
}

process.exit(main());

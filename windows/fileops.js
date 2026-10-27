'use strict';
// Operaciones de archivos del explorador interno (sin Electron, para poder probarlas aparte).
const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const { execFile } = require('child_process');

const WIN = process.platform === 'win32';

function bad(msg) {
  const e = new Error(msg);
  e.userMessage = msg;
  return e;
}

/** Contenido de una carpeta: [{name, isDir, size, mtime}]. Lo que no se puede leer se omite. */
async function list(dir) {
  const items = await fsp.readdir(dir, { withFileTypes: true });
  const out = [];
  await Promise.all(
    items.map(async (it) => {
      try {
        const st = await fsp.stat(path.join(dir, it.name)); // sigue accesos directos
        out.push({ name: it.name, isDir: st.isDirectory(), size: st.isDirectory() ? 0 : st.size, mtime: st.mtimeMs });
      } catch (_) {
        /* sin permiso o enlace roto */
      }
    })
  );
  return out;
}

function drives() {
  if (!WIN) return [];
  const list = [];
  for (let c = 67; c <= 90; c++) {
    const d = String.fromCharCode(c) + ':\\';
    try {
      if (fs.existsSync(d)) list.push(d);
    } catch (_) {
      /* ignorar */
    }
  }
  return list;
}

const norm = (p) => (WIN ? path.resolve(p).toLowerCase() : path.resolve(p));

function isInside(child, parent) {
  const c = norm(child);
  const p = norm(parent);
  return c === p || c.startsWith(p.endsWith(path.sep) ? p : p + path.sep);
}

async function exists(p) {
  try {
    await fsp.lstat(p);
    return true;
  } catch (_) {
    return false;
  }
}

/** Un nombre libre dentro de `dir`: «nombre (2).ext» si ya existe. */
async function uniquePath(dir, name) {
  let target = path.join(dir, name);
  if (!(await exists(target))) return target;
  const ext = path.extname(name);
  const base = ext ? name.slice(0, -ext.length) : name;
  for (let i = 2; ; i++) {
    target = path.join(dir, `${base} (${i})${ext}`);
    if (!(await exists(target))) return target;
  }
}

async function copyOne(src, destDir) {
  const st = await fsp.stat(src);
  if (st.isDirectory() && isInside(destDir, src)) throw bad('No se puede copiar una carpeta dentro de sí misma');
  const target = await uniquePath(destDir, path.basename(src));
  await fsp.cp(src, target, { recursive: true, errorOnExist: true, force: false });
  return target;
}

async function moveOne(src, destDir) {
  if (norm(path.dirname(src)) === norm(destDir)) return src; // ya está ahí
  const st = await fsp.stat(src);
  if (st.isDirectory() && isInside(destDir, src)) throw bad('No se puede mover una carpeta dentro de sí misma');
  const target = await uniquePath(destDir, path.basename(src));
  try {
    await fsp.rename(src, target);
  } catch (e) {
    if (e.code !== 'EXDEV') throw e;
    await fsp.cp(src, target, { recursive: true, errorOnExist: true, force: false });
    await fsp.rm(src, { recursive: true, force: true });
  }
  return target;
}

function validName(name) {
  const n = typeof name === 'string' ? name.trim() : '';
  if (!n || n === '.' || n === '..') throw bad('Nombre no válido');
  // eslint-disable-next-line no-control-regex
  if (/[<>:"/\\|?*\u0000-\u001f]/.test(n)) throw bad('El nombre no puede llevar  < > : " / \\ | ? *');
  if (WIN && /[. ]$/.test(n)) throw bad('El nombre no puede terminar en punto o espacio');
  return n;
}

async function renameOne(src, newName) {
  const n = validName(newName);
  const target = path.join(path.dirname(src), n);
  if (norm(target) !== norm(src) && (await exists(target))) throw bad(`Ya existe «${n}»`);
  await fsp.rename(src, target);
  return target;
}

async function mkdirOne(parent, name) {
  const n = validName(name);
  const target = path.join(parent, n);
  if (await exists(target)) throw bad(`Ya existe «${n}»`);
  await fsp.mkdir(target);
  return target;
}

function run(cmd, args, env) {
  return new Promise((resolve, reject) => {
    execFile(cmd, args, { windowsHide: true, env: { ...process.env, ...env }, maxBuffer: 8 * 1024 * 1024 }, (err, _out, errOut) => {
      if (err) reject(new Error((errOut && String(errOut).trim()) || err.message));
      else resolve();
    });
  });
}

/** Extrae un .zip en `dest`. En Windows usa tar.exe (rechaza rutas que salgan de la carpeta) y, si falla, PowerShell. */
async function unzip(zip, dest) {
  await fsp.mkdir(dest, { recursive: true });
  if (WIN) {
    const sys = path.join(process.env.SystemRoot || 'C:\\Windows', 'System32');
    try {
      await run(path.join(sys, 'tar.exe'), ['-xf', zip, '-C', dest]);
    } catch (_) {
      await run(
        path.join(sys, 'WindowsPowerShell', 'v1.0', 'powershell.exe'),
        ['-NoProfile', '-NonInteractive', '-Command', 'Expand-Archive -LiteralPath $env:AI_ZIP -DestinationPath $env:AI_DEST -Force'],
        { AI_ZIP: zip, AI_DEST: dest }
      );
    }
  } else {
    await run('unzip', ['-o', '-q', zip, '-d', dest]);
  }
}

module.exports = { list, drives, isInside, uniquePath, copyOne, moveOne, renameOne, mkdirOne, unzip, exists };

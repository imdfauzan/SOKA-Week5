#!/usr/bin/env node
/**
 * Server lokal kecil untuk halaman simulasi-cso.html.
 *
 * - Menyajikan halaman HTML dari root repo.
 * - Endpoint POST /api/run menjalankan JAR CloudSim Plus yang asli
 *   (java -jar target/soka-simulasi.jar --run GoCJ_Dataset_N.txt)
 *   lalu mengembalikan stdout Java apa adanya ke browser.
 *
 * Jalankan lewat "jalankan-cloudsim.bat" (disarankan) atau:
 *   node tools/cloudsim-server.js
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const JAR = path.join(ROOT, 'target', 'soka-simulasi.jar');
const PORT = Number(process.env.PORT || 8777);
const TIMEOUT_MS = 240000;

function cariJava() {
  const kandidat = [];
  if (process.env.JAVA_EXE) kandidat.push(process.env.JAVA_EXE);
  if (process.env.JAVA_HOME) kandidat.push(path.join(process.env.JAVA_HOME, 'bin', 'java.exe'));
  kandidat.push('C:\\Program Files\\Java\\jdk-17.0.19\\bin\\java.exe');
  kandidat.push('C:\\Program Files\\Java\\jdk-21\\bin\\java.exe');
  for (const k of kandidat) {
    try { if (fs.existsSync(k)) return k; } catch (_) {}
  }
  return 'java';
}

const JAVA = cariJava();
let sibuk = false;

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.csv': 'text/csv; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
  '.pdf': 'application/pdf'
};

function kirimJson(res, kode, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(kode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

function layaniStatis(res, pathname) {
  const rel = pathname === '/' ? '/simulasi-cso.html' : pathname;
  const file = path.resolve(ROOT, '.' + decodeURIComponent(rel));

  // Jangan pernah keluar dari root repo.
  if (!file.startsWith(ROOT + path.sep) && file !== ROOT) {
    res.writeHead(403).end('forbidden');
    return;
  }
  // Hanya berkas yang memang perlu ditampilkan.
  const ext = path.extname(file).toLowerCase();
  if (!MIME[ext]) { res.writeHead(404).end('not found'); return; }

  fs.readFile(file, (err, buf) => {
    if (err) { res.writeHead(404).end('not found'); return; }
    if (ext === '.html') {
      // Penanda bahwa halaman disajikan oleh bridge Java (hindari probe /api/health sia-sia).
      const html = buf.toString('utf8').replace('<head>', '<head>\n<script>window.SOKA_JAVA_BRIDGE = true;</script>');
      const out = Buffer.from(html, 'utf8');
      res.writeHead(200, { 'Content-Type': MIME[ext], 'Content-Length': out.length, 'Cache-Control': 'no-store' });
      res.end(out);
      return;
    }
    res.writeHead(200, { 'Content-Type': MIME[ext], 'Content-Length': buf.length });
    res.end(buf);
  });
}

function jalankanSimulasi(dataset) {
  return new Promise((resolve) => {
    const t0 = Date.now();
    const anak = spawn(JAVA, ['-jar', JAR, '--run', dataset], {
      cwd: ROOT,
      windowsHide: true
    });

    let out = '', err = '';
    const timer = setTimeout(() => { try { anak.kill(); } catch (_) {} }, TIMEOUT_MS);
    anak.stdout.on('data', d => { out += d.toString(); });
    anak.stderr.on('data', d => { err += d.toString(); });
    anak.on('error', e => {
      clearTimeout(timer);
      resolve({ ok: false, error: 'Gagal menjalankan Java: ' + e.message, stdout: out, stderr: err });
    });
    anak.on('close', kode => {
      clearTimeout(timer);
      resolve({ ok: kode === 0, code: kode, ms: Date.now() - t0, stdout: out, stderr: err });
    });
  });
}

const server = http.createServer((req, res) => {
  const u = new URL(req.url, 'http://127.0.0.1');

  if (u.pathname === '/api/health') {
    let javaVersi = 'tidak diketahui';
    try {
      // java -version menulis ke stderr, jadi stdout + stderr digabung.
      const r = require('child_process').spawnSync(JAVA, ['-version'], { encoding: 'utf8' });
      const teks = ((r.stdout || '') + (r.stderr || '')).trim();
      javaVersi = (teks.split(/\r?\n/)[0] || '').trim();
    } catch (_) {}
    return kirimJson(res, 200, {
      ok: true,
      jar: fs.existsSync(JAR),
      jarPath: JAR,
      java: JAVA,
      javaVersion: javaVersi,
      root: ROOT
    });
  }

  if (u.pathname === '/api/run' && req.method === 'POST') {
    if (!fs.existsSync(JAR)) {
      return kirimJson(res, 500, { ok: false, error: 'target/soka-simulasi.jar belum ada — jalankan build dulu' });
    }
    if (sibuk) {
      return kirimJson(res, 429, { ok: false, error: 'Masih ada simulasi yang berjalan' });
    }

    let body = '';
    req.on('data', d => { body += d; if (body.length > 1e5) req.destroy(); });
    req.on('end', async () => {
      let dataset = 'GoCJ_Dataset_1000.txt';
      try {
        const j = JSON.parse(body || '{}');
        if (j.dataset) dataset = String(j.dataset);
      } catch (_) {}
      if (!/^GoCJ_Dataset_\d+\.txt$/.test(dataset)) {
        return kirimJson(res, 400, { ok: false, error: 'Nama dataset tidak valid: ' + dataset });
      }
      sibuk = true;
      console.log(`[run] ${dataset} …`);
      const hasil = await jalankanSimulasi(dataset);
      sibuk = false;
      console.log(`[run] ${dataset} selesai (${hasil.ms} ms, exit ${hasil.code})`);
      return kirimJson(res, 200, { ...hasil, dataset });
    });
    return;
  }

  layaniStatis(res, u.pathname);
});

server.listen(PORT, '127.0.0.1', () => {
  console.log('');
  console.log('  CloudSim runner siap di http://127.0.0.1:' + PORT + '/');
  console.log('  Java  : ' + JAVA);
  console.log('  JAR   : ' + (fs.existsSync(JAR) ? 'ada' : 'BELUM ADA (jalankan: mvn clean package)'));
  console.log('  Hentikan dengan Ctrl+C.');
  console.log('');
});

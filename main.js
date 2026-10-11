/* =====================================================================
   ANTI.EXAMEN5 para Windows · proceso principal

   Tres ventanas:
   1. Principal: carga la pagina del alumno de antiexamen5.com (entrar con
      codigo, reglas, resultado). Toda la comunicacion con el servidor la
      hace esa pagina, con la misma cola y los mismos reintentos que en el
      navegador.
   2. Examen: abre el examen externo (Google Forms, Moodle...) en una
      sesion aislada, sin acceso a nada del sistema.
   3. Burbuja: siempre encima, muestra tiempo y advertencias.

   Esta app no decide nada: detecta y avisa a la pagina, y la pagina
   informa al servidor, que es quien cuenta las faltas.
   ===================================================================== */
'use strict';
const { app, BrowserWindow, ipcMain, screen, clipboard, dialog, shell, session, Menu } = require('electron');
const path = require('path');
const { crearPermitidos } = require('./permitidos');

const SITIO = (process.env.AE5_SITIO || 'https://antiexamen5.com').replace(/\/$/, '');
const ORIGEN = new URL(SITIO).origin;
const PROTOCOLO = 'antiexamen5';
const DEPURAR = process.env.AE5_DEPURAR === '1' && !app.isPackaged;
// Solo para las pruebas automaticas (nunca en el instalador): acepta el
// certificado de un servidor https local.
if (process.env.AE5_PRUEBA === '1' && !app.isPackaged) app.commandLine.appendSwitch('ignore-certificate-errors');

let principal = null, examen = null, burbuja = null;
let sesion = null;          // examen externo en curso
let enApp = true;           // alguna ventana nuestra tiene el foco
let cerrandoTodo = false;
let ultimoAviso = null;

// Sin menu en Windows y Linux. En Mac hace falta un menu Edicion minimo:
// sin el, Cmd+C y Cmd+V no funcionan en ningun campo de texto.
if (process.platform === 'darwin') {
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    { role: 'appMenu', submenu: [{ role: 'about', label: 'Acerca de ANTI.EXAMEN5' }, { type: 'separator' }, { role: 'quit', label: 'Salir de ANTI.EXAMEN5' }] },
    { role: 'editMenu', label: 'Edición' }
  ]));
} else {
  Menu.setApplicationMenu(null);
}
app.setAppUserModelId('com.antiexamen5.app');

/* ---------------------------------------------------- una sola copia */
if (!app.requestSingleInstanceLock()) { app.quit(); }
app.on('second-instance', (_e, argv) => {
  const enlace = argv.find((a) => a.startsWith(PROTOCOLO + '://'));
  if (enlace && !sesion) abrirEnlace(enlace);
  const v = sesion && examen && !examen.isDestroyed() ? examen : principal;
  if (v) { if (v.isMinimized()) v.restore(); v.show(); v.focus(); }
});
if (process.defaultApp && process.argv.length >= 2) {
  app.setAsDefaultProtocolClient(PROTOCOLO, process.execPath, [path.resolve(process.argv[1])]);
} else {
  app.setAsDefaultProtocolClient(PROTOCOLO);
}

// En Mac los enlaces antiexamen5:// llegan por este evento, no por argv.
let enlacePendiente = null;
app.on('open-url', (e, enlace) => {
  e.preventDefault();
  if (!app.isReady() || !principal) { enlacePendiente = enlace; return; }
  if (!sesion) abrirEnlace(enlace);
  principal.show(); principal.focus();
});

// antiexamen5://e/abc-def  ->  rendir.html?c=abc-def
function urlDeEnlace(enlace) {
  const m = /^antiexamen5:\/\/e\/([a-z]{3}-?[a-z]{3})/i.exec(enlace || '');
  return SITIO + '/rendir.html' + (m ? '?c=' + m[1].toLowerCase() : '');
}
function abrirEnlace(enlace) { if (principal) principal.loadURL(urlDeEnlace(enlace)); }

const esHttps = (u) => { try { return new URL(u).protocol === 'https:'; } catch (e) { return false; } };
function abrirFuera(url) { if (esHttps(url)) shell.openExternal(url); }
const vivo = (w) => w && !w.isDestroyed();
const nuestras = () => BrowserWindow.getAllWindows();

/* ---------------------------------------------------- avisar a la pagina */
function evento(datos) {
  if (vivo(principal)) principal.webContents.send('ae5:evento', datos);
}

/* ===================================================== 1. PRINCIPAL */
function crearPrincipal(url) {
  principal = new BrowserWindow({
    width: 1180, height: 820, minWidth: 380, minHeight: 560,
    backgroundColor: '#11121b', title: 'ANTI.EXAMEN5', show: false, autoHideMenuBar: true,
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true, sandbox: true, nodeIntegration: false,
      backgroundThrottling: false, devTools: DEPURAR, spellcheck: true
    }
  });
  principal.setContentProtection(true);
  principal.once('ready-to-show', () => principal.show());
  principal.loadURL(url || SITIO + '/rendir.html');

  const wc = principal.webContents;
  wc.on('will-navigate', (e, destino) => {
    try { if (new URL(destino).origin === ORIGEN) return; } catch (x) { /* nada */ }
    e.preventDefault();
    if (!sesion) abrirFuera(destino);
  });
  wc.setWindowOpenHandler(({ url: destino }) => { if (!sesion) abrirFuera(destino); return { action: 'deny' }; });
  wc.on('did-fail-load', (_e, codigo, _d, urlFallida, esPrincipal) => {
    if (!esPrincipal || codigo === -3) return; // -3: navegacion cancelada
    principal.loadFile(path.join(__dirname, 'sin-conexion.html'), { query: { volver: urlFallida || SITIO + '/rendir.html' } });
  });
  bloquearAtajos(wc, false);

  principal.on('close', (e) => {
    if (sesion && !cerrandoTodo) {
      e.preventDefault();
      const r = dialog.showMessageBoxSync(principal, {
        type: 'warning', buttons: ['Seguir en el examen', 'Cerrar igual'], defaultId: 0, cancelId: 0,
        title: 'ANTI.EXAMEN5', message: 'Tu examen sigue en curso.',
        detail: 'Si cierras la aplicación, se registra como salida y cuenta como advertencia al volver.'
      });
      if (r === 1) { cerrandoTodo = true; cerrarExamen(); principal.close(); }
    }
  });
  principal.on('closed', () => { principal = null; });
}

/* ===================================================== 2. EXAMEN */
function abrirExamen() {
  if (vivo(examen)) { examen.show(); examen.focus(); return; }
  const area = screen.getPrimaryDisplay().workArea;
  examen = new BrowserWindow({
    x: area.x, y: area.y, width: area.width, height: area.height,
    backgroundColor: '#ffffff', title: sesion.titulo + ' · ANTI.EXAMEN5', autoHideMenuBar: true,
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: {
      partition: 'examen', // en memoria: al cerrar la app no queda ninguna sesion iniciada
      preload: path.join(__dirname, 'examen-preload.js'),
      contextIsolation: true, sandbox: true, nodeIntegration: false, nodeIntegrationInSubFrames: true,
      devTools: DEPURAR, spellcheck: true, safeDialogs: true
    }
  });
  examen.setContentProtection(true); // las capturas y transmisiones ven la ventana en negro
  examen.maximize();
  vigilarContenido(examen.webContents);
  examen.loadURL(sesion.url);
  examen.on('page-title-updated', (e) => { e.preventDefault(); });
  // Cerrar la ventana del examen no termina nada: vuelve al panel.
  examen.on('close', (e) => {
    if (sesion && !cerrandoTodo) { e.preventDefault(); examen.hide(); mostrarPrincipal(); }
  });
  examen.on('closed', () => { examen = null; });
}

function vigilarContenido(wc) {
  wc.on('will-navigate', (e, destino) => {
    if (sesion && !sesion.permitido(destino)) {
      e.preventDefault();
      evento({ tipo: 'falta', falta: 'NAVEGACION_BLOQUEADA', detalle: hostDe(destino) });
    }
  });
  wc.setWindowOpenHandler(({ url: destino }) => {
    if (sesion && sesion.permitido(destino)) {
      // Moodle a veces abre el intento en una ventana emergente: se abre
      // dentro de la app, con las mismas reglas.
      return { action: 'allow', overrideBrowserWindowOptions: {
        parent: examen, autoHideMenuBar: true, backgroundColor: '#ffffff',
        webPreferences: { partition: 'examen', preload: path.join(__dirname, 'examen-preload.js'), contextIsolation: true, sandbox: true, nodeIntegrationInSubFrames: true, devTools: DEPURAR }
      } };
    }
    evento({ tipo: 'falta', falta: 'NAVEGACION_BLOQUEADA', detalle: hostDe(destino) });
    return { action: 'deny' };
  });
  wc.on('did-create-window', (w) => { w.setContentProtection(true); vigilarContenido(w.webContents); });
  bloquearAtajos(wc, true);
}

const hostDe = (u) => { try { return new URL(u).hostname; } catch (e) { return 'página desconocida'; } };

/* --------------------------------------------- teclado y portapapeles */
function bloquearAtajos(wc, esExamen) {
  wc.on('before-input-event', (e, k) => {
    if (k.type !== 'keyDown') return;
    const ctrl = k.control || k.meta;
    const tecla = (k.key || '').toLowerCase();
    const herramientas = tecla === 'f12' || (ctrl && k.shift && ['i', 'j', 'c', 'k'].includes(tecla));
    if (herramientas || (ctrl && tecla === 'u')) {
      e.preventDefault();
      if (sesion) evento({ tipo: 'falta', falta: 'ATAJO_BLOQUEADO', detalle: 'Herramientas de desarrollo' });
      return;
    }
    if (tecla === 'printscreen') {
      if (sesion) evento({ tipo: 'falta', falta: 'CAPTURA_PANTALLA', detalle: 'Tecla Imprimir pantalla' });
      return;
    }
    if (!esExamen || !sesion) return;
    if (ctrl && tecla === 'p') {
      e.preventDefault();
      evento({ tipo: 'falta', falta: 'INTENTO_IMPRIMIR', detalle: 'Ctrl+P' });
    }
    // Copiar y pegar se vigilan en examen-preload.js: asi se atrapan tambien
    // los que vienen del menu o del raton, no solo del teclado.
  });
}

/* ====================================================== 3. BURBUJA */
function crearBurbuja() {
  if (vivo(burbuja)) { burbuja.showInactive(); return; }
  const area = screen.getPrimaryDisplay().workArea;
  const ancho = 248, alto = 148;
  burbuja = new BrowserWindow({
    width: ancho, height: alto, x: area.x + area.width - ancho - 16, y: area.y + area.height - alto - 16,
    frame: false, resizable: false, minimizable: false, maximizable: false, fullscreenable: false,
    alwaysOnTop: true, skipTaskbar: true, show: false, backgroundColor: '#11121b', hasShadow: true,
    title: 'ANTI.EXAMEN5',
    webPreferences: {
      preload: path.join(__dirname, 'burbuja-preload.js'),
      contextIsolation: true, sandbox: true, nodeIntegration: false, devTools: DEPURAR
    }
  });
  burbuja.setAlwaysOnTop(true, 'screen-saver');
  burbuja.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });
  burbuja.loadFile(path.join(__dirname, 'burbuja.html'));
  burbuja.webContents.on('will-navigate', (e) => e.preventDefault());
  burbuja.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  burbuja.once('ready-to-show', () => { burbuja.showInactive(); if (ultimoAviso) burbuja.webContents.send('ae5:estado', ultimoAviso); });
  burbuja.on('close', (e) => { if (sesion && !cerrandoTodo) e.preventDefault(); });
  burbuja.on('closed', () => { burbuja = null; });
}

/* =================================================== foco de la app
   Se mira la app entera, no una ventana: pasar del examen a la burbuja o
   al panel no es salir. Salir es que el foco quede fuera de la app. */
let revisionFoco = 0;
function revisarFoco() {
  clearTimeout(revisionFoco);
  revisionFoco = setTimeout(() => {
    if (!sesion) return;
    const ahora = nuestras().some((w) => !w.isDestroyed() && w.isFocused());
    if (ahora === enApp) return;
    enApp = ahora;
    if (!ahora) evento({ tipo: 'salida', falta: 'CAMBIO_APLICACION', detalle: 'Salió de la app del examen' });
    else evento({ tipo: 'regreso' });
  }, 140);
}
app.on('browser-window-blur', revisarFoco);
app.on('browser-window-focus', revisarFoco);

/* ------------------------------------------------- pantallas extra */
function revisarPantallas(motivo) {
  const n = screen.getAllDisplays().length;
  if (sesion && n > 1) evento({ tipo: 'falta', falta: 'PANTALLA_EXTRA', detalle: n + ' pantallas' + (motivo ? ' (' + motivo + ')' : '') });
}

/* ================================================ mensajes de la pagina */
function deLaPagina(e) {
  try { return vivo(principal) && e.sender === principal.webContents && new URL(e.senderFrame.url).origin === ORIGEN; }
  catch (x) { return false; }
}
function deLaBurbuja(e) { return vivo(burbuja) && e.sender === burbuja.webContents; }

ipcMain.on('ae5:iniciar', (e, info) => {
  if (!deLaPagina(e) || !info || !esHttps(info.url)) return;
  const nueva = !sesion;
  sesion = {
    url: info.url, permitido: crearPermitidos(info.url),
    titulo: String(info.titulo || 'Examen').slice(0, 160),
    alumno: String(info.alumno || '').slice(0, 200)
  };
  enApp = true;
  if (nueva) clipboard.clear(); // lo copiado antes del examen no se puede pegar
  abrirExamen();
  crearBurbuja();
  if (vivo(principal)) principal.hide();
  if (nueva) setTimeout(() => revisarPantallas(''), 1500);
});
ipcMain.on('ae5:estado', (e, est) => {
  if (!deLaPagina(e)) return;
  ultimoAviso = Object.assign({}, est, { titulo: sesion && sesion.titulo, alumno: sesion && sesion.alumno });
  if (vivo(burbuja)) burbuja.webContents.send('ae5:estado', ultimoAviso);
});
ipcMain.on('ae5:aviso', (e, a) => {
  if (!deLaPagina(e) || !vivo(burbuja)) return;
  burbuja.webContents.send('ae5:aviso', a);
});
ipcMain.on('ae5:mostrar-examen', (e) => { if (deLaPagina(e) || deLaBurbuja(e)) { if (sesion) abrirExamen(); } });
ipcMain.on('ae5:mostrar-principal', (e) => { if (deLaPagina(e)) mostrarPrincipal(); });
ipcMain.on('ae5:terminar', (e) => { if (deLaPagina(e)) terminarSesion(); });
ipcMain.on('ae5:burbuja-terminar', (e) => { if (deLaBurbuja(e)) { mostrarPrincipal(); evento({ tipo: 'entregar' }); } });
ipcMain.on('ae5:burbuja-tamano', (e, alto) => {
  if (!deLaBurbuja(e)) return;
  const b = burbuja.getBounds();
  const nuevo = Math.max(120, Math.min(320, Math.round(Number(alto) || 148)));
  burbuja.setBounds({ x: b.x, y: b.y + b.height - nuevo, width: b.width, height: nuevo });
});
ipcMain.on('ae5:examen-falta', (e, f) => {
  if (!sesion || !f || !['COPIAR', 'PEGAR'].includes(f.falta)) return;
  const w = BrowserWindow.fromWebContents(e.sender);
  if (!w || w === principal || w === burbuja) return;
  evento({ tipo: 'falta', falta: f.falta, detalle: String(f.detalle || '').slice(0, 120) });
});
ipcMain.on('ae5:burbuja-lista', (e) => { if (deLaBurbuja(e)) evento({ tipo: 'pedir-estado' }); });

function mostrarPrincipal() {
  if (!vivo(principal)) return;
  principal.show();
  principal.focus();
}
function cerrarExamen() {
  const s = sesion; sesion = null;
  if (vivo(examen)) examen.destroy();
  if (vivo(burbuja)) burbuja.destroy();
  if (s) session.fromPartition('examen').clearStorageData().catch(() => {});
}
function terminarSesion() {
  cerrarExamen();
  mostrarPrincipal();
}

/* ============================================================ inicio */
app.whenReady().then(() => {
  // Ningun sitio dentro de la app puede pedir camara, microfono ni ubicacion.
  for (const s of [session.defaultSession, session.fromPartition('examen')]) {
    s.setPermissionRequestHandler((wc, permiso, responder) => {
      const desdeSitio = (() => { try { return new URL(wc.getURL()).origin === ORIGEN; } catch (e) { return false; } })();
      responder(desdeSitio && (permiso === 'fullscreen' || permiso === 'clipboard-sanitized-write'));
    });
  }
  screen.on('display-added', () => revisarPantallas('se conectó durante el examen'));
  const enlace = enlacePendiente || process.argv.find((a) => a.startsWith(PROTOCOLO + '://'));
  crearPrincipal(enlace ? urlDeEnlace(enlace) : null);
});

app.on('window-all-closed', () => app.quit());
app.on('web-contents-created', (_e, wc) => {
  wc.on('will-attach-webview', (e) => e.preventDefault());
});

/* Corre dentro de la ventana del examen externo, en un mundo aislado: la
   pagina de Google Forms o Moodle no puede verlo ni tocarlo. Solo escucha
   copiar, cortar y pegar, venga del teclado, del menu o del raton.
   - Copiar o cortar dentro de un campo de respuesta: permitido (es suyo).
   - Copiar el enunciado: se bloquea y se anota.
   - Pegar algo que no salio de sus propias respuestas: se bloquea y se anota. */
'use strict';
const { ipcRenderer } = require('electron');

let propio = null;

function editable(nodo) {
  let n = nodo && nodo.nodeType === 3 ? nodo.parentElement : nodo;
  if (!n) n = document.activeElement;
  for (; n; n = n.parentElement) {
    if (n.isContentEditable) return true;
    if (n.tagName === 'TEXTAREA') return true;
    if (n.tagName === 'INPUT' && /^(text|search|email|number|url|tel|)$/i.test(n.getAttribute('type') || '')) return true;
  }
  return false;
}

function textoSeleccionado(objetivo) {
  const a = document.activeElement;
  if (a && (a.tagName === 'TEXTAREA' || a.tagName === 'INPUT') && typeof a.selectionStart === 'number') {
    return a.value.slice(a.selectionStart, a.selectionEnd);
  }
  return String(window.getSelection() || '');
}

function alCopiar(e) {
  const dentro = editable(e.target) || editable(document.activeElement);
  if (dentro) { propio = textoSeleccionado(e.target); return; }
  e.preventDefault();
  e.stopImmediatePropagation();
  if (e.type === 'copy') ipcRenderer.send('ae5:examen-falta', { falta: 'COPIAR', detalle: 'Copió texto del examen' });
}

function alPegar(e) {
  const t = (e.clipboardData && e.clipboardData.getData('text')) || '';
  if (!t || (propio !== null && t === propio)) return;
  e.preventDefault();
  e.stopImmediatePropagation();
  ipcRenderer.send('ae5:examen-falta', { falta: 'PEGAR', detalle: 'Texto de afuera (' + t.length + ' caracteres)' });
}

window.addEventListener('copy', alCopiar, true);
window.addEventListener('cut', alCopiar, true);
window.addEventListener('paste', alPegar, true);
window.addEventListener('drop', (e) => {
  const t = e.dataTransfer && e.dataTransfer.getData('text');
  if (t && t !== propio) {
    e.preventDefault();
    ipcRenderer.send('ae5:examen-falta', { falta: 'PEGAR', detalle: 'Arrastró texto de afuera' });
  }
}, true);

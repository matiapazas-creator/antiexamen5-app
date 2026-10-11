'use strict';
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('burbuja', {
  alEstado: (cb) => ipcRenderer.on('ae5:estado', (_e, d) => cb(d)),
  alAviso: (cb) => ipcRenderer.on('ae5:aviso', (_e, d) => cb(d)),
  verExamen: () => ipcRenderer.send('ae5:mostrar-examen'),
  terminar: () => ipcRenderer.send('ae5:burbuja-terminar'),
  tamano: (alto) => ipcRenderer.send('ae5:burbuja-tamano', alto),
  lista: () => ipcRenderer.send('ae5:burbuja-lista')
});

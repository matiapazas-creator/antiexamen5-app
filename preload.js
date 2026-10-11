/* Puente entre la pagina de antiexamen5.com y la app. Solo se expone si la
   pagina es de verdad antiexamen5.com (o el sitio configurado). */
'use strict';
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('ae5App', {
  version: '1.0.0',
  iniciar: (info) => ipcRenderer.send('ae5:iniciar', info),
  estado: (e) => ipcRenderer.send('ae5:estado', e),
  aviso: (a) => ipcRenderer.send('ae5:aviso', a),
  mostrarExamen: () => ipcRenderer.send('ae5:mostrar-examen'),
  mostrarPrincipal: () => ipcRenderer.send('ae5:mostrar-principal'),
  terminar: () => ipcRenderer.send('ae5:terminar'),
  alEvento: (cb) => { ipcRenderer.on('ae5:evento', (_e, d) => cb(d)); }
});

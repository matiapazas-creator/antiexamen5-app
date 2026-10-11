/* Que paginas puede abrir la ventana del examen.
   - Google Forms y Microsoft Forms: solo sus paginas de formularios y de
     inicio de sesion (no el buscador de Google ni Bing).
   - Cualquier otra plataforma (Moodle de la universidad, etc.): todo el
     sitio del examen, por ejemplo *.upea.edu.bo, mas el inicio de sesion
     de Google o Microsoft que muchos Moodle usan.
   Todo lo demas (buscadores, YouTube, ChatGPT...) se bloquea y se anota. */
'use strict';

// Segundos niveles tipicos de dominios de pais: upea.edu.bo -> sitio "upea.edu.bo".
const SEGUNDO_NIVEL = new Set(['com', 'edu', 'gob', 'gov', 'org', 'net', 'ac', 'co', 'mil', 'int', 'nom', 'sch']);

function sitioDe(host) {
  const p = String(host || '').toLowerCase().replace(/\.$/, '').split('.').filter(Boolean);
  if (p.length <= 2) return p.join('.');
  const ultimo = p[p.length - 1], penultimo = p[p.length - 2];
  if (ultimo.length === 2 && SEGUNDO_NIVEL.has(penultimo)) return p.slice(-3).join('.');
  return p.slice(-2).join('.');
}

const INICIO_SESION = ['accounts.google.com', 'accounts.youtube.com', 'login.microsoftonline.com', 'login.live.com', 'login.microsoft.com'];

// Plataformas grandes: se permiten paginas concretas, no el dominio entero.
const FAMILIAS = [
  { reconoce: ['docs.google.com', 'forms.gle', 'forms.google.com'],
    paginas: ['docs.google.com', 'forms.gle', 'forms.google.com'] },
  { reconoce: ['forms.office.com', 'forms.cloud.microsoft', 'forms.microsoft.com'],
    paginas: ['forms.office.com', 'forms.cloud.microsoft', 'forms.microsoft.com'] }
];

function crearPermitidos(urlExamen) {
  const u = new URL(urlExamen);
  const host = u.hostname.toLowerCase();
  const familia = FAMILIAS.find((f) => f.reconoce.includes(host));
  const exactos = new Set(INICIO_SESION.concat(familia ? familia.paginas : []));
  const sitio = familia ? null : sitioDe(host);

  return function permitido(url) {
    let x;
    try { x = new URL(url); } catch (e) { return false; }
    if (x.protocol === 'about:' || x.protocol === 'blob:' || x.protocol === 'data:') return true;
    if (x.protocol !== 'https:') return false;
    const h = x.hostname.toLowerCase();
    if (exactos.has(h)) return true;
    return !!sitio && sitioDe(h) === sitio;
  };
}

module.exports = { sitioDe, crearPermitidos };

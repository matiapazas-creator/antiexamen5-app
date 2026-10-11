'use strict';
(function () {
  const $ = (id) => document.getElementById(id);
  const B = window.burbuja;
  let total = 0, tAviso = 0;

  const mmss = (s) => {
    if (s === null || s === undefined) return '--:--';
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60;
    const z = (n) => String(n).padStart(2, '0');
    return h ? h + ':' + z(m) + ':' + z(x) : z(m) + ':' + z(x);
  };

  B.alEstado((e) => {
    if (!e) return;
    if (typeof e.resta === 'number') {
      if (e.resta > total) total = e.resta;
      $('reloj').textContent = mmss(e.resta);
      $('reloj').className = 'b-reloj mono' + (e.resta <= 60 ? ' mal' : e.resta <= 300 ? ' avi' : '');
      $('barra').style.width = total ? Math.min(100, ((total - e.resta) / total) * 100) + '%' : '0';
    }
    if (typeof e.faltas === 'number') {
      $('faltas').textContent = e.faltas + '/' + e.limite;
      $('faltas-caja').className = 'b-faltas' + (e.faltas >= e.limite ? ' mal' : e.faltas > 0 ? ' avi' : '');
    }
    $('cab').classList.toggle('sin-red', e.red === false);
    $('cab-texto').textContent = e.red === false ? 'Sin conexión: se guarda y reenvía' : 'Supervisión activa';
  });

  B.alAviso((a) => {
    if (!a) return;
    const caja = $('aviso');
    caja.className = 'b-aviso ' + (a.tipo || 'info');
    $('aviso-titulo').textContent = a.titulo || '';
    $('aviso-texto').textContent = a.texto || '';
    caja.hidden = false;
    B.tamano(document.body.scrollHeight);
    requestAnimationFrame(() => {
      B.tamano(148 + caja.offsetHeight + 12);
      caja.classList.add('ver');
    });
    clearTimeout(tAviso);
    tAviso = setTimeout(() => {
      caja.classList.remove('ver');
      setTimeout(() => { caja.hidden = true; B.tamano(148); }, 260);
    }, a.tipo === 'grave' ? 9000 : 6000);
  });

  $('ver').addEventListener('click', () => B.verExamen());
  $('terminar').addEventListener('click', () => B.terminar());
  B.lista();
})();

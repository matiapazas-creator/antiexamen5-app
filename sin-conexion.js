'use strict';
document.getElementById('reintentar').addEventListener('click', () => {
  const volver = new URLSearchParams(location.search).get('volver') || '';
  if (/^https:\/\//.test(volver)) location.href = volver;
  else location.reload();
});

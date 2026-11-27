// Affiche l'heure de fin d'après l'heure de début et la durée fixe de la classe.
(function () {
  function maj(sel) {
    var cible = sel.closest('.creneau-ligne').querySelector('.fin-seance');
    if (!sel.value) { cible.textContent = 'Fin : —'; return; }
    var p = sel.value.split(':'), fin = parseInt(p[0], 10) * 60 + parseInt(p[1], 10) + parseInt(sel.dataset.duree, 10);
    cible.textContent = 'Fin : ' + String(Math.floor(fin / 60)).padStart(2, '0') + 'h' + String(fin % 60).padStart(2, '0');
  }
  document.querySelectorAll('.heure-debut').forEach(function (s) { s.addEventListener('change', function () { maj(s); }); maj(s); });
})();

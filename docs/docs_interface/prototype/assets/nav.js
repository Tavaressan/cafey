// Caféy — chrome de navegação do protótipo.
// Injeta a barra lateral (desktop), o trilho de ícones (tablet) e a barra de abas (mobile)
// a partir de `data-nav` no elemento `.app`. Um só arquivo para as cinco telas.

(function () {
  // [chave, rótulo curto (abas), rótulo longo (lateral), arquivo, ícone de traço]
  var ITEMS = [
    ['inicio', 'Início', 'Início', 'home.html',
      '<path d="M4 11l8-6 8 6v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z"></path>'],
    ['agenda', 'Agenda', 'Agendamentos', 'schedule.html',
      '<rect x="4" y="6" width="16" height="14" rx="2.5"></rect><path d="M8 3.5v4M16 3.5v4M4 11h16"></path>'],
    ['ritmo', 'Ritmo', 'Ritmo', 'rhythm.html',
      '<path d="M4 19.5h16M5 15l4-5 3.5 3L18 6"></path>'],
    ['cuidados', 'Cuidados', 'Cuidados', 'care.html',
      '<path d="M12 3.5s5.8 6.3 5.8 10.2A5.8 5.8 0 0 1 6.2 13.7C6.2 9.8 12 3.5 12 3.5z"></path>'],
    ['base', 'Base', 'Base', 'base.html',
      '<rect x="7.5" y="7.5" width="9" height="9" rx="2"></rect><path d="M10 4.5v3M14 4.5v3M10 16.5v3M14 16.5v3M4.5 10h3M4.5 14h3M16.5 10h3M16.5 14h3"></path>']
  ];

  var STAR = '<svg class="star star--blue" viewBox="220 220 72 72" aria-hidden="true">' +
    '<path d="M 256 226 Q 263 249 286 256 Q 263 263 256 286 Q 249 263 226 256 Q 249 249 256 226 Z"></path></svg>';

  // Marca oficial; o caminho é relativo à página que injeta o chrome, não a este arquivo.
  var MARK = '<img src="../logo/cafey-logo.svg" alt="" width="34" height="34" style="flex:none">';

  function icon(paths, cls) {
    return '<svg class="' + (cls || '') + '" viewBox="0 0 24 24" aria-hidden="true">' + paths + '</svg>';
  }

  function sidebar(active, note) {
    var nav = ITEMS.map(function (it) {
      var on = it[0] === active;
      return '<a class="sidebar__item' + (on ? ' is-on' : '') + '" href="' + it[3] + '">' +
        icon(it[4]) + '<span>' + it[2] + '</span>' +
        (on ? STAR.replace('star star--blue', 'star star--sm star--blue push') : '') +
        (!on && it[0] === 'cuidados' ? '<span class="sidebar__badge">1</span>' : '') +
        '</a>';
    }).join('');

    return '<aside class="sidebar">' +
      '<div class="sidebar__brand">' + MARK + '<span>Caféy</span></div>' +
      '<nav class="sidebar__nav">' + nav + '</nav>' +
      '<div class="sidebar__card">' +
        '<div class="device__state"><span class="dot"></span>Online · Wi-Fi</div>' +
        '<b>Base da cozinha</b><small>' + note + '</small>' +
      '</div></aside>';
  }

  function rail(active) {
    var items = ITEMS.map(function (it) {
      var on = it[0] === active;
      return '<a class="rail__item' + (on ? ' is-on' : '') + '" href="' + it[3] + '" aria-label="' + it[2] + '">' +
        icon(it[4]) + (on ? STAR : '') + '</a>';
    }).join('');
    return '<aside class="rail">' + MARK + '<div style="height:18px"></div>' + items +
      '<span class="rail__dot"></span></aside>';
  }

  function tabbar(active) {
    var items = ITEMS.map(function (it) {
      var on = it[0] === active;
      return '<a class="tabbar__item' + (on ? ' is-on' : '') + '" href="' + it[3] + '">' +
        icon(it[4]) + '<span>' + it[1] + '</span></a>';
    }).join('');
    return '<nav class="tabbar">' + items + '</nav>';
  }

  var app = document.querySelector('.app');
  if (!app) return;

  var active = app.getAttribute('data-nav') || 'inicio';
  var note = app.getAttribute('data-nav-note') || '';

  app.insertAdjacentHTML('afterbegin', sidebar(active, note) + rail(active));
  document.body.insertAdjacentHTML('beforeend', tabbar(active));
})();

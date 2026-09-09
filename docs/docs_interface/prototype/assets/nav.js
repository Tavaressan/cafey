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

  // CafeyMark — grão em contorno com a estrela ao centro (CafeyMark.dc.html).
  var MARK = '<svg viewBox="112 62 288 388" width="24" height="32" aria-hidden="true" style="flex:none">' +
    '<g fill="none" stroke="#A8412A" stroke-linecap="round" stroke-linejoin="round">' +
    '<path d="M 256 86 C 322 86 372 162 372 252 C 372 348 320 426 256 426 C 192 426 140 348 140 252 C 140 162 190 86 256 86 Z" stroke-width="17"></path>' +
    '<path d="M 256 104 C 252.1 107.6 236.3 118.5 232.5 125.7 C 228.7 133 232.7 140.2 233 147.4 C 233.2 154.7 234 161.9 233.8 169.1 C 233.7 176.4 233.4 183.6 232.3 190.9 C 231.2 198.1 229.5 205.3 227.3 212.6 C 225.2 219.8 222.3 227 219.4 234.3 C 216.5 241.5 213 248.8 210 256 C 207 263.2 203.7 270.5 201.3 277.7 C 198.9 285 196.7 292.2 195.5 299.4 C 194.3 306.7 193.8 313.9 194.3 321.1 C 194.7 328.4 196.1 335.6 198.3 342.9 C 200.5 350.1 203.6 357.3 207.4 364.6 C 211.2 371.8 212.8 379 220.9 386.3 C 229 393.5 250.1 404.4 256 408" stroke-width="14"></path>' +
    '<path d="M 256 104 C 261.9 107.6 283 118.5 291.1 125.7 C 299.2 133 300.8 140.2 304.6 147.4 C 308.4 154.7 311.5 161.9 313.7 169.1 C 315.9 176.4 317.3 183.6 317.7 190.9 C 318.2 198.1 317.7 205.3 316.5 212.6 C 315.3 219.8 313.1 227 310.7 234.3 C 308.3 241.5 305 248.8 302 256 C 299 263.2 295.5 270.5 292.6 277.7 C 289.7 285 286.8 292.2 284.7 299.4 C 282.5 306.7 280.8 313.9 279.7 321.1 C 278.6 328.4 278.3 335.6 278.2 342.9 C 278 350.1 278.8 357.3 279 364.6 C 279.3 371.8 283.3 379 279.5 386.3 C 275.7 393.5 259.9 404.4 256 408" stroke-width="14"></path></g>' +
    '<path d="M 256 226 Q 263 249 286 256 Q 263 263 256 286 Q 249 263 226 256 Q 249 249 256 226 Z" fill="#6E8FBC"></path></svg>';

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

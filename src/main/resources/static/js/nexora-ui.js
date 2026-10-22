/* NEXORA shared progressive enhancements. This file intentionally adds no floating panels,
   injected navigation controls, command palettes, or content-hiding behaviour. */
(() => {
  'use strict';
  const path = window.location.pathname || '/';
  const pageKey = (path.split('/').filter(Boolean)[0] || 'home').replace(/[^a-z0-9-]/gi, '');
  document.body.classList.add('nx-page-' + pageKey);
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const hasNexoraUiStyles = Array.from(document.querySelectorAll('link[rel="stylesheet"]'))
    .some(link => (link.getAttribute('href') || '').includes('/css/nexora-ui.css'));

  // A few legacy templates use simple direct sidebar links. Add icons only to those links;
  // never replace nested navigation markup, labels, or active-state attributes.
  if (hasNexoraUiStyles) {
    const icons = {
      '/dashboard': '⌂', '/academic': '◈', '/career': '✦', '/career-gps': '◎',
      '/evidence': '◇', '/achievements': '✹', '/documents': '▣',
      '/integrations': '⌁', '/development': '↗', '/leaderboard': '♛',
      '/pricing': '✧', '/profile': '◉', '/logout': '⇥'
    };
    document.querySelectorAll('.sidebar > a:not(.nx-brand)').forEach(link => {
      if (link.querySelector('span, b') || link.dataset.iconized) return;
      const href = link.getAttribute('href') || '';
      const label = link.textContent.trim();
      if (!label) return;
      const icon = icons[href] || '•';
      link.textContent = '';
      const symbol = document.createElement('span');
      symbol.className = 'nx-side-icon';
      symbol.setAttribute('aria-hidden', 'true');
      symbol.textContent = icon;
      const text = document.createElement('span');
      text.className = 'nx-side-label';
      text.textContent = label;
      link.append(symbol, text);
      link.title = label;
      link.dataset.iconized = '1';
    });
    const favicon = document.querySelector('link[rel="icon"]') || document.createElement('link');
    favicon.rel = 'icon';
    favicon.type = 'image/svg+xml';
    favicon.href = '/images/nexora-logo.svg';
    if (!favicon.isConnected) document.head.appendChild(favicon);

    if (!reducedMotion && window.matchMedia('(hover:hover) and (pointer:fine)').matches) {
      document.querySelectorAll('.card,.stat,.subject-card,.feature-card,.resource-section,.analysis-card,.topic-card,.unit-card,.dependency-card,.repository-card,.academic-card,.metric-grid>div,.plan-card')
        .forEach(card => {
          card.addEventListener('pointermove', event => {
            const rect = card.getBoundingClientRect();
            card.style.setProperty('--nx-card-x', (event.clientX - rect.left) + 'px');
            card.style.setProperty('--nx-card-y', (event.clientY - rect.top) + 'px');
          }, { passive: true });
        });
    }
  }
})();

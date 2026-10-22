/* Shared page-level search and small UX helpers for NEXORA portal pages. */
(() => {
  'use strict';

  const normalise = value => String(value || '').toLocaleLowerCase().trim();

  function connectLiveFilter(inputId, itemSelector, emptyId) {
    const input = document.getElementById(inputId);
    const items = Array.from(document.querySelectorAll(itemSelector));
    const empty = emptyId ? document.getElementById(emptyId) : null;
    if (!input || !items.length) return;

    const apply = () => {
      const query = normalise(input.value);
      let visible = 0;
      items.forEach(item => {
        const haystack = normalise(item.getAttribute('data-search') || item.textContent);
        const matches = !query || haystack.includes(query);
        item.hidden = !matches;
        if (matches) visible += 1;
      });
      if (empty) empty.hidden = visible !== 0;
    };

    input.addEventListener('input', apply);
    const form = input.closest('form');
    if (form) form.addEventListener('submit', event => {
      // These catalog searches filter already-loaded, sourced records locally.
      event.preventDefault();
      apply();
    });
    apply();
  }

  connectLiveFilter('resourceSearch', '.portal-resource', 'noResourceResults');
  connectLiveFilter('jobSearch', '.portal-job', 'noJobResults');

  // Honour keyboard focus styles, and provide a quick search shortcut where a search exists.
  document.addEventListener('keydown', event => {
    if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== 'k') return;
    const input = document.getElementById('resourceSearch') || document.getElementById('jobSearch');
    if (!input) return;
    event.preventDefault();
    input.focus();
    input.select();
  });
})();

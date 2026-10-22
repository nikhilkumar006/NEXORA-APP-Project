(() => {
  'use strict';
  const input = document.getElementById('careerSearch');
  const results = document.getElementById('careerSearchResults');
  const index = document.getElementById('careerSearchIndex');
  if (input && results && index) {
    const norm = (value) => String(value || '').normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
    const records = Array.from(index.querySelectorAll('a[data-label][data-href]')).map((el) => ({
      label: el.dataset.label || '', subtitle: el.dataset.subtitle || '', kind: el.dataset.kind || '',
      href: el.dataset.href || el.getAttribute('href') || '/career',
      search: norm(`${el.dataset.label || ''} ${el.dataset.search || ''} ${el.dataset.subtitle || ''}`)
    }));
    const score = (record, q) => {
      const label = norm(record.label);
      if (label === q) return 0;
      if (label.startsWith(q)) return 1;
      if (label.includes(q)) return 2;
      if (record.search.split(' ').some((token) => token === q)) return 3;
      if (record.search.includes(q)) return 4;
      return 99;
    };
    let activeIndex = -1;
    let shown = [];
    const hide = () => { results.hidden = true; results.replaceChildren(); input.setAttribute('aria-expanded', 'false'); activeIndex = -1; shown = []; };
    const choose = (record) => { if (record && /^\/career\/(role|family|company|location|skill)\/[a-z0-9-]+$/.test(record.href)) window.location.assign(record.href); };
    const draw = () => {
      const q = norm(input.value);
      results.replaceChildren(); activeIndex = -1;
      if (!q) { hide(); return; }
      shown = records.map((record) => ({...record, score: score(record, q)})).filter((record) => record.score < 99)
        .sort((a, b) => a.score - b.score || a.label.localeCompare(b.label)).slice(0, 8);
      if (!shown.length) {
        const empty = document.createElement('div'); empty.className = 'career-no-results'; empty.textContent = 'No matching careers, skills, companies or locations. Try another term.'; results.appendChild(empty);
      } else {
        shown.forEach((record, position) => {
          const button = document.createElement('button'); button.type = 'button'; button.className = 'career-search-result'; button.setAttribute('role', 'option'); button.setAttribute('aria-selected', 'false');
          const mark = document.createElement('span'); mark.className = 'career-result-mark'; mark.setAttribute('aria-hidden', 'true'); mark.textContent = record.label.trim().charAt(0).toUpperCase() || 'N';
          const copy = document.createElement('span'); copy.className = 'career-result-copy';
          const label = document.createElement('b'); label.textContent = record.label;
          const subtitle = document.createElement('small'); subtitle.textContent = record.subtitle;
          copy.append(label, subtitle);
          const kind = document.createElement('span'); kind.className = 'career-result-kind'; kind.textContent = record.kind;
          button.append(mark, copy, kind); button.addEventListener('click', () => choose(record));
          button.addEventListener('mousemove', () => setActive(position)); results.appendChild(button);
        });
      }
      results.hidden = false; input.setAttribute('aria-expanded', 'true');
    };
    const setActive = (position) => {
      const buttons = Array.from(results.querySelectorAll('.career-search-result'));
      buttons.forEach((button, index) => { const active = index === position; button.classList.toggle('is-active', active); button.setAttribute('aria-selected', String(active)); });
      activeIndex = position;
    };
    input.addEventListener('input', draw);
    input.addEventListener('focus', draw);
    input.addEventListener('keydown', (event) => {
      if (results.hidden) { if (event.key === 'Enter') { input.blur(); } return; }
      const buttons = Array.from(results.querySelectorAll('.career-search-result'));
      if (event.key === 'ArrowDown') { event.preventDefault(); setActive(Math.min(activeIndex + 1, buttons.length - 1)); buttons[activeIndex]?.scrollIntoView({block: 'nearest'}); }
      else if (event.key === 'ArrowUp') { event.preventDefault(); setActive(Math.max(activeIndex - 1, 0)); buttons[activeIndex]?.scrollIntoView({block: 'nearest'}); }
      else if (event.key === 'Enter') { if (activeIndex >= 0) { event.preventDefault(); choose(shown[activeIndex]); } else if (shown[0]) { event.preventDefault(); choose(shown[0]); } }
      else if (event.key === 'Escape') hide();
    });
    document.addEventListener('click', (event) => { if (!event.target.closest('.career-search-wrap')) hide(); });
    document.querySelectorAll('[data-search-preset]').forEach((button) => button.addEventListener('click', () => { input.value = button.dataset.searchPreset || ''; draw(); input.focus(); }));
  }

  const filter = document.getElementById('roleFilter');
  const catalogue = document.getElementById('roleCatalogue') || document.getElementById('familyRoleCatalogue');
  const sort = document.getElementById('roleSort');
  const empty = document.getElementById('roleEmpty');
  if (filter && catalogue) {
    const rows = Array.from(catalogue.querySelectorAll('.role-catalogue-row'));
    const applyFilter = () => {
      const q = norm(filter.value);
      let count = 0;
      rows.forEach((row) => { const visible = !q || norm(`${row.dataset.search || ''} ${row.dataset.roleTitle || ''}`).includes(q); row.hidden = !visible; if (visible) count++; });
      if (empty) empty.hidden = count !== 0;
    };
    filter.addEventListener('input', applyFilter);
  }
  if (sort && catalogue) {
    sort.addEventListener('change', () => {
      const rows = Array.from(catalogue.querySelectorAll('.role-catalogue-row'));
      rows.sort((a, b) => {
        if (sort.value === 'evidence') {
          const ra = Number.parseInt(a.dataset.rank || '9999', 10) || 9999;
          const rb = Number.parseInt(b.dataset.rank || '9999', 10) || 9999;
          if (ra !== rb) return ra - rb;
        }
        return (a.dataset.roleTitle || '').localeCompare(b.dataset.roleTitle || '');
      });
      rows.forEach((row) => catalogue.appendChild(row));
    });
  }
  function norm(value) { return String(value || '').normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase().replace(/[^a-z0-9]+/g, ' ').trim(); }
})();

(() => {
  'use strict';

  const form = document.getElementById('careerGpsForm');
  if (!form) return;

  const primaryPath = document.getElementById('primaryPath');
  const companyInput = document.getElementById('targetCompany');
  const rolePicker = document.getElementById('rolePicker');
  const programPicker = document.getElementById('targetProgram');
  const criteriaContainer = document.getElementById('companyCriteriaContainer');
  const targetRoleValue = document.getElementById('targetRoleValue');
  const campusCard = document.getElementById('campusCard');
  const offcampusCard = document.getElementById('offcampusCard');
  const saveButton = document.getElementById('savePathButton');
  const sourcePicker = document.getElementById('sourceVersionPicker');
  const roleSearch = document.getElementById('roleSearch');
  const roleTiles = Array.from(document.querySelectorAll('.role-tile'));
  const noRoleResults = document.getElementById('noRoleResults');
  const companyNames = Array.from(document.querySelectorAll('#companyOptions option'))
    .map(option => option.value).filter(Boolean);
  const yopNode = Array.from(document.querySelectorAll('.criteria-item')).find(node => (node.textContent || '').includes('Year of passing'));
  const yopText = yopNode ? (yopNode.querySelector('strong')?.textContent || '').trim() : '';
  const yop = /^\d{4}$/.test(yopText) ? yopText : '';
  let requestSequence = 0;

  function selectedPath() {
    return primaryPath.value === 'Off-campus placement' ? 'Off-campus placement' : 'On-campus placement';
  }

  function renderSelectedLabel(card, selected) {
    const titleLine = card.querySelector('.path-title-line');
    if (!titleLine) return;
    let label = titleLine.querySelector('.selected-label');
    if (selected && !label) {
      label = document.createElement('span');
      label.className = 'selected-label';
      label.textContent = 'Selected';
      titleLine.appendChild(label);
    } else if (!selected && label) {
      label.remove();
    }
  }

  function setPath(path) {
    const normalized = path === 'Off-campus placement' ? 'Off-campus placement' : 'On-campus placement';
    primaryPath.value = normalized;
    const campusSelected = normalized === 'On-campus placement';
    campusCard.classList.toggle('is-selected', campusSelected);
    offcampusCard.classList.toggle('is-selected', !campusSelected);
    renderSelectedLabel(campusCard, campusSelected);
    renderSelectedLabel(offcampusCard, !campusSelected);
    companyInput.required = !campusSelected;
    companyInput.setAttribute('aria-required', String(!campusSelected));
    saveButton.innerHTML = campusSelected
      ? 'Set as Primary Path <span>→</span>'
      : 'Set as Primary Path <span>→</span>';
  }

  document.querySelectorAll('[data-path]').forEach(control => {
    control.addEventListener('click', () => setPath(control.getAttribute('data-path')));
  });

  document.querySelectorAll('input[name="targetBand"]').forEach(input => {
    input.addEventListener('change', () => {
      document.querySelectorAll('.band-option').forEach(label => {
        const radio = label.querySelector('input[name="targetBand"]');
        label.classList.toggle('band-active', Boolean(radio && radio.checked));
      });
    });
  });

  if (rolePicker && targetRoleValue) {
    rolePicker.addEventListener('change', () => { targetRoleValue.value = rolePicker.value; });
    targetRoleValue.value = rolePicker.value || targetRoleValue.value;
  }

  function safeText(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    node.textContent = text == null || text === '' ? '—' : String(text);
    return node;
  }

  async function refreshProgrammeOptions() {
    if (!programPicker || !companyInput || !rolePicker) return;
    const seq = ++requestSequence;
    const company = companyInput.value.trim();
    const role = rolePicker.value;
    const params = new URLSearchParams({ company, role });
    if (yop) params.set('yop', yop);
    try {
      const response = await fetch('/career-gps/programs?' + params.toString(), { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' });
      if (!response.ok) return;
      const result = await response.json();
      if (seq !== requestSequence || !Array.isArray(result)) return;
      const before = programPicker.value;
      programPicker.replaceChildren();
      result.forEach(program => {
        const option = document.createElement('option');
        option.value = String(program);
        option.textContent = program === 'Not specified' ? 'General (source does not name a programme)' : String(program);
        programPicker.appendChild(option);
      });
      programPicker.value = result.includes(before) ? before : (result.includes('Not specified') ? 'Not specified' : (result[0] || 'Not specified'));
      await refreshCompanyCriteria();
    } catch (_) {
      // If the source endpoint is temporarily unavailable, keep the server-rendered selector and honest empty state.
    }
  }

  async function refreshCompanyCriteria() {
    if (!criteriaContainer || !companyInput || !rolePicker || !programPicker) return;
    const seq = ++requestSequence;
    const company = companyInput.value.trim();
    const role = rolePicker.value;
    const program = programPicker.value || 'Not specified';
    criteriaContainer.replaceChildren();
    if (!company || !role) {
      criteriaContainer.appendChild(safeText('div', 'company-criteria-empty', 'Select a company, role and hiring programme to check for an exact verified criteria source.'));
      return;
    }
    const params = new URLSearchParams({ company, role, program });
    if (yop) params.set('yop', yop);
    try {
      const response = await fetch('/career-gps/criteria?' + params.toString(), { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' });
      if (!response.ok) throw new Error('Criteria service unavailable');
      const result = await response.json();
      if (seq !== requestSequence || !Array.isArray(result)) return;
      if (result.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'company-criteria-empty';
        empty.appendChild(safeText('strong', '', 'Company-role eligibility evidence'));
        empty.appendChild(safeText('span', '', 'No exact verified source is recorded for this company, role, programme and passing year. Cutoffs remain — rather than being inferred from other jobs.'));
        criteriaContainer.appendChild(empty);
        return;
      }
      const section = document.createElement('div');
      section.className = 'company-role-records';
      const heading = document.createElement('div');
      heading.className = 'company-role-records-title';
      heading.appendChild(safeText('strong', '', 'Verified role-specific requirements'));
      heading.appendChild(safeText('span', '', company + ' · ' + role + ' · ' + program + (yop ? ' · YOP ' + yop : '')));
      section.appendChild(heading);
      result.forEach(item => {
        const row = document.createElement('article');
        row.className = 'company-role-record';
        const copy = document.createElement('div');
        copy.className = 'company-role-record-copy';
        copy.appendChild(safeText('b', '', item.metric));
        copy.appendChild(safeText('span', '', item.target));
        if (item.mandatory) copy.appendChild(safeText('small', '', 'Mandatory'));
        const link = document.createElement('a');
        link.href = item.sourceUrl;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        link.appendChild(safeText('span', '', item.sourceTitle));
        link.appendChild(safeText('small', '', item.observedAt));
        row.append(copy, link);
        section.appendChild(row);
      });
      section.appendChild(safeText('p', 'company-role-disclaimer', 'These are requirements stated in the linked source, not a guarantee of eligibility. A missing rule is not inferred from another role or hiring programme.'));
      criteriaContainer.appendChild(section);
    } catch (_) {
      const empty = document.createElement('div');
      empty.className = 'company-criteria-empty';
      empty.appendChild(safeText('strong', '', 'Criteria are temporarily unavailable'));
      empty.appendChild(safeText('span', '', 'NEXORA could not retrieve the exact company-role records. No eligibility conclusion was drawn.'));
      criteriaContainer.appendChild(empty);
    }
  }

  companyInput.addEventListener('change', () => refreshProgrammeOptions());
  companyInput.addEventListener('blur', () => refreshProgrammeOptions());
  rolePicker?.addEventListener('change', () => refreshProgrammeOptions());
  programPicker?.addEventListener('change', () => refreshCompanyCriteria());
  refreshCompanyCriteria();

  form.addEventListener('submit', event => {
    setPath(selectedPath());
    targetRoleValue.value = rolePicker?.value || targetRoleValue.value;
    if (programPicker && !programPicker.value) programPicker.value = 'Not specified';
    if (selectedPath() === 'Off-campus placement' && !companyInput.value.trim()) {
      event.preventDefault();
      companyInput.focus();
      companyInput.setCustomValidity('Choose or enter a target company to save the off-campus path.');
      companyInput.reportValidity();
    }
  });
  companyInput.addEventListener('input', () => companyInput.setCustomValidity(''));

  function togglePanel(buttonId, panelId, otherPanelId) {
    const button = document.getElementById(buttonId);
    const panel = document.getElementById(panelId);
    if (!button || !panel) return;
    button.addEventListener('click', () => {
      const shouldOpen = panel.hidden;
      panel.hidden = !shouldOpen;
      const other = otherPanelId ? document.getElementById(otherPanelId) : null;
      if (other) other.hidden = true;
      if (shouldOpen) panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });
  }

  togglePanel('howToggle', 'howPanel', 'settingsPanel');

  function filterRoles() {
    const query = (roleSearch?.value || '').trim().toLocaleLowerCase();
    let visible = 0;
    roleTiles.forEach(tile => {
      const searchable = (tile.getAttribute('data-search') || tile.textContent).toLocaleLowerCase();
      const match = !query || searchable.includes(query);
      tile.hidden = !match;
      if (match) visible++;
    });
    const companyMatch = query && companyNames.some(company => company.toLocaleLowerCase().includes(query));
    if (noRoleResults) {
      noRoleResults.hidden = visible > 0 || Boolean(companyMatch);
      noRoleResults.textContent = 'No roles or skills match that search. Try a different keyword.';
    }
  }
  roleSearch?.addEventListener('input', filterRoles);
  roleSearch?.addEventListener('keydown', event => {
    if (event.key === 'Enter') {
      event.preventDefault();
      const query = (roleSearch.value || '').trim().toLocaleLowerCase();
      const companyMatch = companyNames.find(company => company.toLocaleLowerCase() === query)
        || companyNames.find(company => company.toLocaleLowerCase().includes(query));
      if (companyMatch && companyInput) {
        companyInput.value = companyMatch;
        setPath('Off-campus placement');
        offcampusCard?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        companyInput.focus({ preventScroll: true });
      } else {
        document.getElementById('roleLibrary')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }
    if (event.key === 'Escape') {
      roleSearch.value = '';
      filterRoles();
      roleSearch.blur();
    }
  });
  document.addEventListener('keydown', event => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      roleSearch?.focus();
    }
  });

  document.querySelectorAll('.role-use-button').forEach(button => {
    button.addEventListener('click', () => {
      const role = button.getAttribute('data-role');
      if (rolePicker && role) rolePicker.value = role;
      if (targetRoleValue && role) targetRoleValue.value = role;
      setPath('Off-campus placement');
      document.getElementById('targetCompany')?.focus({ preventScroll: true });
      document.getElementById('offcampusCard')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
  });

  // Initialize accessible required state and selected card styling from the server-rendered state.
  setPath(selectedPath());
  filterRoles();
})();

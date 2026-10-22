(() => {
  'use strict';
  const rows = document.getElementById('gradeRows');
  const template = document.getElementById('gradeRowTemplate');
  const addButton = document.getElementById('addSubjectButton');
  const estimate = document.getElementById('sgpaEstimate');
  const form = document.getElementById('gradeForm');
  if (!rows || !template || !addButton || !estimate || !form) return;

  const gradePoints = { O: 10, 'A+': 9, A: 8, 'B+': 7, B: 6, C: 5, F: 0, AB: 0, I: 0 };
  // Credits are the C value from the supplied Semester 3 SRM curriculum table.
  const curriculumCredits = new Map([
    ['transforms and boundary value problems', 4],
    ['design thinking methodology', 3],
    ['computer organization and architecture', 4],
    ['data structures and algorithms', 4],
    ['operating system', 4],
    ['advanced programming practice', 4]
  ]);
  const normalizeName = value => String(value || '').trim().toLocaleLowerCase().replace(/\\s+/g, ' ');
  const mapSubjectCredits = row => {
    const input = row.querySelector('[name="subjectName"]');
    const credits = row.querySelector('[name="credits"]');
    const creditValue = curriculumCredits.get(normalizeName(input?.value));
    if (creditValue && credits) credits.value = String(creditValue);
  };
  const allRows = () => Array.from(rows.querySelectorAll('.grade-row'));
  const recalculate = () => {
    let weighted = 0;
    let totalCredits = 0;
    allRows().forEach(row => {
      const name = row.querySelector('[name="subjectName"]')?.value.trim() || '';
      const credits = Number(row.querySelector('[name="credits"]')?.value || 0);
      const grade = row.querySelector('[name="grade"]')?.value || '';
      if (!name || !Number.isFinite(credits) || credits <= 0 || !(grade in gradePoints)) return;
      weighted += credits * gradePoints[grade];
      totalCredits += credits;
    });
    estimate.textContent = totalCredits ? (weighted / totalCredits).toFixed(2) : '0.00';
  };

  function bindRow(row) {
    const subjectInput = row.querySelector('[name="subjectName"]');
    subjectInput?.addEventListener('input', () => { mapSubjectCredits(row); recalculate(); });
    subjectInput?.addEventListener('change', () => { mapSubjectCredits(row); recalculate(); });
    row.querySelectorAll('select').forEach(control => {
      control.addEventListener('input', recalculate);
      control.addEventListener('change', recalculate);
    });
    mapSubjectCredits(row);
    row.querySelector('.grade-remove')?.addEventListener('click', () => {
      row.remove();
      if (!allRows().length) addRow();
      recalculate();
    });
  }
  function addRow() {
    const row = template.content.firstElementChild.cloneNode(true);
    rows.appendChild(row);
    bindRow(row);
    row.querySelector('[name="subjectName"]')?.focus();
    recalculate();
  }

  allRows().forEach(bindRow);
  addButton.addEventListener('click', addRow);
  form.addEventListener('submit', event => {
    const hasNamedRow = allRows().some(row => (row.querySelector('[name="subjectName"]')?.value || '').trim());
    if (!hasNamedRow) {
      event.preventDefault();
      const firstInput = rows.querySelector('[name="subjectName"]');
      firstInput?.setCustomValidity('Add at least one subject before saving.');
      firstInput?.reportValidity();
      firstInput?.addEventListener('input', () => firstInput.setCustomValidity(''), { once: true });
      return;
    }
    // Native required/type validation handles every non-empty row before submission.
    recalculate();
  });
  recalculate();
})();

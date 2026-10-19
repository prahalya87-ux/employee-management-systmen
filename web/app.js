/* Employee Management System - frontend logic.
   Every action calls the Java server's /api/... endpoints with fetch(). */

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June',
                'July', 'August', 'September', 'October', 'November', 'December'];

let allEmployees = [];   // cached list used by search and sort

// ---------------------------------------------------------
//  Small helpers
// ---------------------------------------------------------
const $ = (selector) => document.querySelector(selector);

function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function money(n) {
  return Number(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function today() {
  const d = new Date();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

async function api(path, method = 'GET', body) {
  const response = await fetch('/api' + path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined
  });
  let data = null;
  try { data = await response.json(); } catch (e) { /* no JSON body */ }
  if (!response.ok) throw new Error((data && data.error) || 'Request failed');
  return data;
}

let toastTimer;
function toast(message, isError = false) {
  const el = $('#toast');
  el.textContent = message;
  el.className = 'toast' + (isError ? ' error' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add('hidden'), 3500);
}

/* Runs an action and shows any error as a red message instead of crashing. */
async function run(action) {
  try { await action(); }
  catch (err) { toast(err.message, true); }
}

// ---------------------------------------------------------
//  Pop-up window (modal)
// ---------------------------------------------------------
function openModal(title, bodyHtml, buttons) {
  $('#modalTitle').textContent = title;
  $('#modalBody').innerHTML = bodyHtml;
  const actions = $('#modalActions');
  actions.innerHTML = '';
  buttons.forEach((b) => {
    const btn = document.createElement('button');
    btn.className = 'btn ' + (b.cls || '');
    btn.textContent = b.label;
    btn.addEventListener('click', b.onClick);
    actions.appendChild(btn);
  });
  $('#modal').classList.remove('hidden');
}

function closeModal() {
  $('#modal').classList.add('hidden');
}

// ---------------------------------------------------------
//  Navigation
// ---------------------------------------------------------
const loaders = {
  dashboard: loadDashboard,
  employees: loadEmployees,
  departments: loadDepartments,
  attendance: setupAttendance,
  salary: setupSalary,
  reports: loadReports
};

function showPage(name) {
  document.querySelectorAll('.page').forEach((p) => p.classList.toggle('active', p.id === name));
  document.querySelectorAll('#nav button').forEach((b) => b.classList.toggle('active', b.dataset.page === name));
  run(loaders[name]);
}

$('#nav').addEventListener('click', (e) => {
  if (e.target.dataset.page) showPage(e.target.dataset.page);
});

// ---------------------------------------------------------
//  Dashboard
// ---------------------------------------------------------
async function loadDashboard() {
  const s = await api('/summary');
  const top = s.highestPaid
    ? `<div class="value">${esc(s.highestPaid.name)}</div><div class="sub">${money(s.highestPaid.salary)}</div>`
    : '<div class="value">-</div>';
  $('#stats').innerHTML = `
    <div class="card"><div class="label">Total Employees</div><div class="value">${s.totalEmployees}</div></div>
    <div class="card"><div class="label">Departments</div><div class="value">${s.totalDepartments}</div></div>
    <div class="card"><div class="label">Total Basic Salary / month</div><div class="value">${money(s.totalSalary)}</div></div>
    <div class="card"><div class="label">Average Salary</div><div class="value">${money(s.averageSalary)}</div></div>
    <div class="card"><div class="label">Highest Paid</div>${top}</div>`;
  $('#deptBars').innerHTML = departmentBars(s.departmentCounts);
}

function departmentBars(counts) {
  if (!counts.length) return '<p class="muted">No departments yet.</p>';
  const max = Math.max(...counts.map((c) => c.count), 1);
  return counts.map((c) => `
    <div class="bar-row">
      <div class="bar-label">${esc(c.name)}</div>
      <div class="bar-track"><div class="bar-fill" style="width:${(c.count / max) * 100}%"></div></div>
      <div class="bar-count">${c.count}</div>
    </div>`).join('');
}

// ---------------------------------------------------------
//  Employees
// ---------------------------------------------------------
async function loadEmployees() {
  allEmployees = await api('/employees');
  renderEmployees();
}

function renderEmployees() {
  const q = $('#empSearch').value.trim().toLowerCase();
  let list = allEmployees.filter((e) =>
    !q ||
    e.name.toLowerCase().includes(q) ||
    e.departmentName.toLowerCase().includes(q) ||
    String(e.empId).includes(q));

  if ($('#empSort').value === 'salary') {
    list = [...list].sort((a, b) => b.basicSalary - a.basicSalary);
  }

  $('#empBody').innerHTML = list.length
    ? list.map((e) => `
      <tr>
        <td>${e.empId}</td>
        <td>${esc(e.name)}</td>
        <td><span class="badge type">${esc(e.type)}</span></td>
        <td>${esc(e.departmentName)}</td>
        <td>${esc(e.jobRole)}</td>
        <td>${esc(e.joiningDate)}</td>
        <td class="num">${money(e.basicSalary)}</td>
        <td>
          <button class="btn small" data-act="view" data-id="${e.empId}">View</button>
          <button class="btn small" data-act="edit" data-id="${e.empId}">Edit</button>
          <button class="btn small danger" data-act="delete" data-id="${e.empId}">Delete</button>
        </td>
      </tr>`).join('')
    : '<tr><td colspan="8" class="muted">No employees found.</td></tr>';
}

$('#empSearch').addEventListener('input', renderEmployees);
$('#empSort').addEventListener('change', renderEmployees);
$('#addEmpBtn').addEventListener('click', () => run(() => openEmployeeForm(null)));

$('#empBody').addEventListener('click', (e) => {
  const btn = e.target.closest('button');
  if (!btn) return;
  const emp = allEmployees.find((x) => x.empId === Number(btn.dataset.id));
  if (!emp) return;
  if (btn.dataset.act === 'view') showEmployee(emp);
  if (btn.dataset.act === 'edit') run(() => openEmployeeForm(emp));
  if (btn.dataset.act === 'delete') deleteEmployee(emp);
});

function showEmployee(e) {
  const rows = [
    ['Employee ID', e.empId], ['Name', e.name], ['Type', e.type],
    ['Age / Gender', `${e.age} / ${e.gender}`], ['Email', e.email], ['Phone', e.phone],
    ['Department', e.departmentName], ['Job Role', e.jobRole],
    ['Joining Date', e.joiningDate], ['Basic Salary', money(e.basicSalary)]
  ];
  openModal('Employee Details',
    rows.map((r) => `<div class="detail-row"><span>${r[0]}</span><span>${esc(r[1])}</span></div>`).join(''),
    [{ label: 'Close', onClick: closeModal }]);
}

async function openEmployeeForm(emp) {
  const depts = await api('/departments');
  if (!depts.length) {
    toast('Create a department first (Departments page).', true);
    return;
  }
  const isEdit = emp !== null;
  const v = (field, fallback = '') => esc(isEmpValue(emp, field) ? emp[field] : fallback);
  const deptOptions = depts.map((d) =>
    `<option value="${d.deptId}" ${isEdit && emp.deptId === d.deptId ? 'selected' : ''}>${esc(d.deptName)}</option>`).join('');
  const sel = (field, value) => (isEdit && emp[field] === value ? 'selected' : '');

  openModal(isEdit ? 'Edit Employee' : 'Add Employee', `
    <div class="grid2">
      <label>Employee ID <input type="number" id="f_empId" value="${v('empId')}" ${isEdit ? 'disabled' : ''}></label>
      <label>Name <input type="text" id="f_name" value="${v('name')}"></label>
      <label>Age <input type="number" id="f_age" value="${v('age')}"></label>
      <label>Gender
        <select id="f_gender">
          <option ${sel('gender', 'Male')}>Male</option>
          <option ${sel('gender', 'Female')}>Female</option>
          <option ${sel('gender', 'Other')}>Other</option>
        </select>
      </label>
      <label>Email <input type="text" id="f_email" value="${v('email')}"></label>
      <label>Phone (10 digits) <input type="text" id="f_phone" value="${v('phone')}"></label>
      <label>Department <select id="f_dept">${deptOptions}</select></label>
      <label>Job Role <input type="text" id="f_role" value="${v('jobRole')}"></label>
      <label>Joining Date <input type="date" id="f_joined" value="${v('joiningDate', today())}"></label>
      <label>Basic Salary <input type="number" step="0.01" id="f_salary" value="${v('basicSalary')}"></label>
      <label>Type
        <select id="f_type" ${isEdit ? 'disabled' : ''}>
          <option ${sel('type', 'Employee')}>Employee</option>
          <option ${sel('type', 'Manager')}>Manager</option>
          <option ${sel('type', 'Developer')}>Developer</option>
        </select>
      </label>
    </div>`,
    [
      { label: 'Cancel', onClick: closeModal },
      { label: isEdit ? 'Save Changes' : 'Add Employee', cls: 'primary', onClick: () => run(() => saveEmployee(isEdit, emp)) }
    ]);
}

function isEmpValue(emp, field) {
  return emp !== null && emp[field] !== undefined && emp[field] !== null;
}

async function saveEmployee(isEdit, emp) {
  const payload = {
    empId: isEdit ? emp.empId : $('#f_empId').value,
    name: $('#f_name').value,
    age: $('#f_age').value,
    gender: $('#f_gender').value,
    email: $('#f_email').value,
    phone: $('#f_phone').value,
    deptId: $('#f_dept').value,
    jobRole: $('#f_role').value,
    joiningDate: $('#f_joined').value,
    basicSalary: $('#f_salary').value,
    type: $('#f_type').value
  };
  const result = isEdit
    ? await api('/employees/' + emp.empId, 'PUT', payload)
    : await api('/employees', 'POST', payload);
  closeModal();
  toast(result.message);
  await loadEmployees();
}

function deleteEmployee(emp) {
  openModal('Delete Employee',
    `<p>Delete <b>${esc(emp.name)}</b> (ID ${emp.empId})? Their attendance and salary records will also be deleted.</p>`,
    [
      { label: 'Cancel', onClick: closeModal },
      {
        label: 'Delete', cls: 'danger',
        onClick: () => run(async () => {
          const r = await api('/employees/' + emp.empId, 'DELETE');
          closeModal();
          toast(r.message);
          await loadEmployees();
        })
      }
    ]);
}

// ---------------------------------------------------------
//  Departments
// ---------------------------------------------------------
let departmentList = [];

async function loadDepartments() {
  departmentList = await api('/departments');
  $('#deptBody').innerHTML = departmentList.length
    ? departmentList.map((d) => `
      <tr>
        <td>${d.deptId}</td>
        <td>${esc(d.deptName)}</td>
        <td>${esc(d.location || '-')}</td>
        <td class="num">${d.employeeCount}</td>
        <td>
          <button class="btn small" data-act="members" data-id="${d.deptId}">Employees</button>
          <button class="btn small" data-act="edit" data-id="${d.deptId}">Edit</button>
          <button class="btn small danger" data-act="delete" data-id="${d.deptId}">Delete</button>
        </td>
      </tr>`).join('')
    : '<tr><td colspan="5" class="muted">No departments yet. Add one.</td></tr>';
}

$('#addDeptBtn').addEventListener('click', () => openDepartmentForm(null));

$('#deptBody').addEventListener('click', (e) => {
  const btn = e.target.closest('button');
  if (!btn) return;
  const dept = departmentList.find((d) => d.deptId === Number(btn.dataset.id));
  if (!dept) return;
  if (btn.dataset.act === 'members') run(() => showDepartmentMembers(dept));
  if (btn.dataset.act === 'edit') openDepartmentForm(dept);
  if (btn.dataset.act === 'delete') {
    openModal('Delete Department', `<p>Delete department <b>${esc(dept.deptName)}</b>?</p>`, [
      { label: 'Cancel', onClick: closeModal },
      {
        label: 'Delete', cls: 'danger',
        onClick: () => run(async () => {
          const r = await api('/departments/' + dept.deptId, 'DELETE');
          closeModal();
          toast(r.message);
          await loadDepartments();
        })
      }
    ]);
  }
});

function openDepartmentForm(dept) {
  const isEdit = dept !== null;
  openModal(isEdit ? 'Edit Department' : 'Add Department', `
    <div class="grid2">
      <label>Department Name <input type="text" id="d_name" value="${isEdit ? esc(dept.deptName) : ''}"></label>
      <label>Location <input type="text" id="d_loc" value="${isEdit ? esc(dept.location || '') : ''}"></label>
    </div>`,
    [
      { label: 'Cancel', onClick: closeModal },
      {
        label: 'Save', cls: 'primary',
        onClick: () => run(async () => {
          const payload = { deptName: $('#d_name').value, location: $('#d_loc').value };
          const r = isEdit
            ? await api('/departments/' + dept.deptId, 'PUT', payload)
            : await api('/departments', 'POST', payload);
          closeModal();
          toast(r.message);
          await loadDepartments();
        })
      }
    ]);
}

async function showDepartmentMembers(dept) {
  const members = await api(`/departments/${dept.deptId}/employees`);
  const body = members.length
    ? `<table><thead><tr><th>ID</th><th>Name</th><th>Job Role</th><th class="num">Salary</th></tr></thead><tbody>` +
      members.map((m) => `<tr><td>${m.empId}</td><td>${esc(m.name)}</td><td>${esc(m.jobRole)}</td><td class="num">${money(m.basicSalary)}</td></tr>`).join('') +
      '</tbody></table>'
    : '<p class="muted">No employees in this department.</p>';
  openModal('Employees in ' + dept.deptName, body, [{ label: 'Close', onClick: closeModal }]);
}

// ---------------------------------------------------------
//  Attendance
// ---------------------------------------------------------
async function fillEmployeeSelect(select) {
  const list = await api('/employees');
  const previous = select.value;
  select.innerHTML = list.length
    ? list.map((e) => `<option value="${e.empId}">${e.empId} - ${esc(e.name)}</option>`).join('')
    : '<option value="">No employees</option>';
  if (previous && list.some((e) => String(e.empId) === previous)) select.value = previous;
}

async function setupAttendance() {
  if (!$('#attDate').value) $('#attDate').value = today();
  $('#attDate').max = today();
  await fillEmployeeSelect($('#attEmp'));
  await loadAttendanceHistory();
}

async function loadAttendanceHistory() {
  const id = $('#attEmp').value;
  const badge = $('#presentBadge');
  if (!id) {
    $('#attBody').innerHTML = '<tr><td colspan="2" class="muted">No employee selected.</td></tr>';
    badge.classList.add('hidden');
    return;
  }
  const data = await api('/attendance/' + id);
  badge.textContent = `Present: ${data.presentDays} day(s)`;
  badge.classList.remove('hidden');
  $('#attBody').innerHTML = data.records.length
    ? data.records.map((r) => `
      <tr><td>${esc(r.date)}</td>
      <td><span class="badge ${r.status === 'Present' ? 'ok' : 'bad'}">${esc(r.status)}</span></td></tr>`).join('')
    : '<tr><td colspan="2" class="muted">No attendance records yet.</td></tr>';
}

$('#attEmp').addEventListener('change', () => run(loadAttendanceHistory));

$('#markBtn').addEventListener('click', () => run(async () => {
  const r = await api('/attendance', 'POST', {
    empId: $('#attEmp').value,
    date: $('#attDate').value,
    status: $('#attStatus').value
  });
  toast(r.message);
  await loadAttendanceHistory();
}));

// ---------------------------------------------------------
//  Salary
// ---------------------------------------------------------
async function setupSalary() {
  if (!$('#salMonth').options.length) {
    $('#salMonth').innerHTML = MONTHS.map((m, i) => `<option value="${i + 1}">${m}</option>`).join('');
    $('#salMonth').value = new Date().getMonth() + 1;
    $('#salYear').value = new Date().getFullYear();
  }
  await fillEmployeeSelect($('#salEmp'));
}

function salaryQuery() {
  return { empId: $('#salEmp').value, month: $('#salMonth').value, year: $('#salYear').value };
}

$('#calcBtn').addEventListener('click', () => run(async () => {
  const slip = await api('/salary', 'POST', salaryQuery());
  renderSlip(slip);
  toast('Salary calculated and saved.');
}));

$('#slipBtn').addEventListener('click', () => run(async () => {
  const q = salaryQuery();
  renderSlip(await api(`/salary?empId=${q.empId}&month=${q.month}&year=${q.year}`));
}));

function renderSlip(s) {
  const row = (label, value) => `<div class="row"><span>${label}</span><span>${money(value)}</span></div>`;
  $('#slipArea').innerHTML = `
    <div class="slip">
      <h2>SALARY SLIP</h2>
      <div class="period">${MONTHS[s.month - 1]} ${s.year}</div>
      <div class="row"><span>Employee</span><span>${esc(s.empName)} (ID ${s.empId})</span></div>
      <div class="row"><span>Department</span><span>${esc(s.department)}</span></div>
      <div class="row"><span>Job Role</span><span>${esc(s.jobRole)}</span></div>
      <div class="section">Earnings</div>
      ${row('Basic Salary', s.basic)}
      ${row('HRA (20%)', s.hra)}
      ${row('DA (10%)', s.da)}
      ${row('Bonus', s.bonus)}
      <div class="row total"><span>Gross Salary</span><span>${money(s.gross)}</span></div>
      <div class="section">Deductions</div>
      ${row('PF (12% of basic)', s.pf)}
      ${row('Income Tax', s.tax)}
      ${row(`Absence (${s.absentDays} day(s))`, s.absentDeduction)}
      <div class="row total"><span>Total Deductions</span><span>${money(s.totalDeductions)}</span></div>
      <div class="net"><span>NET SALARY</span><span>${money(s.net)}</span></div>
    </div>
    <p><button class="btn no-print" onclick="window.print()">Print salary slip</button></p>`;
}

// ---------------------------------------------------------
//  Reports
// ---------------------------------------------------------
async function loadReports() {
  const [summary, rows] = await Promise.all([api('/summary'), api('/reports')]);
  const top = summary.highestPaid
    ? `<div class="value">${esc(summary.highestPaid.name)}</div><div class="sub">${money(summary.highestPaid.salary)}</div>`
    : '<div class="value">-</div>';
  $('#reportCards').innerHTML = `
    <div class="card"><div class="label">Total Employees</div><div class="value">${summary.totalEmployees}</div></div>
    <div class="card"><div class="label">Total Basic Salary / month</div><div class="value">${money(summary.totalSalary)}</div></div>
    <div class="card"><div class="label">Highest Paid Employee</div>${top}</div>`;

  $('#reportDeptBody').innerHTML = summary.departmentCounts.length
    ? summary.departmentCounts.map((d) => `<tr><td>${esc(d.name)}</td><td class="num">${d.count}</td></tr>`).join('')
    : '<tr><td colspan="2" class="muted">No departments.</td></tr>';

  $('#reportBody').innerHTML = rows.length
    ? rows.map((r) => `
      <tr>
        <td>${r.empId}</td><td>${esc(r.name)}</td><td>${esc(r.type)}</td><td>${esc(r.department)}</td>
        <td class="num">${money(r.basic)}</td><td class="num">${money(r.gross)}</td>
        <td class="num">${money(r.deductions)}</td><td class="num">${money(r.net)}</td>
      </tr>`).join('')
    : '<tr><td colspan="8" class="muted">No employees found.</td></tr>';
}

// ---------------------------------------------------------
//  Start
// ---------------------------------------------------------
$('#modal').addEventListener('click', (e) => { if (e.target.id === 'modal') closeModal(); });
showPage('dashboard');
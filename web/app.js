const $ = (s) => document.querySelector(s);
const MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December'];
let session = JSON.parse(localStorage.getItem('smarthr-session') || 'null');
let allEmployees = [];
let attendanceRows = [];
let dirtyAttendance = new Map();
let attendanceDate = '';

function esc(v) { return String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function money(v) { return Number(v || 0).toLocaleString('en-US', {minimumFractionDigits:2, maximumFractionDigits:2}); }
function today() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; }
function empCode(id) { return `EMP${id}`; }

async function api(path, method='GET', body) {
  const headers = {'Content-Type':'application/json'};
  if (session?.token) headers.Authorization = `Bearer ${session.token}`;
  const r = await fetch('/api'+path, {method, headers, body: body ? JSON.stringify(body) : undefined});
  let data;
  try { data = await r.json(); } catch (_) { data = null; }
  if (!r.ok) {
    if (r.status === 401 && session?.token) expireSession(data?.error);
    throw new Error(data?.error || 'Request failed');
  }
  return data;
}

let toastTimer;
function toast(message, error=false) {
  const el=$('#toast'); el.textContent=message; el.className='toast'+(error?' error':'');
  clearTimeout(toastTimer); toastTimer=setTimeout(()=>el.classList.add('hidden'),3500);
}
async function run(fn) { try { await fn(); } catch(e) { toast(e.message,true); } }

function openModal(title, body, actions) {
  $('#modalTitle').textContent=title; $('#modalBody').innerHTML=body;
  $('#modalActions').innerHTML='';
  actions.forEach(a => { const b=document.createElement('button'); b.className=`btn ${a.cls||''}`; b.textContent=a.label; b.onclick=a.onClick; $('#modalActions').appendChild(b); });
  $('#modal').classList.remove('hidden');
}
function closeModal(){ $('#modal').classList.add('hidden'); }

$('#togglePassword').onclick=()=> {
  const input=$('#loginPassword'); input.type=input.type==='password'?'text':'password';
  $('#togglePassword').textContent=input.type==='password'?'Show':'Hide';
};
function showLoginError(message) {
  const error=$('#loginError');
  error.textContent=message;
  error.classList.remove('hidden');
}
function clearLoginError() {
  $('#loginError').textContent='';
  $('#loginError').classList.add('hidden');
}
function expireSession(message='Your session has expired. Please sign in again.') {
  localStorage.removeItem('smarthr-session');
  session=null;
  $('#appShell').classList.add('hidden');
  $('#loginScreen').classList.remove('hidden');
  showLoginError(message);
}
$('#loginForm').onsubmit=async e=> {
  e.preventDefault();
  clearLoginError();
  const button=$('#loginSubmit');
  button.disabled=true;
  button.innerHTML='<span>Signing in…</span><b>· · ·</b>';
  try {
    const result=await api('/auth/login','POST',{username:$('#loginUsername').value.trim(),password:$('#loginPassword').value});
    session=result;
    if ($('#rememberMe').checked) localStorage.setItem('smarthr-session',JSON.stringify(result));
    else localStorage.removeItem('smarthr-session');
    startApp();
  } catch(error) {
    showLoginError(error.message==='Request failed'?'Unable to sign in. Check your username and password.':error.message);
  } finally {
    button.disabled=false;
    button.innerHTML='<span>Sign in</span><b>→</b>';
  }
};
function startApp(){
  $('#loginScreen').classList.add('hidden'); $('#appShell').classList.remove('hidden');
  $('#userAvatar').textContent=(session.username||'U').slice(0,1).toUpperCase();
  $('#currentDate').textContent=new Date().toLocaleDateTimeString?new Date().toLocaleDateString(undefined,{weekday:'short',month:'short',day:'numeric'}):new Date().toLocaleDateString();
  document.querySelectorAll('.admin-only').forEach(el=>el.classList.toggle('hidden',session.role!=='ADMIN'));
  document.querySelectorAll('.employee-leave-only').forEach(el=>el.classList.toggle('hidden',session.role==='ADMIN'));
  document.querySelectorAll('.employee-only').forEach(el=>el.classList.toggle('hidden',session.role!=='EMPLOYEE'));
  $('#submitAttendanceBtn').classList.toggle('hidden',session.role!=='ADMIN');
  showPage('dashboard');
}
async function restoreSession(){
  try {
    const user=await api('/auth/session');
    session={...session,...user};
    startApp();
  } catch(_) {
    localStorage.removeItem('smarthr-session');
    session=null;
  }
}
if(session?.token) restoreSession();

$('#logoutBtn').onclick=()=>{
  if(session?.token) api('/auth/logout','POST').catch(()=>{});
  sessionsClear();
};
function sessionsClear(){ localStorage.removeItem('smarthr-session'); session=null; $('#appShell').classList.add('hidden'); $('#loginScreen').classList.remove('hidden'); }

const pageMeta={dashboard:['OVERVIEW','Dashboard'],employees:['WORKFORCE','Employees'],attendance:['TIME & PRESENCE','Attendance'],leave:['TIME OFF','Leave Management'],salary:['PAYROLL','Salary Management'],reports:['ANALYTICS','Reports']};
async function showPage(name){
  document.querySelectorAll('.page').forEach(p=>p.classList.toggle('active',p.id===name));
  document.querySelectorAll('#nav button[data-page]').forEach(b=>b.classList.toggle('active',b.dataset.page===name));
  $('#pageTitle').textContent=pageMeta[name][1]; $('#pageEyebrow').textContent=pageMeta[name][0];
  await run(async()=> {
    if(name==='dashboard') await loadDashboard();
    if(name==='employees') await loadEmployees();
    if(name==='attendance') await setupAttendance();
    if(name==='leave') await setupLeave();
    if(name==='salary') await setupSalary();
    if(name==='reports') await loadReports();
  });
}
$('#nav').onclick=e=>{const b=e.target.closest('button[data-page]'); if(b){showPage(b.dataset.page);if(window.matchMedia('(max-width: 760px)').matches)closeNavigation();}};

function closeNavigation(){
  $('#appShell').classList.remove('menu-open');
  $('#sidebarOverlay').classList.add('hidden');
  $('#menuToggle').setAttribute('aria-expanded','false');
  $('#menuToggle').setAttribute('aria-label','Open navigation menu');
}
$('#menuToggle').onclick=()=>{
  const shell=$('#appShell');
  if(window.matchMedia('(max-width: 760px)').matches){
    const opening=!shell.classList.contains('menu-open');
    shell.classList.toggle('menu-open',opening);
    $('#sidebarOverlay').classList.toggle('hidden',!opening);
    $('#menuToggle').setAttribute('aria-expanded',String(opening));
    $('#menuToggle').setAttribute('aria-label',opening?'Close navigation menu':'Open navigation menu');
  }else{
    const collapsed=!shell.classList.contains('sidebar-collapsed');
    shell.classList.toggle('sidebar-collapsed',collapsed);
    $('#menuToggle').setAttribute('aria-expanded',String(!collapsed));
    $('#menuToggle').setAttribute('aria-label',collapsed?'Open navigation menu':'Collapse navigation menu');
  }
};
$('#sidebarOverlay').onclick=closeNavigation;

async function loadDashboard(){
  const dashboardGrid=$('#dashboard .dashboard-grid');
  const recentPanel=$('#recentEmployees').closest('.panel');
  if(session.role==='EMPLOYEE'){
    const [employee,attendance,balances]=await Promise.all([
      api('/employees/'+session.empId),
      api('/attendance/'+session.empId),
      api('/leave/balances')
    ]);
    const recordedDays=attendance.records.length;
    const presentDays=attendance.records.filter(row=>row.status==='Present').length;
    const attendanceRate=recordedDays?Math.round(presentDays/recordedDays*100):0;
    const remainingLeave=balances.reduce((total,balance)=>total+balance.remaining,0);
    $('#stats').innerHTML=
      statCard('My Attendance',`${attendanceRate}%`,'present','#16a34a')+
      statCard('Present Days',presentDays,'people','#4f46e5')+
      statCard('Recorded Days',recordedDays,'leave','#6366f1')+
      statCard('Leave Remaining',remainingLeave,'leave','#f59e0b');
    dashboardGrid.classList.add('employee-dashboard');
    recentPanel.classList.add('hidden');
    $('#deptBars').closest('.panel').querySelector('h2').textContent='My profile';
    $('#deptBars').innerHTML=`
      <div class="detail-row"><span>Employee ID</span><strong>${empCode(employee.empId)}</strong></div>
      <div class="detail-row"><span>Name</span><strong>${esc(employee.name)}</strong></div>
      <div class="detail-row"><span>Department</span><strong>${esc(employee.departmentName)}</strong></div>
      <div class="detail-row"><span>Designation</span><strong>${esc(employee.jobRole)}</strong></div>
      <div class="detail-row"><span>Email</span><strong>${esc(employee.email)}</strong></div>
      <div class="detail-row"><span>Phone</span><strong>${esc(employee.phone)}</strong></div>
      <div class="detail-row"><span>Joining date</span><strong>${esc(employee.joiningDate)}</strong></div>
      <div class="detail-row"><span>Employment status</span><strong>${esc(employee.employmentStatus)}</strong></div>`;
    return;
  }
  dashboardGrid.classList.remove('employee-dashboard');
  recentPanel.classList.remove('hidden');
  $('#deptBars').closest('.panel').querySelector('h2').textContent='Department distribution';
  const s=await api('/summary');
  $('#stats').innerHTML=`
    ${statCard('Total Employees',s.totalEmployees,'people','#4f46e5')}
    ${statCard('Present Today',s.presentToday,'present','#16a34a')}
    ${statCard('Absent Today',s.absentToday,'absent','#ef4444')}
    ${statCard('Pending Leave',s.pendingLeaves,'leave','#f59e0b')}`;
  allEmployees=await api('/employees');
  $('#deptBars').innerHTML=s.departmentCounts?.length?s.departmentCounts.map(d=>{
    const max=Math.max(...s.departmentCounts.map(x=>x.count),1);
    return `<div class="bar-row"><div class="bar-label">${esc(d.name)}</div><div class="bar-track"><div class="bar-fill" style="width:${d.count/max*100}%"></div></div><b>${d.count}</b></div>`;
  }).join(''):'<p class="muted">No department data.</p>';
  $('#recentEmployees').innerHTML=allEmployees.slice(-5).reverse().map(e=>`
    <div class="list-row"><div class="mini-avatar">${esc(e.name).charAt(0)}</div><div><strong>${esc(e.name)}</strong><span>${esc(e.departmentName)} · ${esc(e.jobRole)}</span></div><b>${empCode(e.empId)}</b></div>`).join('')||'<p class="muted">No employees yet.</p>';
}
function statCard(label,value,icon,color){return `<div class="card stat-card" style="--accent:${color}"><div class="stat-icon">${icon==='people'?'♙':icon==='present'?'✓':icon==='absent'?'—':'◷'}</div><div><div class="label">${label}</div><div class="value">${value}</div></div></div>`;}

async function loadEmployees(){
  allEmployees=await api('/employees'); renderEmployees();
  const depts=await api('/departments');
  window.departmentOptions=depts;
}
function renderEmployees(){
  const q=$('#empSearch').value.trim().toLowerCase();
  const status=$('#empStatusFilter').value;
  const list=allEmployees.filter(e=>(!q||e.name.toLowerCase().includes(q)||e.departmentName.toLowerCase().includes(q)||empCode(e.empId).toLowerCase().includes(q))&&(!status||e.employmentStatus===status));
  $('#empBody').innerHTML=list.length?list.map(e=>`
    <tr><td><div class="employee-cell"><div class="mini-avatar">${esc(e.name).charAt(0)}</div><div><strong>${esc(e.name)}</strong><span>${empCode(e.empId)} · ${esc(e.email)}</span></div></div></td>
    <td>${esc(e.departmentName)}</td><td>${esc(e.jobRole)}</td><td>${esc(e.joiningDate)}</td><td class="num">${money(e.basicSalary)}</td>
    <td><span class="badge ${e.employmentStatus==='Inactive'?'bad':e.employmentStatus==='On Leave'?'warn':'ok'}">${esc(e.employmentStatus||'Active')}</span></td>
    <td><button class="btn small" data-act="view" data-id="${e.empId}">View</button><button class="btn small" data-act="edit" data-id="${e.empId}">Edit</button><button class="btn small danger" data-act="delete" data-id="${e.empId}">Deactivate</button></td></tr>`).join(''):'<tr><td colspan="7" class="muted">No employees found.</td></tr>';
}
$('#empSearch').oninput=renderEmployees; $('#empStatusFilter').onchange=renderEmployees;
$('#empBody').onclick=e=>{
  const b=e.target.closest('button'); if(!b)return;
  const emp=allEmployees.find(x=>x.empId===Number(b.dataset.id)); if(!emp)return;
  if(b.dataset.act==='view')showEmployee(emp);
  if(b.dataset.act==='edit')run(()=>openEmployeeForm(emp));
  if(b.dataset.act==='delete')deactivateEmployee(emp);
};
async function openEmployeeForm(emp){
  const depts=await api('/departments');
  const isEdit=!!emp;
  let nextId='';
  if(!isEdit) nextId=(await api('/next-employee-id')).empId;
  const v=(field,f='')=>esc(isEdit&&emp[field]!=null?emp[field]:f);
  const deptOptions=depts.map(d=>`<option value="${d.deptId}" ${isEdit&&emp.deptId===d.deptId?'selected':''}>${esc(d.deptName)}</option>`).join('');
  openModal(isEdit?'Edit Employee':'Add Employee',`
    <div class="grid2">
      <label>Employee ID<input id="f_empId" value="${isEdit?empCode(emp.empId):esc(nextId)}" readonly></label>
      <label>Full name<input id="f_name" value="${v('name')}"></label>
      <label>Email<input id="f_email" type="email" value="${v('email')}"></label>
      <label>Phone<input id="f_phone" value="${v('phone')}"></label>
      <label>Gender<select id="f_gender"><option ${isEdit&&emp.gender==='Male'||!isEdit?'selected':''}>Male</option><option ${isEdit&&emp.gender==='Female'?'selected':''}>Female</option><option ${isEdit&&emp.gender==='Other'?'selected':''}>Other</option></select></label>
      <label>Date of birth<input id="f_dob" type="date" value="${v('dateOfBirth')}"></label>
      <label>Department<select id="f_dept">${deptOptions}</select></label>
      <label>Designation<input id="f_role" value="${v('jobRole')}"></label>
      <label>Joining date<input id="f_joined" type="date" value="${v('joiningDate',today())}"></label>
      <label>Basic salary<input id="f_salary" type="number" step="0.01" value="${v('basicSalary')}"></label>
      <label>Employment status<select id="f_status"><option ${isEdit&&emp.employmentStatus==='Active'||!isEdit?'selected':''}>Active</option><option ${isEdit&&emp.employmentStatus==='Inactive'?'selected':''}>Inactive</option><option ${isEdit&&emp.employmentStatus==='On Leave'?'selected':''}>On Leave</option></select></label>
      <label>Type<select id="f_type" ${isEdit?'disabled':''}><option ${isEdit&&emp.type==='Employee'||!isEdit?'selected':''}>Employee</option><option ${isEdit&&emp.type==='Manager'?'selected':''}>Manager</option><option ${isEdit&&emp.type==='Developer'?'selected':''}>Developer</option></select></label>
      <label class="full">Address<textarea id="f_address" rows="2">${v('address')}</textarea></label>
      ${isEdit?'':`<label>Username<input id="f_username"></label><label>Password<input id="f_password" type="password" placeholder="Minimum 6 characters"></label>`}
    </div>`,[
      {label:'Cancel',onClick:closeModal},{label:isEdit?'Save changes':'Add employee',cls:'primary',onClick:()=>run(()=>saveEmployee(isEdit,emp))}
    ]);
}
async function saveEmployee(isEdit,emp){
  const payload={empId:$('#f_empId').value,name:$('#f_name').value,age:18,email:$('#f_email').value,phone:$('#f_phone').value,gender:$('#f_gender').value,
    dateOfBirth:$('#f_dob').value,address:$('#f_address').value,deptId:$('#f_dept').value,jobRole:$('#f_role').value,
    joiningDate:$('#f_joined').value,basicSalary:$('#f_salary').value,employmentStatus:$('#f_status').value,type:$('#f_type').value,
    username:$('#f_username')?.value||'',password:$('#f_password')?.value||''};
  const r=await api(isEdit?'/employees/'+emp.empId:'/employees',isEdit?'PUT':'POST',payload);
  closeModal(); toast(r.message); await loadEmployees();
}
function showEmployee(e){openModal('Employee Details',`
  <div class="detail-row"><span>Employee ID</span><strong>${empCode(e.empId)}</strong></div><div class="detail-row"><span>Name</span><strong>${esc(e.name)}</strong></div>
  <div class="detail-row"><span>Email</span><strong>${esc(e.email)}</strong></div><div class="detail-row"><span>Phone</span><strong>${esc(e.phone)}</strong></div>
  <div class="detail-row"><span>Department</span><strong>${esc(e.departmentName)}</strong></div><div class="detail-row"><span>Designation</span><strong>${esc(e.jobRole)}</strong></div>
  <div class="detail-row"><span>Joining date</span><strong>${esc(e.joiningDate)}</strong></div><div class="detail-row"><span>Address</span><strong>${esc(e.address||'-')}</strong></div>`,
  [{label:'Close',onClick:closeModal}]);}
function deactivateEmployee(e){openModal('Deactivate employee',`<p>Deactivate <strong>${esc(e.name)}</strong>? Historical attendance, leave and salary records will be preserved.</p>`,[
  {label:'Cancel',onClick:closeModal},{label:'Deactivate',cls:'danger',onClick:()=>run(async()=>{const r=await api('/employees/'+e.empId,'DELETE');closeModal();toast(r.message);await loadEmployees();})}]);}
$('#addEmpBtn').onclick=()=>run(()=>openEmployeeForm(null));

async function setupAttendance(){
  const employeeMode=session.role==='EMPLOYEE';
  $('#attendanceStats').classList.toggle('employee-attendance-cards',employeeMode);
  $('#attDateControl').classList.toggle('hidden',employeeMode);
  $('#attMonthControl').classList.toggle('hidden',!employeeMode);
  $('#employeeAttendanceFilter').classList.toggle('hidden',!employeeMode);
  $('#attendanceFilter').classList.toggle('hidden',employeeMode);
  $('#demoMonthBtn').classList.toggle('hidden',employeeMode);
  $('#submitAttendanceBtn').classList.toggle('hidden',employeeMode);
  if(employeeMode){
    const selectedMonth=$('#attMonth').value||today().slice(0,7);
    $('#attMonth').value=selectedMonth;
    $('#attMonth').max=today().slice(0,7);
    const history=await api('/attendance/'+session.empId);
    attendanceRows=history.records
      .filter(record=>record.date.slice(0,7)===selectedMonth)
      .map(record=>({empId:Number(session.empId),name:history.employee,date:record.date,status:record.status}));
    attendanceDate=selectedMonth;
    dirtyAttendance.clear();
    $('#attHead').innerHTML='<tr><th>Date</th><th>Employee</th><th>Status</th></tr>';
    renderAttendance();
    return;
  }
  $('#attDateControl').classList.remove('hidden');
  $('#attendanceFilter').classList.remove('hidden');
  $('#demoMonthBtn').classList.remove('hidden');
  $('#submitAttendanceBtn').classList.toggle('hidden',session.role!=='ADMIN');
  $('#attHead').innerHTML='<tr><th>Employee ID</th><th>Employee</th><th>Department</th><th>Designation</th><th>Status</th><th>Action</th></tr>';
  const selectedDate=$('#attDate').value||today();
  if(attendanceDate&&selectedDate!==attendanceDate&&dirtyAttendance.size&&!confirm('Discard unsaved attendance changes?')){
    $('#attDate').value=attendanceDate;
    return;
  }
  $('#attDate').value=selectedDate; $('#attDate').max=today();
  let nextRows;
  try {
    nextRows=await api('/attendance/date/'+selectedDate);
    allEmployees=await api('/employees');
  } catch(error) {
    if(attendanceDate)$('#attDate').value=attendanceDate;
    throw error;
  }
  dirtyAttendance.clear();
  attendanceDate=selectedDate;
  attendanceRows=nextRows;
  attendanceRows.forEach(row=>row.savedStatus=row.status);
  const depts=[...new Set(allEmployees.map(e=>e.departmentName).filter(Boolean))];
  const prev=$('#attDept').value; $('#attDept').innerHTML='<option value="">All departments</option>'+depts.map(d=>`<option>${esc(d)}</option>`).join(''); $('#attDept').value=prev;
  renderAttendance();
}
function renderAttendance(){
  if(session.role==='EMPLOYEE'){
    const query=$('#attEmployeeSearch').value.trim().toLowerCase();
    const visibleRows=attendanceRows.filter(row=>!query||row.name.toLowerCase().includes(query));
    const present=attendanceRows.filter(row=>row.status==='Present').length;
    const absent=attendanceRows.filter(row=>row.status==='Absent').length;
    $('#attendanceStats').innerHTML=
      statCard('Present days',present,'present','#16a34a')+
      statCard('Absent days',absent,'absent','#ef4444');
    $('#attBody').innerHTML=visibleRows.length?visibleRows.map(row=>{
      const date=new Date(row.date+'T00:00:00').toLocaleDateString(undefined,{weekday:'short',year:'numeric',month:'short',day:'numeric'});
      return `<tr><td>${esc(date)}</td><td>${esc(row.name)}</td><td><span class="badge ${row.status==='Present'?'ok':'bad'}">${esc(row.status)}</span></td></tr>`;
    }).join(''):`<tr><td colspan="3" class="muted">${attendanceRows.length?'No attendance matches that name.':'No attendance records for this month.'}</td></tr>`;
    return;
  }
  const q=$('#attSearch').value.trim().toLowerCase(), dept=$('#attDept').value;
  const list=attendanceRows.filter(r=>(!q||r.name.toLowerCase().includes(q)||empCode(r.empId).toLowerCase().includes(q))&&(!dept||r.department===dept));
  const present=list.filter(r=>r.status==='Present').length;
  const absent=list.length-present;
  $('#attendanceStats').innerHTML=statCard('Total Employees',list.length,'people','#4f46e5')+statCard('Present',present,'present','#16a34a')+statCard('Absent',absent,'absent','#ef4444')+statCard('Attendance',list.length?Math.round(present/list.length*100):0,'leave','#f59e0b');
  $('#attBody').innerHTML=list.length?list.map(r=>`<tr class="${dirtyAttendance.has(r.empId)?'attendance-pending':''}"><td>${empCode(r.empId)}</td><td><div class="employee-cell"><div class="mini-avatar">${esc(r.name).charAt(0)}</div><strong>${esc(r.name)}</strong></div></td><td>${esc(r.department)}</td><td>${esc(r.designation)}</td><td><span class="badge ${r.status==='Present'?'ok':'bad'}">${r.status}${dirtyAttendance.has(r.empId)?' · pending':''}</span></td><td>${session.role==='ADMIN'?`<button type="button" class="attendance-toggle ${r.status==='Present'?'present':''}" aria-label="Toggle ${esc(r.name)} attendance" title="Toggle attendance" data-id="${r.empId}">${r.status==='Present'?'✓':''}</button>`:'-'}</td></tr>`).join(''):'<tr><td colspan="6" class="muted">No employees found.</td></tr>';
  $('#attendancePendingCount').textContent=dirtyAttendance.size?`(${dirtyAttendance.size})`:'';
  $('#submitAttendanceBtn').disabled=dirtyAttendance.size===0;
}
$('#attDate').onchange=()=>run(setupAttendance); $('#attSearch').oninput=renderAttendance; $('#attDept').onchange=renderAttendance;
$('#attMonth').onchange=()=>run(setupAttendance);
$('#attEmployeeSearch').oninput=renderAttendance;
$('#demoMonthBtn').onclick=()=>run(async()=>{
  if(session.role==='EMPLOYEE')$('#attMonth').value='2026-10';
  else $('#attDate').value='2026-10-30';
  await setupAttendance();
  if(session.role==='EMPLOYEE'||attendanceDate==='2026-10-30')toast('Showing the October 2026 demo month.');
});
$('#attBody').onclick=e=>{
  const b=e.target.closest('.attendance-toggle');if(!b)return;
  const row=attendanceRows.find(r=>r.empId===Number(b.dataset.id));if(!row)return;
  row.status=row.status==='Present'?'Absent':'Present';
  if(row.status===row.savedStatus)dirtyAttendance.delete(row.empId);else dirtyAttendance.set(row.empId,row.status);
  renderAttendance();
};
$('#submitAttendanceBtn').onclick=()=>run(async()=>{
  if(!dirtyAttendance.size)return;
  const payload={date:attendanceDate};
  dirtyAttendance.forEach((status,empId)=>payload[`status_${empId}`]=status);
  const result=await api('/attendance/submit','POST',payload);
  attendanceRows.forEach(row=>row.savedStatus=row.status);
  dirtyAttendance.clear();
  renderAttendance();
  toast(result.message);
});

async function setupLeave(){
  if(!$('#leaveStart').value){$('#leaveStart').value=today();$('#leaveEnd').value=today();}
  $('#leaveStart').min=today();
  const requests=await api('/leave');
  renderLeave(requests);
  if(session.role==='EMPLOYEE') {const balances=await api('/leave/balances');$('#leaveBalances').innerHTML=balances.map(b=>`<div class="leave-balance"><div><strong>${esc(b.leaveType)}</strong><span>${b.used} used · ${b.remaining} remaining</span></div><div class="progress"><div style="width:${b.allocated?b.remaining/b.allocated*100:0}%"></div></div><b>${b.remaining}/${b.allocated}</b></div>`).join('');}
  else $('#leaveBalances').innerHTML='<p class="muted">Select an employee account to review employee balances.</p>';
}
function renderLeave(rows){
  $('#leaveBody').innerHTML=rows.length?rows.map(r=>`<tr><td><strong>${esc(r.employeeName)}</strong><span class="subcell">${empCode(r.empId)}</span></td><td>${esc(r.leaveType)}</td><td>${esc(r.startDate)}</td><td>${esc(r.endDate)}</td><td>${esc(r.reason)}</td><td><span class="badge ${r.status==='APPROVED'?'ok':r.status==='REJECTED'?'bad':'warn'}">${r.status}</span></td>${session.role==='ADMIN'?`<td>${r.status==='PENDING'?`<button class="btn small" data-leave="${r.leaveId}" data-status="APPROVED">Approve</button><button class="btn small danger" data-leave="${r.leaveId}" data-status="REJECTED">Reject</button>`:'-'}</td>`:''}</tr>`).join(''):'<tr><td colspan="7" class="muted">No leave requests.</td></tr>';
}
$('#leaveForm').onsubmit=e=>run(async()=>{e.preventDefault();await api('/leave','POST',{leaveType:$('#leaveType').value,startDate:$('#leaveStart').value,endDate:$('#leaveEnd').value,reason:$('#leaveReason').value});toast('Leave request submitted.');$('#leaveReason').value='';await setupLeave();});
$('#leaveBody').onclick=e=>{const b=e.target.closest('[data-leave]');if(!b)return;run(async()=>{await api('/leave/decide/'+b.dataset.leave,'POST',{status:b.dataset.status});toast(`Leave ${b.dataset.status.toLowerCase()}.`);await setupLeave();});};

async function setupSalary(){
  allEmployees=await api('/employees');
  $('#salEmp').innerHTML=allEmployees.map(e=>`<option value="${e.empId}">${empCode(e.empId)} - ${esc(e.name)}</option>`).join('');
  if(!$('#salMonth').options.length){$('#salMonth').innerHTML=MONTHS.map((m,i)=>`<option value="${i+1}">${m}</option>`).join('');}
  if(session.role==='EMPLOYEE'){
    $('#salEmp').value=session.empId;
    $('#salEmp').disabled=true;
    $('#salEmployeeControl').classList.remove('hidden');
    const history=await api('/salary/history');
    const now=new Date();
    const pastHistory=history.filter(s=>s.year===now.getFullYear()&&s.month<now.getMonth()+1);
    const salaryMonths=pastHistory.map(s=>s.month).sort((a,b)=>a-b);
    $('#salMonth').innerHTML=salaryMonths.map(month=>`<option value="${month}">${MONTHS[month-1]}</option>`).join('');
    $('#salYear').value=now.getFullYear();
    $('#salYear').disabled=true;
    const selectedSalary=pastHistory[0];
    if(selectedSalary){
      $('#salMonth').value=selectedSalary.month;
    }
  }else{
    $('#salEmp').disabled=false;
    $('#salEmployeeControl').classList.remove('hidden');
    $('#salYear').disabled=false;
    $('#salMonth').innerHTML=MONTHS.map((m,i)=>`<option value="${i+1}">${m}</option>`).join('');
    $('#salMonth').value=new Date().getMonth()+1;
    $('#salYear').value=new Date().getFullYear();
  }
  await loadSalarySlip();
}
async function loadSalarySlip(){
  if(!$('#salEmp').value)return;
  const q=`?empId=${$('#salEmp').value}&month=${$('#salMonth').value}&year=${$('#salYear').value}`;
  try{$('#slipArea').innerHTML=renderSlip(await api('/salary'+q));}catch(e){$('#slipArea').innerHTML=`<div class="panel muted">${esc(e.message)}</div>`;}
}
$('#calcBtn').onclick=()=>run(async()=>{const s=await api('/salary','POST',{empId:$('#salEmp').value,month:$('#salMonth').value,year:$('#salYear').value});$('#slipArea').innerHTML=renderSlip(s);toast('Salary slip generated.');});
['salEmp','salMonth','salYear'].forEach(id=>$('#'+id).onchange=()=>run(loadSalarySlip));
function renderSlip(s){return `<div class="slip"><div class="payslip-brand"><div class="brand-mark">S</div><div><strong>SmartHR Technologies</strong><span>Employee Payroll Department</span></div></div><h2>Salary Slip</h2><div class="period">${MONTHS[s.month-1]} ${s.year}</div>
  <div class="payslip-grid"><div><span>Employee</span><strong>${esc(s.empName)} (${empCode(s.empId)})</strong></div><div><span>Department</span><strong>${esc(s.department)}</strong></div><div><span>Designation</span><strong>${esc(s.jobRole)}</strong></div></div>
  <div class="payslip-columns"><div><h3>Earnings</h3>${slipRow('Basic salary',s.basic)}${slipRow('HRA',s.hra)}${slipRow('DA',s.da)}${slipRow('Bonus',s.bonus)}${slipRow('Gross salary',s.gross,'total')}</div><div><h3>Deductions</h3>${slipRow('PF',s.pf)}${slipRow('Income tax',s.tax)}${slipRow('Absence deduction',s.absentDeduction)}${slipRow('Total deductions',s.totalDeductions,'total')}</div></div>
  <div class="net"><span>Net salary</span><strong>${money(s.net)}</strong></div><div class="payslip-actions no-print"><button class="btn" onclick="window.print()">Download / Print PDF</button></div></div>`;}
function slipRow(label,value,cls=''){return `<div class="row ${cls}"><span>${label}</span><span>${money(value)}</span></div>`;}

async function loadReports(){
  const [summary,rows]=await Promise.all([api('/summary'),api('/reports')]);
  $('#reportCards').innerHTML=statCard('Total Employees',summary.totalEmployees,'people','#4f46e5')+statCard('Monthly payroll',summary.totalSalary,'payroll','#6366f1')+statCard('Present Today',summary.presentToday,'present','#16a34a');
  $('#reportBody').innerHTML=rows.length?rows.map(r=>`<tr><td>${empCode(r.empId)}</td><td><strong>${esc(r.name)}</strong></td><td>${esc(r.department)}</td><td class="num">${money(r.basic)}</td><td class="num">${money(r.gross)}</td><td class="num"><strong>${money(r.net)}</strong></td></tr>`).join(''):'<tr><td colspan="6" class="muted">No report data.</td></tr>';
}

$('#modalClose').onclick=closeModal;
$('#modal').onclick=e=>{if(e.target.id==='modal')closeModal();};

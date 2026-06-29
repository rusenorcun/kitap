'use strict';

const state = {
  token: localStorage.getItem('kitap_token') || null,
  user: JSON.parse(localStorage.getItem('kitap_user') || 'null'),
};

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

function toast(message, type = '') {
  const el = $('#toast');
  el.textContent = message;
  el.className = `toast ${type}`;
  setTimeout(() => el.classList.add('hidden'), 3500);
}

async function api(path, { method = 'GET', body, isForm = false } = {}) {
  const headers = {};
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  if (!isForm) headers['Content-Type'] = 'application/json';

  const res = await fetch(`/api${path}`, {
    method,
    headers,
    body: isForm ? body : body ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try { data = await res.json(); } catch (_e) { /* boş yanıt */ }
  if (!res.ok) throw new Error((data && data.error) || 'Bir hata oluştu.');
  return data;
}

function setSession(token, user) {
  state.token = token;
  state.user = user;
  localStorage.setItem('kitap_token', token);
  localStorage.setItem('kitap_user', JSON.stringify(user));
  render();
}

function logout() {
  state.token = null;
  state.user = null;
  localStorage.removeItem('kitap_token');
  localStorage.removeItem('kitap_user');
  render();
}

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ));
}

const LEVELS = { ortaokul: 'Ortaokul', lise: 'Lise', universite: 'Üniversite', hepsi: 'Tüm seviyeler' };

// ---------- Görünüm yönetimi ----------
function render() {
  const authView = $('#auth-view');
  const donorView = $('#donor-view');
  const studentView = $('#student-view');
  const navUser = $('#nav-user');

  [authView, donorView, studentView].forEach((v) => v.classList.add('hidden'));

  if (!state.user) {
    authView.classList.remove('hidden');
    navUser.classList.add('hidden');
    return;
  }

  navUser.classList.remove('hidden');
  $('#user-greeting').textContent = `${state.user.name} (${state.user.role === 'donor' ? 'Bağışçı' : 'Öğrenci'})`;

  if (state.user.role === 'donor') {
    donorView.classList.remove('hidden');
    loadDonorView();
  } else {
    studentView.classList.remove('hidden');
    loadStudentView();
  }
}

// ---------- Bağışçı paneli ----------
async function loadDonorView() {
  try {
    const [mine, requests] = await Promise.all([
      api('/donations/mine'),
      api('/requests?status=open'),
    ]);
    renderMyDonations(mine);
    renderOpenRequests(requests);
  } catch (err) {
    toast(err.message, 'error');
  }
}

function renderMyDonations(list) {
  const el = $('#my-donations');
  if (!list.length) { el.innerHTML = '<p class="empty">Henüz bağış yapmadınız.</p>'; return; }
  el.innerHTML = list.map((d) => `
    <div class="item">
      <div class="item-title">${esc(d.book_title)}
        <span class="badge ${d.remaining > 0 ? 'badge-open' : 'badge-done'}">
          ${d.remaining > 0 ? `${d.remaining}/${d.quantity} kaldı` : 'tükendi'}
        </span>
      </div>
      <div class="item-meta">
        ${esc(d.book_author || 'Yazar belirtilmemiş')} · Hedef: ${LEVELS[d.target_level]} · ${d.claimed} kişi aldı
      </div>
      ${d.claimers && d.claimers.length
        ? `<div class="item-meta">Alanlar: ${d.claimers.map((c) => `${esc(c.name)} (${LEVELS[c.school_level] || '-'})`).join(', ')}</div>`
        : ''}
    </div>
  `).join('');
}

function renderOpenRequests(list) {
  const el = $('#open-requests');
  if (!list.length) { el.innerHTML = '<p class="empty">Açık istek yok.</p>'; return; }
  el.innerHTML = list.map((r) => `
    <div class="item">
      <div class="item-title">${esc(r.book_title)}</div>
      <div class="item-meta">
        ${esc(r.book_author || 'Yazar belirtilmemiş')} · ${esc(r.student_name)} (${LEVELS[r.school_level] || '-'})
      </div>
      ${r.description ? `<div class="item-meta">${esc(r.description)}</div>` : ''}
      <div class="item-actions">
        <button class="btn btn-success btn-small" data-fulfill="${r.id}">Satın Al / Karşıla</button>
      </div>
    </div>
  `).join('');
  $$('[data-fulfill]').forEach((btn) => btn.addEventListener('click', () => fulfillRequest(btn.dataset.fulfill)));
}

async function fulfillRequest(id) {
  try {
    await api(`/requests/${id}/fulfill`, { method: 'POST' });
    toast('İstek karşılandı, teşekkürler!', 'success');
    loadDonorView();
  } catch (err) {
    toast(err.message, 'error');
  }
}

// ---------- Öğrenci paneli ----------
async function loadStudentView() {
  try {
    const [quota, donations, myRequests, myClaims] = await Promise.all([
      api('/me/quota'),
      api('/donations'),
      api('/requests/mine'),
      api('/donations/claimed/mine'),
    ]);
    renderQuota(quota);
    renderOpenDonations(donations, quota);
    renderMyRequests(myRequests);
    renderMyClaims(myClaims);
  } catch (err) {
    toast(err.message, 'error');
  }
}

function renderQuota(q) {
  $('#quota-info').innerHTML = `
    <div class="quota-stat"><strong>${q.weeklyRemaining}/${q.weeklyLimit}</strong>Bu hafta kalan</div>
    <div class="quota-stat"><strong>${q.quarterlyRemaining}/${q.quarterlyLimit}</strong>Bu 3 ayda kalan</div>
  `;
}

function renderOpenDonations(list, quota) {
  const el = $('#open-donations');
  const myLevel = state.user.school_level;
  const visible = list.filter((d) => d.target_level === 'hepsi' || d.target_level === myLevel);
  if (!visible.length) { el.innerHTML = '<p class="empty">Uygun bağış bulunamadı.</p>'; return; }
  const canReceive = quota.canReceive;
  el.innerHTML = visible.map((d) => `
    <div class="item">
      <div class="item-title">${esc(d.book_title)}
        <span class="badge badge-open">${d.remaining} adet</span>
      </div>
      <div class="item-meta">
        ${esc(d.book_author || 'Yazar belirtilmemiş')} · Bağışçı: ${esc(d.donor_name)} · ${LEVELS[d.target_level]}
      </div>
      ${d.description ? `<div class="item-meta">${esc(d.description)}</div>` : ''}
      <div class="item-actions">
        <button class="btn btn-primary btn-small" data-claim="${d.id}" ${canReceive ? '' : 'disabled'}>
          ${canReceive ? 'Kitabı Al' : 'Kotanız dolu'}
        </button>
      </div>
    </div>
  `).join('');
  $$('[data-claim]').forEach((btn) => btn.addEventListener('click', () => claimDonation(btn.dataset.claim)));
}

async function claimDonation(id) {
  try {
    await api(`/donations/${id}/claim`, { method: 'POST' });
    toast('Kitap başarıyla alındı!', 'success');
    loadStudentView();
  } catch (err) {
    toast(err.message, 'error');
  }
}

function renderMyRequests(list) {
  const el = $('#my-requests');
  if (!list.length) { el.innerHTML = '<p class="empty">Henüz istek oluşturmadınız.</p>'; return; }
  el.innerHTML = list.map((r) => `
    <div class="item">
      <div class="item-title">${esc(r.book_title)}
        <span class="badge ${r.status === 'open' ? 'badge-open' : 'badge-done'}">
          ${r.status === 'open' ? 'bekliyor' : 'karşılandı'}
        </span>
      </div>
      <div class="item-meta">
        ${esc(r.book_author || 'Yazar belirtilmemiş')}
        ${r.status === 'fulfilled' && r.fulfilled_by_name ? `· ${esc(r.fulfilled_by_name)} karşıladı` : ''}
      </div>
      ${r.status === 'open'
        ? `<div class="item-actions"><button class="btn btn-danger btn-small" data-del-req="${r.id}">İptal Et</button></div>`
        : ''}
    </div>
  `).join('');
  $$('[data-del-req]').forEach((btn) => btn.addEventListener('click', () => deleteRequest(btn.dataset.delReq)));
}

async function deleteRequest(id) {
  try {
    await api(`/requests/${id}`, { method: 'DELETE' });
    toast('İstek iptal edildi.', 'success');
    loadStudentView();
  } catch (err) {
    toast(err.message, 'error');
  }
}

function renderMyClaims(list) {
  const el = $('#my-claims');
  if (!list.length) { el.innerHTML = '<p class="empty">Henüz kitap almadınız.</p>'; return; }
  el.innerHTML = list.map((c) => `
    <div class="item">
      <div class="item-title">${esc(c.book_title)}</div>
      <div class="item-meta">${esc(c.book_author || '')} · Bağışçı: ${esc(c.donor_name)}</div>
    </div>
  `).join('');
}

// ---------- Form & sekme olayları ----------
function initTabs() {
  $$('.tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      $$('.tab').forEach((t) => t.classList.remove('active'));
      $$('.tab-panel').forEach((p) => p.classList.remove('active'));
      tab.classList.add('active');
      const map = {
        login: '#form-login',
        'register-donor': '#form-register-donor',
        'register-student': '#form-register-student',
      };
      $(map[tab.dataset.tab]).classList.add('active');
    });
  });
}

function formData(form) {
  return Object.fromEntries(new FormData(form).entries());
}

function initForms() {
  $('#form-login').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      const { token, user } = await api('/auth/login', { method: 'POST', body: formData(e.target) });
      setSession(token, user);
      toast('Giriş başarılı.', 'success');
    } catch (err) { toast(err.message, 'error'); }
  });

  $('#form-register-donor').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      const { token, user } = await api('/auth/register/donor', { method: 'POST', body: formData(e.target) });
      setSession(token, user);
      toast('Bağışçı kaydı tamamlandı.', 'success');
    } catch (err) { toast(err.message, 'error'); }
  });

  $('#form-register-student').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      const { token, user } = await api('/auth/register/student', {
        method: 'POST', body: new FormData(e.target), isForm: true,
      });
      setSession(token, user);
      toast('Öğrenci kaydı tamamlandı.', 'success');
    } catch (err) { toast(err.message, 'error'); }
  });

  $('#form-donation').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await api('/donations', { method: 'POST', body: formData(e.target) });
      e.target.reset();
      toast('Bağış yayınlandı.', 'success');
      loadDonorView();
    } catch (err) { toast(err.message, 'error'); }
  });

  $('#form-request').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await api('/requests', { method: 'POST', body: formData(e.target) });
      e.target.reset();
      toast('İstek oluşturuldu.', 'success');
      loadStudentView();
    } catch (err) { toast(err.message, 'error'); }
  });

  $('#btn-logout').addEventListener('click', logout);
}

initTabs();
initForms();
render();

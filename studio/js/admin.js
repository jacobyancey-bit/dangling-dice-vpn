/**
 * Dangling Dice Studios™ — Administrator Portal Client Application
 */

const API_BASE = (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') && window.location.port !== '5000' && window.location.port !== '3000'
  ? 'http://localhost:5000/api'
  : window.location.origin + '/api';
let adminToken = localStorage.getItem('dangling_admin_jwt');
let adminUser = null;
let selectedCategory = 'All';
let catalogItems = [];

// Logging helper
function logAdmin(msg, isSuccess = false) {
  const box = document.getElementById('adminLogBox');
  if (!box) return;
  const time = new Date().toLocaleTimeString();
  const div = document.createElement('div');
  div.className = 'log-line';
  div.innerHTML = `<span style="color: var(--text-faint)">[${time}]</span> <span style="color: ${isSuccess ? 'var(--neon-emerald)' : 'var(--text-muted)'}">${msg}</span>`;
  box.appendChild(div);
  box.scrollTop = box.scrollHeight;
}

function showAlert(id, msg, isSuccess = false) {
  const el = document.getElementById(id);
  if (!el) return;
  el.className = `alert ${isSuccess ? 'alert-success' : 'alert-error'}`;
  el.textContent = msg;
  el.style.display = 'block';
}

function clearAlert(id) {
  const el = document.getElementById(id);
  if (!el) return;
  el.style.display = 'none';
  el.textContent = '';
}

// Authentication Handlers
async function handleAdminLogin(e) {
  e.preventDefault();
  clearAlert('loginAlert');
  const email = document.getElementById('adminEmail').value;
  const password = document.getElementById('adminPassword').value;

  try {
    logAdmin(`Authenticating ${email}...`);
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });
    const json = await res.json();
    if (!res.ok) throw new Error(json.message || 'Login failed');

    if (json.data.role !== 'admin') {
      throw new Error('Access Denied: This account does not possess administrator privileges.');
    }

    adminToken = json.data.token;
    adminUser = json.data;
    localStorage.setItem('dangling_admin_jwt', adminToken);
    logAdmin(`Authenticated as Administrator: ${adminUser.name}`, true);
    showDashboard();
  } catch (err) {
    showAlert('loginAlert', err.message);
    logAdmin(`Authentication failed: ${err.message}`);
  }
}

function logoutAdmin() {
  adminToken = null;
  adminUser = null;
  localStorage.removeItem('dangling_admin_jwt');
  const loginView = document.getElementById('loginView');
  const dashboardView = document.getElementById('dashboardView');
  const headerLogoutBtn = document.getElementById('headerLogoutBtn');
  if (loginView) loginView.style.display = 'block';
  if (dashboardView) dashboardView.style.display = 'none';
  if (headerLogoutBtn) headerLogoutBtn.style.display = 'none';
  logAdmin('Signed out of Administrator Portal.');
}

async function verifyExistingSession() {
  if (!adminToken) return;
  try {
    const res = await fetch(`${API_BASE}/auth/profile`, {
      headers: { 'Authorization': `Bearer ${adminToken}` }
    });
    const json = await res.json();
    if (res.ok && json.data.role === 'admin') {
      adminUser = json.data;
      showDashboard();
    } else {
      logoutAdmin();
    }
  } catch (err) {
    logoutAdmin();
  }
}

function showDashboard() {
  const loginView = document.getElementById('loginView');
  const dashboardView = document.getElementById('dashboardView');
  const headerLogoutBtn = document.getElementById('headerLogoutBtn');
  const statAdminName = document.getElementById('statAdminName');
  if (loginView) loginView.style.display = 'none';
  if (dashboardView) dashboardView.style.display = 'block';
  if (headerLogoutBtn) headerLogoutBtn.style.display = 'inline-flex';
  if (statAdminName && adminUser) statAdminName.textContent = `${adminUser.name} (${adminUser.email})`;
  fetchInventory();
}

// Category Filter
function filterCategory(cat) {
  selectedCategory = cat;
  document.querySelectorAll('.pill-btn').forEach(btn => {
    const text = btn.textContent;
    const matches = (cat === 'All' && text.includes('All')) ||
                    (cat === 'Coding Tutorials' && text.includes('Coding')) ||
                    (cat === 'Beats & Audio' && text.includes('Beats')) ||
                    (cat === 'Mobile Apps' && text.includes('Mobile')) ||
                    (cat === 'Studio Games' && text.includes('Games')) ||
                    (cat === 'Developer Tools' && text.includes('Dev Tools'));
    btn.classList.toggle('active', matches);
  });
  fetchInventory();
}

// Inventory CRUD
async function fetchInventory() {
  const tbody = document.getElementById('inventoryTableBody');
  const countEl = document.getElementById('statItemCount');
  if (!tbody) return;
  try {
    const query = selectedCategory !== 'All' ? `?category=${encodeURIComponent(selectedCategory)}` : '';
    logAdmin(`GET /api/items${query}...`);
    const res = await fetch(`${API_BASE}/items${query}`);
    const json = await res.json();
    if (!res.ok) throw new Error(json.message);

    catalogItems = json.data || [];
    if (countEl) countEl.textContent = catalogItems.length;

    if (catalogItems.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 2rem;">No items found in this category. Click "+ Publish New Item" above to add one.</td></tr>`;
      return;
    }

    tbody.innerHTML = catalogItems.map(item => {
      const priceLabel = item.price === 0 ? 'FREE' : `$${Number(item.price).toFixed(2)}`;
      return `
        <tr>
          <td>
            <div style="font-weight: 700; color: #fff;">${escapeHtml(item.title)}</div>
            <div style="font-size: 0.78rem; color: var(--text-muted); max-width: 380px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${escapeHtml(item.description)}</div>
          </td>
          <td><span class="badge-tag">${escapeHtml(item.category)}</span></td>
          <td style="font-weight: 600;">${priceLabel}</td>
          <td>
            ${item.mediaUrl ? `<a href="${escapeHtml(item.mediaUrl)}" target="_blank" rel="noopener noreferrer" style="color: var(--neon-cyan); text-decoration: none; font-size: 0.8rem;">Open Link ↗</a>` : '<span style="color: var(--text-faint);">-</span>'}
          </td>
          <td>
            <div style="display: flex; gap: 0.4rem;">
              <button class="btn btn-secondary btn-sm" onclick="openEditModal('${item._id}')">✏️ Edit</button>
              <button class="btn btn-danger btn-sm" onclick="handleDelete('${item._id}')">🗑️</button>
            </div>
          </td>
        </tr>
      `;
    }).join('');

    logAdmin(`Loaded ${catalogItems.length} items from MongoDB Atlas.`, true);
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="5" style="color: var(--neon-red); text-align: center; padding: 2rem;">Error: ${escapeHtml(err.message)}</td></tr>`;
    logAdmin(`Fetch error: ${err.message}`);
  }
}

// Publish Modal
function openPublishModal() {
  clearAlert('publishAlert');
  const form = document.getElementById('publishForm');
  const modal = document.getElementById('publishModal');
  if (form) form.reset();
  if (modal) modal.style.display = 'flex';
}

function closePublishModal() {
  const modal = document.getElementById('publishModal');
  if (modal) modal.style.display = 'none';
  clearAlert('publishAlert');
}

async function handlePublishSubmit(e) {
  e.preventDefault();
  clearAlert('publishAlert');

  const title = document.getElementById('newTitle').value;
  const category = document.getElementById('newCategory').value;
  const mediaUrl = document.getElementById('newMediaUrl').value;
  const tags = document.getElementById('newTags').value;
  const price = parseFloat(document.getElementById('newPrice').value) || 0;
  const description = document.getElementById('newDesc').value;

  try {
    logAdmin(`POST /api/items creating "${title}"...`);
    const res = await fetch(`${API_BASE}/items`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${adminToken}`
      },
      body: JSON.stringify({
        title,
        category,
        mediaUrl,
        tags,
        price,
        description,
        imageUrl: category === 'Beats & Audio' ? '/images/banner.jpg' : '/images/logo.png'
      })
    });

    const json = await res.json();
    if (!res.ok) throw new Error(json.message);

    logAdmin(`Successfully published resource "${json.data.title}"`, true);
    closePublishModal();
    fetchInventory();
  } catch (err) {
    showAlert('publishAlert', err.message);
    logAdmin(`Publish error: ${err.message}`);
  }
}

// Edit Modal
function openEditModal(id) {
  const item = catalogItems.find(i => i._id === id || i.id === id);
  if (!item) return;

  const editModal = document.getElementById('editModal');
  document.getElementById('editItemId').value = item._id;
  document.getElementById('editTitle').value = item.title || '';
  document.getElementById('editCategory').value = item.category || 'Coding Tutorials';
  document.getElementById('editMediaUrl').value = item.mediaUrl || '';
  document.getElementById('editTags').value = Array.isArray(item.tags) ? item.tags.join(', ') : (item.tags || '');
  document.getElementById('editPrice').value = item.price !== undefined ? item.price : 0;
  document.getElementById('editDesc').value = item.description || '';
  clearAlert('editAlert');

  if (editModal) editModal.style.display = 'flex';
}

function closeEditModal() {
  const editModal = document.getElementById('editModal');
  if (editModal) editModal.style.display = 'none';
  clearAlert('editAlert');
}

async function handleEditSubmit(e) {
  e.preventDefault();
  clearAlert('editAlert');

  const id = document.getElementById('editItemId').value;
  const title = document.getElementById('editTitle').value;
  const category = document.getElementById('editCategory').value;
  const mediaUrl = document.getElementById('editMediaUrl').value;
  const tags = document.getElementById('editTags').value;
  const price = parseFloat(document.getElementById('editPrice').value) || 0;
  const description = document.getElementById('editDesc').value;

  try {
    logAdmin(`PUT /api/items/${id} updating "${title}"...`);
    const res = await fetch(`${API_BASE}/items/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${adminToken}`
      },
      body: JSON.stringify({
        title,
        category,
        mediaUrl,
        tags,
        price,
        description
      })
    });

    const json = await res.json();
    if (!res.ok) throw new Error(json.message);

    logAdmin(`Successfully updated resource "${json.data.title}"`, true);
    closeEditModal();
    fetchInventory();
  } catch (err) {
    showAlert('editAlert', err.message);
    logAdmin(`Update error: ${err.message}`);
  }
}

async function handleDelete(id) {
  if (!confirm('Are you sure you want to permanently delete this resource from the catalog?')) return;
  try {
    logAdmin(`DELETE /api/items/${id}...`);
    const res = await fetch(`${API_BASE}/items/${id}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${adminToken}` }
    });
    const json = await res.json();
    if (!res.ok) throw new Error(json.message);

    logAdmin(`Deleted resource ${id}`, true);
    fetchInventory();
  } catch (err) {
    alert(`Delete failed: ${err.message}`);
    logAdmin(`Delete error: ${err.message}`);
  }
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

// Startup
window.addEventListener('DOMContentLoaded', () => {
  verifyExistingSession();
});

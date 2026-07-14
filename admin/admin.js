const SUPABASE_URL = 'https://trazqymrstiqaqhqkuhz.supabase.co';
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRyYXpxeW1yc3RpcWFxaHFrdWh6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMyNDYzMDEsImV4cCI6MjA5ODgyMjMwMX0.9E7c3I4AG2IKYxCpi4GAzvs9uyyB4mkeOinoAi8owQw';

let currentUser = null;
let allReports = [];
let currentEditId = null;

const loginScreen = document.getElementById('loginScreen');
const dashboardScreen = document.getElementById('dashboardScreen');
const loginForm = document.getElementById('loginForm');
const loginEmail = document.getElementById('loginEmail');
const loginPassword = document.getElementById('loginPassword');
const loginError = document.getElementById('loginError');
const loginBtn = document.getElementById('loginBtn');
const logoutBtn = document.getElementById('logoutBtn');
const adminName = document.getElementById('adminName');
const reportsBody = document.getElementById('reportsBody');
const loading = document.getElementById('loading');
const searchInput = document.getElementById('searchInput');
const filterType = document.getElementById('filterType');
const filterStatus = document.getElementById('filterStatus');
const filterDate = document.getElementById('filterDate');
const editModal = document.getElementById('editModal');
const closeModal = document.getElementById('closeModal');
const editForm = document.getElementById('editForm');
const editStatus = document.getElementById('editStatus');
const editDescription = document.getElementById('editDescription');
const editType = document.getElementById('editType');
const saveBtn = document.getElementById('saveBtn');
const deleteBtn = document.getElementById('deleteBtn');

// Headers for Supabase REST API
function authHeaders() {
    return {
        'apikey': SUPABASE_ANON_KEY,
        'Content-Type': 'application/json'
    };
}

// Page navigation
document.querySelectorAll('.nav-link').forEach(link => {
    link.addEventListener('click', (e) => {
        e.preventDefault();
        const page = link.dataset.page;
        document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
        link.classList.add('active');
        document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
        document.getElementById('page-' + page).classList.add('active');
        if (page === 'reports') loadReports();
    });
});

// Check existing session on load
checkSession();

loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    loginBtn.disabled = true;
    loginError.textContent = '';
    await handleLogin();
    loginBtn.disabled = false;
});

logoutBtn.addEventListener('click', () => {
    localStorage.removeItem('admin_session');
    currentUser = null;
    showLogin();
});

function checkSession() {
    const session = localStorage.getItem('admin_session');
    if (session) {
        try {
            currentUser = JSON.parse(session);
            adminName.textContent = currentUser.email || 'Admin';
            showDashboard();
            loadReports();
        } catch {
            localStorage.removeItem('admin_session');
            showLogin();
        }
    } else {
        showLogin();
    }
}

async function handleLogin() {
    const email = loginEmail.value.trim().toLowerCase();
    const password = loginPassword.value;

    if (!email || !password) {
        loginError.textContent = 'Please enter email and password';
        return;
    }

    try {
        const res = await fetch(
            `${SUPABASE_URL}/rest/v1/admins?email=eq.${encodeURIComponent(email)}&select=*`,
            { headers: authHeaders() }
        );

        if (!res.ok) {
            loginError.textContent = 'Database error (HTTP ' + res.status + ')';
            return;
        }

        const data = await res.json();

        if (!data || data.length === 0) {
            loginError.textContent = 'Invalid email or password';
            return;
        }

        const admin = data[0];
        const passwordHash = await sha256(password);

        if (passwordHash !== admin.password_hash) {
            loginError.textContent = 'Invalid email or password';
            return;
        }

        currentUser = { id: admin.id, email: admin.email, name: admin.name || 'Admin' };
        localStorage.setItem('admin_session', JSON.stringify(currentUser));
        adminName.textContent = currentUser.email;
        showDashboard();
        loadReports();
    } catch (err) {
        loginError.textContent = 'Login failed: ' + err.message;
    }
}

async function sha256(message) {
    const encoder = new TextEncoder();
    const data = encoder.encode(message);
    const hashBuffer = await crypto.subtle.digest('SHA-256', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

function showLogin() {
    loginScreen.classList.remove('hidden');
    dashboardScreen.classList.add('hidden');
    loginBtn.disabled = false;
    loginPassword.value = '';
}

function showDashboard() {
    loginScreen.classList.add('hidden');
    dashboardScreen.classList.remove('hidden');
}

async function loadReports() {
    loading.classList.remove('hidden');

    try {
        const res = await fetch(
            `${SUPABASE_URL}/rest/v1/hazards?select=*&order=timestamp.desc`,
            { headers: authHeaders() }
        );
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const data = await res.json();

        allReports = data.map(r => ({
            ...r,
            timestamp: r.timestamp ? new Date(r.timestamp) : new Date()
        }));
        loading.classList.add('hidden');
        updateStats();
        renderReports();
    } catch (err) {
        loading.classList.add('hidden');
        console.error('Error loading reports:', err);
    }
}

function updateStats() {
    const total = allReports.length;
    const newCount = allReports.filter(r => r.status === 'New').length;
    const investigating = allReports.filter(r => r.status === 'Under Investigation').length;
    const resolved = allReports.filter(r => r.status === 'Resolved').length;

    document.getElementById('totalReports').textContent = total;
    document.getElementById('newReports').textContent = newCount;
    document.getElementById('investigatingReports').textContent = investigating;
    document.getElementById('resolvedReports').textContent = resolved;
}

function renderReports() {
    const search = searchInput.value.toLowerCase();
    const type = filterType.value;
    const status = filterStatus.value;
    const date = filterDate.value;

    const filtered = allReports.filter(r => {
        const matchSearch = (r.user_name?.toLowerCase() || '').includes(search) ||
                           (r.description?.toLowerCase() || '').includes(search);
        const matchType = !type || r.hazard_type === type;
        const matchStatus = !status || r.status === status;
        const matchDate = !date || formatDate(r.timestamp) === date;
        return matchSearch && matchType && matchStatus && matchDate;
    });

    if (filtered.length === 0) {
        reportsBody.innerHTML = '<tr><td colspan="9" class="empty">No reports found</td></tr>';
        return;
    }

    reportsBody.innerHTML = filtered.map(r => `
        <tr>
            <td>${escapeHtml(r.user_name || 'Unknown')}</td>
            <td>${formatDateTime(r.timestamp)}</td>
            <td><span class="badge type-${r.hazard_type?.replace(/\s+/g, '-')}">${escapeHtml(r.hazard_type)}</span></td>
            <td>${escapeHtml(r.description || '')}</td>
            <td>${r.photo_url ? `<a href="${r.photo_url}" target="_blank" class="photo-link">View Photo</a>` : '-'}</td>
            <td>${parseFloat(r.latitude).toFixed(4)}, ${parseFloat(r.longitude).toFixed(4)}</td>
            <td><span class="badge status-${r.status?.replace(/\s+/g, '-')}">${r.status}</span></td>
            <td class="user-agent-cell" title="${escapeHtml(r.user_agent || '')}">${escapeHtml(truncate(r.user_agent, 30))}</td>
            <td>
                <button class="btn-sm btn-edit" onclick="openEdit('${r.id}')">Edit</button>
            </td>
        </tr>
    `).join('');
}

searchInput.addEventListener('input', renderReports);
filterType.addEventListener('change', renderReports);
filterStatus.addEventListener('change', renderReports);
filterDate.addEventListener('change', renderReports);

function openEdit(id) {
    currentEditId = id;
    const report = allReports.find(r => String(r.id) === String(id));
    if (!report) return;

    editStatus.value = report.status || 'New';
    editDescription.value = report.description || '';
    editType.value = report.hazard_type || 'Pothole';
    editModal.classList.remove('hidden');
}

closeModal.addEventListener('click', () => {
    editModal.classList.add('hidden');
    currentEditId = null;
});

window.addEventListener('click', (e) => {
    if (e.target === editModal) {
        editModal.classList.add('hidden');
        currentEditId = null;
    }
});

editForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!currentEditId) return;

    saveBtn.disabled = true;
    try {
        const res = await fetch(
            `${SUPABASE_URL}/rest/v1/hazards?id=eq.${currentEditId}`,
            {
                method: 'PATCH',
                headers: authHeaders(),
                body: JSON.stringify({
                    status: editStatus.value,
                    description: editDescription.value,
                    hazard_type: editType.value
                })
            }
        );
        if (!res.ok) throw new Error('HTTP ' + res.status);
        editModal.classList.add('hidden');
        currentEditId = null;
        loadReports();
    } catch (err) {
        alert('Error saving: ' + err.message);
    }
    saveBtn.disabled = false;
});

deleteBtn.addEventListener('click', async () => {
    if (!currentEditId) return;
    if (!confirm('Are you sure you want to delete this report?')) return;

    deleteBtn.disabled = true;
    try {
        const res = await fetch(
            `${SUPABASE_URL}/rest/v1/hazards?id=eq.${currentEditId}`,
            {
                method: 'DELETE',
                headers: authHeaders()
            }
        );
        if (!res.ok) throw new Error('HTTP ' + res.status);
        editModal.classList.add('hidden');
        currentEditId = null;
        loadReports();
    } catch (err) {
        alert('Error deleting: ' + err.message);
    }
    deleteBtn.disabled = false;
});

function formatDate(date) {
    const d = new Date(date);
    return d.toISOString().split('T')[0];
}

function formatDateTime(date) {
    const d = new Date(date);
    return d.toLocaleString();
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function truncate(str, len) {
    if (!str) return '';
    return str.length > len ? str.substring(0, len) + '...' : str;
}

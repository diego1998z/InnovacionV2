/* ═══════════════════════════════════════
   app.js — Controlador principal
   ═══════════════════════════════════════ */

// ── State ─────────────────────────────────────────
let state = {
    clients: [],
    selectedEvalClient: null,
    selectedSimClient: null,
    currentEval: null,
    chatClientId: null,
    editClientId: null,
    dashboardCharts: [],
};

// ── Init ──────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    const token = localStorage.getItem('credit_token');
    if (token) showApp();
    else showLogin();

    document.getElementById('loginForm').addEventListener('submit', handleLogin);
    document.getElementById('logoutBtn').addEventListener('click', handleLogout);
    document.getElementById('newClientBtn').addEventListener('click', () => openClientModal());
    document.getElementById('clientForm').addEventListener('submit', handleClientSave);
    document.getElementById('clientSearch').addEventListener('input', filterClients);
    document.getElementById('evalSearch').addEventListener('input', () => renderEvalClientList(document.getElementById('evalSearch').value));
    document.getElementById('simSearch').addEventListener('input',  () => renderSimClientList(document.getElementById('simSearch').value));

    document.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', e => {
            e.preventDefault();
            navigateTo(item.dataset.page);
        });
    });
});

// ── Auth ──────────────────────────────────────────
async function handleLogin(e) {
    e.preventDefault();
    const username = document.getElementById('loginUsername').value;
    const password = document.getElementById('loginPassword').value;
    const errEl = document.getElementById('loginError');
    errEl.classList.add('hidden');
    try {
        const user = await api.login(username, password);
        showApp(user);
    } catch (err) {
        errEl.textContent = err.message;
        errEl.classList.remove('hidden');
    }
}

function handleLogout() {
    api.clearToken();
    document.getElementById('app').classList.add('hidden');
    document.getElementById('loginScreen').classList.remove('hidden');
}

function showLogin() {
    document.getElementById('loginScreen').classList.remove('hidden');
    document.getElementById('app').classList.add('hidden');
}

function showApp(user) {
    document.getElementById('loginScreen').classList.add('hidden');
    document.getElementById('app').classList.remove('hidden');
    const u = user || JSON.parse(localStorage.getItem('credit_user') || '{}');
    if (u.fullName) {
        document.getElementById('userName').textContent = u.fullName;
        document.getElementById('userRole').textContent = u.role || '';
        document.getElementById('userAvatar').textContent = u.fullName[0].toUpperCase();
    }
    navigateTo('dashboard');
}

// ── Navigation ────────────────────────────────────
async function navigateTo(page) {
    document.querySelectorAll('.page').forEach(p => { p.classList.remove('active'); p.classList.add('hidden'); });
    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
    document.getElementById(page + 'Page').classList.remove('hidden');
    document.getElementById(page + 'Page').classList.add('active');
    document.querySelector(`[data-page="${page}"]`)?.classList.add('active');

    switch (page) {
        case 'dashboard':   await loadDashboard(); break;
        case 'clients':     await loadClients(); break;
        case 'evaluations': await loadClients(); renderEvalClientList(); break;
        case 'simulator':   await loadClients(); renderSimClientList(); break;
    }
}

// ── Dashboard ─────────────────────────────────────
async function loadDashboard() {
    try {
        const res = await api.getDashboard();
        const s = res.data;
        document.getElementById('totalClients').textContent = s.totalClients;
        document.getElementById('activeClients').textContent = s.activeClients;
        document.getElementById('evaluatedClients').textContent = s.evaluatedClients;
        document.getElementById('avgScore').textContent = Math.round(s.averageScore) || '—';
        document.getElementById('basicCount').textContent = s.basicCount;
        document.getElementById('intermediateCount').textContent = s.intermediateCount;
        document.getElementById('advancedCount').textContent = s.advancedCount;
        document.getElementById('highRiskCount').textContent = s.clientsWithOverdue;
        renderMlStats(s.mlMetrics || {});
        renderDashboardCharts(s);
    } catch { /* offline mode - show zeros */ }
}

function renderDashboardCharts(s) {
    state.dashboardCharts.forEach(chart => chart.destroy());
    state.dashboardCharts = [];

    const riskData = s.riskDistribution || {};
    const profileCounts = {
        BASIC: s.basicCount || 0,
        INTERMEDIATE: s.intermediateCount || 0,
        ADVANCED: s.advancedCount || 0,
    };
    const avgByProfile = s.averageScoreByProfile || {};
    const products = s.recommendedProducts || {};
    const eligible = s.eligibleByProduct || {};
    const ml = s.mlMetrics || {};

    addChart('riskChart', {
        type: 'doughnut',
        data: {
            labels: ['Muy Bajo', 'Bajo', 'Medio', 'Alto', 'Muy Alto'],
            datasets: [{
                data: [
                    riskData.VERY_LOW || 0, riskData.LOW || 0,
                    riskData.MEDIUM || 0, riskData.HIGH || 0, riskData.VERY_HIGH || 0
                ],
                backgroundColor: ['#00c9a7','#4285f4','#ff8c42','#e74c3c','#8b0000'],
                borderWidth: 2, borderColor: '#162032'
            }]
        },
        options: { plugins: { legend: { labels: { color: '#8fa3bf', font: { size: 12 } } } }, cutout: '65%' }
    });

    addChart('profileChart', {
        type: 'doughnut',
        data: {
            labels: ['Básico', 'Intermedio', 'Avanzado'],
            datasets: [{
                data: [profileCounts.BASIC, profileCounts.INTERMEDIATE, profileCounts.ADVANCED],
                backgroundColor: ['#4285f4', '#ff8c42', '#00c9a7'],
                borderWidth: 2,
                borderColor: '#162032'
            }]
        },
        options: { plugins: { legend: { labels: { color: '#8fa3bf' } } }, cutout: '60%' }
    });

    addChart('scoreBySegmentChart', {
        type: 'bar',
        data: {
            labels: ['Básico', 'Intermedio', 'Avanzado'],
            datasets: [{
                data: [avgByProfile.BASIC || 0, avgByProfile.INTERMEDIATE || 0, avgByProfile.ADVANCED || 0],
                backgroundColor: ['#4285f440','#ff8c4240','#00c9a740'],
                borderColor: ['#4285f4','#ff8c42','#00c9a7'],
                borderWidth: 2, borderRadius: 6
            }]
        },
        options: chartAxisOptions(false)
    });

    addChart('productsChart', horizontalBarConfig(products, '#00c9a7', 'Recomendaciones'));
    addChart('eligibleProductsChart', horizontalBarConfig(eligible, '#4285f4', 'Clientes aptos'));
    addChart('highRiskChart', {
        type: 'bar',
        data: {
            labels: ['Medio', 'Alto', 'Muy Alto'],
            datasets: [{
                data: [riskData.MEDIUM || 0, riskData.HIGH || 0, riskData.VERY_HIGH || 0],
                backgroundColor: ['#ff8c4240', '#e74c3c40', '#8b000060'],
                borderColor: ['#ff8c42', '#e74c3c', '#8b0000'],
                borderWidth: 2,
                borderRadius: 6
            }]
        },
        options: chartAxisOptions(false)
    });

    addChart('confusionChart', {
        type: 'bar',
        data: confusionDataset(ml.confusionMatrix || {}),
        options: chartAxisOptions(true)
    });

    addChart('featureImportanceChart', horizontalBarConfig(translateFeatureImportance(ml.featureImportance || {}), '#ff8c42', 'Importancia'));
}

function addChart(canvasId, config) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;
    state.dashboardCharts.push(new Chart(canvas.getContext('2d'), config));
}

function renderMlStats(ml) {
    document.getElementById('mlAccuracy').textContent = pct(ml.accuracy);
    document.getElementById('mlTrainAccuracy').textContent = pct(ml.trainingAccuracy);
    document.getElementById('mlTestAccuracy').textContent = pct(ml.testAccuracy);
    document.getElementById('mlTrainRows').textContent = ml.trainingRecords || '—';
    document.getElementById('mlTestRows').textContent = ml.testRecords || '—';
}

function horizontalBarConfig(dataObj, color, label) {
    const entries = Object.entries(dataObj).sort((a, b) => b[1] - a[1]).slice(0, 6);
    const safeEntries = entries.length ? entries : [['Sin datos', 0]];
    return {
        type: 'bar',
        data: {
            labels: safeEntries.map(([label]) => label),
            datasets: [{ data: safeEntries.map(([, value]) => value), label, backgroundColor: color + '40', borderColor: color, borderWidth: 2, borderRadius: 6 }]
        },
        options: { ...chartAxisOptions(false), indexAxis: 'y' }
    };
}

function confusionDataset(matrix) {
    const labels = ['BASIC', 'INTERMEDIATE', 'ADVANCED'];
    return {
        labels: labels.map(profileLabel),
        datasets: labels.map((predicted, index) => ({
            label: `Pred. ${profileLabel(predicted)}`,
            data: labels.map(actual => matrix[actual]?.[predicted] || 0),
            backgroundColor: ['#4285f460', '#ff8c4260', '#00c9a760'][index],
            borderColor: ['#4285f4', '#ff8c42', '#00c9a7'][index],
            borderWidth: 1,
            borderRadius: 4
        }))
    };
}

function translateFeatureImportance(features) {
    const labels = {
        monthly_income: 'Ingresos mensuales',
        payment_history_rate: 'Historial de pagos',
        savings_level: 'Nivel de ahorro',
        active_credits: 'Créditos activos',
        debt_ratio: 'Nivel de endeudamiento',
        payment_capacity: 'Capacidad de pago',
        employment_months: 'Antigüedad laboral',
        conventional_score: 'Score convencional',
        product_count: 'Productos previos',
        has_mora: 'Mora financiera',
        age: 'Edad'
    };
    return Object.fromEntries(Object.entries(features).map(([key, value]) => [labels[key] || key, value]));
}

function chartAxisOptions(showLegend) {
    return {
        responsive: true,
        plugins: { legend: { display: showLegend, labels: { color: '#8fa3bf', font: { size: 11 } } } },
        scales: {
            y: { ticks: { color: '#8fa3bf', font: { size: 11 } }, grid: { color: '#1e2d42' } },
            x: { ticks: { color: '#8fa3bf', font: { size: 11 } }, grid: { color: '#1e2d42' } }
        }
    };
}

// ── Clients ───────────────────────────────────────
async function loadClients() {
    try {
        const res = await api.getClients();
        state.clients = res.data || [];
        renderClientsTable(state.clients);
        renderEvalClientList();
        renderSimClientList();
    } catch { state.clients = []; }
}

function renderClientsTable(clients) {
    const tbody = document.getElementById('clientsBody');
    if (!tbody) return;
    tbody.innerHTML = clients.map(c => `
        <tr>
            <td>${escapeHtml(c.dni)}</td>
            <td><strong>${escapeHtml(c.fullName)}</strong></td>
            <td>${escapeHtml(c.age)}</td>
            <td>S/ ${fmt(c.monthlyIncome)}</td>
            <td>S/ ${fmt(c.totalSavings)}</td>
            <td><span class="badge ${statusBadge(c.status)}">${escapeHtml(c.status)}</span></td>
            <td>
                <button class="btn-icon" onclick="openClientModal(${safeId(c.id)})" title="Editar">✏️</button>
                <button class="btn-icon" onclick="openEvalForClient(${safeId(c.id)})" title="Evaluar">🔍</button>
                <button class="btn-icon" onclick="openChatModal(${safeId(c.id)})" title="Explicar modelo">💬</button>
            </td>
        </tr>`).join('');
}

function filterClients() {
    const q = document.getElementById('clientSearch').value.toLowerCase();
    const filtered = state.clients.filter(c =>
        c.fullName.toLowerCase().includes(q) || c.dni.includes(q));
    renderClientsTable(filtered);
}

function renderEvalClientList(filter = '') {
    const list = document.getElementById('evalClientList');
    if (!list) return;
    const filtered = state.clients.filter(c =>
        c.fullName.toLowerCase().includes(filter.toLowerCase()) || c.dni.includes(filter));
    list.innerHTML = filtered.map(c => `
        <div class="client-list-item ${sameId(state.selectedEvalClient?.id, c.id) ? 'selected' : ''}"
             onclick="selectEvalClient(${safeId(c.id)})">
            <div class="item-name">${escapeHtml(c.fullName)}</div>
            <div class="item-dni">DNI: ${escapeHtml(c.dni)}</div>
        </div>`).join('') || '<div style="color:var(--text-3);font-size:13px">Sin resultados</div>';
}

function renderSimClientList(filter = '') {
    const list = document.getElementById('simClientList');
    if (!list) return;
    const filtered = state.clients.filter(c =>
        c.fullName.toLowerCase().includes(filter.toLowerCase()) || c.dni.includes(filter));
    list.innerHTML = filtered.map(c => `
        <div class="client-list-item ${sameId(state.selectedSimClient?.id, c.id) ? 'selected' : ''}"
             onclick="selectSimClient(${safeId(c.id)})">
            <div class="item-name">${escapeHtml(c.fullName)}</div>
            <div class="item-dni">DNI: ${escapeHtml(c.dni)}</div>
        </div>`).join('') || '<div style="color:var(--text-3);font-size:13px">Sin resultados</div>';
}

// ── Client Modal ──────────────────────────────────
function openClientModal(clientId = null) {
    state.editClientId = clientId;
    document.getElementById('clientModalTitle').textContent = clientId ? 'Editar Cliente' : 'Nuevo Cliente';
    if (clientId) {
        const c = findClientById(clientId);
        if (c) {
            document.getElementById('fDni').value    = c.dni;
            document.getElementById('fName').value   = c.fullName;
            document.getElementById('fAge').value    = c.age;
            document.getElementById('fPhone').value  = c.phone || '';
            document.getElementById('fEmail').value  = c.email || '';
            document.getElementById('fAddress').value= c.address || '';
            document.getElementById('fIncome').value = c.monthlyIncome;
            document.getElementById('fSavings').value= c.totalSavings;
            document.getElementById('fDebts').value  = c.currentDebts || 0;
            document.getElementById('fDni').disabled = true;
        }
    } else {
        document.getElementById('clientForm').reset();
        document.getElementById('fDni').disabled = false;
    }
    document.getElementById('clientModal').classList.remove('hidden');
}

async function handleClientSave(e) {
    e.preventDefault();
    const data = {
        dni:          document.getElementById('fDni').value,
        fullName:     document.getElementById('fName').value,
        age:          parseInt(document.getElementById('fAge').value),
        phone:        document.getElementById('fPhone').value,
        email:        document.getElementById('fEmail').value,
        address:      document.getElementById('fAddress').value,
        monthlyIncome: parseFloat(document.getElementById('fIncome').value),
        totalSavings:  parseFloat(document.getElementById('fSavings').value),
        currentDebts:  parseFloat(document.getElementById('fDebts').value) || 0,
    };
    showLoading(true);
    try {
        if (state.editClientId) await api.updateClient(state.editClientId, data);
        else await api.createClient(data);
        closeModal('clientModal');
        await loadClients();
    } catch (err) { alert('Error: ' + err.message); }
    finally { showLoading(false); }
}

// ── Evaluations ───────────────────────────────────
async function selectEvalClient(clientId) {
    state.selectedEvalClient = findClientById(clientId);
    renderEvalClientList(document.getElementById('evalSearch').value);
    renderEvalPanel(state.selectedEvalClient, null);
}

function openEvalForClient(clientId) {
    navigateTo('evaluations').then(() => selectEvalClient(clientId));
}

function renderEvalPanel(client, evalResult) {
    const panel = document.getElementById('evalPanel');
    panel.innerHTML = `
        <div class="eval-header">
            <div>
                <div class="eval-client-name">${escapeHtml(client.fullName)}</div>
                <div class="eval-client-dni">DNI: ${escapeHtml(client.dni)} · Ingresos: S/ ${fmt(client.monthlyIncome)} · Ahorros: S/ ${fmt(client.totalSavings)}</div>
            </div>
        </div>
        ${evalResult ? renderEvalResult(evalResult) : `
        <div class="eval-placeholder">
            <div class="placeholder-icon">📊</div>
            <p>Haz clic en "Evaluar con Árbol" para clasificar este cliente</p>
        </div>`}
        <div class="eval-actions">
            <button class="btn-primary" onclick="runEvaluation(${safeId(client.id)})">Evaluar con Árbol</button>
            <button class="btn-secondary" onclick="openChatModal(${safeId(client.id)})">Explicar modelo</button>
            <button class="btn-secondary" onclick="loadPastEvals(${safeId(client.id)})">📋 Historial</button>
        </div>`;
}

function renderEvalResult(r) {
    const pct = Math.max(0, Math.min(100, Math.round((Number(r.traditionalScore) / 950) * 100) || 0));
    const breakdown = r.scoreBreakdown || {};
    const riskColor = { VERY_LOW: 'green', LOW: 'blue', MEDIUM: 'orange', HIGH: 'orange', VERY_HIGH: 'red' };

    return `
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
            <div class="score-display">
                <div class="score-big">${escapeHtml(r.traditionalScore)}</div>
                <div class="score-label">Score Tradicional · ${escapeHtml(r.scoreInterpretation)}</div>
                <div class="score-bar-wrap">
                    <div class="score-bar"><div class="score-bar-fill" style="width:${pct}%"></div></div>
                </div>
            </div>
            <div>
                <div class="ai-result-grid">
                    <div class="ai-result-item">
                        <div class="label">Perfil del árbol</div>
                        <div class="value">${escapeHtml(r.aiProfile || '—')}</div>
                    </div>
                    <div class="ai-result-item">
                        <div class="label">Riesgo</div>
                        <div class="value"><span class="badge badge-${riskColor[r.riskLevel] || 'gray'}">${escapeHtml(r.riskLevel || '—')}</span></div>
                    </div>
                    <div class="ai-result-item">
                        <div class="label">Línea sugerida</div>
                        <div class="value" style="color:var(--accent)">S/ ${fmt(r.suggestedCreditLine)}</div>
                    </div>
                </div>
            </div>
        </div>
        <div class="divider"></div>
        <h4 style="font-size:13px;color:var(--text-2);margin-bottom:10px">Desglose del Score</h4>
        <div class="score-breakdown">
            ${Object.entries(breakdown).map(([k, v]) => `
                <div class="breakdown-item">
                    <span class="bk-label">${escapeHtml(breakdownLabel(k))}</span>
                    <span class="bk-value ${v >= 0 ? 'positive' : 'negative'}">${v >= 0 ? '+' : ''}${escapeHtml(v)}</span>
                </div>`).join('')}
        </div>
        <div class="divider"></div>
        <h4 style="font-size:13px;color:var(--text-2);margin-bottom:10px">Justificación del Árbol</h4>
        <div class="ai-justification">${escapeHtml(r.aiJustification || 'Sin justificación disponible.')}</div>
        ${r.aiRecommendations ? `
        <h4 style="font-size:13px;color:var(--text-2);margin:10px 0">Recomendaciones</h4>
        <div class="ai-justification">${escapeHtml(r.aiRecommendations)}</div>` : ''}`;
}

async function runEvaluation(clientId) {
    showLoading(true);
    try {
        const res = await api.evaluate(clientId);
        state.currentEval = res.data;
        const client = findClientById(clientId);
        renderEvalPanel(client, res.data);
    } catch (err) { alert('Error evaluando: ' + err.message); }
    finally { showLoading(false); }
}

async function loadPastEvals(clientId) {
    showLoading(true);
    try {
        const res = await api.getEvaluations(clientId);
        const evals = res.data || [];
        alert(`Evaluaciones anteriores: ${evals.length}\n${evals.map(e =>
            `${e.evaluatedAt?.substring(0,10)} — Score: ${e.traditionalScore} — ${e.aiProfile}`).join('\n')}`);
    } catch { }
    finally { showLoading(false); }
}

// ── Simulator ─────────────────────────────────────
function selectSimClient(clientId) {
    state.selectedSimClient = findClientById(clientId);
    renderSimClientList(document.getElementById('simSearch').value);
    renderSimControls(state.selectedSimClient);
}

function renderSimControls(client) {
    const panel = document.getElementById('simControls');
    panel.innerHTML = `
        <h3 style="font-size:16px;font-weight:700;margin-bottom:20px">${escapeHtml(client.fullName)}</h3>
        <div class="sim-fields">
            <div class="sim-field-group">
                <label>Ingresos mensuales (S/)</label>
                <div class="sim-original">Actual: S/ ${fmt(client.monthlyIncome)}</div>
                <input type="number" id="simIncome" class="sim-input" value="${escapeAttr(client.monthlyIncome)}" min="0" step="100">
            </div>
            <div class="sim-field-group">
                <label>Ahorros totales (S/)</label>
                <div class="sim-original">Actual: S/ ${fmt(client.totalSavings)}</div>
                <input type="number" id="simSavings" class="sim-input" value="${escapeAttr(client.totalSavings)}" min="0" step="500">
            </div>
            <div class="sim-field-group">
                <label>Deudas actuales (S/)</label>
                <div class="sim-original">Actual: S/ ${fmt(client.currentDebts || 0)}</div>
                <input type="number" id="simDebts" class="sim-input" value="${escapeAttr(client.currentDebts || 0)}" min="0" step="100">
            </div>
        </div>
        <button class="btn-primary mt-12" onclick="runSimulation(${safeId(client.id)})" style="margin-top:20px">⚡ Simular Escenario</button>
        <div class="sim-result" id="simResult"></div>`;

    // Patch input styles
    document.querySelectorAll('.sim-input').forEach(inp => {
        Object.assign(inp.style, {
            width:'100%', background:'#0f1a27', border:'1px solid var(--border)',
            borderRadius:'8px', padding:'9px 12px', color:'var(--text-1)',
            fontSize:'14px', outline:'none', marginTop:'4px'
        });
    });
}

async function runSimulation(clientId) {
    const income  = parseFloat(document.getElementById('simIncome').value);
    const savings = parseFloat(document.getElementById('simSavings').value);
    const debts   = parseFloat(document.getElementById('simDebts').value);
    showLoading(true);
    try {
        const res = await api.simulate(clientId, {
            monthlyIncome: income, totalSavings: savings, currentDebts: debts
        });
        const r = res.data;
        const client = state.selectedSimClient;
        const original = state.currentEval?.traditionalScore || '—';
        const diff = state.currentEval ? (r.traditionalScore - state.currentEval.traditionalScore) : null;
        const simResultEl = document.getElementById('simResult');
        simResultEl.classList.add('visible');
        simResultEl.innerHTML = `
            <div class="sim-compare">
                <div class="sim-score-box">
                    <div class="sim-score-num original">${escapeHtml(original)}</div>
                    <div style="font-size:12px;color:var(--text-2)">Score actual</div>
                </div>
                <div class="sim-arrow">→</div>
                <div class="sim-score-box">
                    <div class="sim-score-num simulated">${escapeHtml(r.traditionalScore)}</div>
                    <div style="font-size:12px;color:var(--accent)">Score simulado</div>
                </div>
                ${diff !== null ? `<div class="sim-score-box">
                    <div style="font-size:28px;font-weight:900;color:${diff>=0?'var(--green)':'var(--red)'}">${diff>=0?'+':''}${escapeHtml(diff)}</div>
                    <div style="font-size:12px;color:var(--text-2)">Diferencia</div>
                </div>` : ''}
            </div>
            <div class="divider"></div>
            <div style="display:flex;gap:16px;justify-content:center;flex-wrap:wrap">
                <div class="ai-result-item" style="padding:12px 20px">
                    <div class="label">Perfil simulado</div>
                    <div class="value">${escapeHtml(r.aiProfile || '—')}</div>
                </div>
                <div class="ai-result-item" style="padding:12px 20px">
                    <div class="label">Riesgo simulado</div>
                    <div class="value">${escapeHtml(r.riskLevel || '—')}</div>
                </div>
                <div class="ai-result-item" style="padding:12px 20px">
                    <div class="label">Línea sugerida</div>
                    <div class="value" style="color:var(--accent)">S/ ${fmt(r.suggestedCreditLine)}</div>
                </div>
            </div>
            ${r.aiJustification ? `<div class="ai-justification" style="margin-top:12px">${escapeHtml(r.aiJustification)}</div>` : ''}`;
    } catch (err) { alert('Error simulando: ' + err.message); }
    finally { showLoading(false); }
}

// ── Chat Modal ────────────────────────────────────
function openChatModal(clientId, clientName) {
    state.chatClientId = clientId;
    clientName = clientName || findClientById(clientId)?.fullName || '';
    document.getElementById('chatClientName').textContent = clientName;
    document.getElementById('chatMessages').innerHTML = `
        <div class="chat-msg assistant">
            <div class="msg-bubble">Hola. Puedo explicar la clasificación del Árbol de Decisión para <strong>${escapeHtml(clientName)}</strong>, justificar el score o revisar las recomendaciones financieras.</div>
        </div>`;
    document.getElementById('chatModal').classList.remove('hidden');
    document.getElementById('chatInput').focus();
}

async function sendChat() {
    const input = document.getElementById('chatInput');
    const msg = input.value.trim();
    if (!msg) return;
    input.value = '';
    appendChatMsg('user', msg);
    const typingEl = appendChatMsg('assistant', '...');
    try {
        const res = await api.chat(state.chatClientId, msg);
        typingEl.querySelector('.msg-bubble').textContent = res.data.reply;
    } catch (err) {
        typingEl.querySelector('.msg-bubble').textContent = 'Error al consultar la explicación del modelo.';
    }
}

function appendChatMsg(role, text) {
    const msgs = document.getElementById('chatMessages');
    const div = document.createElement('div');
    div.className = `chat-msg ${role}`;
    const bubble = document.createElement('div');
    bubble.className = 'msg-bubble';
    bubble.textContent = text;
    div.appendChild(bubble);
    msgs.appendChild(div);
    msgs.scrollTop = msgs.scrollHeight;
    return div;
}

// ── Helpers ───────────────────────────────────────
function closeModal(id) { document.getElementById(id).classList.add('hidden'); }

function showLoading(show) {
    let overlay = document.getElementById('loadingOverlay');
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = 'loadingOverlay';
        overlay.className = 'loading-overlay';
        overlay.innerHTML = '<div class="spinner"></div>';
        document.body.appendChild(overlay);
    }
    overlay.classList.toggle('hidden', !show);
}

function fmt(n) {
    if (n == null) return '0.00';
    return parseFloat(n).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function pct(value) {
    if (value == null) return '—';
    return `${Math.round(value * 1000) / 10}%`;
}

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>'"]/g, char => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        "'": '&#39;',
        '"': '&quot;'
    }[char]));
}

function escapeAttr(value) {
    return escapeHtml(value).replace(/`/g, '&#96;');
}

function safeId(value) {
    const id = Number(value);
    return Number.isFinite(id) ? id : 0;
}

function sameId(a, b) {
    return Number(a) === Number(b);
}

function findClientById(clientId) {
    return state.clients.find(c => sameId(c.id, clientId));
}

function profileLabel(profile) {
    return { BASIC: 'Básico', INTERMEDIATE: 'Intermedio', ADVANCED: 'Avanzado' }[profile] || profile;
}

function statusBadge(s) {
    return { ACTIVE: 'badge-green', INACTIVE: 'badge-gray', BLACKLISTED: 'badge-red' }[s] || 'badge-gray';
}

function breakdownLabel(k) {
    return { income:'Ingresos', savings:'Ahorros', age:'Edad', debtRatio:'Relación deuda',
             paymentHistory:'Historial pagos', overdues:'Moras', financialProducts:'Productos' }[k] || k;
}

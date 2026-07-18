/* ═══════════════════════════════════════
   api.js — Capa de comunicación con el backend
   ═══════════════════════════════════════ */

const API_BASE = (window.CREDIT_AI_API_BASE || 'http://localhost:8080/api').replace(/\/$/, '');

const api = {
    _token: localStorage.getItem('credit_token'),

    setToken(t) {
        this._token = t;
        localStorage.setItem('credit_token', t);
    },

    clearToken() {
        this._token = null;
        localStorage.removeItem('credit_token');
        localStorage.removeItem('credit_user');
    },

    async _request(method, path, body = null) {
        const opts = {
            method,
            headers: {
                'Content-Type': 'application/json',
                ...(this._token ? { Authorization: `Bearer ${this._token}` } : {})
            }
        };
        if (body) opts.body = JSON.stringify(body);
        const res = await fetch(`${API_BASE}${path}`, opts);
        const json = await res.json();
        if (!res.ok) throw new Error(json.message || `HTTP ${res.status}`);
        return json;
    },

    get:    (path)        => api._request('GET',    path),
    post:   (path, body)  => api._request('POST',   path, body),
    put:    (path, body)  => api._request('PUT',    path, body),
    delete: (path)        => api._request('DELETE', path),

    // ── Auth ──────────────────────────────────────
    async login(username, password) {
        const res = await this.post('/auth/login', { username, password });
        this.setToken(res.data.token);
        localStorage.setItem('credit_user', JSON.stringify(res.data));
        return res.data;
    },

    // ── Clients ───────────────────────────────────
    getClients:    ()     => api.get('/clients'),
    getClient:     (id)   => api.get(`/clients/${id}`),
    createClient:  (data) => api.post('/clients', data),
    updateClient:  (id, data) => api.put(`/clients/${id}`, data),
    deleteClient:  (id)   => api.delete(`/clients/${id}`),
    getHistory:    (id)   => api.get(`/clients/${id}/history`),
    addHistory:    (id, data) => api.post(`/clients/${id}/history`, data),

    // ── Evaluations ───────────────────────────────
    evaluate:      (clientId)      => api.post(`/evaluations/client/${clientId}`, {}),
    simulate:      (clientId, sim) => api.post(`/evaluations/client/${clientId}/simulate`, sim),
    chat:          (clientId, msg) => api.post(`/evaluations/client/${clientId}/chat`, { message: msg }),
    getEvaluations:(clientId)      => api.get(`/evaluations/client/${clientId}`),

    // ── Dashboard ─────────────────────────────────
    getDashboard:  () => api.get('/dashboard/stats'),
};

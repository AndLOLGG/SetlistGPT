(function () {
    'use strict';

    const $ = (id) => document.getElementById(id);

    function getCookie(name) {
        const m = document.cookie.match('(?:^|; )' + name.replace(/([$?*|{}\]\\^])/g, '\\$1') + '=([^;]*)');
        return m ? decodeURIComponent(m[1]) : null;
    }

    async function fetchJson(url, opts = {}) {
        const headers = Object.assign({ Accept: 'application/json' }, opts.headers || {});
        let body = opts.body;
        if (body && typeof body === 'object' && !(body instanceof FormData)) {
            headers['Content-Type'] = headers['Content-Type'] || 'application/json; charset=UTF-8';
            body = JSON.stringify(body);
        }
        const xsrf = getCookie('XSRF-TOKEN');
        if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;
        const res = await fetch(url, { credentials: 'same-origin', ...opts, headers, body });
        const ct = res.headers.get('content-type') || '';
        const isJson = ct.includes('application/json');
        if (!res.ok) {
            const msg = isJson ? await res.json() : await res.text();
            throw new Error(`HTTP ${res.status}: ${isJson ? JSON.stringify(msg) : msg}`);
        }
        return isJson ? res.json() : res.text();
    }

    function ensureArea() {
        // Reuse existing containers on the frontpage
        const area = $('publicResults') || $('contentArea');
        if (!area) return null;
        // Force left align like frontpage.js does
        area.style.textAlign = 'left';
        return area;
    }

    function renderForm() {
        return `
            <h3>Create Profile</h3>
            <div class="form" style="text-align:left;display:flex;flex-wrap:wrap;gap:12px 16px;align-items:center;">
                <label>Username <input id="cpName" type="text" autocomplete="username" /></label>
                <label>Password <input id="cpPassword" type="password" autocomplete="new-password" /></label>
                <label>Type
                    <select id="cpType">
                        <option value="MUSICIAN" selected>Musician</option>
                        <option value="ADMIN">Admin</option>
                    </select>
                </label>
                <button id="cpSubmitBtn" type="button">Create</button>
                <span id="cpStatus" class="meta" aria-live="polite"></span>
            </div>
        `;
    }

    async function doRegister(name, password, type) {
        // Adjust to your backend endpoint. If not present, an error is shown.
        // Expected payload: { name, password, type }
        return fetchJson('/api/profile/register', {
            method: 'POST',
            body: { name, password, type }
        });
    }

    async function doLogin(name, password) {
        return fetchJson('/api/login', {
            method: 'POST',
            body: { name, password }
        });
    }

    function showCreateProfile() {
        const area = ensureArea();
        if (!area) {
            alert('Create profile UI not available.');
            return;
        }
        area.innerHTML = renderForm();

        const status = document.getElementById('cpStatus');
        const btn = document.getElementById('cpSubmitBtn');

        if (btn && !btn.dataset.attached) {
            btn.dataset.attached = 'true';
            btn.addEventListener('click', async () => {
                const name = (document.getElementById('cpName')?.value || '').trim();
                const password = (document.getElementById('cpPassword')?.value || '').trim();
                const type = (document.getElementById('cpType')?.value || 'MUSICIAN').trim();

                if (!name || !password) {
                    if (status) status.textContent = 'Username and password are required.';
                    return;
                }

                try {
                    if (status) status.textContent = 'Creating profile...';
                    const created = await doRegister(name, password, type);

                    if (status) status.textContent = 'Signing in...';
                    const authed = await doLogin(name, password);

                    // Mirror frontpage.js behavior
                    sessionStorage.setItem('authOk', 'true');
                    if (authed && authed.type === 'ADMIN') {
                        window.location.href = '/admin';
                        return;
                    }
                    if (status) status.textContent = 'Profile created. You are signed in.';
                    // Switch to auth view if frontpage helpers exist
                    if (window.SetlistGPT && typeof window.SetlistGPT.showAuthView === 'function') {
                        window.SetlistGPT.showAuthView();
                    }
                } catch (e) {
                    const msg = String(e.message || e);
                    if (/HTTP 404/i.test(msg)) {
                        if (status) status.textContent = 'Registration endpoint not available.';
                    } else {
                        if (status) status.textContent = `Failed: ${msg}`;
                    }
                }
            });
        }
    }

    // Expose API used by `frontpage.js`
    window.CreateProfile = Object.assign(window.CreateProfile || {}, {
        showCreateProfile
    });
})();
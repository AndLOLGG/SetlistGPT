// Defensive login guard to prevent native GET and force JSON POST to /api/login
(function () {
    'use strict';

    function getCookie(n){const m=document.cookie.match('(?:^|; )'+n.replace(/([$?*|{}\]\\^])/g,'\\$1')+'=([^;]*)');return m?decodeURIComponent(m[1]):null;}
    fetch('/api/login',{
        method:'POST',
        credentials:'include',
        headers:{
            'Content-Type':'application/json; charset=UTF-8',
            'X-XSRF-TOKEN': getCookie('XSRF-TOKEN') || ''
        },
        body: JSON.stringify({ name:'admin', password:'admin' })
    }).then(r=>r.json()).then(console.log).catch(console.error);

    function looksLikeCredentialForm(form) {
        // Accept if action is /api/login OR it has username+password inputs
        try {
            const act = form.getAttribute('action') || '';
            const u = new URL(act, location.href);
            if (u.pathname === '/api/login') return true;
        } catch { /* ignore */ }
        const hasUser = form.querySelector('#username, [name="username"], [name="name"], input[type="text"]');
        const hasPass = form.querySelector('#password, [name="password"], input[type="password"]');
        return !!(hasUser && hasPass);
    }

    async function postLogin(name, password) {
        const headers = { Accept: 'application/json', 'Content-Type': 'application/json; charset=UTF-8' };
        const xsrf = getCookie('XSRF-TOKEN');
        if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;

        const res = await fetch('/api/login', {
            method: 'POST',
            credentials: 'include',
            headers,
            body: JSON.stringify({ name, password })
        });
        const ct = res.headers.get('content-type') || '';
        const isJson = ct.includes('application/json');
        if (!res.ok) {
            const msg = isJson ? await res.json() : await res.text();
            throw new Error(`HTTP ${res.status}: ${isJson ? JSON.stringify(msg) : msg}`);
        }
        return isJson ? res.json() : res.text();
    }

    function readField(form, selectors) {
        for (const sel of selectors) {
            const el = form.querySelector(sel);
            if (el && 'value' in el) {
                const v = (el.value || '').trim();
                if (v) return v;
            }
        }
        return '';
    }

    async function handleLoginSubmit(form) {
        const status = form.querySelector('.meta, [aria-live]');
        const name = readField(form, ['#username', '[name="username"]', '[name="name"]', 'input[type="text"]']);
        const password = readField(form, ['#password', '[name="password"]', 'input[type="password"]']);

        if (!name || !password) {
            if (status) status.textContent = 'Enter username and password.';
            return;
        }
        try {
            if (status) status.textContent = 'Signing in...';
            const authed = await postLogin(name, password);
            sessionStorage.setItem('authOk', 'true');
            if (authed && authed.type === 'ADMIN') {
                location.href = '/admin';
                return;
            }
            if (status) status.textContent = '';
            if (window.SetlistGPT && typeof window.SetlistGPT.showAuthView === 'function') {
                window.SetlistGPT.showAuthView();
            }
        } catch (e) {
            if (status) status.textContent = 'Login failed.';
            console.error(e);
        }
    }

    function neutralizeForm(form) {
        form.setAttribute('action', '#');
        form.setAttribute('method', 'post');
    }

    function installLoginGuards() {
        // Neutralize any form that looks like a credential form
        document.querySelectorAll('form').forEach((form) => {
            if (!looksLikeCredentialForm(form)) return;

            neutralizeForm(form);

            // Force all submit-like buttons to be non-submitting
            form.querySelectorAll('input[type="submit"], button[type="submit"]').forEach((btn) => {
                btn.setAttribute('data-original-type', btn.getAttribute('type') || 'submit');
                btn.setAttribute('type', 'button');
            });

            // Also handle explicit login button, if present
            const loginBtn = form.querySelector('button#loginBtn, button[name="login"], input[name="login"]');
            if (loginBtn && !loginBtn.dataset.loginGuardAttached) {
                loginBtn.dataset.loginGuardAttached = 'true';
                loginBtn.addEventListener('click', (ev) => {
                    ev.preventDefault();
                    handleLoginSubmit(form);
                });
            }
        });

        // Global capturing submit handler (Enter key, etc.)
        document.addEventListener('submit', (ev) => {
            const form = ev.target;
            if (!(form instanceof HTMLFormElement)) return;
            if (!looksLikeCredentialForm(form)) return;
            ev.preventDefault();
            ev.stopImmediatePropagation();
            handleLoginSubmit(form);
        }, true);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', installLoginGuards);
    } else {
        installLoginGuards();
    }
})();
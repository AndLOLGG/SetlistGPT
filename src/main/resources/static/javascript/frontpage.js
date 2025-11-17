/**
 * frontpage.js
 * - DOM helpers
 * - CSRF‑aware fetch JSON helper
 * - Public/Auth view switching and session restore
 * - Login/Logout handlers
 * - Load repertoires and setlists
 * - Create repertoire/setlist flow (multi‑set support)
 * - Public navigation handlers
 */
(function () {
    'use strict';

    // ---------- DOM helpers ----------
    const $ = (id) => document.getElementById(id);
    const show = (el) => el && el.classList.remove('hidden');
    const hide = (el) => el && el.classList.add('hidden');
    const clear = (el) => el && (el.innerHTML = '');

    // ---------- Enums mirrored from backend ----------
    const GENRE_GROUPS = [
        'POP_GROUP','ROCK_GROUP','HARD_ROCK_GROUP','METAL_GROUP',
        'ELECTRONIC_GROUP','URBAN_GROUP','ACOUSTIC_GROUP','LATIN_WORLD_GROUP'
    ];
    const GENRES = [
        'POP','ROCK','ALTERNATIVE','INDIE','METAL','PUNK','HARD_ROCK','HEAVY_METAL',
        'HIP_HOP','RAP','RNB','SOUL','FUNK','BLUES','JAZZ','CLASSICAL','AMBIENT',
        'ELECTRONIC','DANCE','HOUSE','TRANCE','TECHNO','REGGAE','LATIN','FOLK',
        'COUNTRY','WORLD','DISCO'
    ];
    const MOODS = [
        'HAPPY','SAD','ENERGETIC','CALM','ANGRY','ROMANTIC','MELANCHOLIC','UPLIFTING',
        'DARK','CHILL','PARTY','DRIVING','MELLOW','INTENSE','DREAMY','NOSTALGIC','GROOVY'
    ];

    // ---------- Utils ----------
    function fmtDuration(sec) {
        const s = Math.max(0, sec | 0);
        const m = (s / 60) | 0;
        const r = s % 60;
        return m + ':' + String(r).padStart(2, '0');
    }
    function escapeHtml(s) {
        return String(s ?? '')
            .replace(/&/g,'&amp;')
            .replace(/</g,'&lt;')
            .replace(/>/g,'&gt;')
            .replace(/"/g,'&quot;')
            .replace(/'/g,'&#39;');
    }
    function getCookie(name) {
        const m = document.cookie.match('(?:^|; )' + name.replace(/([$?*|{}\]\\^])/g, '\\$1') + '=([^;]*)');
        return m ? decodeURIComponent(m[1]) : null;
    }
    async function fetchJson(url, opts = {}) {
        const headers = Object.assign({ Accept: 'application/json' }, opts.headers || {});
        let body = opts.body;
        if (body && typeof body === 'object' && !(body instanceof FormData)) {
            headers['Content-Type'] = 'application/json';
            body = JSON.stringify(body);
        }
        const xsrf = getCookie('XSRF-TOKEN');
        if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;
        const res = await fetch(url, { credentials: 'include', ...opts, headers, body });
        const ct = res.headers.get('content-type') || '';
        if (!res.ok) {
            let errText = ct.includes('application/json') ? JSON.stringify(await res.json()) : await res.text();
            throw new Error('HTTP ' + res.status + ' ' + res.statusText + ' - ' + errText);
        }
        return ct.includes('application/json') ? res.json() : res.text();
    }

    function enforceLeftAlignment() {
        const ca = $('contentArea');
        if (ca) ca.style.textAlign = 'left';
        const pr = $('publicResults');
        if (pr) pr.style.textAlign = 'left';
    }

    // ---------- Views ----------
    function showPublicHome() {
        show($('publicView'));
        hide($('authView'));
        show($('publicHomeActions'));
        hide($('publicBrowseActions'));
        hide($('loginArea'));
        clear($('publicResults'));
        const status = $('publicStatus');
        if (status) status.textContent = '';
        enforceLeftAlignment();
    }
    function showPublicBrowser() {
        show($('publicView'));
        hide($('authView'));
        hide($('publicHomeActions'));
        show($('publicBrowseActions'));
        hide($('loginArea'));
        clear($('publicResults'));
        const status = $('publicStatus');
        if (status) status.textContent = 'Browse public content.';
        enforceLeftAlignment();
    }
    function showAuthView() {
        hide($('publicView'));
        show($('authView'));
        hide($('loginArea'));
        const content = $('contentArea');
        if (content) content.innerHTML = '<p class="meta">Select an action.</p>';
        enforceLeftAlignment();
    }

    // ---------- Render helpers ----------
    function renderRepertoireList(list, { clickableIds = false } = {}) {
        if (!Array.isArray(list) || list.length === 0) return '<p>No repertoires.</p>';
        const rows = list.map(r => {
            const id = escapeHtml(r.id);
            const title = escapeHtml(r.title || '(untitled)');
            return `<li data-id="${id}" ${clickableIds ? 'class="clickable"' : ''}>${title}</li>`;
        });
        return `<ul>${rows.join('')}</ul>`;
    }
    function renderSetlistSummaries(list) {
        if (!Array.isArray(list) || list.length === 0) return '<p>No setlists found.</p>';
        const rows = list.map(s => {
            const id = escapeHtml(s.id);
            const title = escapeHtml(s.title || '(untitled)');
            const dur = fmtDuration(s.totalDurationSeconds || 0);
            const cnt = s.items ?? 0;
            const ts = escapeHtml(s.createdAt || '');
            return `<li><strong>${title}</strong> - ${cnt} songs - ${dur} <span class="meta">${ts}</span></li>`;
        });
        return `<ul>${rows.join('')}</ul>`;
    }
    function renderSongListNumbered(songs) {
        if (!Array.isArray(songs) || songs.length === 0) return '<p>No songs.</p>';
        const items = songs.map((s,i) => {
            const title = escapeHtml(s.title || s.artist || '(untitled)');
            const artist = escapeHtml(s.artist || '');
            const dur = fmtDuration(s.durationInSeconds || (s.durationMinutes*60 + s.durationSeconds) || 0);
            return `<li>${i+1}. ${title}${artist? ' - ' + artist : ''} <span class="meta">${dur}</span></li>`;
        });
        return `<ol>${items.join('')}</ol>`;
    }

    // ---------- Public handlers ----------
    function attachPublicControls() {
        const showLoginBtn = $('showLoginBtn');
        const seePublicBtn = $('seePublicBtn');
        const backBtn = $('backToPublicBtn');
        const loginArea = $('loginArea');
        const loginBtn = $('loginBtn');
        const createProfileBtn = $('createProfileBtn');
        const publicRepsBtn = $('publicRepertoiresBtn');
        const publicSetlistsBtn = $('publicSetlistsBtn');
        const results = $('publicResults');
        const status = $('publicStatus');

        // Single canonical doLogin (form-encoded, sends XSRF header if cookie present)
        const doLogin = async () => {
            if (!status) return;
            const uEl = $('username');
            const pEl = $('password');
            const u = uEl ? uEl.value.trim() : '';
            const p = pEl ? pEl.value : '';
            if (!u || !p) {
                status.textContent = 'Enter username and password.';
                return;
            }
            status.textContent = 'Logging in...';
            if (loginBtn) loginBtn.disabled = true;

            try {
                const form = new URLSearchParams();
                form.append('username', u);
                form.append('password', p);

                // NOTE: remove `redirect: 'manual'` to avoid some browsers returning opaque status 0 for redirects.
                const res = await fetch('/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: form.toString(),
                    credentials: 'include'
                });

                console.debug('/login fetch ->', { status: res.status, statusText: res.statusText, url: res.url, redirected: res.redirected, type: res.type });
                try { console.debug('Resp headers:', Array.from(res.headers.entries())); } catch (e) {}

                // If browser returned an opaque/zero result, treat as failure and fall back.
                if (!res || res.status === 0 || (res.type === 'opaque' && !res.ok)) {
                    throw new Error('Opaque / status 0 response from fetch');
                }

                const location = (res.headers.get('location') || '').toLowerCase();
                const redirectedToError = (location && location.includes('error')) || (res.url && res.url.toLowerCase().includes('error'));
                const success = (res.ok || res.status === 302 || res.status === 303 || res.status === 204 || res.redirected) && !redirectedToError;

                if (success) {
                    sessionStorage.setItem('authOk','1');
                    status.textContent = '';
                    hide(loginArea);
                    showAuthView();
                    try { attachAuthControls(); } catch (e) {}
                    return;
                }

                // Try to build helpful message
                let msg = `Login failed (status ${res.status}).`;
                try {
                    const ct = res.headers.get('content-type') || '';
                    if (ct.includes('application/json')) {
                        const j = await res.clone().json().catch(()=>null);
                        if (j && (j.error || j.message)) msg = 'Login failed: ' + (j.error || j.message);
                    } else {
                        const t = await res.clone().text().catch(()=>null);
                        if (t) {
                            const stripped = t.replace(/<[^>]*>/g,'').trim();
                            if (stripped && stripped.length < 200) msg = 'Login failed: ' + stripped;
                        }
                    }
                } catch (e) { /* ignore parse errors */ }

                status.textContent = msg;
            } catch (e) {
                // Detailed debug info for network/opaque errors
                console.error('doLogin fetch failed:', e);
                // Fallback: submit a real form (ensures browser handles redirects/cookies)
                try {
                    const realForm = document.createElement('form');
                    realForm.method = 'post';
                    realForm.action = '/login';
                    // ensure credentials are posted with expected parameter names
                    const inU = document.createElement('input');
                    inU.type = 'hidden';
                    inU.name = 'username';
                    inU.value = u;
                    const inP = document.createElement('input');
                    inP.type = 'hidden';
                    inP.name = 'password';
                    inP.value = p;
                    realForm.appendChild(inU);
                    realForm.appendChild(inP);

                    // If a server-side CSRF hidden input exists on the page (Thymeleaf), clone it into the fallback form.
                    const existingCsrf = document.querySelector('input[type="hidden"][name*="csrf"], input[type="hidden"][name*="_csrf"]');
                    if (existingCsrf && existingCsrf.name && existingCsrf.value) {
                        const cs = document.createElement('input');
                        cs.type = 'hidden';
                        cs.name = existingCsrf.name;
                        cs.value = existingCsrf.value;
                        realForm.appendChild(cs);
                    }

                    // append, submit and remove
                    document.body.appendChild(realForm);
                    realForm.submit();
                    // do not reach the cleanup too soon; browser will navigate
                } catch (formErr) {
                    console.error('Fallback form submit failed:', formErr);
                    status.textContent = 'Login failed.';
                }
            } finally {
                if (loginBtn) loginBtn.disabled = false;
            }
        };

        if (showLoginBtn && !showLoginBtn.dataset.attached) {
            showLoginBtn.addEventListener('click', () => {
                show(loginArea);
                if (status) status.textContent = '';
            });
            showLoginBtn.dataset.attached = '1';
        }
        if (loginBtn) loginBtn.setAttribute('type','button');
        if (loginBtn && !loginBtn.dataset.attached) {
            loginBtn.addEventListener('click', doLogin);
            loginBtn.dataset.attached='1';
        }
        const loginForm = $('loginForm');
        if (loginForm && !loginForm.dataset.attached) {
            loginForm.addEventListener('submit', e => { e.preventDefault(); doLogin(); });
            loginForm.dataset.attached='1';
        }
        if (createProfileBtn && !createProfileBtn.dataset.attached) {
            createProfileBtn.addEventListener('click', () => {
                window.location.href='/profile';
            });
            createProfileBtn.dataset.attached='1';
        }
        if (seePublicBtn && !seePublicBtn.dataset.attached) {
            seePublicBtn.addEventListener('click', () => {
                showPublicBrowser();
            });
            seePublicBtn.dataset.attached='1';
        }
        if (backBtn && !backBtn.dataset.attached) {
            backBtn.addEventListener('click', () => {
                showPublicHome();
            });
            backBtn.dataset.attached='1';
        }
        if (publicRepsBtn && !publicRepsBtn.dataset.attached) {
            publicRepsBtn.addEventListener('click', async () => {
                results.innerHTML = '<p>Loading public repertoires...</p>';
                if (status) status.textContent = '';
                enforceLeftAlignment();
                try {
                    const data = await fetchJson('/api/repertoires'); // assumes endpoint exists
                    results.innerHTML = '<h3>Public Repertoires</h3>' + renderRepertoireList(data);
                } catch (e) {
                    results.innerHTML = '<p>Error loading repertoires.</p>';
                }
            });
            publicRepsBtn.dataset.attached='1';
        }
        if (publicSetlistsBtn && !publicSetlistsBtn.dataset.attached) {
            publicSetlistsBtn.addEventListener('click', async () => {
                results.innerHTML = '<p>Loading public setlists...</p>';
                if (status) status.textContent = '';
                enforceLeftAlignment();
                try {
                    const data = await fetchJson('/api/setlists');
                    results.innerHTML = '<h3>Public Setlists</h3>' + renderSetlistSummaries(data);
                } catch (e) {
                    results.innerHTML = '<p>Error loading setlists.</p>';
                }
            });
            publicSetlistsBtn.dataset.attached='1';
        }
    }

    async function showPublicRepertoireDetails(id) {
        const results = $('publicResults');
        const status = $('publicStatus');
        if (!results) return;
        try {
            if (status) status.textContent = 'Loading repertoire...';
            const rep = await fetchJson('/api/repertoires/' + encodeURIComponent(id));
            if (status) status.textContent = '';
            results.innerHTML = '<h3>Repertoire</h3>' + renderSongListNumbered(rep.songs || []);
        } catch (e) {
            if (status) status.textContent = 'Error.';
            results.innerHTML = '<p>Failed to load repertoire.</p>';
        }
    }

    // ---------- Auth handlers ----------
    function attachAuthControls() {
        // grab the auth view controls (may be undefined if not present)
        const newSetlistBtn = $('newSetlistBtn');
        const setlistsBtn = $('setlistsBtn');
        const newRepertoireBtn = $('newRepertoireBtn');
        const myRepertoiresBtn = $('myRepertoiresBtn');
        const logoutBtn = $('logoutBtn');

        if (newSetlistBtn && !newSetlistBtn.dataset.attached) {
            newSetlistBtn.addEventListener('click', createSetlistFlow);
            newSetlistBtn.dataset.attached='1';
        }
        if (setlistsBtn && !setlistsBtn.dataset.attached) {
            setlistsBtn.addEventListener('click', loadMySetlists);
            setlistsBtn.dataset.attached='1';
        }
        if (newRepertoireBtn && !newRepertoireBtn.dataset.attached) {
            newRepertoireBtn.addEventListener('click', createRepertoireFlow);
            newRepertoireBtn.dataset.attached='1';
        }
        if (myRepertoiresBtn && !myRepertoiresBtn.dataset.attached) {
            myRepertoiresBtn.addEventListener('click', loadMyRepertoires);
            myRepertoiresBtn.dataset.attached='1';
        }
        if (logoutBtn && !logoutBtn.dataset.attached) {
            logoutBtn.addEventListener('click', async () => {
                try { await fetchJson('/logout',{ method:'POST' }); } catch(e){}
                sessionStorage.removeItem('authOk');
                showPublicHome();
            });
            logoutBtn.dataset.attached='1';
        }
    }

    // ---------- Auth: data loaders ----------
    async function loadMySetlists() {
        const area = $('contentArea');
        if (!area) return;
        area.textContent = 'Loading setlists...';
        enforceLeftAlignment();
        try {
            const list = await fetchJson('/api/setlists');
            area.innerHTML = '<h3>My Setlists</h3>' + renderSetlistSummaries(list);
        } catch (e) {
            area.innerHTML = '<p>Error loading setlists.</p>';
        }
    }
    async function loadMyRepertoires() {
        const area = $('contentArea');
        if (!area) return;
        area.textContent = 'Loading repertoires...';
        enforceLeftAlignment();
        try {
            const reps = await fetchJson('/api/repertoires');
            area.innerHTML = '<h3>My Repertoires</h3>' + renderRepertoireList(reps, { clickableIds:true });
            // click handler
            area.querySelectorAll('li.clickable').forEach(li => {
                li.addEventListener('click', () => showRepertoireDetails(li.dataset.id));
            });
        } catch (e) {
            area.innerHTML = '<p>Error loading repertoires.</p>';
        }
    }
    async function showRepertoireDetails(id) {
        const area = $('contentArea');
        if (!area) return;
        area.textContent = 'Loading repertoire...';
        enforceLeftAlignment();
        try {
            const rep = await fetchJson('/api/repertoires/' + encodeURIComponent(id));
            area.innerHTML = `<h3>Repertoire: ${escapeHtml(rep.title || '(untitled)')}</h3>` + renderSongListNumbered(rep.songs || []);
        } catch (e) {
            area.innerHTML = '<p>Error loading repertoire.</p>';
        }
    }

    // ---------- Auth: create flows ----------
    function createRepertoireFlow() {
        const area = $('contentArea');
        if (!area) return;
        area.innerHTML = `
            <h3>New Repertoire</h3>
            <p>Not implemented in this snippet.</p>
        `;
        enforceLeftAlignment();
    }

    function createSetlistFlow() {
        const area = $('contentArea');
        if (!area) return;

        const opt = (v, lbl = v) => `<option value="${escapeHtml(v)}">${escapeHtml(lbl)}</option>`;
        const pretty = (s) => String(s).replace(/_/g, ' ').replace(/ GROUP$/i, ' Group');

        const genreSelectHtml = [
            '<option value="">(none)</option>',
            ...GENRE_GROUPS.map(g => opt(g, pretty(g))),
            ...GENRES.map(g => opt(g, pretty(g)))
        ].join('');
        const moodSelectHtml = [
            '<option value="">(none)</option>',
            ...MOODS.map(m => opt(m, pretty(m)))
        ].join('');

        const durationRowsHtml = [1, 2, 3].map(n => `
        <div class="durRow" id="slDurRow${n}" style="display:none;gap:12px;align-items:center;flex-wrap:wrap;">
            <label style="min-width:90px;">Set ${n} Duration</label>
            <label>Minutes
                <input id="slMin${n}" type="number" min="0" max="59" value="${n === 1 ? 10 : 0}" style="width:70px;">
            </label>
            <label>Seconds
                <input id="slSec${n}" type="number" min="0" max="59" value="0" style="width:70px;">
            </label>
        </div>
    `).join('');

        area.innerHTML = `
        <h3>New Setlist</h3>
        <div class="form" style="text-align:left;display:flex;flex-direction:column;gap:14px;max-width:1000px;">

            <div class="row" style="display:flex;align-items:center;gap:24px;flex-wrap:wrap;">
                <label>Sets
                    <select id="slSets">
                        <option value="1" selected>1</option>
                        <option value="2">2</option>
                        <option value="3">3</option>
                    </select>
                </label>
                <span class="meta">Choose how many separate sets to build.</span>
            </div>

            <div class="row" style="display:flex;align-items:center;gap:24px;flex-wrap:wrap;">
                <label>Title
                    <input id="slTitle" type="text" placeholder="Title or Artist required">
                </label>
                <label>Artist
                    <input id="slArtist" type="text" placeholder="Title or Artist required">
                </label>
                <label>Genre / Group
                    <select id="slGenre">${genreSelectHtml}</select>
                </label>
            </div>

            <div class="row" style="display:flex;align-items:center;gap:24px;flex-wrap:wrap;">
                <label>Mood
                    <select id="slMood">${moodSelectHtml}</select>
                </label>
            </div>

            <div id="slDurationRows" style="display:flex;flex-direction:column;gap:8px;">
                ${durationRowsHtml}
            </div>

            <div class="row" style="display:flex;align-items:center;gap:24px;flex-wrap:wrap;">
                <label><input id="slReuse" type="checkbox"> Allow Reuse</label>
                <button id="slBuildBtn" type="button">Build &amp; Save</button>
                <span id="slStatus" class="meta" aria-live="polite"></span>
            </div>
        </div>
        <div id="slResult" style="margin-top:16px"></div>
    `;
        enforceLeftAlignment();

        const setsSel = $('slSets');
        function updateSetRowsVisibility() {
            const sets = parseInt(setsSel.value, 10) || 1;
            for (let i = 1; i <= 3; i++) {
                const row = $('slDurRow' + i);
                if (row) row.style.display = i <= sets ? 'flex' : 'none';
            }
        }
        setsSel.addEventListener('change', updateSetRowsVisibility);
        updateSetRowsVisibility();

        const buildBtn = $('slBuildBtn');
        const status = $('slStatus');
        const result = $('slResult');

        if (buildBtn && !buildBtn.dataset.attached) {
            buildBtn.addEventListener('click', async () => {
                status.textContent = 'Building...';
                result.innerHTML = '';
                const baseTitle = $('slTitle').value.trim();
                const artist = $('slArtist').value.trim();
                const genre = $('slGenre').value.trim();
                const mood = $('slMood').value.trim();
                const reuse = $('slReuse').checked;
                const sets = parseInt(setsSel.value, 10) || 1;

                if (!baseTitle && !artist) {
                    status.textContent = 'Enter title or artist.';
                    return;
                }

                const durations = [];
                for (let i = 1; i <= sets; i++) {
                    const m = parseInt(($('slMin' + i).value || '0'), 10);
                    const s = parseInt(($('slSec' + i).value || '0'), 10);
                    durations.push({
                        minutes: isNaN(m) ? 0 : Math.min(Math.max(m, 0), 59),
                        seconds: isNaN(s) ? 0 : Math.min(Math.max(s, 0), 59)
                    });
                }

                try {
                    const allSets = [];
                    for (let i = 0; i < sets; i++) {
                        const { minutes, seconds } = durations[i];
                        const title = baseTitle
                            ? `${baseTitle} - Set ${i + 1}`
                            : `Set ${i + 1}`;
                        const payload = {
                            title,
                            artist,
                            genre,
                            bpm: null,
                            mood,
                            durationMinutes: minutes,
                            durationSeconds: seconds,
                            allowReuse: reuse
                        };
                        const songs = await fetchJson('/api/setlist', {
                            method: 'POST',
                            body: payload
                        });
                        if (Array.isArray(songs)) {
                            allSets.push({ title, songs });
                        }
                    }

                    const blocks = allSets.map(set => {
                        const durSec = set.songs.reduce((t, s) =>
                            t + (s.durationMinutes * 60 + s.durationSeconds), 0);
                        return `
                        <div class="setBlock" style="margin-bottom:24px;">
                            <h4>${escapeHtml(set.title)} (${fmtDuration(durSec)})</h4>
                            ${renderSongListNumbered(set.songs)}
                        </div>
                    `;
                    }).join('');

                    const totalSeconds = allSets.reduce((t, set) =>
                        t + set.songs.reduce((tt, s) => tt + (s.durationMinutes * 60 + s.durationSeconds), 0), 0);

                    result.innerHTML = (blocks || '<p>No songs generated.</p>') +
                        (allSets.length > 1
                            ? `<p><strong>Total Combined Duration:</strong> ${fmtDuration(totalSeconds)}</p>`
                            : '');
                    status.textContent = 'Done.';
                } catch (e) {
                    console.error(e);
                    status.textContent = 'Error building setlist.';
                }
            });
            buildBtn.dataset.attached = '1';
        }
    }

    // ---------- Init ----------
    function init() {
        attachPublicControls();
        attachAuthControls();
        if (sessionStorage.getItem('authOk')) {
            showAuthView();
        } else {
            showPublicHome();
        }
        enforceLeftAlignment();
    }

    document.addEventListener('DOMContentLoaded', init);
})();

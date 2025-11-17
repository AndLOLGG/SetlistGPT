// javascript
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
console.log('frontpage.js loaded');
(function () {
    'use strict';

    // ---------- DOM helpers ----------
    const $ = (id) => document.getElementById(id);
    const show = (el) => el && el.classList.remove('hidden');
    const hide = (el) => el && el.classList.add('hidden');
    const clear = (el) => el && (el.innerHTML = '');

    // Admin visibility helper
    function updateAdminVisibility() {
        const btn = $('adminBtn'); // element id in your HTML for the Admin button
        if (!btn) return;
        try {
            const prof = JSON.parse(sessionStorage.getItem('profile') || 'null');
            if (prof && prof.type === 'ADMIN') {
                show(btn);
            } else {
                hide(btn);
            }
        } catch (e) {
            hide(btn);
        }
    }

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
        updateAdminVisibility();
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
        updateAdminVisibility();
    }
    function showAuthView() {
        hide($('publicView'));
        show($('authView'));
        hide($('loginArea'));
        const content = $('contentArea');
        if (content) content.innerHTML = '<p class="meta">Select an action.</p>';
        enforceLeftAlignment();
        updateAdminVisibility();
    }

    // ---------- Render helpers ----------
    function renderRepertoireList(list, { clickableIds = false } = {}) {
        if (!Array.isArray(list) || list.length === 0) return '<p>No repertoires.</p>';
        const rows = list.map(r => {
            const id = escapeHtml(r.id);
            const title = escapeHtml(r.title || '(untitled)');
            // When clickableIds is true, make the <li> focusable and expose role="button" for screen readers.
            return `<li data-id="${id}" ${clickableIds ? 'class="clickable" tabindex="0" role="button"' : ''}>${title}</li>`;
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
        const items = songs.map((s, i) => {
            const title = escapeHtml(s.title || s.artist || '(untitled)');
            const artist = escapeHtml(s.artist || '');
            const dur = fmtDuration(s.durationInSeconds || (s.durationMinutes*60 + s.durationSeconds) || 0);

            // build metadata (genre, bpm, mood)
            const metaParts = [];
            if (s.genre) metaParts.push(escapeHtml(s.genre));
            if (s.bpm != null && s.bpm !== '') metaParts.push(escapeHtml(String(s.bpm)) + ' bpm');
            if (s.mood) metaParts.push(escapeHtml(s.mood));
            const metaText = metaParts.length ? ` <span class="meta">(${metaParts.join(', ')})</span>` : '';

            // rely on <ol> numbering - don't prefix with i+1
            return `<li>${title}${artist ? ' - ' + artist : ''}${metaText} <span class="meta">${dur}</span></li>`;
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
            // debug: ensure handler actually runs (helps track down native form submits)
            console.log('doLogin invoked', { username: (document.getElementById('username')||{}).value });

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
                // Use the collected `u` and `p` (was incorrectly using `username`/`password`)
                const profile = await fetchJson('/api/login', {
                    method: 'POST',
                    body: { name: u, password: p }
                });

                // debug: show server-provided profile/xsrf token
                console.log('login response', profile);

                // Ensure browser receives Set-Cookie for XSRF-TOKEN before protected requests
                try {
                    const csrfResp = await fetchJson('/api/csrf'); // fetchJson uses credentials:'include'
                    if (!getCookie('XSRF-TOKEN') && csrfResp && csrfResp.token) {
                        document.cookie = 'XSRF-TOKEN=' + encodeURIComponent(csrfResp.token) + '; path=/';
                    }
                } catch (e) {
                    if (profile && profile.xsrfToken) {
                        document.cookie = 'XSRF-TOKEN=' + encodeURIComponent(profile.xsrfToken) + '; path=/';
                    }
                }

                sessionStorage.setItem('authOk', '1');
                try { sessionStorage.setItem('profile', JSON.stringify(profile)); } catch (e) {}
                updateAdminVisibility();
                status.textContent = '';
                hide(loginArea);
                showAuthView();
                try { attachAuthControls(); } catch (e) {}
                return;
            } catch (e) {
                console.error('Login error:', e);
                const msg = String(e && e.message ? e.message : 'Login failed');
                if (/401|Unauthorized/i.test(msg)) {
                    status.textContent = 'Invalid username or password.';
                } else if (/Missing credentials|400/i.test(msg)) {
                    status.textContent = 'Missing credentials.';
                } else {
                    status.textContent = msg.length > 200 ? msg.slice(0, 200) + '…' : msg;
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
        if (loginForm) {
            try { loginForm.action = '#'; } catch (e) {}
            if (!loginForm.dataset.attached) {
                // non-capturing handler triggers the AJAX doLogin flow; native submits are prevented by the global capture guard.
                loginForm.addEventListener('submit', e => { e.preventDefault(); doLogin(); });
                loginForm.dataset.attached='1';
            }
        }
        if (createProfileBtn && !createProfileBtn.dataset.attached) {
            createProfileBtn.addEventListener('click', () => {
                // Prefer SPA inline renderer when available so Create Profile works without a full navigation.
                try {
                    if (window.CreateProfile && typeof window.CreateProfile.showCreateProfile === 'function') {
                        window.CreateProfile.showCreateProfile();
                        return;
                    }
                } catch (e) {
                    // swallow and fall back to navigation
                }
                // Fallback: navigate to /profile (server serves the SPA)
                window.location.href = '/profile';
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
                    // explicitly call the public endpoint and make items clickable
                    const data = await fetchJson('/api/repertoires/public');
                    results.innerHTML = '<h3>Public Repertoires</h3>' + renderRepertoireList(data, { clickableIds: true });

                    // attach click handlers to show repertoire details (public)
                    results.querySelectorAll('li.clickable').forEach(li => {
                        li.addEventListener('click', () => showPublicRepertoireDetails(li.dataset.id));
                    });
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
                sessionStorage.removeItem('profile');
                updateAdminVisibility();
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

        const genreOptions = ['<option value="">(none)</option>', ...GENRES.map(g => `<option value="${escapeHtml(g)}">${escapeHtml(g)}</option>`)].join('');
        const moodOptions = ['<option value="">(none)</option>', ...MOODS.map(m => `<option value="${escapeHtml(m)}">${escapeHtml(m)}</option>`)].join('');

        area.innerHTML = `
        <h3>New Repertoire</h3>
        <div class="form" style="text-align:left;display:flex;flex-direction:column;gap:12px;max-width:1100px;">
            <div class="row" style="display:flex;gap:12px;align-items:center;flex-wrap:wrap;">
                <label style="min-width:120px;">Name
                    <input id="repName" type="text" placeholder="Repertoire name">
                </label>
                <label style="display:flex;align-items:center;gap:8px;">
                    <input id="repPublic" type="checkbox"> Public (viewable offline)
                </label>
                <label style="min-width:220px;">
                    Number of rows
                    <input id="repRows" type="number" min="0" value="3" style="width:80px;margin-left:6px;">
                </label>
                <button id="repMakeRowsBtn" type="button">Generate rows</button>
                <button id="repAddRowBtn" type="button">Add song</button>
                <button id="repClearBtn" type="button">Clear rows</button>
            </div>

            <div id="repRowsContainer" style="display:flex;flex-direction:column;gap:10px;margin-top:8px;"></div>

            <div style="display:flex;gap:12px;align-items:center;margin-top:8px;">
                <button id="repCreateBtn" type="button">Create Repertoire</button>
                <span id="repStatus" class="meta" aria-live="polite"></span>
            </div>
        </div>
    `;
        enforceLeftAlignment();

        const container = $('repRowsContainer');
        const rowsInput = $('repRows');
        const makeRowsBtn = $('repMakeRowsBtn');
        const addRowBtn = $('repAddRowBtn');
        const clearBtn = $('repClearBtn');
        const createBtn = $('repCreateBtn');
        const status = $('repStatus');

        // helper: create a single song row DOM and append
        let rowCounter = 0;
        function appendSongRow(defaults = {}) {
            rowCounter += 1;
            const id = 'repSongRow' + rowCounter;
            const title = escapeHtml(defaults.title || '');
            const artist = escapeHtml(defaults.artist || '');
            const bpm = defaults.bpm != null ? String(defaults.bpm) : '';
            const minutes = (typeof defaults.durationMinutes === 'number') ? defaults.durationMinutes : 0;
            const seconds = (typeof defaults.durationSeconds === 'number') ? defaults.durationSeconds : 30;

            const html = `
            <div id="${id}" class="repSongRow" style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;border:1px solid #ddd;padding:8px;">
                <label style="min-width:160px;">Title
                    <input class="repTitle" type="text" value="${title}" placeholder="Title">
                </label>
                <label style="min-width:160px;">Artist
                    <input class="repArtist" type="text" value="${artist}" placeholder="Artist">
                </label>
                <label style="min-width:140px;">Genre
                    <select class="repGenre">${GENRES.map(g => `<option value="${escapeHtml(g)}">${escapeHtml(g)}</option>`).join('')}</select>
                </label>
                <label style="min-width:100px;">BPM
                    <input class="repBpm" type="number" min="1" max="300" value="${escapeHtml(bpm)}" style="width:80px;">
                </label>
                <label style="min-width:140px;">Mood
                    <select class="repMood">${MOODS.map(m => `<option value="${escapeHtml(m)}">${escapeHtml(m)}</option>`).join('')}</select>
                </label>
                <label style="min-width:120px;display:flex;gap:6px;align-items:center;">Duration
                    <input class="repMin" type="number" min="0" max="59" value="${minutes}" style="width:60px;"> :
                    <input class="repSec" type="number" min="0" max="59" value="${seconds}" style="width:60px;">
                </label>
                <button type="button" class="repRemoveRowBtn">Remove</button>
            </div>
        `;
            const tpl = document.createElement('div');
            tpl.innerHTML = html;
            const rowEl = tpl.firstElementChild;
            // apply defaults for selects/inputs after insertion if provided
            if (defaults.genre) rowEl.querySelector('.repGenre').value = defaults.genre;
            if (defaults.mood) rowEl.querySelector('.repMood').value = defaults.mood;
            if (defaults.bpm != null) rowEl.querySelector('.repBpm').value = defaults.bpm;
            container.appendChild(rowEl);

            const remBtn = rowEl.querySelector('.repRemoveRowBtn');
            remBtn.addEventListener('click', () => rowEl.remove());
            return rowEl;
        }

        function clearRows() {
            container.innerHTML = '';
        }

        // return true when row is completely blank (no meaningful data)
        function isRowCompletelyBlank(rowEl) {
            const title = (rowEl.querySelector('.repTitle').value || '').trim();
            const artist = (rowEl.querySelector('.repArtist').value || '').trim();
            const genre = (rowEl.querySelector('.repGenre').value || '').trim();
            const mood = (rowEl.querySelector('.repMood').value || '').trim();
            const bpm = (rowEl.querySelector('.repBpm').value || '').trim();
            const min = parseInt(rowEl.querySelector('.repMin').value || '0', 10) || 0;
            const sec = parseInt(rowEl.querySelector('.repSec').value || '0', 10) || 0;
            const total = min * 60 + sec;
            return !title && !artist && !genre && !mood && !bpm && total === 0;
        }

        // Adjust rows to target count without wiping existing input values.
        // - If increasing: append rows.
        // - If decreasing: only remove trailing completely blank rows; refuse if data would be lost.
        function adjustRows(targetCount) {
            const existingRows = Array.from(container.querySelectorAll('.repSongRow'));
            const current = existingRows.length;
            if (targetCount === current) return;
            if (targetCount > current) {
                const toAdd = targetCount - current;
                for (let i = 0; i < toAdd; i++) appendSongRow();
                return;
            }
            // targetCount < current: attempt to remove trailing blank rows
            let neededRemovals = current - targetCount;
            for (let i = existingRows.length - 1; i >= 0 && neededRemovals > 0; i--) {
                const r = existingRows[i];
                if (isRowCompletelyBlank(r)) {
                    r.remove();
                    neededRemovals--;
                } else {
                    // can't remove non-blank trailing row
                    break;
                }
            }
            if (neededRemovals > 0) {
                status.textContent = 'Cannot reduce rows: some trailing rows contain data. Remove them manually first.';
                // update rowsInput to reflect actual count
                rowsInput.value = container.querySelectorAll('.repSongRow').length;
            }
        }

        makeRowsBtn.addEventListener('click', () => {
            const n = Math.max(0, parseInt(rowsInput.value, 10) || 0);
            adjustRows(n);
        });
        addRowBtn.addEventListener('click', () => appendSongRow());
        clearBtn.addEventListener('click', () => clearRows());

        // initialize with a few rows without wiping if user reopens this view repeatedly
        if (container.querySelectorAll('.repSongRow').length === 0) {
            adjustRows(Math.max(1, Math.min(50, parseInt(rowsInput.value, 10) || 3)));
        }

        createBtn.addEventListener('click', async () => {
            status.textContent = '';
            const nameEl = $('repName');
            const name = nameEl ? nameEl.value.trim() : '';
            if (!name) {
                status.textContent = 'Enter a name for the repertoire.';
                return;
            }
            const isPublic = $('repPublic').checked;

            // collect song rows
            const songEls = Array.from(container.querySelectorAll('.repSongRow'));
            const songs = [];
            for (let i = 0; i < songEls.length; i++) {
                const row = songEls[i];
                const title = (row.querySelector('.repTitle').value || '').trim();
                const artist = (row.querySelector('.repArtist').value || '').trim();
                const genre = (row.querySelector('.repGenre').value || '') || null;
                const mood = (row.querySelector('.repMood').value || '') || null;
                const bpmRaw = row.querySelector('.repBpm').value;
                const bpm = bpmRaw === '' ? null : parseInt(bpmRaw, 10);
                const minutes = parseInt(row.querySelector('.repMin').value || '0', 10) || 0;
                const seconds = parseInt(row.querySelector('.repSec').value || '0', 10) || 0;
                const totalSec = minutes * 60 + seconds;

                // skip completely blank rows
                const completelyBlank = !title && !artist && totalSec === 0 && !genre && !mood && (bpm == null);
                if (completelyBlank) continue;

                // require duration if some identifying data present
                const hasId = title || artist;
                if (hasId && totalSec === 0) {
                    status.textContent = `Enter a duration for song ${i + 1} or remove the row.`;
                    return;
                }

                songs.push({
                    title: title || null,
                    artist: artist || null,
                    genre: genre || null,
                    bpm: bpm,
                    mood: mood || null,
                    durationMinutes: minutes,
                    durationSeconds: seconds
                });
            }

            const payload = {
                name: name,
                visibility: isPublic ? 'PUBLIC' : 'PRIVATE',
                songs: songs
            };

            status.textContent = 'Creating...';

            async function doPost() {
                return await fetchJson('/api/repertoires', {
                    method: 'POST',
                    body: payload
                });
            }

            try {
                await doPost();
                status.textContent = 'Repertoire created.';
                try { loadMyRepertoires(); } catch (e) {}
                showAuthView();
            } catch (e) {
                const msg = String(e && e.message ? e.message : e);
                if (/HTTP\s*403/.test(msg)) {
                    // Do not perform an automatic GET/refresh (that previously triggered logout/redirect behavior).
                    // Instead inform the user and keep them in the auth view so they can refresh or re-login manually.
                    status.textContent = 'Forbidden — CSRF token missing or expired. Refresh the page or re-login and try again.';
                    return;
                }
                const out = msg.length > 200 ? msg.slice(0, 200) + '…' : msg;
                status.textContent = out;
            }
        });
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
        // Install last-resort defensive guard FIRST so it always captures native submits (Enter, etc.)
        try {
            const lf = document.getElementById('loginForm');
            if (lf && !lf.dataset.nativeGuard) {
                try { lf.action = '#'; } catch (e) {}
                // capture=true so this runs before other submit listeners and prevents native navigation
                lf.addEventListener('submit', (e) => { e.preventDefault(); e.stopImmediatePropagation(); }, { capture: true, passive: false });
                lf.dataset.nativeGuard = '1';
            }
            // Also install a global capturing submit listener as a backup (covers forms added later)
            if (!document.documentElement.dataset.loginGlobalGuard) {
                document.addEventListener('submit', (ev) => {
                    const form = ev.target;
                    if (!(form instanceof HTMLFormElement)) return;
                    // very small heuristic: block submits from forms that look like credential forms
                    try {
                        const hasUser = form.querySelector('#username, [name="username"], [name="name"], input[type="text"]');
                        const hasPass = form.querySelector('#password, [name="password"], input[type="password"]');
                        if (hasUser && hasPass) {
                            ev.preventDefault();
                            ev.stopImmediatePropagation();
                        }
                    } catch (ex) { /* ignore */ }
                }, { capture: true, passive: false });
                document.documentElement.dataset.loginGlobalGuard = '1';
            }
        } catch (e) { /* ignore */ }

        // Now the app controls (they may add submit handlers but the guard runs first)
        attachPublicControls();
        attachAuthControls();
        updateAdminVisibility(); // <-- ensure correct visibility on initial load

        if (sessionStorage.getItem('authOk')) {
            showAuthView();
        } else {
            showPublicHome();
        }
        enforceLeftAlignment();
    }

    document.addEventListener('DOMContentLoaded', init);
})();

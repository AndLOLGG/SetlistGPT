/**
 * frontpage.js
 * - DOM helpers
 * - CSRF‑aware fetch JSON helper
 * - Public/Auth view switching and session restore
 * - Login/Logout handlers
 * - Load repertoires and songs
 * - Create repertoire/setlist flow (multi‑set support; row‑breaks for layout)
 * - Public navigation handlers
 * - Diagnostics export
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
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }
    function getCookie(name) {
        const m = document.cookie.match('(?:^|; )' + name.replace(/([$?*|{}\]\\^])/g, '\\$1') + '=([^;]*)');
        return m ? decodeURIComponent(m[1]) : null;
    }
    async function fetchJson(url, opts = {}) {
        const headers = Object.assign({ Accept: 'application/json' }, opts.headers || {});
        let body = opts.body;
        if (!(body instanceof FormData) && body && typeof body === 'object') {
            headers['Content-Type'] = headers['Content-Type'] || 'application/json; charset=UTF-8';
            body = JSON.stringify(body);
        }
        const xsrf = getCookie('XSRF-TOKEN');
        if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;

        const res = await fetch(url, { credentials: 'same-origin', ...opts, headers, body });
        const ct = res.headers.get('content-type') || '';
        const isJson = ct.includes('application/json');
        if (!res.ok) {
            const msg = isJson ? JSON.stringify(await res.json()).slice(0, 200) : (await res.text()).slice(0, 200);
            throw new Error(`HTTP ${res.status}: ${msg}`);
        }
        return isJson ? res.json() : res.text();
    }

    // Ensure left alignment where we inject content (overrides any global center styles)
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
            const id = r.id ?? '';
            const name = escapeHtml(r.name ?? '(unnamed)');
            const vis = escapeHtml(String(r.visibility ?? 'PRIVATE'));
            const caption = `${name} — Visibility: ${vis}`;
            if (clickableIds && id) {
                return `<li><button type="button" class="linklike" data-rep-id="${id}">${caption}</button></li>`;
            }
            return `<li>${caption}</li>`;
        });
        return `<ul>${rows.join('')}</ul>`;
    }

    function renderSetlistSummaries(list) {
        if (!Array.isArray(list) || list.length === 0) return '<p>No setlists found.</p>';
        const rows = list.map(s => {
            const title = escapeHtml(s.title ?? 'Setlist');
            const dur = Number(s.totalDurationSeconds ?? 0);
            const when = escapeHtml(s.createdAt ?? '');
            const extra = [];
            if (dur) extra.push(`Duration: ${fmtDuration(dur)}`);
            if (when) extra.push(when);
            return `<li><strong>${title}</strong>${extra.length ? ' — ' + extra.join(' • ') : ''}</li>`;
        });
        return `<ul>${rows.join('')}</ul>`;
    }

    function renderSongListNumbered(songs) {
        if (!Array.isArray(songs) || songs.length === 0) return '<p>No songs.</p>';
        const items = songs.map(s => {
            const title = escapeHtml(s.title ?? '(untitled)');
            const artist = escapeHtml(s.artist ?? '');
            const mood = escapeHtml(s.mood ?? '');
            const g = escapeHtml(s.genre ?? '');
            const d = fmtDuration(Number(s.durationInSeconds ?? 0));
            const bits = [title, artist && `— ${artist}`, g && `(${g})`, mood && `[${mood}]`, d && ` — ${d}`]
                .filter(Boolean)
                .join(' ');
            return `<li>${bits}</li>`;
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

        if (showLoginBtn && !showLoginBtn.dataset.attached) {
            showLoginBtn.dataset.attached = 'true';
            showLoginBtn.addEventListener('click', () => {
                show(loginArea);
                const u = $('username'); if (u) u.focus();
            });
        }
        if (loginBtn && !loginBtn.dataset.attached) {
            loginBtn.dataset.attached = 'true';
            loginBtn.addEventListener('click', async () => {
                if (status) status.textContent = 'Logging in...';
                // Placeholder demo login
                await new Promise(r => setTimeout(r, 200));
                sessionStorage.setItem('authOk', 'true');
                if (status) status.textContent = '';
                showAuthView();
            });
        }
        if (createProfileBtn && !createProfileBtn.dataset.attached) {
            createProfileBtn.dataset.attached = 'true';
            createProfileBtn.addEventListener('click', () => {
                window.location.href = '/profile/create';
            });
        }
        if (seePublicBtn && !seePublicBtn.dataset.attached) {
            seePublicBtn.dataset.attached = 'true';
            seePublicBtn.addEventListener('click', () => {
                showPublicBrowser();
            });
        }
        if (backBtn && !backBtn.dataset.attached) {
            backBtn.dataset.attached = 'true';
            backBtn.addEventListener('click', () => {
                showPublicHome();
            });
        }
        if (publicRepsBtn && !publicRepsBtn.dataset.attached) {
            publicRepsBtn.dataset.attached = 'true';
            publicRepsBtn.addEventListener('click', async () => {
                if (!results) return;
                results.textContent = 'Loading public repertoires...';
                try {
                    const list = await fetchJson('/api/repertoires/public');
                    results.innerHTML = renderRepertoireList(list, { clickableIds: true });
                    results.querySelectorAll('button.linklike[data-rep-id]').forEach(btn => {
                        btn.addEventListener('click', () => showPublicRepertoireDetails(btn.dataset.repId));
                    });
                } catch (e) {
                    results.textContent = 'Failed to load public repertoires.';
                }
            });
        }
        if (publicSetlistsBtn && !publicSetlistsBtn.dataset.attached) {
            publicSetlistsBtn.dataset.attached = 'true';
            publicSetlistsBtn.addEventListener('click', async () => {
                if (!results) return;
                results.textContent = 'Loading setlists...';
                try {
                    const list = await fetchJson('/api/setlists');
                    results.innerHTML = renderSetlistSummaries(list);
                } catch (e) {
                    results.textContent = 'Failed to load setlists.';
                }
            });
        }
    }

    async function showPublicRepertoireDetails(id) {
        const results = $('publicResults');
        const status = $('publicStatus');
        if (!results) return;
        try {
            results.textContent = 'Loading repertoire...';
            const r = await fetchJson(`/api/repertoires/${id}`);
            const songs = await fetchJson(`/api/repertoires/${id}/songs`);
            results.innerHTML = `
                <h3>${escapeHtml(r.name ?? 'Repertoire')}</h3>
                <p class="meta">Visibility: ${escapeHtml(r.visibility ?? 'PRIVATE')}</p>
                ${renderSongListNumbered(songs)}
            `;
            if (status) status.textContent = '';
        } catch (e) {
            if (status) status.textContent = 'Failed to load repertoire.';
            results.textContent = '';
        }
    }

    // ---------- Auth handlers ----------
    function attachAuthControls() {
        const newSetlistBtn = $('newSetlistBtn');
        const setlistsBtn = $('setlistsBtn');
        const logoutBtn = $('logoutBtn');
        const newRepertoireBtn = $('newRepertoireBtn');
        const myRepertoiresBtn = $('myRepertoiresBtn');

        if (newSetlistBtn && !newSetlistBtn.dataset.attached) {
            newSetlistBtn.dataset.attached = 'true';
            newSetlistBtn.addEventListener('click', () => createSetlistFlow());
        }
        if (setlistsBtn && !setlistsBtn.dataset.attached) {
            setlistsBtn.dataset.attached = 'true';
            setlistsBtn.addEventListener('click', () => loadMySetlists());
        }
        if (newRepertoireBtn && !newRepertoireBtn.dataset.attached) {
            newRepertoireBtn.dataset.attached = 'true';
            newRepertoireBtn.addEventListener('click', () => createRepertoireFlow());
        }
        if (myRepertoiresBtn && !myRepertoiresBtn.dataset.attached) {
            myRepertoiresBtn.dataset.attached = 'true';
            myRepertoiresBtn.addEventListener('click', () => loadMyRepertoires());
        }
        if (logoutBtn && !logoutBtn.dataset.attached) {
            logoutBtn.dataset.attached = 'true';
            logoutBtn.addEventListener('click', () => {
                sessionStorage.removeItem('authOk');
                showPublicHome();
            });
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
            area.innerHTML = `
                <h3>My Setlists</h3>
                ${renderSetlistSummaries(list)}
            `;
        } catch (e) {
            area.textContent = 'Failed to load setlists.';
        }
    }

    async function loadMyRepertoires() {
        const area = $('contentArea');
        if (!area) return;
        area.textContent = 'Loading repertoires...';
        enforceLeftAlignment();
        try {
            const list = await fetchJson('/api/repertoires');
            area.innerHTML = `
                <h3>My Repertoires</h3>
                ${renderRepertoireList(list, { clickableIds: true })}
            `;
            area.querySelectorAll('button.linklike[data-rep-id]').forEach(btn => {
                btn.addEventListener('click', () => showRepertoireDetails(btn.dataset.repId));
            });
        } catch (e) {
            area.textContent = 'Failed to load repertoires.';
        }
    }

    async function showRepertoireDetails(id) {
        const area = $('contentArea');
        if (!area) return;
        area.textContent = 'Loading repertoire...';
        enforceLeftAlignment();
        try {
            const r = await fetchJson(`/api/repertoires/${id}`);
            const songs = await fetchJson(`/api/repertoires/${id}/songs`);
            area.innerHTML = `
                <h3>${escapeHtml(r.name ?? 'Repertoire')}</h3>
                <p class="meta">Visibility: ${escapeHtml(r.visibility ?? 'PRIVATE')}</p>
                ${renderSongListNumbered(songs)}
            `;
        } catch (e) {
            area.textContent = 'Failed to load repertoire.';
        }
    }

    // ---------- Auth: create flows ----------
    function createRepertoireFlow() {
        const area = $('contentArea');
        if (!area) return;
        area.innerHTML = `
            <h3>New Repertoire</h3>
            <div class="form" style="text-align:left;display:flex;flex-wrap:wrap;gap:12px 16px;align-items:center;">
                <label>Name <input id="repName" type="text" /></label>
                <label>Visibility
                    <select id="repVisibility">
                        <option value="PRIVATE">Private</option>
                        <option value="PUBLIC">Public</option>
                    </select>
                </label>
                <button id="createRepBtn" type="button">Create</button>
                <span id="repStatus" class="meta" aria-live="polite"></span>
            </div>
        `;
        enforceLeftAlignment();
        const btn = $('createRepBtn');
        const status = $('repStatus');
        if (btn && !btn.dataset.attached) {
            btn.dataset.attached = 'true';
            btn.addEventListener('click', async () => {
                try {
                    const name = ($('repName')?.value || '').trim();
                    const visibility = $('repVisibility')?.value || 'PRIVATE';
                    if (!name) {
                        if (status) status.textContent = 'Name is required.';
                        return;
                    }
                    if (status) status.textContent = 'Creating...';
                    const created = await fetchJson('/api/repertoires', {
                        method: 'POST',
                        body: { name, visibility }
                    });
                    if (status) status.textContent = `Created: ${escapeHtml(created.name)}.`;
                } catch (e) {
                    if (status) status.textContent = 'Failed to create repertoire.';
                }
            });
        }
    }

    function createSetlistFlow() {
        const area = $('contentArea');
        if (!area) return;

        const opt = (v, lbl = v) => `<option value="${escapeHtml(v)}">${escapeHtml(lbl)}</option>`;
        const pretty = (s) => String(s).replace(/_/g, ' ').replace(/ GROUP$/i, ' Group');
        const groupOpts = GENRE_GROUPS.map(g => opt(g, pretty(g)));
        const genreOpts = GENRES.map(g => opt(g, pretty(g)));
        const genreSelectHtml = [
            '<option value="">(none)</option>',
            '<optgroup label="Groups">', ...groupOpts, '</optgroup>',
            '<optgroup label="Genres">', ...genreOpts, '</optgroup>'
        ].join('');
        const moodSelectHtml = [
            '<option value="">(none)</option>',
            ...MOODS.map(m => opt(m, pretty(m)))
        ].join('');

        // Note: Sets selector is FIRST; each subsequent group is forced to a new row.
        area.innerHTML = `
        <h3>New Setlist</h3>
        <div class="form" style="text-align:left;display:flex;flex-wrap:wrap;gap:12px 16px;align-items:center;">

            <!-- Row 1: Sets (first, on top line) -->
            <label>Sets
                <select id="slSetCount">
                    <option value="1">1 set</option>
                    <option value="2">2 sets</option>
                    <option value="3">3 sets</option>
                </select>
            </label>

            <!-- Row break -->
            <div style="flex-basis:100%;height:0;"></div>

            <!-- Row 2: Title / Artist / Genre -->
            <label>Title <input id="slTitle" type="text" /></label>
            <label>Artist <input id="slArtist" type="text" /></label>
            <label>Genre or Group
                <select id="slGenre">${genreSelectHtml}</select>
            </label>

            <!-- Row break -->
            <div style="flex-basis:100%;height:0;"></div>

            <!-- Row 3: Mood -->
            <label class="mood-line" style="display:inline-flex;align-items:center;gap:8px;">
                <span>Mood</span>
                <select id="slMood">${moodSelectHtml}</select>
            </label>

            <!-- Row break -->
            <div style="flex-basis:100%;height:0;"></div>

            <!-- Row 4/5/6: Per-set durations -->
            <label id="set1Box" style="display:inline-flex;align-items:center;gap:6px;">
                <span>Set 1 (mm:ss)</span>
                <input id="slM1" type="number" min="0" max="59" value="10" style="width:5em" />
                <span>:</span>
                <input id="slS1" type="number" min="0" max="59" value="0" style="width:5em" />
            </label>

            <div style="flex-basis:100%;height:0;"></div>

            <label id="set2Box" style="display:none;align-items:center;gap:6px;">
                <span>Set 2 (mm:ss)</span>
                <input id="slM2" type="number" min="0" max="59" value="0" style="width:5em" />
                <span>:</span>
                <input id="slS2" type="number" min="0" max="59" value="0" style="width:5em" />
            </label>

            <div id="brSet2" style="display:none;flex-basis:100%;height:0;"></div>

            <label id="set3Box" style="display:none;align-items:center;gap:6px;">
                <span>Set 3 (mm:ss)</span>
                <input id="slM3" type="number" min="0" max="59" value="0" style="width:5em" />
                <span>:</span>
                <input id="slS3" type="number" min="0" max="59" value="0" style="width:5em" />
            </label>

            <!-- Row break -->
            <div style="flex-basis:100%;height:0;"></div>

            <!-- Row 7: Reuse + Build -->
            <label style="display:inline-flex;align-items:center;gap:6px;">
                <input id="slReuse" type="checkbox" />
                <span>Allow song reuse</span>
            </label>
            <button id="buildSetlistBtn" type="button">Build</button>
            <span id="setlistStatus" class="meta" aria-live="polite"></span>
        </div>
        <div id="setlistResults" style="text-align:left;"></div>
        `;
        enforceLeftAlignment();

        // Toggle set boxes based on count
        const setCountSel = $('slSetCount');
        function syncSetBoxes() {
            const c = Number(setCountSel.value || '1');
            const s2 = $('set2Box'), br2 = $('brSet2'), s3 = $('set3Box');
            if (c >= 2) {
                if (s2) s2.style.display = 'inline-flex';
                if (br2) br2.style.display = 'block';
            } else {
                if (s2) s2.style.display = 'none';
                if (br2) br2.style.display = 'none';
            }
            if (c >= 3) {
                if (s3) s3.style.display = 'inline-flex';
            } else {
                if (s3) s3.style.display = 'none';
            }
        }
        setCountSel.addEventListener('change', syncSetBoxes);
        syncSetBoxes();

        const btn = $('buildSetlistBtn');
        const status = $('setlistStatus');
        const results = $('setlistResults');

        function clamp01(v, max) {
            const n = Number(v || 0);
            if (!Number.isFinite(n)) return 0;
            return Math.max(0, Math.min(max, n | 0));
        }
        function secondsFrom(mm, ss) {
            return clamp01(mm, 59) * 60 + clamp01(ss, 59);
        }

        // Partition a flat song list into N sets by target durations
        function partitionIntoSets(songs, setDurations) {
            const sets = setDurations.map(() => []);
            const totals = setDurations.map(() => 0);
            let setIdx = 0;

            for (const song of songs) {
                if (setIdx >= setDurations.length) break;
                const d = Math.max(0, Number(song.durationInSeconds || 0));
                // place into current set if it fits, otherwise move to next set
                if (totals[setIdx] + d <= setDurations[setIdx]) {
                    sets[setIdx].push(song);
                    totals[setIdx] += d;
                } else {
                    // try next sets until it fits (or spill into last anyway)
                    let placed = false;
                    for (let j = setIdx + 1; j < setDurations.length; j++) {
                        if (totals[j] + d <= setDurations[j]) {
                            sets[j].push(song);
                            totals[j] += d;
                            placed = true;
                            break;
                        }
                    }
                    if (!placed) {
                        // if nowhere strictly fits, keep adding to the current set (spill)
                        sets[setIdx].push(song);
                        totals[setIdx] += d;
                    }
                }
                // advance set when the current reached target (or exceeded slightly)
                while (setIdx < setDurations.length && totals[setIdx] >= setDurations[setIdx]) {
                    setIdx++;
                }
            }
            return { sets, totals };
        }

        if (btn && !btn.dataset.attached) {
            btn.dataset.attached = 'true';
            btn.addEventListener('click', async () => {
                try {
                    if (status) status.textContent = 'Building...';
                    results.innerHTML = '';

                    const title = ($('slTitle')?.value || '').trim();
                    const artist = ($('slArtist')?.value || '').trim();
                    const genre = $('slGenre')?.value || '';
                    const mood = $('slMood')?.value || '';
                    const allowReuse = !!$('slReuse')?.checked;
                    const setCount = Number($('slSetCount')?.value || '1');

                    const d1 = secondsFrom($('slM1')?.value, $('slS1')?.value);
                    const d2 = setCount >= 2 ? secondsFrom($('slM2')?.value, $('slS2')?.value) : 0;
                    const d3 = setCount >= 3 ? secondsFrom($('slM3')?.value, $('slS3')?.value) : 0;

                    const setDurations = [d1, d2, d3].slice(0, setCount);
                    const total = setDurations.reduce((a, b) => a + b, 0);

                    if (total < 1) {
                        if (status) status.textContent = 'Please enter a duration.';
                        return;
                    }
                    if (total > 59 * 60 + 59) {
                        if (status) status.textContent = 'Total duration must be \u2264 59:59.';
                        return;
                    }

                    const req = {
                        title, artist, genre, mood,
                        durationMinutes: Math.floor(total / 60),
                        durationSeconds: total % 60,
                        allowReuse
                    };

                    const songs = await fetchJson('/api/setlist', { method: 'POST', body: req });

                    // Distribute combined result into the requested sets for display
                    const { sets, totals } = partitionIntoSets(songs, setDurations);

                    const parts = [];
                    for (let i = 0; i < sets.length; i++) {
                        parts.push(`<h4>Set ${i + 1} — ${fmtDuration(totals[i])}/${fmtDuration(setDurations[i])}</h4>`);
                        parts.push(renderSongListNumbered(sets[i]));
                    }
                    const grand = totals.reduce((a, b) => a + b, 0);
                    results.innerHTML = `
                        <h3>Built Setlist</h3>
                        <p class="meta">Total: ${fmtDuration(grand)} (target ${fmtDuration(total)})</p>
                        ${parts.join('')}
                    `;
                    if (status) status.textContent = 'Done.';
                } catch (e) {
                    if (status) status.textContent = 'Failed to build setlist.';
                }
            });
        }
    }

    // ---------- Boot ----------
    function boot() {
        attachPublicControls();
        attachAuthControls();
        if (sessionStorage.getItem('authOk') === 'true') {
            showAuthView();
        } else {
            showPublicHome();
        }

        // Diagnostics export (optional)
        window.SetlistGPT = Object.assign(window.SetlistGPT || {}, {
            fmtDuration, fetchJson,
            showPublicHome, showPublicBrowser, showAuthView,
            loadMyRepertoires, loadMySetlists, createSetlistFlow, createRepertoireFlow
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }
})();

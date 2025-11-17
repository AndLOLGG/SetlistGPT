(function () {
    'use strict';

    const $ = id => document.getElementById(id);

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
        // Add CSRF header if cookie is present
        const xsrf = getCookie('XSRF-TOKEN');
        if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;

        const res = await fetch(url, { credentials: 'include', ...opts, headers, body });
        const ct = res.headers.get('content-type') || '';
        if (!res.ok) {
            if (res.status === 401 || res.status === 403) {
                throw new Error(res.status === 401 ? 'Not authenticated as ADMIN' : 'Forbidden (need ADMIN)');
            }
            if (ct.includes('application/json')) {
                const msg = await res.json();
                throw new Error(`HTTP ${res.status}: ${JSON.stringify(msg)}`);
            } else {
                const txt = await res.text();
                throw new Error(`HTTP ${res.status}: ${txt}`);
            }
        }
        return ct.includes('application/json') ? res.json() : res.text();
    }


    function escapeHtml(s) {
        return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function renderProfileRow(dto) {
        const name = escapeHtml(dto.name ?? '(unnamed)');
        const reps = dto.repertoireCount ?? 0;
        const setlists = dto.setlistCount ?? 0;
        const id = dto.id ?? '';
        return `<div class="admin-row">
            <button class="linklike profile-btn" data-id="${id}">${name}</button>
            <span class="meta">Repertoires: ${reps} • Setlists: ${setlists}</span>
        </div>`;
    }

    async function loadGrouped() {
        const adminsEl = $('adminsList'), musoEl = $('musiciansList'), sumEl = $('summaryArea');
        adminsEl.textContent = 'Loading…';
        musoEl.textContent = 'Loading…';
        sumEl.textContent = '';
        try {
            const dto = await fetchJson('/api/admin/profiles/grouped');
            adminsEl.innerHTML = (dto.admins && dto.admins.length) ? dto.admins.map(renderProfileRow).join('') : '<p>No admins.</p>';
            musoEl.innerHTML = (dto.musicians && dto.musicians.length) ? dto.musicians.map(renderProfileRow).join('') : '<p>No musicians.</p>';
            const totals = [];
            if (dto.totalProfiles != null) totals.push(`Profiles: ${dto.totalProfiles}`);
            if (dto.repertoires != null) totals.push(`Repertoires: ${dto.repertoires}`);
            if (dto.songs != null) totals.push(`Songs: ${dto.songs}`);
            if (dto.setlists != null) totals.push(`Setlists: ${dto.setlists}`);
            if (dto.currentAdminName) totals.push(`You: ${escapeHtml(dto.currentAdminName)}`);
            sumEl.textContent = totals.join(' • ');
            attachProfileButtons();
        } catch (e) {
            adminsEl.textContent = 'Failed to load.';
            musoEl.textContent = 'Failed to load.';
            sumEl.textContent = String(e);
        }
    }

    function attachProfileButtons() {
        document.querySelectorAll('.profile-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = btn.dataset.id;
                if (id) loadProfileDetail(id);
            });
        });
    }

    async function loadProfileDetail(profileId) {
        const detailsEl = $('profileDetails'), songsEl = $('itemSongs');
        detailsEl.textContent = 'Loading profile…';
        songsEl.textContent = '';
        try {
            const dto = await fetchJson(`/api/admin/profiles/${profileId}`);
            const repHtml = (dto.repertoires && dto.repertoires.length)
                ? dto.repertoires.map(r => `<li><button class="linklike rep-btn" data-id="${r.id}">${escapeHtml(r.title)} (${r.songCount})</button></li>`).join('')
                : '<p>No repertoires.</p>';
            const setHtml = (dto.setlists && dto.setlists.length)
                ? dto.setlists.map(s => `<li><button class="linklike set-btn" data-id="${s.id}">${escapeHtml(s.title)} (${s.songCount})</button></li>`).join('')
                : '<p>No setlists.</p>';
            detailsEl.innerHTML = `
                <h3>${escapeHtml(dto.name ?? 'Profile')}</h3>
                <div class="meta">id: ${dto.id}</div>
                <div style="display:flex;gap:20px;flex-wrap:wrap">
                    <div style="flex:1"><h4>Repertoires</h4><ul>${repHtml}</ul></div>
                    <div style="flex:1"><h4>Setlists</h4><ul>${setHtml}</ul></div>
                </div>
            `;
            attachItemButtons();
        } catch (e) {
            detailsEl.textContent = 'Failed to load profile.';
            songsEl.textContent = String(e);
        }
    }

    function attachItemButtons() {
        document.querySelectorAll('.rep-btn').forEach(b => {
            b.addEventListener('click', () => {
                const id = b.dataset.id;
                if (id) loadRepertoireSongs(id);
            });
        });
        document.querySelectorAll('.set-btn').forEach(b => {
            b.addEventListener('click', () => {
                const id = b.dataset.id;
                if (id) loadSetlistSongs(id);
            });
        });
    }

    async function loadRepertoireSongs(repertoireId) {
        const el = $('itemSongs');
        el.textContent = 'Loading songs…';
        try {
            const list = await fetchJson(`/api/admin/repertoires/${repertoireId}/songs`);
            el.innerHTML = renderSongsList(list);
        } catch (e) {
            el.textContent = 'Failed to load songs.';
        }
    }

    async function loadSetlistSongs(setlistId) {
        const el = $('itemSongs');
        el.textContent = 'Loading songs…';
        try {
            const list = await fetchJson(`/api/admin/setlists/${setlistId}/songs`);
            el.innerHTML = renderSongsList(list);
        } catch (e) {
            el.textContent = 'Failed to load songs.';
        }
    }

    function renderSongsList(list) {
        if (!Array.isArray(list) || list.length === 0) return '<p>No songs.</p>';
        return `<ol>${list.map(s => `<li>${escapeHtml(s.title ?? '(untitled)')} ${s.artist ? '— ' + escapeHtml(s.artist) : ''} ${s.durationInSeconds ? '(' + Math.floor(s.durationInSeconds/60) + ':' + String(s.durationInSeconds%60).padStart(2,'0') + ')' : ''}</li>`).join('')}</ol>`;
    }

    // boot
    document.addEventListener('DOMContentLoaded', loadGrouped);
})();
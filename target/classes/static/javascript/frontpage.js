console.log('frontpage.js loaded');
document.addEventListener('DOMContentLoaded', () => {
    const publicView = document.getElementById('publicView');
    const authView = document.getElementById('authView');
    const loginArea = document.getElementById('loginArea');
    const loginForm = document.getElementById('loginForm');
    const usernameInput = document.getElementById('username');
    const passwordInput = document.getElementById('password');
    const publicStatus = document.getElementById('publicStatus');
    const publicResults = document.getElementById('publicResults');
    const contentArea = document.getElementById('contentArea');

    const publicNav = document.getElementById('publicNav');
    const publicActions = publicView ? publicView.querySelector('.actions') : null;

    if (!publicView || !authView || !publicActions) {
        console.warn('frontpage.js: essential DOM elements missing, aborting init');
        return;
    }

    // track previous loginArea visibility
    let prevLoginHidden = null;

    function showAuth(isAuth, username) {
        if (isAuth) {
            publicView.classList.add('hidden');
            authView.classList.remove('hidden');
            if (contentArea) contentArea.textContent = username ? `Signed in as ${username}` : 'Signed in';
        } else {
            publicView.classList.remove('hidden');
            authView.classList.add('hidden');
            if (contentArea) contentArea.textContent = '';
        }
    }

    async function checkAuth() {
        try {
            // use ProfileController.me endpoint
            const res = await fetch('/api/profile/me', { credentials: 'same-origin' });
            if (!res.ok) { showAuth(false); return; }
            const data = await res.json();
            showAuth(Boolean(data && data.name), data && data.name);
        } catch (e) {
            showAuth(false);
        }
    }

    function showPublicNav() {
        prevLoginHidden = loginArea ? loginArea.classList.contains('hidden') : true;
        if (publicActions) publicActions.classList.add('hidden');
        if (loginArea) loginArea.classList.add('hidden');
        if (publicNav) publicNav.classList.remove('hidden');
        if (publicResults) publicResults.textContent = '';
    }

    function hidePublicNav() {
        if (publicNav) publicNav.classList.add('hidden');
        if (publicActions) publicActions.classList.remove('hidden');
        if (loginArea) {
            if (prevLoginHidden === null) loginArea.classList.add('hidden');
            else if (prevLoginHidden) loginArea.classList.add('hidden');
            else loginArea.classList.remove('hidden');
        }
        prevLoginHidden = null;
        if (publicResults) publicResults.textContent = '';
    }

    async function loadAndShow(path, failMessage) {
        if (publicResults) publicResults.textContent = 'Loading...';
        try {
            const res = await fetch(path, { credentials: 'same-origin' });
            if (!res.ok) { if (publicResults) publicResults.textContent = failMessage; return; }
            const data = await res.json();
            if (publicResults) publicResults.textContent = JSON.stringify(data, null, 2);
        } catch (e) {
            if (publicResults) publicResults.textContent = 'Error loading data';
        }
    }

    // Buttons - guard each element before attaching handlers
    const createProfileBtn = document.getElementById('createProfileBtn');
    if (createProfileBtn) {
        createProfileBtn.addEventListener('click', () => {
            window.location.href = '/profile/create';
        });
    }

    const showLoginBtn = document.getElementById('showLoginBtn');
    if (showLoginBtn && loginArea) {
        showLoginBtn.addEventListener('click', () => {
            loginArea.classList.toggle('hidden');
        });
    }

    const publicRepsBtn = document.getElementById('publicRepsBtn');
    if (publicRepsBtn) publicRepsBtn.addEventListener('click', () => showPublicNav());

    const publicRepsNavBtn = document.getElementById('publicRepsNavBtn');
    if (publicRepsNavBtn) publicRepsNavBtn.addEventListener('click', async () => {
        // RepertoireController public endpoint is /api/repertoires/public
        await loadAndShow('/api/repertoires/public', 'Failed to load public repertoires');
    });

    const publicSetlistsNavBtn = document.getElementById('publicSetlistsNavBtn');
    if (publicSetlistsNavBtn) publicSetlistsNavBtn.addEventListener('click', async () => {
        // If you add a public setlists endpoint later, update this path
        await loadAndShow('/api/setlists/public', 'Failed to load public setlists');
    });

    const publicBackBtn = document.getElementById('publicBackBtn');
    if (publicBackBtn) publicBackBtn.addEventListener('click', () => hidePublicNav());

    if (loginForm) {
        loginForm.addEventListener('submit', async (ev) => {
            ev.preventDefault();
            if (publicStatus) publicStatus.textContent = 'Signing in...';
            try {
                const res = await fetch('/api/profile/login', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ name: usernameInput ? usernameInput.value : '', password: passwordInput ? passwordInput.value : '' })
                });
                if (!res.ok) {
                    if (publicStatus) publicStatus.textContent = 'Login failed';
                    return;
                }
                if (publicStatus) publicStatus.textContent = 'Signed in';
                await checkAuth();
            } catch (e) {
                if (publicStatus) publicStatus.textContent = 'Login error';
            }
        });
    }

    const repsBtn = document.getElementById('repsBtn');
    if (repsBtn) repsBtn.addEventListener('click', () => { window.location.href = '/repertoires'; });

    const setlistsBtn = document.getElementById('setlistsBtn');
    if (setlistsBtn) setlistsBtn.addEventListener('click', () => { window.location.href = '/setlists'; });

    const songsBtn = document.getElementById('songsBtn');
    if (songsBtn) songsBtn.addEventListener('click', () => { window.location.href = '/songs'; });

    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) logoutBtn.addEventListener('click', async () => {
        try {
            // ProfileController.logout uses PUT at /api/profile/logout
            const res = await fetch('/api/profile/logout', { method: 'PUT', credentials: 'same-origin' });
            if (res.ok) checkAuth();
            else if (contentArea) contentArea.textContent = 'Logout failed';
        } catch (e) {
            if (contentArea) contentArea.textContent = 'Logout error';
        }
    });

    // initial check
    checkAuth();
});
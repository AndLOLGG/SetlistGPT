(function () {
    console.log('create-profile.js loaded'); // visibility to confirm script is active

    function getCookie(name) {
        const m = document.cookie.match('(?:^|; )' + name.replace(/([$?*|{}\]\\^])/g, '\\$1') + '=([^;]*)');
        return m ? decodeURIComponent(m[1]) : null;
    }

    function attachSignupHandler() {
        const form = document.getElementById('signupForm');
        const status = document.getElementById('status');
        const btn = form ? document.getElementById('createProfileBtn') : null;

        if (!form || !btn) {
            console.warn('create-profile.js: signupForm or button not found on the page');
            return;
        }

        // prevent double-binding if already attached
        if (btn.dataset.signupAttached === 'true') {
            console.debug('create-profile.js: click handler already attached');
            return;
        }
        btn.dataset.signupAttached = 'true';

        console.debug('create-profile.js: attaching click handler');

        btn.addEventListener('click', async () => {
            try {
                if (status) status.textContent = 'Creating profile...';
                console.debug('create-profile.js: createProfileBtn clicked');

                const payload = {
                    name: (document.getElementById('name') || {}).value?.trim() || '',
                    password: (document.getElementById('password') || {}).value?.trim() || ''
                };

                // basic front-end validation
                if (!payload.name || !payload.password) {
                    if (status) status.textContent = 'Username and password are required.';
                    return;
                }

                // Send as JSON to match backend controller expectations
                const headers = {
                    'Content-Type': 'application/json; charset=UTF-8',
                    'Accept': 'application/json'
                };

                // add CSRF token if Spring Security is enforcing CSRF
                const xsrf = getCookie('XSRF-TOKEN');
                if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;

                const res = await fetch('/api/profile/signup', {
                    method: 'POST',
                    headers,
                    body: JSON.stringify(payload),
                    credentials: 'same-origin'
                });

                // accept 200 or 201 as success
                if (res.ok && (res.status === 200 || res.status === 201)) {
                    if (status) status.textContent = 'Profile created. Redirecting to start...';
                    setTimeout(() => { window.location.href = '/'; }, 700);
                    return;
                }

                if (res.status === 409) {
                    if (status) status.textContent = 'Username already exists. Choose another.';
                    return;
                }

                const text = await res.text().catch(() => '');
                const msg = text || res.statusText || 'Signup failed';
                if (status) status.textContent = 'Signup failed: ' + msg;
                console.warn('create-profile.js: signup failed', res.status, msg);
            } catch (e) {
                if (status) status.textContent = 'Network error creating profile';
                console.error('create-profile.js error', e);
            }
        });
    }

    // Run once immediately (defer ensures DOM is parsed), and once on DOMContentLoaded as a safety net.
    attachSignupHandler();
    document.addEventListener('DOMContentLoaded', attachSignupHandler);
})();
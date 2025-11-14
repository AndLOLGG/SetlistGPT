(function () {
    const form = document.getElementById('signupForm');
    const status = document.getElementById('status');
    if (!form) {
        console.warn('create-profile.js: signupForm not found on the page');
        return;
    }

    form.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        status.textContent = 'Creating profile...';
        const payload = {
            name: document.getElementById('name').value.trim(),
            password: document.getElementById('password').value.trim()
        };

        try {
            const res = await fetch('/api/profile/signup', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
                credentials: 'same-origin'
            });

            if (res.status === 201) {
                status.textContent = 'Profile created. Redirecting to start...';
                setTimeout(() => window.location.href = '/', 700);
                return;
            }

            if (res.status === 409) {
                status.textContent = 'Username already exists. Choose another.';
                return;
            }

            const text = await res.text();
            status.textContent = 'Signup failed: ' + (text || res.statusText);
        } catch (e) {
            status.textContent = 'Network error creating profile';
            console.error('create-profile.js error', e);
        }
    });
})();
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('signupForm');
    const status = document.getElementById('status');

    if (!form) {
        console.warn('create-profile.js: signupForm not found on the page');
        return;
    }

    form.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        status.textContent = 'Creating profile...';
        const payload = {
            name: document.getElementById('name').value.trim(),
            password: document.getElementById('password').value.trim()
        };

        try {
            const res = await fetch('/api/profile/signup', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
                credentials: 'same-origin'
            });

            if (res.status === 201) {
                status.textContent = 'Profile created. Redirecting to start...';
                setTimeout(() => window.location.href = '/', 700);
                return;
            }

            if (res.status === 409) {
                status.textContent = 'Username already exists. Choose another.';
                return;
            }

            // try to show server message if any
            const text = await res.text();
            status.textContent = 'Signup failed: ' + (text || res.statusText);
        } catch (e) {
            status.textContent = 'Network error creating profile';
            console.error('create-profile.js error', e);
        }
    });
});
// Stub: immediately unregisters the old COI service worker
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', event => {
    event.waitUntil(
        self.registration.unregister().then(() => self.clients.matchAll()).then(clients =>
            clients.forEach(c => c.navigate(c.url))
        )
    );
});

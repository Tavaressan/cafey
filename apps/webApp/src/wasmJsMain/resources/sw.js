// Service worker de notificações Web Push do Cafey (#365).
self.addEventListener('push', function (event) {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch (e) {
    data = { titulo: 'Cafey', corpo: event.data ? event.data.text() : '' };
  }

  const titulo = data.titulo || 'Cafey';
  const corpo = data.corpo || '';
  const tag = data.tag || data.tipo || 'cafey';
  const options = {
    body: corpo,
    tag: tag,
    data: data,
    icon: '/icon-192.png',
    badge: '/favicon.png',
  };

  event.waitUntil(self.registration.showNotification(titulo, options));
});

self.addEventListener('notificationclick', function (event) {
  event.notification.close();
  const data = event.notification.data || {};
  const dispositivoId = data.dispositivoId;
  const targetPath = dispositivoId ? '/?dispositivo=' + encodeURIComponent(dispositivoId) : '/';
  const targetUrl = new URL(targetPath, self.location.origin).href;

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (clientList) {
      for (let i = 0; i < clientList.length; i++) {
        const client = clientList[i];
        const clientUrl = new URL(client.url);
        if (clientUrl.origin === self.location.origin) {
          if (dispositivoId) {
            // Tenta focar uma aba do app e navegar para o dispositivo.
            try {
              client.navigate(targetUrl);
            } catch (e) {
              // Alguns navegadores podem não suportar navigate(); foco já ajuda.
            }
          }
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(targetUrl);
      }
    })
  );
});

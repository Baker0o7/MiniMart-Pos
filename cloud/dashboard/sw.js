// Caches only this page's own files so the dashboard opens quickly and installs to a home screen.
// It never touches requests to other sites (the database, the sign-in service), so no shop data is stored here.
const CACHE = "minimart-dashboard-shell-v2";
const SHELL = ["./", "index.html", "app.js", "vendor/supabase.js", "manifest.webmanifest", "icon.svg"];
self.addEventListener("install", e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener("activate", e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", e => {
  const u = new URL(e.request.url);
  if (e.request.method !== "GET" || u.origin !== self.location.origin) return;
  // config.js is not cached: it holds the backend address and must always be current.
  if (u.pathname.endsWith("/config.js")) return;
  e.respondWith(fetch(e.request).then(r => { const copy = r.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)); return r; })
    .catch(() => caches.match(e.request).then(m => m || caches.match("index.html"))));
});

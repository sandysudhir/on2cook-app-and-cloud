const CACHE_NAME = "on2cook-cloud-v21";
const CORE_ASSETS = [
  "./",
  "./index.html",
  "./manifest.webmanifest",
  "./data/seed-recipes.json",
  "./data/order-recipes-manifest.json?v=20260612q",
  "./data/order_recipes/VEGETABLE%20UPMA.zip?v=20260612q",
  "./data/order_recipes/VEG%20HAKKA%20NOODLE.zip?v=20260612q",
  "./data/order_recipes/PAAL%20PAYASAM%20.zip?v=20260612q",
  "./data/order_recipes/SPINACH%20OMELETTE.zip?v=20260612q",
  "./data/order_recipes/VEG%20BURGER%20PATTY.zip?v=20260612q",
  "./data/order_recipes/CHI%20LEMON%20COR%20SP%20.zip?v=20260612q",
  "./data/order_recipes/ONION%20PAKODA%20.zip?v=20260612q",
  "./data/order_recipes/DAL%20MAKHANI.zip?v=20260612q",
  "./data/order_recipes/KUNG%20PAO%20CHICKEN.zip?v=20260612q",
  "./data/order_recipes/MASOOR%20DAL%20.zip?v=20260612q",
  "./src/styles.css?v=20260612q",
  "./src/app.js?v=20260612q",
  "./src/ble-transport.js?v=20260612q",
  "./src/data-store.js?v=20260612q",
  "./src/zip-reader.js?v=20260612q",
  "./assets/app_banner.png"
];

self.addEventListener("install", (event) => {
  self.skipWaiting();
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(CORE_ASSETS)));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    Promise.all([
      caches.keys().then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))),
      self.clients.claim()
    ])
  );
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  event.respondWith(
    fetch(event.request)
      .then((response) => {
        const clone = response.clone();
        caches.open(CACHE_NAME).then((cache) => {
          cache.put(event.request, clone).catch(() => {});
        });
        return response;
      })
      .catch(() => caches.match(event.request))
  );
});

import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["crispy-chicken.webp", "burger.svg", "duitnow-qr.svg", "icon.svg"],
      manifest: {
        name: "Crispy Burger",
        short_name: "Crispy Burger",
        description: "Order crispy chicken burgers delivered to your dorm room.",
        theme_color: "#FFC93C",
        background_color: "#FFC93C",
        display: "standalone",
        start_url: "/",
        icons: [
          { src: "icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" },
          { src: "icon.svg", sizes: "any", type: "image/svg+xml", purpose: "maskable" }
        ]
      },
      workbox: {
        // Precache the app shell and versioned static assets for reliable offline startup.
        navigateFallback: "/index.html",
        globPatterns: ["**/*.{js,css,html,svg,woff2}"],
        runtimeCaching: [
          {
            // Public product/media images may be reused offline. Private receipts never match this rule.
            urlPattern: /^https:\/\/[a-z0-9-]+\.supabase\.co\/storage\/v1\/object\/public\//i,
            handler: "CacheFirst",
            options: {
              cacheName: "cbms-public-images-v1",
              expiration: {
                maxEntries: 60,
                maxAgeSeconds: 60 * 60 * 24 * 7
              },
              cacheableResponse: {
                statuses: [0, 200]
              }
            }
          },
          {
            // Keep all Supabase data, RPC, signed URLs and private storage network-only.
            urlPattern: /^https:\/\/[a-z0-9-]+\.supabase\.co\//i,
            handler: "NetworkOnly"
          }
        ]
      }
    })
  ]
});

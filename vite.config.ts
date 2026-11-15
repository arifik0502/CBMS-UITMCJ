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
      includeAssets: ["burger.svg", "duitnow-qr.svg", "icon.svg"],
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
        // Never cache API/RPC traffic; only the app shell and static assets.
        navigateFallback: "/index.html",
        globPatterns: ["**/*.{js,css,html,svg,woff2}"]
      }
    })
  ]
});

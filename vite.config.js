import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  root: "preview",
  plugins: [react()],
  server: {
    allowedHosts: true,
  },
  resolve: {
    alias: {
      "react-native": "react-native-web",
    },
  },
  build: {
    outDir: "../dist",
    emptyOutDir: true,
  },
});
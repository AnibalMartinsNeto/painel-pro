/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // PROXY: o front roda em localhost:5173 e a API em localhost:8080.
    // Para o navegador são "origens" diferentes, e ele bloquearia as
    // chamadas (regra de segurança chamada CORS). Com o proxy, o front
    // chama "/api/..." na própria origem e o Vite repassa ao backend.
    // Endereço da API: padrão 8080; outro via variável, ex.: API_URL=http://localhost:8081
    proxy: {
      '/api': process.env.API_URL ?? 'http://localhost:8080',
      '/actuator': process.env.API_URL ?? 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom', // simula um navegador (DOM) dentro do Node
    setupFiles: ['./src/test/setup.ts'],
  },
})

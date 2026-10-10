import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import Statement from './pages/Statement'

/**
 * App = componente raiz. Aqui montamos o **roteador SPA**.
 *
 * Conceitos:
 * - BrowserRouter: lê/escreve o URL do navegador (History API).
 * - Routes: escolhe **a primeira** Route que casa com o path atual.
 * - Route path="/login" element={<Login />} → "se URL for /login, mostre Login".
 * - path="*" (coringa) + Navigate to="/login" → **rota não encontrada = volta pro login**.
 *
 * Nota: <Navigate> é um componente que **redireciona** (troca o URL e monta o alvo).
 * O atributo `replace` impede que o usuário dê "Voltar" no navegador e caia de novo na rota inválida.
 */
function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Rotas públicas */}
        <Route path="/login" element={<Login />} />
        <Route path="/cadastro" element={<Register />} />

        {/* Rotas que exigem login (protegidas) — Fase 4.2 fará a guarda real com token */}
        <Route path="/" element={<Dashboard />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/extrato" element={<Statement />} />

        {/* Fallback: qualquer URL não listada → redireciona para /login */}
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
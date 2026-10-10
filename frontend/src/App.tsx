import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import RequireAuth from './components/RequireAuth'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import Statement from './pages/Statement'

/**
 * App = componente raiz com roteador SPA + guard de rotas protegidas.
 *
 * Estrutura:
 * - Rotas públicas: /login, /cadastro
 * - Rotas protegidas (RequireAuth): /, /dashboard, /extrato
 *   - RequireAuth lê hasToken() → se true renderiza <Outlet /> (a rota filha)
 *   - Se false → <Navigate to="/login" replace />
 * - Fallback * → /login
 */
function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Rotas públicas */}
        <Route path="/login" element={<Login />} />
        <Route path="/cadastro" element={<Register />} />

        {/* Rotas protegidas — guard RequireAuth */}
        <Route element={<RequireAuth />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/extrato" element={<Statement />} />
        </Route>

        {/* Fallback: qualquer URL não listada → redireciona para /login */}
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
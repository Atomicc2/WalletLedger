import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import RequireAuth from './components/RequireAuth'
import { ToastProvider } from './context/ToastContext'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import Statement from './pages/Statement'

/**
 * App = componente raiz com roteador SPA + guard de rotas protegidas + ToastProvider.
 *
 * Ordem dos providers (de fora para dentro):
 * 1. BrowserRouter — habilita roteamento
 * 2. ToastProvider — disponibiliza toasts para toda a árvore
 * 3. Routes — define as rotas
 */
function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
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
      </ToastProvider>
    </BrowserRouter>
  )
}

export default App
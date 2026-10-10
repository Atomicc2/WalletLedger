import { Navigate, Outlet } from 'react-router-dom'
import { hasToken } from '../services/token'

/**
 * Guard de rota protegida — componente que **envolve** rotas que exigem login.
 *
 * Como funciona:
 * - Se `hasToken()` retorna true → renderiza `<Outlet />` (a rota filha real, ex.: Dashboard).
 * - Se false → `<Navigate to="/login" replace />` (redireciona pra login).
 *
 * O `replace` impede que o usuário dê "Voltar" no navegador e caia de novo na rota protegida.
 *
 * Uso no App.tsx:
 *   <Route element={<RequireAuth />}>
 *     <Route path="/dashboard" element={<Dashboard />} />
 *     <Route path="/extrato" element={<Statement />} />
 *   </Route>
 */
export default function RequireAuth() {
  return hasToken() ? <Outlet /> : <Navigate to="/login" replace />
}
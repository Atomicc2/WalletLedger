import axios from 'axios'
import { getToken, removeToken } from './token'

/**
 * Instância **única** do Axios configurada para a nossa API.
 *
 * Vantagens de centralizar aqui:
 * - Base URL num só lugar (fácil mudar para produção).
 * - Interceptors de request/response valem para **todas** as chamadas.
 * - TypeScript conhece os tipos de request/response.
 */

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  headers: {
    'Content-Type': 'application/json',
  },
  // withCredentials: true // se usássemos cookies httpOnly; com Bearer token não precisa
})

/**
 * REQUEST INTERCEPTOR → roda **antes** de cada chamada HTTP.
 * Injeta o header Authorization: Bearer <token> se houver token salvo.
 */
api.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * RESPONSE INTERCEPTOR → roda **depois** que a resposta chega (sucesso ou erro).
 * Tratamento global de 401:
 * - Se o backend devolve 401 (token inválido/expirado/revogado),
 *   limpamos o token local e redirecionamos para /login.
 * - O `window.location.href` força navegação **fora** do React Router
 *   (garante limpeza total de estado).
 */
api.interceptors.response.use(
  (response) => response, // sucesso: passa direto
  (error) => {
    if (error.response?.status === 401) {
      removeToken()
      // Evita loop se já estiver na tela de login
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    // Importante: **rejeita a Promise** para o caller poder tratar (try/catch)
    return Promise.reject(error)
  }
)

export default api
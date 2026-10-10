import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'
import { setToken } from '../services/token'

/**
 * Tela de Login — agora **chama a API real** do backend.
 *
 * Novos conceitos:
 * - **useNavigate()**: hook do React Router para redirecionar **programaticamente**
 *   (depois do login bem-sucedido). Equivalente a `response.sendRedirect()` no Spring,
 *   mas roda no navegador sem novo request de página.
 * - **Estados de UI**: `loading` (desabilita botão + mostra "Entrando...") e `error`
 *   (mostra mensagem amigável vinda do backend no padrão RFC 7807).
 * - **try/catch + async/await**: padrão para chamadas assíncronas.
 *   O interceptor do Axios já trata 401 global, mas erros 400 (validação)
 *   ou 500 caem aqui.
 */
export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const navigate = useNavigate()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      // POST /api/auth/login → { accessToken, tokenType, expiresIn }
      const response = await api.post('/auth/login', { email, password })

      // Backend devolve: { accessToken: "eyJ...", tokenType: "Bearer", expiresIn: 86400 }
      const { accessToken } = response.data

      // Guarda o token no localStorage (o interceptor vai usá-lo nas próximas chamadas)
      setToken(accessToken)

      // Redireciona para o dashboard (rota protegida)
      navigate('/dashboard', { replace: true })
    } catch (err: unknown) {
      // Tipagem defensiva: o erro do Axios tem shape { response?: { data?: { detail?: string } } }
      const axiosError = err as { response?: { data?: { detail?: string } } }
      const msg = axiosError.response?.data?.detail ?? 'Erro ao fazer login. Tente novamente.'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={containerStyle}>
      <h2>🔐 Login</h2>

      {error && <div style={errorStyle}>{error}</div>}

      <form onSubmit={handleSubmit} style={formStyle}>
        <div style={fieldStyle}>
          <label htmlFor="email">E-mail</label>
          <input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="seu@email.com"
            required
            disabled={loading}
            style={inputStyle}
          />
        </div>
        <div style={fieldStyle}>
          <label htmlFor="password">Senha</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            required
            disabled={loading}
            style={inputStyle}
          />
        </div>
        <button type="submit" disabled={loading} style={buttonStyle}>
          {loading ? 'Entrando…' : 'Entrar'}
        </button>
      </form>

      <p style={{ marginTop: '1rem', color: '#666' }}>
        Ainda não tem conta? <a href="/cadastro">Cadastre-se</a>
      </p>
    </div>
  )
}

const containerStyle = { maxWidth: '360px', margin: '3rem auto', padding: '1.5rem', border: '1px solid #ddd', borderRadius: '8px', fontFamily: 'system-ui' } as const
const formStyle = { display: 'flex', flexDirection: 'column', gap: '1rem' } as const
const fieldStyle = { display: 'flex', flexDirection: 'column', gap: '0.35rem' } as const
const inputStyle = { padding: '0.6rem', border: '1px solid #ccc', borderRadius: '4px', fontSize: '1rem' } as const
const buttonStyle = { padding: '0.7rem', background: '#2563eb', color: '#fff', border: 'none', borderRadius: '4px', fontSize: '1rem', cursor: 'pointer', opacity: 1 } as const
const errorStyle = { padding: '0.75rem', background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '4px', color: '#991b1b', fontSize: '0.9rem' } as const
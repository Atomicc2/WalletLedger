import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'
import { useToast } from '../context/ToastContext'

/**
 * Tela de Cadastro — agora **chama a API real** do backend.
 *
 * Fluxo:
 * 1. Usuário preenche nome, e-mail, senha.
 * 2. Submit → POST /api/users (público, sem token).
 * 3. Sucesso (201) → toast "Conta criada!" + redireciona para /login.
 * 4. Erro (400/409) → toast com mensagem do backend (RFC 7807).
 *
 * Conceitos:
 * - **useNavigate**: redirecionamento programático após sucesso.
 * - **useToast**: feedback visual não-bloqueante.
 * - **loading state**: desabilita botão durante request.
 */
export default function Register() {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)

  const navigate = useNavigate()
  const { success, error: toastError } = useToast()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim() || !email.trim() || !password.trim()) return
    setLoading(true)

    try {
      // POST /api/users → { id, name, email, defaultAccountId, createdAt }
      await api.post('/users', { name, email, password })

      success('Conta criada com sucesso! Faça login para entrar.')
      // replace: true impede "Voltar" no navegador cair no cadastro de novo
      navigate('/login', { replace: true })
    } catch (err: unknown) {
      const axiosError = err as { response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } } }
      const resp = axiosError.response?.data
      // Backend devolve RFC 7807: { detail, errors: { campo: mensagem } }
      const msg = resp?.errors
        ? Object.entries(resp.errors).map(([k, v]) => `${k}: ${v}`).join('; ')
        : resp?.detail ?? 'Erro ao criar conta. Tente novamente.'
      toastError(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={containerStyle}>
      <h2>📝 Cadastro</h2>
      <form onSubmit={handleSubmit} style={formStyle}>
        <div style={fieldStyle}>
          <label htmlFor="name">Nome</label>
          <input
            id="name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Seu nome"
            required
            disabled={loading}
            style={inputStyle}
          />
        </div>
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
            minLength={6}
            disabled={loading}
            style={inputStyle}
          />
        </div>
        <button type="submit" disabled={loading} style={buttonStyle}>
          {loading ? 'Criando…' : 'Criar conta'}
        </button>
      </form>
      <p style={{ marginTop: '1rem', color: '#666' }}>
        Já tem conta? <a href="/login">Entre</a>
      </p>
    </div>
  )
}

const containerStyle = { maxWidth: '360px', margin: '3rem auto', padding: '1.5rem', border: '1px solid #ddd', borderRadius: '8px', fontFamily: 'system-ui' } as const
const formStyle = { display: 'flex', flexDirection: 'column', gap: '1rem' } as const
const fieldStyle = { display: 'flex', flexDirection: 'column', gap: '0.35rem' } as const
const inputStyle = { padding: '0.6rem', border: '1px solid #ccc', borderRadius: '4px', fontSize: '1rem' } as const
const buttonStyle = { padding: '0.7rem', background: '#16a34a', color: '#fff', border: 'none', borderRadius: '4px', fontSize: '1rem', cursor: 'pointer' } as const
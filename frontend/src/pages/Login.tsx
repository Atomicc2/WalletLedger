import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'
import { setToken } from '../services/token'
import { useToast } from '../context/ToastContext'
import { Button, Input, Card } from '../components/ui'

/**
 * Tela de Login — usa componentes UI reutilizáveis (Button, Input, Card).
 */
export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const navigate = useNavigate()
  const { success, error: toastError } = useToast()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const response = await api.post('/auth/login', { email, password })
      const { accessToken } = response.data

      setToken(accessToken)
      success('Login realizado com sucesso!')
      navigate('/dashboard', { replace: true })
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: { detail?: string } } }
      const msg = axiosError.response?.data?.detail ?? 'Erro ao fazer login. Tente novamente.'
      setError(msg)
      toastError(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-gray-50">
      <Card className="w-full max-w-md" padding="lg">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-gray-900">💰 WalletLedger</h1>
          <p className="text-gray-500 mt-1">Entre na sua conta</p>
        </div>

        {error && (
          <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm" role="alert">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <Input
            label="E-mail"
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="seu@email.com"
            required
            disabled={loading}
            autoComplete="email"
            autoFocus
          />

          <Input
            label="Senha"
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            required
            disabled={loading}
            autoComplete="current-password"
          />

          <Button type="submit" variant="primary" fullWidth loading={loading} size="lg">
            {loading ? 'Entrando…' : 'Entrar'}
          </Button>
        </form>

        <p className="mt-6 text-center text-gray-600 text-sm">
          Ainda não tem conta? <a href="/cadastro" className="text-blue-600 hover:underline font-medium">Cadastre-se</a>
        </p>
      </Card>
    </div>
  )
}
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'
import { useToast } from '../context/ToastContext'
import { Button, Input, Card } from '../components/ui'

/**
 * Tela de Cadastro — usa componentes UI reutilizáveis.
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
      await api.post('/users', { name, email, password })
      success('Conta criada com sucesso! Faça login para entrar.')
      navigate('/login', { replace: true })
    } catch (err: unknown) {
      const axiosError = err as { response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } } }
      const resp = axiosError.response?.data
      const msg = resp?.errors
        ? Object.entries(resp.errors).map(([k, v]) => `${k}: ${v}`).join('; ')
        : resp?.detail ?? 'Erro ao criar conta. Tente novamente.'
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
          <p className="text-gray-500 mt-1">Crie sua conta</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <Input
            label="Nome"
            id="name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Seu nome"
            required
            disabled={loading}
            autoComplete="name"
            autoFocus
          />

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
          />

          <Input
            label="Senha"
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            required
            minLength={6}
            disabled={loading}
            autoComplete="new-password"
            hint="Mínimo 6 caracteres"
          />

          <Button type="submit" variant="success" fullWidth loading={loading} size="lg">
            {loading ? 'Criando…' : 'Criar conta'}
          </Button>
        </form>

        <p className="mt-6 text-center text-gray-600 text-sm">
          Já tem conta? <a href="/login" className="text-blue-600 hover:underline font-medium">Entre</a>
        </p>
      </Card>
    </div>
  )
}
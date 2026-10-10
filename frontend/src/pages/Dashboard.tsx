import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'
import { useToast } from '../context/ToastContext'
import { Button, Input, Card, Modal } from '../components/ui'

/**
 * Dashboard — tela principal logada com modais de Depositar/Transferir.
 *
 * Usa componentes UI: Card, Button, Input, Modal, Badge.
 */
export default function Dashboard() {
  const [balance, setBalance] = useState<string>('—')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [myAccountId, setMyAccountId] = useState<string | null>(null)

  // Estados dos modais
  const [isDepositOpen, setIsDepositOpen] = useState(false)
  const [isTransferOpen, setIsTransferOpen] = useState(false)

  // Estados dos formulários
  const [depositAmount, setDepositAmount] = useState('')
  const [transferAmount, setTransferAmount] = useState('')
  const [transferTarget, setTransferTarget] = useState('')

  // Loading por ação
  const [depositLoading, setDepositLoading] = useState(false)
  const [transferLoading, setTransferLoading] = useState(false)

  const navigate = useNavigate()
  const { success, error: toastError } = useToast()

  // --- Busca conta do usuário (UUID) ---
  const fetchMyAccount = useCallback(async () => {
    try {
      const response = await api.get('/accounts/me')
      setMyAccountId(response.data.accountId)
    } catch (err: unknown) {
      const axiosError = err as { response?: { status?: number; data?: { detail?: string } } }
      if (axiosError.response?.status !== 401) {
        setError(axiosError.response?.data?.detail ?? 'Erro ao carregar dados da conta.')
      }
    }
  }, [])

  // --- Função reutilizável de buscar saldo ---
  const fetchBalance = useCallback(async () => {
    try {
      const response = await api.get('/accounts/me/balance')
      setBalance(`${response.data.currency} ${response.data.balance.toLocaleString('pt-BR', { minimumFractionDigits: 2 })}`)
      setError(null)
    } catch (err: unknown) {
      const axiosError = err as { response?: { status?: number; data?: { detail?: string } } }
      if (axiosError.response?.status !== 401) {
        setError(axiosError.response?.data?.detail ?? 'Erro ao carregar saldo.')
      }
    } finally {
      setLoading(false)
    }
  }, [])

  // Busca inicial
  useEffect(() => {
    fetchMyAccount()
    fetchBalance()
  }, [fetchMyAccount, fetchBalance])

  // --- Handlers de Depósito ---
  const handleDepositSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!depositAmount.trim() || !myAccountId) return
    setDepositLoading(true)
    setError(null)

    try {
      await api.post('/transactions/deposit', {
        idempotencyKey: crypto.randomUUID(),
        targetAccountId: myAccountId,
        amount: parseFloat(depositAmount),
      })
      success('Depósito realizado com sucesso!')
      setIsDepositOpen(false)
      setDepositAmount('')
      fetchBalance()
    } catch (err: unknown) {
      const axiosError = err as { response?: { status?: number; data?: { detail?: string } } }
      if (axiosError.response?.status !== 401) {
        toastError(axiosError.response?.data?.detail ?? 'Erro ao depositar.')
      }
    } finally {
      setDepositLoading(false)
    }
  }

  // --- Handlers de Transferência ---
  const handleTransferSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!transferAmount.trim() || !transferTarget.trim() || !myAccountId) return
    setTransferLoading(true)
    setError(null)

    try {
      await api.post('/transactions/transfer', {
        idempotencyKey: crypto.randomUUID(),
        sourceAccountId: myAccountId,
        targetAccountId: transferTarget,
        amount: parseFloat(transferAmount),
      })
      success('Transferência realizada com sucesso!')
      setIsTransferOpen(false)
      setTransferAmount('')
      setTransferTarget('')
      fetchBalance()
    } catch (err: unknown) {
      const axiosError = err as { response?: { status?: number; data?: { detail?: string } } }
      if (axiosError.response?.status !== 401) {
        toastError(axiosError.response?.data?.detail ?? 'Erro ao transferir.')
      }
    } finally {
      setTransferLoading(false)
    }
  }

  const handleLogout = () => {
    localStorage.removeItem('walletledger_token')
    navigate('/login')
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <h1 className="text-xl font-bold text-gray-900">💰 WalletLedger</h1>
            <nav className="flex items-center gap-4">
              <a href="/dashboard" className="text-blue-600 hover:text-blue-700 font-medium">Dashboard</a>
              <a href="/extrato" className="text-blue-600 hover:text-blue-700 font-medium">Extrato</a>
              <Button variant="danger" size="sm" onClick={handleLogout}>Sair</Button>
            </nav>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
        {/* Saldo */}
        <Card className="mb-6" padding="lg">
          <div className="flex items-baseline justify-between">
            <div>
              <p className="text-sm text-gray-500">Seu saldo</p>
              <p className="text-4xl font-bold text-green-600 mt-1">{loading ? 'Carregando…' : balance}</p>
            </div>
            {error && <span className="text-red-600 text-sm">{error}</span>}
          </div>
        </Card>

        {/* Ações rápidas */}
        <Card className="mb-6" padding="lg">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Ações rápidas</h3>
          <div className="flex gap-4">
            <Button variant="primary" onClick={() => setIsDepositOpen(true)} disabled={loading || !myAccountId}>
              💸 Depositar
            </Button>
            <Button variant="secondary" onClick={() => setIsTransferOpen(true)} disabled={loading || !myAccountId}>
              🔄 Transferir
            </Button>
          </div>
        </Card>

        {/* Modal Depositar */}
        <Modal
          isOpen={isDepositOpen}
          onClose={() => setIsDepositOpen(false)}
          title="💸 Depositar"
          size="md"
        >
          <form onSubmit={handleDepositSubmit} className="space-y-4">
            <Input
              label="Valor (R$)"
              id="depositAmount"
              type="number"
              step="0.01"
              min="0.01"
              value={depositAmount}
              onChange={(e) => setDepositAmount(e.target.value)}
              placeholder="Ex: 100.00"
              required
              autoFocus
            />
            <div className="flex justify-end gap-3 pt-2">
              <Button variant="ghost" type="button" onClick={() => setIsDepositOpen(false)}>
                Cancelar
              </Button>
              <Button variant="primary" type="submit" loading={depositLoading}>
                {depositLoading ? 'Depositando…' : 'Confirmar'}
              </Button>
            </div>
          </form>
        </Modal>

        {/* Modal Transferir */}
        <Modal
          isOpen={isTransferOpen}
          onClose={() => setIsTransferOpen(false)}
          title="🔄 Transferir"
          size="md"
        >
          <form onSubmit={handleTransferSubmit} className="space-y-4">
            <Input
              label="Conta destino (UUID)"
              id="transferTarget"
              type="text"
              value={transferTarget}
              onChange={(e) => setTransferTarget(e.target.value)}
              placeholder="UUID da conta destinatária"
              required
              autoFocus
            />
            <Input
              label="Valor (R$)"
              id="transferAmount"
              type="number"
              step="0.01"
              min="0.01"
              value={transferAmount}
              onChange={(e) => setTransferAmount(e.target.value)}
              placeholder="Ex: 50.00"
              required
            />
            <div className="flex justify-end gap-3 pt-2">
              <Button variant="ghost" type="button" onClick={() => setIsTransferOpen(false)}>
                Cancelar
              </Button>
              <Button variant="primary" type="submit" loading={transferLoading}>
                {transferLoading ? 'Transferindo…' : 'Confirmar'}
              </Button>
            </div>
          </form>
        </Modal>
      </main>
    </div>
  )
}
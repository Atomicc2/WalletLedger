import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'
import { useToast } from '../context/ToastContext'

/**
 * Dashboard — tela principal logada.
 *
 * Novos recursos (Fase 4.4):
 * - **Modais** de Depositar / Transferir (estado `isDepositOpen` / `isTransferOpen`)
 * - **Formulários controlados** (`useState` por campo)
 * - **Toast** via `useToast()` — feedback visual não-bloqueante
 * - **Refetch de saldo** após sucesso (chama `fetchBalance()` novamente)
 * - **idempotencyKey** gerada no frontend (`crypto.randomUUID()`)
 * - Busca **conta do usuário** (`/api/accounts/me`) no mount para obter o UUID
 *   e usar em depósito (targetAccountId = própria conta) e transferência (sourceAccountId = própria conta)
 */
export default function Dashboard() {
  const [balance, setBalance] = useState<string>('—')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // UUID da conta do usuário autenticado (obtido via /api/accounts/me)
  const [myAccountId, setMyAccountId] = useState<string | null>(null)

  // Estados dos modais
  const [isDepositOpen, setIsDepositOpen] = useState(false)
  const [isTransferOpen, setIsTransferOpen] = useState(false)

  // Estados dos formulários
  const [depositAmount, setDepositAmount] = useState('')
  const [transferAmount, setTransferAmount] = useState('')
  const [transferTarget, setTransferTarget] = useState('')

  // Loading por ação (para desabilitar botões durante request)
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

  // Busca inicial: conta + saldo
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
        targetAccountId: myAccountId, // deposita na PRÓPRIA conta
        amount: parseFloat(depositAmount),
      })
      success('Depósito realizado com sucesso!')
      setIsDepositOpen(false)
      setDepositAmount('')
      fetchBalance() // atualiza saldo na tela
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
        sourceAccountId: myAccountId, // transfere DA PRÓPRIA conta
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
    <div style={containerStyle}>
      <header style={headerStyle}>
        <h1>💰 WalletLedger</h1>
        <nav style={navStyle}>
          <a href="/dashboard" style={linkStyle}>Dashboard</a>
          <a href="/extrato" style={linkStyle}>Extrato</a>
          <button onClick={handleLogout} style={logoutBtn}>Sair</button>
        </nav>
      </header>

      <main style={mainStyle}>
        <section style={cardStyle}>
          <h2 style={{ marginTop: 0 }}>Seu saldo</h2>
          <p style={balanceStyle}>{loading ? 'Carregando…' : balance}</p>
          {error && <p style={errorStyle}>{error}</p>}
        </section>

        <section style={cardStyle}>
          <h3>Ações rápidas</h3>
          <div style={actionsStyle}>
            <button onClick={() => setIsDepositOpen(true)} style={actionBtn} disabled={loading || !myAccountId}>💸 Depositar</button>
            <button onClick={() => setIsTransferOpen(true)} style={actionBtn} disabled={loading || !myAccountId}>🔄 Transferir</button>
          </div>
        </section>
      </main>

      {/* Modal Depositar */}
      {isDepositOpen && (
        <div style={modalOverlay} onClick={() => setIsDepositOpen(false)}>
          <div style={modalContent} onClick={(e) => e.stopPropagation()}>
            <h3>💸 Depositar</h3>
            <form onSubmit={handleDepositSubmit} style={formStyle}>
              <div style={fieldStyle}>
                <label htmlFor="depositAmount">Valor (R$)</label>
                <input
                  id="depositAmount"
                  type="number"
                  step="0.01"
                  min="0.01"
                  value={depositAmount}
                  onChange={(e) => setDepositAmount(e.target.value)}
                  placeholder="Ex: 100.00"
                  required
                  autoFocus
                  style={inputStyle}
                />
              </div>
              <div style={modalActions}>
                <button type="button" onClick={() => setIsDepositOpen(false)} style={cancelBtn}>Cancelar</button>
                <button type="submit" disabled={depositLoading} style={confirmBtn}>
                  {depositLoading ? 'Depositando…' : 'Confirmar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal Transferir */}
      {isTransferOpen && (
        <div style={modalOverlay} onClick={() => setIsTransferOpen(false)}>
          <div style={modalContent} onClick={(e) => e.stopPropagation()}>
            <h3>🔄 Transferir</h3>
            <form onSubmit={handleTransferSubmit} style={formStyle}>
              <div style={fieldStyle}>
                <label htmlFor="transferTarget">Conta destino (UUID)</label>
                <input
                  id="transferTarget"
                  type="text"
                  value={transferTarget}
                  onChange={(e) => setTransferTarget(e.target.value)}
                  placeholder="UUID da conta destinatária"
                  required
                  autoFocus
                  style={inputStyle}
                />
              </div>
              <div style={fieldStyle}>
                <label htmlFor="transferAmount">Valor (R$)</label>
                <input
                  id="transferAmount"
                  type="number"
                  step="0.01"
                  min="0.01"
                  value={transferAmount}
                  onChange={(e) => setTransferAmount(e.target.value)}
                  placeholder="Ex: 50.00"
                  required
                  style={inputStyle}
                />
              </div>
              <div style={modalActions}>
                <button type="button" onClick={() => setIsTransferOpen(false)} style={cancelBtn}>Cancelar</button>
                <button type="submit" disabled={transferLoading} style={confirmBtn}>
                  {transferLoading ? 'Transferindo…' : 'Confirmar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <p style={{ marginTop: '2rem', fontSize: '0.85rem', color: '#888', textAlign: 'center' }}>
        🚧 Placeholder da Fase 4.1 — integração real completa
      </p>
    </div>
  )
}

const containerStyle = { minHeight: '100vh', fontFamily: 'system-ui', background: '#f8fafc' } as const
const headerStyle = { background: '#fff', borderBottom: '1px solid #e5e7eb', padding: '1rem 2rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' } as const
const navStyle = { display: 'flex', gap: '1rem', alignItems: 'center' } as const
const linkStyle = { textDecoration: 'none', color: '#2563eb', fontWeight: 500 } as const
const logoutBtn = { padding: '0.5rem 1rem', background: '#ef4444', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer' } as const
const mainStyle = { maxWidth: '640px', margin: '2rem auto', padding: '0 1rem' } as const
const cardStyle = { background: '#fff', border: '1px solid #e5e7eb', borderRadius: '8px', padding: '1.5rem', marginBottom: '1.5rem', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' } as const
const balanceStyle = { fontSize: '2.5rem', fontWeight: 700, color: '#16a34a', margin: '0.5rem 0' } as const
const actionsStyle = { display: 'flex', gap: '1rem' } as const
const actionBtn = { flex: 1, padding: '1rem', background: '#f3f4f6', border: '1px solid #e5e7eb', borderRadius: '8px', fontSize: '1rem', cursor: 'pointer' } as const
const errorStyle = { color: '#991b1b', background: '#fef2f2', border: '1px solid #fecaca', padding: '0.75rem', borderRadius: '4px', marginTop: '1rem' } as const

// Estilos dos modais
const modalOverlay = { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem' } as const
const modalContent = { background: '#fff', borderRadius: '10px', padding: '1.5rem', width: '100%', maxWidth: '400px', boxShadow: '0 10px 40px rgba(0,0,0,0.2)' } as const
const formStyle = { display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1rem' } as const
const fieldStyle = { display: 'flex', flexDirection: 'column', gap: '0.35rem' } as const
const inputStyle = { padding: '0.6rem', border: '1px solid #ccc', borderRadius: '4px', fontSize: '1rem' } as const
const modalActions = { display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' } as const
const cancelBtn = { padding: '0.6rem 1rem', background: '#f3f4f6', color: '#374151', border: '1px solid #d1d5db', borderRadius: '6px', cursor: 'pointer' } as const
const confirmBtn = { padding: '0.6rem 1rem', background: '#2563eb', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 500 } as const
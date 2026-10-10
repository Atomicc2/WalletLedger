import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

/**
 * Dashboard — tela principal logada. Busca saldo real da API.
 *
 * Fluxo:
 * 1. Monta o componente → useEffect roda uma vez ([]).
 * 2. Chama GET /api/accounts/me/balance (interceptor injeta Bearer token).
 * 3. Sucesso → setBalance + setLoading(false).
 * 4. Erro 401 → interceptor já limpa token e redireciona para /login.
 *    Erro outro → mostra mensagem amigável.
 */
export default function Dashboard() {
  const [balance, setBalance] = useState<string>('—')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const navigate = useNavigate()

  useEffect(() => {
    let mounted = true

    const fetchBalance = async () => {
      try {
        const response = await api.get('/accounts/me/balance')
        // Backend devolve: { accountId, balance, currency }
        if (mounted) {
          setBalance(`${response.data.currency} ${response.data.balance.toLocaleString('pt-BR', { minimumFractionDigits: 2 })}`)
          setLoading(false)
        }
      } catch (err: unknown) {
        if (!mounted) return
        const axiosError = err as { response?: { status?: number; data?: { detail?: string } } }
        // 401 já tratado pelo interceptor (redireciona para /login)
        if (axiosError.response?.status !== 401) {
          setError(axiosError.response?.data?.detail ?? 'Erro ao carregar saldo.')
        }
        setLoading(false)
      }
    }

    fetchBalance()

    // Cleanup: evita setState se componente desmontar antes da resposta
    return () => { mounted = false }
  }, [])

  return (
    <div style={containerStyle}>
      <header style={headerStyle}>
        <h1>💰 WalletLedger</h1>
        <nav style={navStyle}>
          <a href="/dashboard" style={linkStyle}>Dashboard</a>
          <a href="/extrato" style={linkStyle}>Extrato</a>
          <button onClick={() => { localStorage.removeItem('walletledger_token'); navigate('/login') }} style={logoutBtn}>Sair</button>
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
            <button style={actionBtn} disabled={loading}>💸 Depositar</button>
            <button style={actionBtn} disabled={loading}>🔄 Transferir</button>
          </div>
        </section>
      </main>

      <p style={{ marginTop: '2rem', fontSize: '0.85rem', color: '#888', textAlign: 'center' }}>
        🚧 Placeholder da Fase 4.1 — integração real dos botões na Fase 4.4
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
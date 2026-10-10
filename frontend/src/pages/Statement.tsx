import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

/**
 * Extrato — lista de lançamentos reais da API.
 */
interface LedgerEntry {
  id: string
  type: 'DEBIT' | 'CREDIT'
  amount: number
  description: string
  createdAt: string
}

export default function Statement() {
  const [entries, setEntries] = useState<LedgerEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const navigate = useNavigate()

  useEffect(() => {
    let mounted = true

    const fetchStatement = async () => {
      try {
        const response = await api.get('/accounts/me/statement')
        // Backend devolve array de { id, accountId, entryType, amount, createdAt }
        if (mounted) {
          setEntries(response.data.map((e: any) => ({
            id: e.id,
            type: e.entryType,
            amount: e.amount,
            description: e.entryType === 'CREDIT' ? 'Crédito recebido' : 'Débito realizado',
            createdAt: e.createdAt,
          })))
          setLoading(false)
        }
      } catch (err: unknown) {
        if (!mounted) return
        const axiosError = err as { response?: { status?: number; data?: { detail?: string } } }
        if (axiosError.response?.status !== 401) {
          setError(axiosError.response?.data?.detail ?? 'Erro ao carregar extrato.')
        }
        setLoading(false)
      }
    }

    fetchStatement()
    return () => { mounted = false }
  }, [])

  const formatMoney = (v: number) => v.toLocaleString('pt-BR', { minimumFractionDigits: 2 })

  return (
    <div style={containerStyle}>
      <header style={headerStyle}>
        <h1>💰 WalletLedger</h1>
        <nav style={navStyle}>
          <a href="/dashboard" style={linkStyle}>Dashboard</a>
          <a href="/extrato" style={{ ...linkStyle, fontWeight: 'bold' }}>Extrato</a>
          <button onClick={() => { localStorage.removeItem('walletledger_token'); navigate('/login') }} style={logoutBtn}>Sair</button>
        </nav>
      </header>

      <main style={mainStyle}>
        <section style={cardStyle}>
          <h2 style={{ marginTop: 0, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            Extrato da conta
            <span style={{ fontSize: '0.85rem', fontWeight: 400, color: '#666' }}>{entries.length} lançamento(s)</span>
          </h2>

          {loading ? (
            <p style={{ textAlign: 'center', color: '#666', padding: '2rem' }}>Carregando…</p>
          ) : error ? (
            <p style={errorStyle}>{error}</p>
          ) : entries.length === 0 ? (
            <p style={{ textAlign: 'center', color: '#999', padding: '2rem' }}>Nenhum lançamento ainda.</p>
          ) : (
            <table style={tableStyle}>
              <thead>
                <tr>
                  <th>Data</th>
                  <th>Tipo</th>
                  <th>Descrição</th>
                  <th style={moneyTh}>Valor</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((e) => (
                  <tr key={e.id}>
                    <td>{new Date(e.createdAt).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' })}</td>
                    <td>
                      <span style={{ ...badgeStyle, background: e.type === 'CREDIT' ? '#dcfce7' : '#fee2e2', color: e.type === 'CREDIT' ? '#166534' : '#991b1b' }}>
                        {e.type === 'CREDIT' ? '➕ Crédito' : '➖ Débito'}
                      </span>
                    </td>
                    <td>{e.description}</td>
                    <td style={moneyTd}>
                      {e.type === 'CREDIT' ? '+' : '−'} R$ {formatMoney(e.amount)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </main>

      <p style={{ marginTop: '2rem', fontSize: '0.85rem', color: '#888', textAlign: 'center' }}>
        🚧 Placeholder da Fase 4.1 — integração real completa na Fase 4.4
      </p>
    </div>
  )
}

const containerStyle = { minHeight: '100vh', fontFamily: 'system-ui', background: '#f8fafc' } as const
const headerStyle = { background: '#fff', borderBottom: '1px solid #e5e7eb', padding: '1rem 2rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' } as const
const navStyle = { display: 'flex', gap: '1rem', alignItems: 'center' } as const
const linkStyle = { textDecoration: 'none', color: '#2563eb', fontWeight: 500 } as const
const logoutBtn = { padding: '0.5rem 1rem', background: '#ef4444', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer' } as const
const mainStyle = { maxWidth: '800px', margin: '2rem auto', padding: '0 1rem' } as const
const cardStyle = { background: '#fff', border: '1px solid #e5e7eb', borderRadius: '8px', padding: '1.5rem', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' } as const
const tableStyle = { width: '100%', borderCollapse: 'collapse' } as const
const moneyTh = { textAlign: 'right', padding: '0.75rem', background: '#f9fafb', borderBottom: '1px solid #e5e7eb' } as const
const moneyTd = { textAlign: 'right', padding: '0.75rem', fontWeight: 600, fontFamily: 'monospace' } as const
const badgeStyle = { padding: '0.25rem 0.5rem', borderRadius: '9999px', fontSize: '0.75rem', fontWeight: 600 } as const
const errorStyle = { textAlign: 'center', padding: '2rem', color: '#991b1b', background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '8px' } as const
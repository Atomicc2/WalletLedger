import { useState, useEffect } from 'react'

/**
 * Extrato — lista de lançamentos (LedgerEntry).
 * Aqui usamos **estado para lista** (`LedgerEntry[]`) e `useEffect` para buscar.
 * O TypeScript força a gente a tipar o array: `LedgerEntry[]`.
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

  useEffect(() => {
    const fetchStatement = async () => {
      // const resp = await api.get('/accounts/me/statement') // Fase 4.3
      // setEntries(resp.data)
      setEntries([
        { id: '1', type: 'CREDIT', amount: 500, description: 'Depósito inicial', createdAt: '2026-10-08T10:30:00Z' },
        { id: '2', type: 'DEBIT', amount: 50, description: 'Transferência para João', createdAt: '2026-10-08T14:15:00Z' },
        { id: '3', type: 'CREDIT', amount: 200, description: 'Depósito via PIX', createdAt: '2026-10-09T09:00:00Z' },
      ])
      setLoading(false)
    }
    fetchStatement()
  }, [])

  const formatMoney = (v: number) => `R$ ${v.toFixed(2).replace('.', ',')}`

  return (
    <div style={containerStyle}>
      <header style={headerStyle}>
        <h1>💰 WalletLedger</h1>
        <nav style={navStyle}>
          <a href="/dashboard" style={linkStyle}>Dashboard</a>
          <a href="/extrato" style={{ ...linkStyle, fontWeight: 'bold' }}>Extrato</a>
          <button onClick={() => console.log('Logout simulado')} style={logoutBtn}>Sair</button>
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
                    <td>{new Date(e.createdAt).toLocaleDateString('pt-BR')}</td>
                    <td>
                      <span style={{ ...badgeStyle, background: e.type === 'CREDIT' ? '#dcfce7' : '#fee2e2', color: e.type === 'CREDIT' ? '#166534' : '#991b1b' }}>
                        {e.type === 'CREDIT' ? '➕ Crédito' : '➖ Débito'}
                      </span>
                    </td>
                    <td>{e.description}</td>
                    <td style={moneyTd}>
                      {e.type === 'CREDIT' ? '+' : '−'} {formatMoney(e.amount)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </main>

      <p style={{ marginTop: '2rem', fontSize: '0.85rem', color: '#888', textAlign: 'center' }}>
        🚧 Placeholder da Fase 4.1 — integração real na Fase 4.3
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
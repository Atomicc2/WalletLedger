import { useState, useEffect } from 'react'

/**
 * Dashboard — tela principal logada.
 * Aqui introduzimos **useEffect**: hook para **efeitos colaterais**
 * (buscar dados da API, assinar eventos, timers).
 *
 * Sintaxe: useEffect(() => { ... }, [dependências])
 * - O array vazio [] = "roda **uma vez** ao montar o componente" (≈ @PostConstruct).
 * - Se tivesse [token] = "roda sempre que 'token' mudar".
 */
export default function Dashboard() {
  const [balance, setBalance] = useState<string>('—')
  const [loading, setLoading] = useState(true)

  // Simula a busca de saldo (Fase 4.3 fará o fetch real com token)
  useEffect(() => {
    const fetchBalance = async () => {
      // const resp = await api.get('/accounts/me/balance')  // Fase 4.3
      // setBalance(resp.data.balance)
      setBalance('R$ 1.234,56') // mock
      setLoading(false)
    }
    fetchBalance()
  }, []) // [] = roda só no mount

  return (
    <div style={containerStyle}>
      <header style={headerStyle}>
        <h1>💰 WalletLedger</h1>
        <nav style={navStyle}>
          <a href="/dashboard" style={linkStyle}>Dashboard</a>
          <a href="/extrato" style={linkStyle}>Extrato</a>
          <button onClick={() => console.log('Logout simulado')} style={logoutBtn}>Sair</button>
        </nav>
      </header>

      <main style={mainStyle}>
        <section style={cardStyle}>
          <h2 style={{ marginTop: 0 }}>Seu saldo</h2>
          <p style={balanceStyle}>{loading ? 'Carregando…' : balance}</p>
        </section>

        <section style={cardStyle}>
          <h3>Ações rápidas</h3>
          <div style={actionsStyle}>
            <button style={actionBtn}>💸 Depositar</button>
            <button style={actionBtn}>🔄 Transferir</button>
          </div>
        </section>
      </main>

      <p style={{ marginTop: '2rem', fontSize: '0.85rem', color: '#888', textAlign: 'center' }}>
        🚧 Placeholder da Fase 4.1 — integração real com API na Fase 4.3
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
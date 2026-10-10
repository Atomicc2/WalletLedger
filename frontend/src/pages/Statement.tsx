import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'

/**
 * Extrato — lista paginada de lançamentos com filtros.
 *
 * Novos recursos (Fase 4.6):
 * - **Paginação**: página atual, tamanho, total de páginas/elementos.
 * - **Filtros**: tipo (DÉBITO/CRÉDITO/TODOS), período (data início/fim).
 * - **Ordenação**: createdAt desc (mais recentes primeiro).
 * - **Formatação completa**: data + hora no padrão pt-BR.
 */
interface LedgerEntry {
  id: string
  type: 'DEBIT' | 'CREDIT'
  amount: number
  description: string
  createdAt: string
}

interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

type FilterType = 'ALL' | 'DEBIT' | 'CREDIT'

export default function Statement() {
  const [entries, setEntries] = useState<LedgerEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Paginação
  const [page, setPage] = useState(0)
  const [size] = useState(10)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  // Filtros
  const [filterType, setFilterType] = useState<FilterType>('ALL')
  const [startDate, setStartDate] = useState<string>('') // ISO YYYY-MM-DD
  const [endDate, setEndDate] = useState<string>('')

  const navigate = useNavigate()

  const fetchStatement = useCallback(async () => {
    setLoading(true)
    setError(null)

    try {
      const params = new URLSearchParams()
      params.set('page', String(page))
      params.set('size', String(size))
      params.set('sort', 'createdAt,desc')

      if (filterType !== 'ALL') {
        params.set('type', filterType)
      }
      if (startDate) {
        params.set('startDate', startDate)
      }
      if (endDate) {
        params.set('endDate', endDate)
      }

      const response = await api.get<PageResponse<LedgerEntry>>(`/accounts/me/statement?${params.toString()}`)

      setEntries(response.data.content)
      setTotalPages(response.data.totalPages)
      setTotalElements(response.data.totalElements)
      setError(null)
    } catch (err: unknown) {
      const axiosError = err as { response?: { status?: number; data?: { detail?: string } } }
      if (axiosError.response?.status !== 401) {
        setError(axiosError.response?.data?.detail ?? 'Erro ao carregar extrato.')
      }
    } finally {
      setLoading(false)
    }
  }, [page, size, filterType, startDate, endDate])

  // Busca quando filtros/paginação mudam
  useEffect(() => {
    fetchStatement()
  }, [fetchStatement])

  const handlePageChange = (newPage: number) => {
    if (newPage >= 0 && newPage < totalPages) {
      setPage(newPage)
    }
  }

  const handleFilterChange = () => {
    setPage(0) // volta para primeira página ao filtrar
  }

  const formatMoney = (v: number) => v.toLocaleString('pt-BR', { minimumFractionDigits: 2 })

  const formatDateTime = (iso: string) =>
    new Date(iso).toLocaleString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })

  const clearFilters = () => {
    setFilterType('ALL')
    setStartDate('')
    setEndDate('')
    setPage(0)
  }

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
            <span style={{ fontSize: '0.85rem', fontWeight: 400, color: '#666' }}>
              {totalElements} lançamento(s)
            </span>
          </h2>

          {/* Filtros */}
          <div style={filtersStyle}>
            <div style={filterGroupStyle}>
              <label htmlFor="filterType" style={labelStyle}>Tipo</label>
              <select
                id="filterType"
                value={filterType}
                onChange={(e) => { setFilterType(e.target.value as FilterType); handleFilterChange() }}
                style={selectStyle}
              >
                <option value="ALL">Todos</option>
                <option value="CREDIT">Créditos</option>
                <option value="DEBIT">Débitos</option>
              </select>
            </div>

            <div style={filterGroupStyle}>
              <label htmlFor="startDate" style={labelStyle}>De</label>
              <input
                id="startDate"
                type="date"
                value={startDate}
                onChange={(e) => { setStartDate(e.target.value); handleFilterChange() }}
                style={inputStyle}
              />
            </div>

            <div style={filterGroupStyle}>
              <label htmlFor="endDate" style={labelStyle}>Até</label>
              <input
                id="endDate"
                type="date"
                value={endDate}
                onChange={(e) => { setEndDate(e.target.value); handleFilterChange() }}
                style={inputStyle}
              />
            </div>

            {(filterType !== 'ALL' || startDate || endDate) && (
              <button onClick={clearFilters} style={clearBtn}>Limpar filtros</button>
            )}
          </div>

          {/* Tabela / Estados */}
          {loading ? (
            <p style={{ textAlign: 'center', color: '#666', padding: '2rem' }}>Carregando…</p>
          ) : error ? (
            <p style={errorStyle}>{error}</p>
          ) : entries.length === 0 ? (
            <p style={{ textAlign: 'center', color: '#999', padding: '2rem' }}>
              Nenhum lançamento encontrado.
            </p>
          ) : (
            <>
              <table style={tableStyle}>
                <thead>
                  <tr>
                    <th>Data/Hora</th>
                    <th>Tipo</th>
                    <th>Descrição</th>
                    <th style={moneyTh}>Valor</th>
                  </tr>
                </thead>
                <tbody>
                  {entries.map((e) => (
                    <tr key={e.id}>
                      <td>{formatDateTime(e.createdAt)}</td>
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

              {/* Paginação */}
              <div style={paginationStyle}>
                <button
                  onClick={() => handlePageChange(page - 1)}
                  disabled={page === 0}
                  style={{ ...pageBtn, opacity: page === 0 ? 0.5 : 1, cursor: page === 0 ? 'not-allowed' : 'pointer' }}
                >
                  ← Anterior
                </button>
                <span style={pageInfoStyle}>
                  Página {page + 1} de {totalPages} ({totalElements} total)
                </span>
                <button
                  onClick={() => handlePageChange(page + 1)}
                  disabled={page === totalPages - 1}
                  style={{ ...pageBtn, opacity: page === totalPages - 1 ? 0.5 : 1, cursor: page === totalPages - 1 ? 'not-allowed' : 'pointer' }}
                >
                  Próxima →
                </button>
              </div>
            </>
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
const mainStyle = { maxWidth: '900px', margin: '2rem auto', padding: '0 1rem' } as const
const cardStyle = { background: '#fff', border: '1px solid #e5e7eb', borderRadius: '8px', padding: '1.5rem', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' } as const
const tableStyle = { width: '100%', borderCollapse: 'collapse' } as const
const moneyTh = { textAlign: 'right', padding: '0.75rem', background: '#f9fafb', borderBottom: '1px solid #e5e7eb' } as const
const moneyTd = { textAlign: 'right', padding: '0.75rem', fontWeight: 600, fontFamily: 'monospace' } as const
const badgeStyle = { padding: '0.25rem 0.5rem', borderRadius: '9999px', fontSize: '0.75rem', fontWeight: 600 } as const
const errorStyle = { textAlign: 'center', padding: '2rem', color: '#991b1b', background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '8px' } as const

// Estilos dos filtros
const filtersStyle = { display: 'flex', flexWrap: 'wrap', gap: '1rem', alignItems: 'flex-end', marginBottom: '1.5rem', paddingBottom: '1rem', borderBottom: '1px solid #e5e7eb' } as const
const filterGroupStyle = { display: 'flex', flexDirection: 'column', gap: '0.35rem', minWidth: '140px' } as const
const labelStyle = { fontSize: '0.8rem', fontWeight: 600, color: '#374151' } as const
const selectStyle = { padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', fontSize: '0.9rem', background: '#fff' } as const
const inputStyle = { padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', fontSize: '0.9rem' } as const
const clearBtn = { padding: '0.5rem 0.75rem', background: '#f3f4f6', color: '#374151', border: '1px solid #d1d5db', borderRadius: '4px', cursor: 'pointer', fontSize: '0.85rem', height: 'fit-content' } as const

// Estilos da paginação
const paginationStyle = { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '1.5rem', paddingTop: '1rem', borderTop: '1px solid #e5e7eb' } as const
const pageBtn = { padding: '0.5rem 1rem', background: '#2563eb', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '0.9rem' } as const
const pageInfoStyle = { color: '#6b7280', fontSize: '0.9rem', whiteSpace: 'nowrap' } as const
import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'
import { Button, Input, Select, Card, Badge } from '../components/ui'

/**
 * Extrato — lista paginada com filtros, usando componentes UI.
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
  const [startDate, setStartDate] = useState<string>('')
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

  useEffect(() => {
    fetchStatement()
  }, [fetchStatement])

  const handlePageChange = (newPage: number) => {
    if (newPage >= 0 && newPage < totalPages) {
      setPage(newPage)
    }
  }

  const handleFilterChange = () => {
    setPage(0)
  }

  const clearFilters = () => {
    setFilterType('ALL')
    setStartDate('')
    setEndDate('')
    setPage(0)
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

  const getBadgeVariant = (type: 'DEBIT' | 'CREDIT') =>
    type === 'CREDIT' ? 'success' : 'danger'

  const getBadgeLabel = (type: 'DEBIT' | 'CREDIT') =>
    type === 'CREDIT' ? '➕ Crédito' : '➖ Débito'

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <h1 className="text-xl font-bold text-gray-900">💰 WalletLedger</h1>
            <nav className="flex items-center gap-4">
              <a href="/dashboard" className="text-blue-600 hover:text-blue-700 font-medium">Dashboard</a>
              <a href="/extrato" className="text-blue-600 hover:text-blue-700 font-medium font-bold">Extrato</a>
              <Button variant="danger" size="sm" onClick={() => { localStorage.removeItem('walletledger_token'); navigate('/login') }}>
                Sair
              </Button>
            </nav>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
        <Card padding="lg">
          {/* Cabeçalho com contador */}
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-bold text-gray-900">Extrato da conta</h2>
            <span className="text-gray-500 text-sm">{totalElements} lançamento(s)</span>
          </div>

          {/* Filtros */}
          <div className="flex flex-wrap gap-4 items-end mb-6 pb-4 border-b border-gray-200">
            <div className="flex flex-col gap-1 min-w-[140px]">
              <label className="text-xs font-medium text-gray-700">Tipo</label>
              <Select
                id="filterType"
                value={filterType}
                onChange={(e) => { setFilterType(e.target.value as FilterType); handleFilterChange() }}
                options={[
                  { value: 'ALL', label: 'Todos' },
                  { value: 'CREDIT', label: 'Créditos' },
                  { value: 'DEBIT', label: 'Débitos' },
                ]}
              />
            </div>

            <div className="flex flex-col gap-1 min-w-[140px]">
              <label className="text-xs font-medium text-gray-700">De</label>
              <Input
                id="startDate"
                type="date"
                value={startDate}
                onChange={(e) => { setStartDate(e.target.value); handleFilterChange() }}
              />
            </div>

            <div className="flex flex-col gap-1 min-w-[140px]">
              <label className="text-xs font-medium text-gray-700">Até</label>
              <Input
                id="endDate"
                type="date"
                value={endDate}
                onChange={(e) => { setEndDate(e.target.value); handleFilterChange() }}
              />
            </div>

            {(filterType !== 'ALL' || startDate || endDate) && (
              <Button variant="ghost" size="sm" onClick={clearFilters} className="h-fit">
                Limpar filtros
              </Button>
            )}
          </div>

          {/* Tabela / Estados */}
          {loading ? (
            <div className="text-center py-8 text-gray-500">Carregando…</div>
          ) : error ? (
            <div className="text-center py-8 text-red-600 bg-red-50 border border-red-200 rounded-lg">{error}</div>
          ) : entries.length === 0 ? (
            <div className="text-center py-8 text-gray-400">Nenhum lançamento encontrado.</div>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="text-left text-sm text-gray-500 border-b border-gray-200">
                      <th className="pb-3 font-medium">Data/Hora</th>
                      <th className="pb-3 font-medium">Tipo</th>
                      <th className="pb-3 font-medium">Descrição</th>
                      <th className="pb-3 font-medium text-right">Valor</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {entries.map((e) => (
                      <tr key={e.id} className="hover:bg-gray-50">
                        <td className="py-3 text-sm text-gray-900">{formatDateTime(e.createdAt)}</td>
                        <td className="py-3">
                          <Badge variant={getBadgeVariant(e.type)}>{getBadgeLabel(e.type)}</Badge>
                        </td>
                        <td className="py-3 text-sm text-gray-700">{e.description}</td>
                        <td className="py-3 text-sm font-mono font-medium text-right">
                          <span className={e.type === 'CREDIT' ? 'text-green-600' : 'text-red-600'}>
                            {e.type === 'CREDIT' ? '+' : '−'} R$ {formatMoney(e.amount)}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Paginação */}
              <div className="flex items-center justify-between mt-6 pt-4 border-t border-gray-200">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => handlePageChange(page - 1)}
                  disabled={page === 0}
                >
                  ← Anterior
                </Button>
                <span className="text-sm text-gray-500">
                  Página {page + 1} de {totalPages} ({totalElements} total)
                </span>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => handlePageChange(page + 1)}
                  disabled={page === totalPages - 1}
                >
                  Próxima →
                </Button>
              </div>
            </>
          )}
        </Card>
      </main>
    </div>
  )
}
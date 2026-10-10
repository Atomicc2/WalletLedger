import { createContext, useContext, useState, type ReactNode } from 'react'

/**
 * Toast Context — notificações não-bloqueantes (sucesso/erro) que aparecem no canto
 * e somem sozinhas após alguns segundos.
 *
 * Conceitos:
 * - **Context API**: forma de passar dados para componentes filhos **sem props** (evita "prop drilling").
 * - **Provider**: componente que envolve a árvore e expõe o valor do context.
 * - **useContext**: hook para consumir o context em qualquer descendente.
 */

interface Toast {
  id: number
  type: 'success' | 'error'
  message: string
}

interface ToastContextType {
  toasts: Toast[]
  success: (msg: string) => void
  error: (msg: string) => void
  remove: (id: number) => void
}

const ToastContext = createContext<ToastContextType | undefined>(undefined)

/** Hook de conveniência — lança erro se usado fora do Provider. */
export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast deve ser usado dentro de ToastProvider')
  return ctx
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  let nextId = 0

  const addToast = (type: 'success' | 'error', message: string) => {
    const id = ++nextId
    setToasts((prev) => [...prev, { id, type, message }])
    // Auto-remove após 4 segundos
    setTimeout(() => removeToast(id), 4000)
  }

  const removeToast = (id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }

  return (
    <ToastContext.Provider value={{ toasts, success: (m) => addToast('success', m), error: (m) => addToast('error', m), remove: removeToast }}>
      {children}
      {/* Container fixo no canto superior direito */}
      <div style={containerStyle}>
        {toasts.map((t) => (
          <div key={t.id} style={{ ...toastStyle, ...(t.type === 'success' ? successStyle : errorStyle) }}>
            <span>{t.message}</span>
            <button onClick={() => removeToast(t.id)} style={closeBtn}>×</button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

const containerStyle = { position: 'fixed', top: '1rem', right: '1rem', zIndex: 9999, display: 'flex', flexDirection: 'column', gap: '0.5rem' } as const
const toastStyle = { padding: '0.85rem 1rem', borderRadius: '6px', boxShadow: '0 4px 12px rgba(0,0,0,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '1rem', minWidth: '280px', maxWidth: '400px', fontSize: '0.9rem', animation: 'slideIn 0.3s ease' } as const
const successStyle = { background: '#dcfce7', color: '#166534', border: '1px solid #86efac' } as const
const errorStyle = { background: '#fef2f2', color: '#991b1b', border: '1px solid #fecaca' } as const
const closeBtn = { background: 'transparent', border: 'none', fontSize: '1.2rem', cursor: 'pointer', lineHeight: 1, padding: 0 } as const
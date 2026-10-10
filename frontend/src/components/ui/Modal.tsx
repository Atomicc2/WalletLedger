import { type ReactNode } from 'react'

/**
 * Modal — diálogo modal acessível com overlay.
 *
 * Props:
 * - isOpen: boolean (controla visibilidade)
 * - onClose: () => void (chamado ao clicar no overlay ou fechar)
 * - title: string (título do modal)
 * - children: ReactNode (conteúdo do corpo)
 * - size: 'sm' | 'md' | 'lg' | 'xl' (largura máxima)
 * - showCloseButton: boolean (mostra X no canto)
 */
interface ModalProps {
  isOpen: boolean
  onClose: () => void
  title: string
  children: ReactNode
  size?: 'sm' | 'md' | 'lg' | 'xl'
  showCloseButton?: boolean
}

const sizeStyles = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-xl',
}

export default function Modal({ isOpen, onClose, title, children, size = 'md', showCloseButton = true }: ModalProps) {
  if (!isOpen) return null

  return (
    <div
      className="fixed inset-0 z-50 overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
    >
      {/* Overlay — clica para fechar */}
      <div
        className="fixed inset-0 bg-black/50 transition-opacity"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Conteúdo centralizado */}
      <div className="flex min-h-full items-center justify-center p-4">
        <div
          className={`w-full ${sizeStyles[size]} bg-white rounded-xl shadow-xl transform transition-all`}
        >
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
            <h2 id="modal-title" className="text-lg font-semibold text-gray-900">
              {title}
            </h2>
            {showCloseButton && (
              <button
                type="button"
                onClick={onClose}
                className="p-1 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
                aria-label="Fechar modal"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            )}
          </div>

          {/* Body */}
          <div className="p-6">{children}</div>
        </div>
      </div>
    </div>
  )
}
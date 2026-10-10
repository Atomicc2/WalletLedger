/**
 * Badge — etiqueta colorida para status/tipos.
 *
 * Props:
 * - variant: 'success' | 'danger' | 'warning' | 'info' | 'neutral'
 * - children: conteúdo (texto ou ícone + texto)
 * - className: classes extras
 */
interface BadgeProps {
  variant?: 'success' | 'danger' | 'warning' | 'info' | 'neutral'
  children: React.ReactNode
  className?: string
}

const variantStyles = {
  success: 'bg-green-100 text-green-800',
  danger: 'bg-red-100 text-red-800',
  warning: 'bg-yellow-100 text-yellow-800',
  info: 'bg-blue-100 text-blue-800',
  neutral: 'bg-gray-100 text-gray-800',
}

export default function Badge({ variant = 'neutral', children, className = '' }: BadgeProps) {
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${variantStyles[variant]} ${className}`}>
      {children}
    </span>
  )
}
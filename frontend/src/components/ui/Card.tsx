import { type ReactNode } from 'react'

/**
 * Card — container com borda, sombra e padding padrão.
 *
 * Props:
 * - children: conteúdo
 * - className: classes extras
 * - padding: 'none' | 'sm' | 'md' | 'lg' (padding interno)
 * - hover: boolean (eleva sombra ao passar mouse)
 */
interface CardProps {
  children: ReactNode
  className?: string
  padding?: 'none' | 'sm' | 'md' | 'lg'
  hover?: boolean
}

const paddingStyles = {
  none: '',
  sm: 'p-4',
  md: 'p-6',
  lg: 'p-8',
}

export default function Card({ children, className = '', padding = 'md', hover = false }: CardProps) {
  return (
    <div
      className={`
        bg-white rounded-xl border border-gray-200 shadow-sm
        ${paddingStyles[padding]}
        ${hover ? 'hover:shadow-md transition-shadow duration-200' : ''}
        ${className}
      `}
    >
      {children}
    </div>
  )
}
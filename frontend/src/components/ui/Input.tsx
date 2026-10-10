import { type InputHTMLAttributes, forwardRef } from 'react'

/**
 * Input — campo de texto reutilizável com label, erro e hint.
 *
 * Props:
 * - label: string (renderiza <label> associado via htmlFor)
 * - error: string (mostra mensagem de erro em vermelho)
 * - hint: string (texto de ajuda abaixo do campo)
 * - ...rest: demais props nativas do <input>
 */
interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
  hint?: string
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, hint, id, className = '', ...props }, ref) => {
    const inputId = id || label?.toLowerCase().replace(/\s+/g, '-')

    return (
      <div className="w-full">
        {label && (
          <label htmlFor={inputId} className="block text-sm font-medium text-gray-700 mb-1">
            {label}
          </label>
        )}
        <input
          ref={ref}
          id={inputId}
          className={`
            w-full px-3 py-2 rounded-lg border transition-colors
            bg-white text-gray-900 placeholder:text-gray-400
            focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
            disabled:bg-gray-50 disabled:text-gray-500 disabled:cursor-not-allowed
            ${error ? 'border-red-300 focus:ring-red-500' : 'border-gray-300 hover:border-gray-400'}
            ${className}
          `}
          aria-invalid={error ? 'true' : 'false'}
          aria-describedby={error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined}
          {...props}
        />
        {error && (
          <p id={`${inputId}-error`} className="mt-1 text-sm text-red-600" role="alert">{error}</p>
        )}
        {hint && !error && (
          <p id={`${inputId}-hint`} className="mt-1 text-sm text-gray-500">{hint}</p>
        )}
      </div>
    )
  }
)

Input.displayName = 'Input'
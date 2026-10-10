import { useState } from 'react'

/**
 * Componente = função que devolve o que aparece na tela (JSX).
 * 'useState' é um **hook** (gancho) que guarda **estado** — dados que mudam
 * e fazem o React re-renderizar (desenhar de novo) automaticamente.
 *
 * Aqui: `email` e `password` são o que o usuário digita nos inputs.
 * `setEmail`/`setPassword` são as funções para atualizar esses valores.
 */
export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  /** Handler simples: impede o <form> de recarregar a página (padrão do HTML). */
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    console.log('Login simulado:', { email, password })
    // Fase 4.2: chamar API de login, guardar token, redirecionar para /
  }

  return (
    <div style={containerStyle}>
      <h2>🔐 Login</h2>
      <form onSubmit={handleSubmit} style={formStyle}>
        <div style={fieldStyle}>
          <label htmlFor="email">E-mail</label>
          <input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="seu@email.com"
            required
            style={inputStyle}
          />
        </div>
        <div style={fieldStyle}>
          <label htmlFor="password">Senha</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            required
            style={inputStyle}
          />
        </div>
        <button type="submit" style={buttonStyle}>Entrar</button>
      </form>
      <p style={{ marginTop: '1rem', color: '#666' }}>
        Ainda não tem conta? <a href="/cadastro">Cadastre-se</a>
      </p>
      <p style={{ marginTop: '1rem', fontSize: '0.85rem', color: '#888' }}>
        🚧 Placeholder da Fase 4.1 — integração real com JWT na Fase 4.2
      </p>
    </div>
  )
}

/** Estilos inline simples (sem CSS externo por enquanto). */
const containerStyle = { maxWidth: '360px', margin: '3rem auto', padding: '1.5rem', border: '1px solid #ddd', borderRadius: '8px', fontFamily: 'system-ui' } as const
const formStyle = { display: 'flex', flexDirection: 'column', gap: '1rem' } as const
const fieldStyle = { display: 'flex', flexDirection: 'column', gap: '0.35rem' } as const
const inputStyle = { padding: '0.6rem', border: '1px solid #ccc', borderRadius: '4px', fontSize: '1rem' } as const
const buttonStyle = { padding: '0.7rem', background: '#2563eb', color: '#fff', border: 'none', borderRadius: '4px', fontSize: '1rem', cursor: 'pointer' } as const
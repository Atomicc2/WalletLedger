import { useState } from 'react'

/**
 * Tela de cadastro — mesma ideia do Login: estado para cada campo,
 * handler de submit que impede recarregamento.
 */
export default function Register() {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    console.log('Cadastro simulado:', { name, email, password })
    // Fase 4.2: POST /api/users → sucesso → redireciona para /login
  }

  return (
    <div style={containerStyle}>
      <h2>📝 Cadastro</h2>
      <form onSubmit={handleSubmit} style={formStyle}>
        <div style={fieldStyle}>
          <label htmlFor="name">Nome</label>
          <input
            id="name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Seu nome"
            required
            style={inputStyle}
          />
        </div>
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
            minLength={6}
            style={inputStyle}
          />
        </div>
        <button type="submit" style={buttonStyle}>Criar conta</button>
      </form>
      <p style={{ marginTop: '1rem', color: '#666' }}>
        Já tem conta? <a href="/login">Entre</a>
      </p>
      <p style={{ marginTop: '1rem', fontSize: '0.85rem', color: '#888' }}>
        🚧 Placeholder da Fase 4.1 — integração real na Fase 4.2
      </p>
    </div>
  )
}

const containerStyle = { maxWidth: '360px', margin: '3rem auto', padding: '1.5rem', border: '1px solid #ddd', borderRadius: '8px', fontFamily: 'system-ui' } as const
const formStyle = { display: 'flex', flexDirection: 'column', gap: '1rem' } as const
const fieldStyle = { display: 'flex', flexDirection: 'column', gap: '0.35rem' } as const
const inputStyle = { padding: '0.6rem', border: '1px solid #ccc', borderRadius: '4px', fontSize: '1rem' } as const
const buttonStyle = { padding: '0.7rem', background: '#16a34a', color: '#fff', border: 'none', borderRadius: '4px', fontSize: '1rem', cursor: 'pointer' } as const
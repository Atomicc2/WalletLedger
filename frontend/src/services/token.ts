/**
 * Helpers para guardar/ler/limpar o token JWT no **localStorage**.
 *
 * Por que localStorage?
 * - Persiste entre recarregamentos da página e fechamento do navegador.
 * - API síncrona e simples (getItem/setItem/removeItem).
 * - O token JWT de acesso (expiração 24h) é adequado para localStorage.
 *   Refresh tokens longos NÃO devem ficar aqui (usar httpOnly cookie no futuro).
 */

const TOKEN_KEY = 'walletledger_token'

/** Recupera o token (ou null se não existir). */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/** Salva o token recebido do backend. */
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

/** Remove o token (logout). */
export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** Verifica se há token salvo (útil para guards de rota). */
export function hasToken(): boolean {
  return getToken() !== null
}
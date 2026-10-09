-- V5: destino do depósito (Fase 3.1)
--
-- Enquanto uma transação está PENDING não existem ledger_entries (o dinheiro
-- ainda não moveu), então nada no banco liga essa transação a uma conta.
-- A própria transação passa a registrar PARA QUAL conta o depósito será
-- creditado quando for liquidado (webhook do provedor ou worker da fila).
--
-- Nullable: transferências e estornos continuam usando apenas as ledger_entries
-- (eles nunca ficam PENDING), então só os depósitos preenchem esta coluna.
ALTER TABLE transactions
    ADD COLUMN target_account_id UUID REFERENCES accounts(id) ON DELETE RESTRICT;
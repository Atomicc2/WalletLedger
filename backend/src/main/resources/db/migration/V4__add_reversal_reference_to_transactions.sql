-- V4: Estornos de transações (Fase 3.2)
--
-- Adiciona uma referência opcional à transação ORIGINAL que foi estornada.
-- O estorno NÃO apaga os lançamentos originais (auditoria): ele cria uma NOVA
-- transação COMPLETED com lançamentos compensatórios INVERTIDOS, e esta coluna
-- mantém o vínculo de auditoria entre o estorno e a transação original.
ALTER TABLE transactions
    ADD COLUMN reversal_of_id UUID REFERENCES transactions(id) ON DELETE RESTRICT;
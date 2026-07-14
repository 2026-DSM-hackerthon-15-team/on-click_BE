DROP INDEX IF EXISTS idx_sale_transactions_store_sold_at;

CREATE INDEX idx_sale_transactions_store_sold_at
    ON sale_transactions (store_id, sold_at, id);

CREATE INDEX idx_sale_transactions_store_created_at_id
    ON sale_transactions (store_id, created_at, id);

CREATE INDEX idx_sale_transactions_store_status_id
    ON sale_transactions (store_id, status, id);

CREATE INDEX idx_sale_transactions_store_transaction_id
    ON sale_transactions (store_id, id);

-- Inventory owns this table. Nothing here refers to another context's tables.
CREATE TABLE stock_items (
    sku VARCHAR(64) PRIMARY KEY,
    on_hand INTEGER NOT NULL,
    CONSTRAINT ck_stock_items_on_hand CHECK (on_hand >= 0)
);

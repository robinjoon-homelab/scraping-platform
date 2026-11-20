-- Owned by the ordering context. sku is the reference the customer asked for, not a key into inventory:
-- the contexts share this database but not tables, so there is no foreign key and no join to stock data.
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL,
    quantity INT NOT NULL CHECK (quantity BETWEEN 1 AND 100),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PLACED', 'CANCELLED')),
    placed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

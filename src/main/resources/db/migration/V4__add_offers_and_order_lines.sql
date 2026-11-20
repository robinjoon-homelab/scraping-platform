-- Offers and orders are separate ordering aggregates. order_lines.sku is a copied value, not a foreign key into
-- offers, so historical orders stay readable whatever happens to their offers.
CREATE TABLE offers (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uk_offers_sku UNIQUE (sku),
    CONSTRAINT ck_offers_status CHECK (status IN ('ON_SALE', 'DISCONTINUED'))
);

-- Lines belong to the order aggregate and are written and read only with their order.
CREATE TABLE order_lines (
    order_id UUID NOT NULL,
    line_position INT NOT NULL,
    sku VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT pk_order_lines PRIMARY KEY (order_id, line_position),
    CONSTRAINT fk_order_lines_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT uk_order_lines_sku UNIQUE (order_id, sku),
    CONSTRAINT ck_order_lines_position CHECK (line_position BETWEEN 0 AND 9),
    CONSTRAINT ck_order_lines_quantity CHECK (quantity BETWEEN 1 AND 100)
);

-- Every existing order has exactly one line. No offers are created for them.
INSERT INTO order_lines (order_id, line_position, sku, quantity)
SELECT id, 0, sku, quantity FROM orders;

ALTER TABLE orders DROP COLUMN sku;
ALTER TABLE orders DROP COLUMN quantity;

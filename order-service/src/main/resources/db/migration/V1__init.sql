-- V1: poczatkowy schemat order-service
-- Odpowiada encjom Order (@Entity) oraz OrderItem (@Embeddable, @ElementCollection).
-- Hibernate przy starcie weryfikuje (ddl-auto: validate), ze ten schemat pasuje do encji.

CREATE TABLE orders (
    id               VARCHAR(255)                NOT NULL,
    session_id       VARCHAR(255)                NOT NULL,
    status           VARCHAR(255)                NOT NULL,
    total            NUMERIC(19, 4)              NOT NULL,
    shipping_address VARCHAR(255)                NOT NULL,
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

-- Tabela kolekcji dla List<OrderItem> (@ElementCollection + @CollectionTable).
-- order_id to klucz obcy do orders (z @JoinColumn). Pozycja nie istnieje bez zamowienia.
CREATE TABLE order_items (
    order_id     VARCHAR(255)   NOT NULL,
    product_id   VARCHAR(255)   NOT NULL,
    product_name VARCHAR(255)   NOT NULL,
    unit_price   NUMERIC(19, 4) NOT NULL,
    quantity     INTEGER        NOT NULL,
    subtotal     NUMERIC(19, 4) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

-- Indeks pod najczestsze zapytanie: zamowienia per sesja (findAllBySessionId).
CREATE INDEX idx_orders_session_id ON orders (session_id);

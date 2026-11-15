CREATE TABLE insumo (
    id             UUID PRIMARY KEY,
    nome           VARCHAR(100)  NOT NULL UNIQUE,
    unidade        VARCHAR(10)   NOT NULL CHECK (unidade IN ('KG', 'L', 'UN', 'SACO')),
    custo_unitario NUMERIC(10,2) NOT NULL CHECK (custo_unitario >= 0)
);

ALTER TABLE produto
    ADD COLUMN origem VARCHAR(20) NOT NULL DEFAULT 'PRODUCAO_PROPRIA'
        CHECK (origem IN ('PRODUCAO_PROPRIA', 'FORNECEDOR'));

ALTER TABLE produto
    ADD COLUMN rendimento INTEGER CHECK (rendimento > 0);

CREATE TABLE item_receita (
    id         UUID PRIMARY KEY,
    produto_id UUID          NOT NULL REFERENCES produto (id),
    insumo_id  UUID          NOT NULL REFERENCES insumo (id),
    quantidade NUMERIC(10,3) NOT NULL CHECK (quantidade > 0),

    CONSTRAINT uk_item_receita_produto_insumo UNIQUE (produto_id, insumo_id)
);

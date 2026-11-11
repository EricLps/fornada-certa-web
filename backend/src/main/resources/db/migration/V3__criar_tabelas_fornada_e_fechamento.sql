CREATE TABLE fornada (

    id             UUID PRIMARY KEY,
    produto_id     UUID        NOT NULL REFERENCES produto (id),
    data           DATE        NOT NULL,
    turno          VARCHAR(10) NOT NULL CHECK (turno IN ('MANHA', 'TARDE')),
    qtd_produzida  INTEGER     NOT NULL CHECK (qtd_produzida > 0),
    pacotes_usados INTEGER              CHECK (pacotes_usados > 0),

    CONSTRAINT uk_fornada_data_produto_turno UNIQUE (data, produto_id, turno)
);

CREATE TABLE fechamento (
    id                UUID PRIMARY KEY,
    produto_id        UUID          NOT NULL REFERENCES produto (id),
    data              DATE          NOT NULL,
    qtd_sobra         INTEGER       NOT NULL CHECK (qtd_sobra >= 0),
    qtd_doada         INTEGER       NOT NULL DEFAULT 0 CHECK (qtd_doada >= 0),
    qtd_reaproveitada INTEGER       NOT NULL DEFAULT 0 CHECK (qtd_reaproveitada >= 0),
    custo_unitario    NUMERIC(10,2) NOT NULL CHECK (custo_unitario >= 0),
    horario_esgotou   TIME,

    CONSTRAINT uk_fechamento_data_produto UNIQUE (data, produto_id),
    CONSTRAINT ck_fechamento_destino_sobra CHECK (qtd_doada + qtd_reaproveitada <= qtd_sobra),
    CONSTRAINT ck_fechamento_esgotou_sem_sobra CHECK (horario_esgotou IS NULL OR qtd_sobra = 0)
);
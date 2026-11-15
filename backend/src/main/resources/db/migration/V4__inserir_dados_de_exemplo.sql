INSERT INTO produto (id, nome, preco, custo, status) VALUES
    (gen_random_uuid(), 'Pão francês',   0.80, 0.35, TRUE),
    (gen_random_uuid(), 'Pão de queijo', 3.50, 1.40, TRUE),
    (gen_random_uuid(), 'Sonho',         6.50, 2.60, TRUE),
    (gen_random_uuid(), 'Baguete',       9.90, 3.80, TRUE),
    (gen_random_uuid(), 'Croissant',     7.00, 3.10, TRUE),
    (gen_random_uuid(), 'Bolo de fubá',  5.00, 1.80, TRUE)
ON CONFLICT (nome) DO NOTHING;

CREATE TEMPORARY TABLE exemplo ON COMMIT DROP AS
WITH perfil (nome, base, fracao_manha, por_pacote) AS (
    VALUES ('Pão francês',   300, 0.65, 80),
           ('Pão de queijo',  60, 1.00, NULL),
           ('Sonho',          35, 0.00, NULL),
           ('Baguete',        25, 1.00, NULL),
           ('Croissant',      30, 1.00, NULL),
           ('Bolo de fubá',   18, 0.00, NULL)
),
dias AS (
    SELECT (CURRENT_DATE - g)::date AS data, g
    FROM generate_series(1, 28) AS g
),
demanda AS (
    SELECT p.id AS produto_id,
           p.custo,
           d.data,
           pf.fracao_manha,
           pf.por_pacote,
           ROUND(pf.base
                 * CASE EXTRACT(ISODOW FROM d.data)
                       WHEN 6 THEN 1.35
                       WHEN 7 THEN 1.20
                       WHEN 1 THEN 0.85
                       ELSE 1.00
                   END
                 * (1 + ((d.g * 7 + LENGTH(pf.nome) * 3) % 11 - 5) / 50.0))::int AS vendas,
           (d.g + LENGTH(pf.nome)) % 9 AS sorteio
    FROM perfil pf
    JOIN produto p ON p.nome = pf.nome
    CROSS JOIN dias d
),
producao AS (
    SELECT *,
           CASE WHEN sorteio = 0
                THEN ROUND(vendas * 0.92)::int
                ELSE ROUND(vendas * (1.08 + sorteio / 100.0))::int
           END AS produzido
    FROM demanda
)
SELECT *,
       ROUND(produzido * fracao_manha)::int AS manha,
       produzido - ROUND(produzido * fracao_manha)::int AS tarde,
       CASE WHEN sorteio = 0 THEN 0 ELSE produzido - vendas END AS sobra
FROM producao;

INSERT INTO fornada (id, produto_id, data, turno, qtd_produzida, pacotes_usados)
SELECT gen_random_uuid(), produto_id, data, 'MANHA', manha, CEIL(manha::numeric / por_pacote)::int
FROM exemplo
WHERE manha > 0
UNION ALL
SELECT gen_random_uuid(), produto_id, data, 'TARDE', tarde, CEIL(tarde::numeric / por_pacote)::int
FROM exemplo
WHERE tarde > 0
ON CONFLICT (data, produto_id, turno) DO NOTHING;

INSERT INTO fechamento (id, produto_id, data, qtd_sobra, qtd_doada, qtd_reaproveitada, custo_unitario, horario_esgotou)
SELECT gen_random_uuid(),
       produto_id,
       data,
       sobra,
       FLOOR(sobra * 0.40)::int,
       FLOOR(sobra * 0.25)::int,
       custo,
       CASE WHEN sobra = 0 THEN make_time(13 + EXTRACT(DAY FROM data)::int % 5, 30, 0) END
FROM exemplo
ON CONFLICT (data, produto_id) DO NOTHING;

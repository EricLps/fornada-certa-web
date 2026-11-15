INSERT INTO insumo (id, nome, unidade, custo_unitario) VALUES
    (gen_random_uuid(), 'Farinha de trigo',             'KG',    5.00),
    (gen_random_uuid(), 'Fubá',                         'KG',    4.90),
    (gen_random_uuid(), 'Polvilho azedo',               'KG',    9.00),
    (gen_random_uuid(), 'Queijo minas padrão',          'KG',   48.00),
    (gen_random_uuid(), 'Açúcar',                       'KG',    4.50),
    (gen_random_uuid(), 'Sal',                          'KG',    2.00),
    (gen_random_uuid(), 'Manteiga',                     'KG',   40.00),
    (gen_random_uuid(), 'Fermento biológico',           'KG',   40.00),
    (gen_random_uuid(), 'Fermento químico',             'KG',   30.00),
    (gen_random_uuid(), 'Doce de leite',                'KG',   22.00),
    (gen_random_uuid(), 'Leite',                        'L',     6.00),
    (gen_random_uuid(), 'Óleo de soja',                 'L',     9.00),
    (gen_random_uuid(), 'Ovo',                          'UN',    0.80),
    (gen_random_uuid(), 'Saco de massa de pão francês', 'SACO', 28.00),
    (gen_random_uuid(), 'Saco de massa de baguete',     'SACO', 45.60)
ON CONFLICT (nome) DO NOTHING;

CREATE TEMPORARY TABLE ficha_exemplo ON COMMIT DROP AS
SELECT *
FROM (VALUES
    ('Pão francês',   'FORNECEDOR',       80, 'Saco de massa de pão francês', 1.000),
    ('Baguete',       'FORNECEDOR',       12, 'Saco de massa de baguete',     1.000),
    ('Pão de queijo', 'PRODUCAO_PROPRIA', 30, 'Polvilho azedo',               1.000),
    ('Pão de queijo', 'PRODUCAO_PROPRIA', 30, 'Queijo minas padrão',          0.500),
    ('Pão de queijo', 'PRODUCAO_PROPRIA', 30, 'Leite',                        0.500),
    ('Pão de queijo', 'PRODUCAO_PROPRIA', 30, 'Ovo',                          4.000),
    ('Pão de queijo', 'PRODUCAO_PROPRIA', 30, 'Óleo de soja',                 0.200),
    ('Sonho',         'PRODUCAO_PROPRIA', 15, 'Farinha de trigo',             1.000),
    ('Sonho',         'PRODUCAO_PROPRIA', 15, 'Açúcar',                       0.200),
    ('Sonho',         'PRODUCAO_PROPRIA', 15, 'Leite',                        0.300),
    ('Sonho',         'PRODUCAO_PROPRIA', 15, 'Ovo',                          3.000),
    ('Sonho',         'PRODUCAO_PROPRIA', 15, 'Fermento biológico',           0.020),
    ('Sonho',         'PRODUCAO_PROPRIA', 15, 'Manteiga',                     0.100),
    ('Sonho',         'PRODUCAO_PROPRIA', 15, 'Doce de leite',                1.000),
    ('Croissant',     'PRODUCAO_PROPRIA', 10, 'Farinha de trigo',             1.000),
    ('Croissant',     'PRODUCAO_PROPRIA', 10, 'Manteiga',                     0.500),
    ('Croissant',     'PRODUCAO_PROPRIA', 10, 'Leite',                        0.250),
    ('Croissant',     'PRODUCAO_PROPRIA', 10, 'Açúcar',                       0.100),
    ('Croissant',     'PRODUCAO_PROPRIA', 10, 'Fermento biológico',           0.020),
    ('Croissant',     'PRODUCAO_PROPRIA', 10, 'Ovo',                          2.000),
    ('Bolo de fubá',  'PRODUCAO_PROPRIA',  8, 'Fubá',                         0.500),
    ('Bolo de fubá',  'PRODUCAO_PROPRIA',  8, 'Açúcar',                       0.400),
    ('Bolo de fubá',  'PRODUCAO_PROPRIA',  8, 'Ovo',                          4.000),
    ('Bolo de fubá',  'PRODUCAO_PROPRIA',  8, 'Leite',                        0.500),
    ('Bolo de fubá',  'PRODUCAO_PROPRIA',  8, 'Óleo de soja',                 0.200),
    ('Bolo de fubá',  'PRODUCAO_PROPRIA',  8, 'Fermento químico',             0.020)
) AS f (produto, origem, rendimento, insumo, quantidade)
WHERE EXISTS (
    SELECT 1
    FROM produto p
    WHERE p.nome = f.produto
      AND p.rendimento IS NULL
      AND NOT EXISTS (SELECT 1 FROM item_receita i WHERE i.produto_id = p.id)
);

INSERT INTO item_receita (id, produto_id, insumo_id, quantidade)
SELECT gen_random_uuid(), p.id, i.id, f.quantidade
FROM ficha_exemplo f
JOIN produto p ON p.nome = f.produto
JOIN insumo i ON i.nome = f.insumo;

UPDATE produto p
SET origem = f.origem,
    rendimento = f.rendimento
FROM (SELECT DISTINCT produto, origem, rendimento FROM ficha_exemplo) f
WHERE p.nome = f.produto;

UPDATE produto p
SET custo = ROUND(t.total / p.rendimento, 2)
FROM (
    SELECT r.produto_id, SUM(r.quantidade * i.custo_unitario) AS total
    FROM item_receita r
    JOIN insumo i ON i.id = r.insumo_id
    GROUP BY r.produto_id
) t
WHERE t.produto_id = p.id
  AND p.nome IN (SELECT produto FROM ficha_exemplo);

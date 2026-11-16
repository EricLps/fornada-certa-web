ALTER TABLE insumo ADD COLUMN itens_por_saco INTEGER CHECK (itens_por_saco > 0);

ALTER TABLE insumo ADD CONSTRAINT ck_insumo_itens_por_saco CHECK (unidade = 'SACO' OR itens_por_saco IS NULL);

UPDATE insumo i
SET itens_por_saco = (
    SELECT MAX(p.rendimento)
    FROM item_receita r
    JOIN produto p ON p.id = r.produto_id
    WHERE r.insumo_id = i.id
      AND p.origem = 'FORNECEDOR'
)
WHERE i.unidade = 'SACO';

ALTER TABLE produto ADD COLUMN fatias INTEGER CHECK (fatias >= 2);

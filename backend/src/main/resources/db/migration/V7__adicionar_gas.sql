ALTER TABLE insumo DROP CONSTRAINT insumo_unidade_check;

ALTER TABLE insumo ADD CONSTRAINT ck_insumo_unidade CHECK (unidade IN ('KG', 'L', 'UN', 'SACO', 'GAS'));

ALTER TABLE insumo ADD COLUMN fornadas_por_botijao INTEGER CHECK (fornadas_por_botijao > 0);

ALTER TABLE insumo ADD CONSTRAINT ck_insumo_fornadas_por_botijao CHECK (unidade = 'GAS' OR fornadas_por_botijao IS NULL);

package org.ericlps.fornadacerta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.ericlps.fornadacerta.entity.enums.OrigemProduto;

import java.math.BigDecimal;
import java.util.List;

public record ProdutoPatchRequest(
        @Size(min = 1, max = 100) String nome,
        @Positive BigDecimal preco,
        Boolean status,
        OrigemProduto origem,
        @Positive Integer rendimento,
        @Size(min = 1) @Valid List<ItemReceitaRequest> itens
        ) {

    public boolean alteraFichaTecnica() {
        return origem != null || rendimento != null || itens != null;
    }

    public boolean fichaTecnicaCompleta() {
        return origem != null && rendimento != null && itens != null;
    }
}

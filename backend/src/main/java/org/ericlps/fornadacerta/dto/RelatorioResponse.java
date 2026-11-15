package org.ericlps.fornadacerta.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RelatorioResponse(
        LocalDate inicio,
        LocalDate fim,
        Totais total,
        List<PorProduto> porProduto,
        List<PorDia> porDia
) {

    public record Totais(
            int diasFechados,
            int produzido,
            int vendido,
            int sobra,
            int doado,
            int reaproveitado,
            int descartado,
            BigDecimal custoDescarte,
            BigDecimal taxaDesperdicio
    ) {
    }

    public record PorProduto(
            UUID produtoId,
            String produtoNome,
            int produzido,
            int vendido,
            int descartado,
            BigDecimal custoDescarte,
            BigDecimal taxaDesperdicio
    ) {
    }

    public record PorDia(
            LocalDate data,
            String diaDaSemana,
            int produzido,
            int vendido,
            int descartado
    ) {
    }
}

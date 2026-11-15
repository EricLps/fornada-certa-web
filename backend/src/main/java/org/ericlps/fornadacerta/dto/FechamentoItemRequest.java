package org.ericlps.fornadacerta.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalTime;
import java.util.UUID;

public record FechamentoItemRequest(
        @NotNull UUID produtoId,
        @NotNull @PositiveOrZero Integer qtdSobra,
        @PositiveOrZero Integer qtdDoada,
        @PositiveOrZero Integer qtdReaproveitada,
        LocalTime horarioEsgotou
) {

    public int doadaOuZero() {
        return (qtdDoada != null) ? qtdDoada : 0;
    }

    public int reaproveitadaOuZero() {
        return (qtdReaproveitada != null) ? qtdReaproveitada : 0;
    }
}

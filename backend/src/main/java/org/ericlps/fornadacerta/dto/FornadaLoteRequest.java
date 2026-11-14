package org.ericlps.fornadacerta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.ericlps.fornadacerta.entity.enums.Turno;

import java.time.LocalDate;
import java.util.List;

public record FornadaLoteRequest(
        LocalDate data,
        @NotNull Turno turno,
        @NotEmpty @Valid List<FornadaItemRequest> itens) {
}

package org.ericlps.fornadacerta.calculo;

import java.time.LocalDate;

public record VendaDiaria(
        LocalDate data,
        int qtdVendida,
        boolean esgotou
) {
}

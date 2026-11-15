package org.ericlps.fornadacerta.calculo;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class CalculadoraCusto {

    public record Componente(BigDecimal quantidade, BigDecimal custoUnitario) {

        public BigDecimal custo() {
            return quantidade.multiply(custoUnitario);
        }
    }

    public BigDecimal custoUnitario(List<Componente> componentes, int rendimento) {

        if (rendimento <= 0) {
            throw new IllegalArgumentException("O rendimento precisa ser maior que zero.");
        }

        BigDecimal total = componentes.stream()
                .map(Componente::custo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return total.divide(BigDecimal.valueOf(rendimento), 2, RoundingMode.HALF_UP);
    }
}

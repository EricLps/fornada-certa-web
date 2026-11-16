package org.ericlps.fornadacerta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Insumo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UnidadeMedida unidade;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal custoUnitario;

    private Integer itensPorSaco;

    private Integer fornadasPorBotijao;

    public BigDecimal custoPorUnidadeDeUso() {
        if (unidade == UnidadeMedida.GAS && fornadasPorBotijao != null) {
            return custoUnitario.divide(BigDecimal.valueOf(fornadasPorBotijao), 4, RoundingMode.HALF_UP);
        }
        return custoUnitario;
    }

}

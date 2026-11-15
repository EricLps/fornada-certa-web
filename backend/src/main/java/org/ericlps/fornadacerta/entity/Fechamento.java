package org.ericlps.fornadacerta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Fechamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private Integer qtdSobra;

    @Column(nullable = false)
    private Integer qtdDoada;

    @Column(nullable = false)
    private Integer qtdReaproveitada;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal custoUnitario;

    private LocalTime horarioEsgotou;

}

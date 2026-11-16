package org.ericlps.fornadacerta.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ericlps.fornadacerta.entity.enums.OrigemProduto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String nome;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal custo;

    @Column(nullable = false)
    private Boolean status = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemProduto origem = OrigemProduto.PRODUCAO_PROPRIA;

    private Integer rendimento;

    private Integer fatias;

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemReceita> receita = new ArrayList<>();

}

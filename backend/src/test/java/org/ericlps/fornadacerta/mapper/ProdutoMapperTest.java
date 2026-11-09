package org.ericlps.fornadacerta.mapper;

import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.entity.Produto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProdutoMapperTest {

    private final ProdutoMapper mapper = new ProdutoMapper();

    @Test
    @DisplayName("toEntity copia nome e preço e cria o produto como ativo, sem id")
    void toEntity() {
        ProdutoRequest request = new ProdutoRequest("Pão francês", new BigDecimal("0.80"));

        Produto produto = mapper.toEntity(request);

        assertThat(produto.getId()).isNull();
        assertThat(produto.getNome()).isEqualTo("Pão francês");
        assertThat(produto.getPreco()).isEqualByComparingTo("0.80");
        assertThat(produto.getStatus()).isTrue();
    }

    @Test
    @DisplayName("toResponse copia todos os campos da entity")
    void toResponse() {
        UUID id = UUID.randomUUID();
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome("Sonho");
        produto.setPreco(new BigDecimal("6.50"));
        produto.setStatus(false);

        ProdutoResponse response = mapper.toResponse(produto);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.nome()).isEqualTo("Sonho");
        assertThat(response.valor()).isEqualByComparingTo("6.50");
        assertThat(response.ativo()).isFalse();
    }

    @Test
    @DisplayName("atualizar altera nome e preço, mas mantém id e status")
    void atualizar() {
        UUID id = UUID.randomUUID();
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome("Pão de queijo");
        produto.setPreco(new BigDecimal("3.00"));
        produto.setStatus(false);

        mapper.atualizar(produto, new ProdutoRequest("Pão de queijo grande", new BigDecimal("4.50")));

        assertThat(produto.getId()).isEqualTo(id);
        assertThat(produto.getNome()).isEqualTo("Pão de queijo grande");
        assertThat(produto.getPreco()).isEqualByComparingTo("4.50");
        assertThat(produto.getStatus()).isFalse();
    }
}

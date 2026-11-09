package org.ericlps.fornadacerta.mapper;

import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.entity.Produto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
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

    @Test
    @DisplayName("toResponseList converte todos os produtos, na mesma ordem")
    void toResponseList() {
        Produto primeiro = new Produto();
        primeiro.setId(UUID.randomUUID());
        primeiro.setNome("Pão francês");
        primeiro.setPreco(new BigDecimal("0.80"));
        primeiro.setStatus(true);
        Produto segundo = new Produto();
        segundo.setId(UUID.randomUUID());
        segundo.setNome("Sonho");
        segundo.setPreco(new BigDecimal("6.50"));
        segundo.setStatus(false);

        List<ProdutoResponse> responses = mapper.toResponseList(List.of(primeiro, segundo));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(primeiro.getId());
        assertThat(responses.get(0).nome()).isEqualTo("Pão francês");
        assertThat(responses.get(0).valor()).isEqualByComparingTo("0.80");
        assertThat(responses.get(0).ativo()).isTrue();
        assertThat(responses.get(1).id()).isEqualTo(segundo.getId());
        assertThat(responses.get(1).nome()).isEqualTo("Sonho");
        assertThat(responses.get(1).ativo()).isFalse();
    }

    @Test
    @DisplayName("toResponseList com lista vazia devolve lista vazia")
    void toResponseListVazia() {
        assertThat(mapper.toResponseList(List.of())).isEmpty();
    }
}

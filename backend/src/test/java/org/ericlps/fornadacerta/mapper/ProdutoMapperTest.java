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

    private Produto produto(UUID id, String nome, String preco, boolean status) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setPreco(new BigDecimal(preco));
        produto.setStatus(status);
        return produto;
    }

    // ---------- toEntity ----------

    @Test
    @DisplayName("toEntity copia nome e preço do request")
    void toEntityCopiaCampos() {
        Produto produto = mapper.toEntity(new ProdutoRequest("Pão francês", new BigDecimal("0.80")));

        assertThat(produto.getNome()).isEqualTo("Pão francês");
        assertThat(produto.getPreco()).isEqualByComparingTo("0.80");
    }

    @Test
    @DisplayName("toEntity cria produto novo: sem id e ativo")
    void toEntityProdutoNovo() {
        Produto produto = mapper.toEntity(new ProdutoRequest("Sonho", new BigDecimal("6.50")));

        assertThat(produto.getId()).isNull();
        assertThat(produto.getStatus()).isTrue();
    }

    // ---------- toResponse ----------

    @Test
    @DisplayName("toResponse copia todos os campos de um produto ativo")
    void toResponseAtivo() {
        UUID id = UUID.randomUUID();

        ProdutoResponse response = mapper.toResponse(produto(id, "Baguete", "9.90", true));

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.nome()).isEqualTo("Baguete");
        assertThat(response.valor()).isEqualByComparingTo("9.90");
        assertThat(response.ativo()).isTrue();
    }

    @Test
    @DisplayName("toResponse mantém o status de um produto inativo")
    void toResponseInativo() {
        ProdutoResponse response = mapper.toResponse(produto(UUID.randomUUID(), "Sonho", "6.50", false));

        assertThat(response.ativo()).isFalse();
    }

    // ---------- toResponseList ----------

    @Test
    @DisplayName("toResponseList converte todos os itens mantendo a ordem")
    void toResponseListMantemOrdem() {
        List<Produto> produtos = List.of(
                produto(UUID.randomUUID(), "Baguete", "9.90", true),
                produto(UUID.randomUUID(), "Croissant", "7.00", false),
                produto(UUID.randomUUID(), "Sonho", "6.50", true));

        List<ProdutoResponse> responses = mapper.toResponseList(produtos);

        assertThat(responses).extracting(ProdutoResponse::nome)
                .containsExactly("Baguete", "Croissant", "Sonho");
        assertThat(responses).extracting(ProdutoResponse::ativo)
                .containsExactly(true, false, true);
    }

    @Test
    @DisplayName("toResponseList com lista vazia devolve lista vazia")
    void toResponseListVazia() {
        assertThat(mapper.toResponseList(List.of())).isEmpty();
    }
}

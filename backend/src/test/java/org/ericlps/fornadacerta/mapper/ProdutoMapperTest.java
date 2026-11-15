package org.ericlps.fornadacerta.mapper;

import org.ericlps.fornadacerta.dto.ItemReceitaRequest;
import org.ericlps.fornadacerta.dto.ItemReceitaResponse;
import org.ericlps.fornadacerta.dto.ProdutoDetalheResponse;
import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.entity.Insumo;
import org.ericlps.fornadacerta.entity.ItemReceita;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.entity.enums.OrigemProduto;
import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProdutoMapperTest {

    private final ProdutoMapper mapper = new ProdutoMapper();

    private Produto produto(UUID id, String nome, String preco, String custo, boolean status) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setPreco(new BigDecimal(preco));
        produto.setCusto(new BigDecimal(custo));
        produto.setStatus(status);
        return produto;
    }

    private Insumo insumo(String nome, UnidadeMedida unidade, String custo) {
        Insumo insumo = new Insumo();
        insumo.setId(UUID.randomUUID());
        insumo.setNome(nome);
        insumo.setUnidade(unidade);
        insumo.setCustoUnitario(new BigDecimal(custo));
        return insumo;
    }

    // ---------- toEntity ----------

    @Test
    @DisplayName("toEntity copia só nome e preço: custo, origem e receita ficam com a ficha técnica")
    void toEntityCopiaCampos() {
        Produto produto = mapper.toEntity(new ProdutoRequest("Pão francês", new BigDecimal("0.80"),
                OrigemProduto.FORNECEDOR, 80, List.of(new ItemReceitaRequest(UUID.randomUUID(), BigDecimal.ONE))));

        assertThat(produto.getNome()).isEqualTo("Pão francês");
        assertThat(produto.getPreco()).isEqualByComparingTo("0.80");
        assertThat(produto.getCusto()).isNull();
        assertThat(produto.getReceita()).isEmpty();
        assertThat(produto.getRendimento()).isNull();
    }

    @Test
    @DisplayName("toEntity cria produto novo: sem id e ativo")
    void toEntityProdutoNovo() {
        Produto produto = mapper.toEntity(new ProdutoRequest("Sonho", new BigDecimal("6.50"),
                OrigemProduto.PRODUCAO_PROPRIA, 20, List.of()));

        assertThat(produto.getId()).isNull();
        assertThat(produto.getStatus()).isTrue();
    }

    // ---------- toResponse ----------

    @Test
    @DisplayName("toResponse copia todos os campos, incluindo origem e rendimento")
    void toResponseAtivo() {
        UUID id = UUID.randomUUID();
        Produto produto = produto(id, "Baguete", "9.90", "4.95", true);
        produto.setOrigem(OrigemProduto.FORNECEDOR);
        produto.setRendimento(30);

        ProdutoResponse response = mapper.toResponse(produto);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.nome()).isEqualTo("Baguete");
        assertThat(response.preco()).isEqualByComparingTo("9.90");
        assertThat(response.custo()).isEqualByComparingTo("4.95");
        assertThat(response.ativo()).isTrue();
        assertThat(response.origem()).isEqualTo(OrigemProduto.FORNECEDOR);
        assertThat(response.rendimento()).isEqualTo(30);
    }

    @Test
    @DisplayName("toResponse de produto antigo (sem ficha): produção própria e rendimento nulo")
    void toResponseProdutoAntigo() {
        ProdutoResponse response = mapper.toResponse(produto(UUID.randomUUID(), "Sonho", "6.50", "3.25", false));

        assertThat(response.ativo()).isFalse();
        assertThat(response.origem()).isEqualTo(OrigemProduto.PRODUCAO_PROPRIA);
        assertThat(response.rendimento()).isNull();
    }

    // ---------- toResponseList ----------

    @Test
    @DisplayName("toResponseList converte todos os itens mantendo a ordem")
    void toResponseListMantemOrdem() {
        List<Produto> produtos = List.of(
                produto(UUID.randomUUID(), "Baguete", "9.90", "4.95", true),
                produto(UUID.randomUUID(), "Croissant", "7.00", "3.50", false),
                produto(UUID.randomUUID(), "Sonho", "6.50", "3.25", true));

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

    // ---------- toDetalhe ----------

    @Test
    @DisplayName("toDetalhe traz os itens em ordem alfabética, com custo parcial = quantidade x custo do insumo")
    void toDetalheComItens() {
        Produto bolo = produto(UUID.randomUUID(), "Bolo de fubá", "4.00", "1.20", true);
        bolo.setRendimento(10);
        Insumo ovo = insumo("Ovo", UnidadeMedida.UN, "0.80");
        Insumo fuba = insumo("Fubá", UnidadeMedida.KG, "4.90");
        bolo.getReceita().add(new ItemReceita(bolo, ovo, new BigDecimal("4")));
        bolo.getReceita().add(new ItemReceita(bolo, fuba, new BigDecimal("0.500")));

        ProdutoDetalheResponse detalhe = mapper.toDetalhe(bolo);

        assertThat(detalhe.nome()).isEqualTo("Bolo de fubá");
        assertThat(detalhe.rendimento()).isEqualTo(10);
        assertThat(detalhe.itens()).extracting(ItemReceitaResponse::insumoNome).containsExactly("Fubá", "Ovo");

        ItemReceitaResponse itemFuba = detalhe.itens().getFirst();
        assertThat(itemFuba.insumoId()).isEqualTo(fuba.getId());
        assertThat(itemFuba.unidade()).isEqualTo(UnidadeMedida.KG);
        assertThat(itemFuba.quantidade()).isEqualByComparingTo("0.5");
        assertThat(itemFuba.custoUnitario()).isEqualByComparingTo("4.90");
        assertThat(itemFuba.custoParcial()).isEqualByComparingTo("2.45");
        assertThat(detalhe.itens().get(1).custoParcial()).isEqualByComparingTo("3.20");
    }

    @Test
    @DisplayName("toDetalhe arredonda o custo parcial para centavos: 0,333 kg x 5,00 = 1,67")
    void toDetalheArredonda() {
        Produto produto = produto(UUID.randomUUID(), "Pão", "1.00", "0.10", true);
        produto.getReceita().add(new ItemReceita(produto, insumo("Farinha", UnidadeMedida.KG, "5.00"), new BigDecimal("0.333")));

        ItemReceitaResponse item = mapper.toDetalhe(produto).itens().getFirst();

        assertThat(item.custoParcial()).isEqualByComparingTo("1.67");
        assertThat(item.custoParcial().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("toDetalhe de produto sem ficha técnica: lista de itens vazia")
    void toDetalheSemItens() {
        assertThat(mapper.toDetalhe(produto(UUID.randomUUID(), "Sonho", "6.50", "3.25", true)).itens()).isEmpty();
    }
}

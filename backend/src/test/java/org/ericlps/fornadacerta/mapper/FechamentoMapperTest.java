package org.ericlps.fornadacerta.mapper;

import org.ericlps.fornadacerta.dto.FechamentoItemRequest;
import org.ericlps.fornadacerta.dto.FechamentoResponse;
import org.ericlps.fornadacerta.entity.Fechamento;
import org.ericlps.fornadacerta.entity.Produto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FechamentoMapperTest {

    private final FechamentoMapper mapper = new FechamentoMapper();

    private static final LocalDate DATA = LocalDate.of(2026, 11, 14);

    private Produto produto(String nome, String custo) {
        Produto produto = new Produto();
        produto.setId(UUID.randomUUID());
        produto.setNome(nome);
        produto.setPreco(new BigDecimal("1.00"));
        produto.setCusto(new BigDecimal(custo));
        return produto;
    }

    private Fechamento fechamento(Produto produto, int sobra, int doada, int reaproveitada, String custoUnitario) {
        Fechamento fechamento = new Fechamento();
        fechamento.setId(UUID.randomUUID());
        fechamento.setProduto(produto);
        fechamento.setData(DATA);
        fechamento.setQtdSobra(sobra);
        fechamento.setQtdDoada(doada);
        fechamento.setQtdReaproveitada(reaproveitada);
        fechamento.setCustoUnitario(new BigDecimal(custoUnitario));
        return fechamento;
    }

    // ---------- toEntity ----------

    @Test
    @DisplayName("toEntity copia os dados do item, a data do lote e o custo atual do produto")
    void toEntityCopiaTudo() {
        Produto produto = produto("Pão francês", "0.40");

        Fechamento fechamento = mapper.toEntity(new FechamentoItemRequest(produto.getId(), 30, 10, 5, null), produto, DATA);

        assertThat(fechamento.getId()).isNull();
        assertThat(fechamento.getProduto()).isSameAs(produto);
        assertThat(fechamento.getData()).isEqualTo(DATA);
        assertThat(fechamento.getQtdSobra()).isEqualTo(30);
        assertThat(fechamento.getQtdDoada()).isEqualTo(10);
        assertThat(fechamento.getQtdReaproveitada()).isEqualTo(5);
        assertThat(fechamento.getCustoUnitario()).isEqualByComparingTo("0.40");
        assertThat(fechamento.getHorarioEsgotou()).isNull();
    }

    @Test
    @DisplayName("toEntity transforma doada e reaproveitada ausentes em zero")
    void toEntityAusentesViramZero() {
        Produto produto = produto("Sonho", "2.00");

        Fechamento fechamento = mapper.toEntity(new FechamentoItemRequest(produto.getId(), 4, null, null, null), produto, DATA);

        assertThat(fechamento.getQtdDoada()).isZero();
        assertThat(fechamento.getQtdReaproveitada()).isZero();
    }

    @Test
    @DisplayName("toEntity guarda o horário em que esgotou")
    void toEntityHorarioEsgotou() {
        Produto produto = produto("Croissant", "3.00");

        Fechamento fechamento = mapper.toEntity(
                new FechamentoItemRequest(produto.getId(), 0, null, null, LocalTime.of(10, 30)), produto, DATA);

        assertThat(fechamento.getHorarioEsgotou()).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    @DisplayName("o custo é uma cópia: mudar o produto depois não altera o fechamento")
    void custoEhCopia() {
        Produto produto = produto("Pão francês", "0.40");
        Fechamento fechamento = mapper.toEntity(new FechamentoItemRequest(produto.getId(), 30, null, null, null), produto, DATA);

        produto.setCusto(new BigDecimal("0.55"));

        assertThat(fechamento.getCustoUnitario()).isEqualByComparingTo("0.40");
    }

    // ---------- toResponse ----------

    @Test
    @DisplayName("toResponse calcula descartada (sobra - doada - reaproveitada) e o custo do descarte")
    void toResponseCalculaDescarte() {
        Produto produto = produto("Pão francês", "0.40");

        FechamentoResponse response = mapper.toResponse(fechamento(produto, 30, 6, 4, "0.50"));

        assertThat(response.produtoId()).isEqualTo(produto.getId());
        assertThat(response.produtoNome()).isEqualTo("Pão francês");
        assertThat(response.data()).isEqualTo(DATA);
        assertThat(response.qtdSobra()).isEqualTo(30);
        assertThat(response.qtdDoada()).isEqualTo(6);
        assertThat(response.qtdReaproveitada()).isEqualTo(4);
        assertThat(response.qtdDescartada()).isEqualTo(20);
        assertThat(response.custoUnitario()).isEqualByComparingTo("0.50");   // o da época, não o atual do produto
        assertThat(response.custoDescarte()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("tudo doado ou reaproveitado: descartada zero e custo de descarte zero")
    void semDescarte() {
        FechamentoResponse response = mapper.toResponse(fechamento(produto("Sonho", "2.00"), 8, 5, 3, "2.00"));

        assertThat(response.qtdDescartada()).isZero();
        assertThat(response.custoDescarte()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("toResponseList mantém a ordem")
    void toResponseList() {
        List<FechamentoResponse> responses = mapper.toResponseList(List.of(
                fechamento(produto("Baguete", "4.00"), 2, 0, 0, "4.00"),
                fechamento(produto("Sonho", "2.00"), 1, 0, 0, "2.00")));

        assertThat(responses).extracting(FechamentoResponse::produtoNome).containsExactly("Baguete", "Sonho");
    }
}

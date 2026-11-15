package org.ericlps.fornadacerta.mapper;

import org.ericlps.fornadacerta.dto.FornadaItemRequest;
import org.ericlps.fornadacerta.dto.FornadaResponse;
import org.ericlps.fornadacerta.entity.Fornada;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.entity.enums.Turno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FornadaMapperTest {

    private final FornadaMapper mapper = new FornadaMapper();

    private static final LocalDate DATA = LocalDate.of(2026, 11, 15);

    private Produto produto(String nome) {
        Produto produto = new Produto();
        produto.setId(UUID.randomUUID());
        produto.setNome(nome);
        produto.setPreco(new BigDecimal("0.80"));
        produto.setCusto(new BigDecimal("0.40"));
        return produto;
    }

    private Fornada fornada(Produto produto, Turno turno, int qtd, Integer pacotes) {
        Fornada fornada = new Fornada();
        fornada.setId(UUID.randomUUID());
        fornada.setProduto(produto);
        fornada.setData(DATA);
        fornada.setTurno(turno);
        fornada.setQtdProduzida(qtd);
        fornada.setPacotesUsados(pacotes);
        return fornada;
    }

    // ---------- toEntity ----------

    @Test
    @DisplayName("toEntity junta o item com produto, data e turno do lote")
    void toEntityPreencheTudo() {
        Produto produto = produto("Pão francês");
        FornadaItemRequest item = new FornadaItemRequest(produto.getId(), 250, 3);

        Fornada fornada = mapper.toEntity(item, produto, DATA, Turno.MANHA);

        assertThat(fornada.getId()).isNull();
        assertThat(fornada.getProduto()).isSameAs(produto);
        assertThat(fornada.getData()).isEqualTo(DATA);
        assertThat(fornada.getTurno()).isEqualTo(Turno.MANHA);
        assertThat(fornada.getQtdProduzida()).isEqualTo(250);
        assertThat(fornada.getPacotesUsados()).isEqualTo(3);
    }

    @Test
    @DisplayName("toEntity mantém pacotesUsados nulo quando não informado")
    void toEntitySemPacotes() {
        Produto produto = produto("Sonho");

        Fornada fornada = mapper.toEntity(new FornadaItemRequest(produto.getId(), 40, null), produto, DATA, Turno.TARDE);

        assertThat(fornada.getPacotesUsados()).isNull();
        assertThat(fornada.getTurno()).isEqualTo(Turno.TARDE);
    }

    // ---------- toResponse ----------

    @Test
    @DisplayName("toResponse traz id e nome do produto, além dos dados da fornada")
    void toResponse() {
        Produto produto = produto("Baguete");
        Fornada fornada = fornada(produto, Turno.TARDE, 60, 2);

        FornadaResponse response = mapper.toResponse(fornada);

        assertThat(response.id()).isEqualTo(fornada.getId());
        assertThat(response.produtoId()).isEqualTo(produto.getId());
        assertThat(response.produtoNome()).isEqualTo("Baguete");
        assertThat(response.data()).isEqualTo(DATA);
        assertThat(response.turno()).isEqualTo(Turno.TARDE);
        assertThat(response.qtdProduzida()).isEqualTo(60);
        assertThat(response.pacotesUsados()).isEqualTo(2);
    }

    // ---------- toResponseList ----------

    @Test
    @DisplayName("toResponseList mantém a ordem e converte todos os itens")
    void toResponseList() {
        List<Fornada> fornadas = List.of(
                fornada(produto("Baguete"), Turno.MANHA, 60, null),
                fornada(produto("Sonho"), Turno.MANHA, 40, null));

        assertThat(mapper.toResponseList(fornadas))
                .extracting(FornadaResponse::produtoNome)
                .containsExactly("Baguete", "Sonho");
    }

    @Test
    @DisplayName("toResponseList com lista vazia devolve lista vazia")
    void toResponseListVazia() {
        assertThat(mapper.toResponseList(List.of())).isEmpty();
    }
}

package org.ericlps.fornadacerta.mapper;

import org.ericlps.fornadacerta.dto.FechamentoItemRequest;
import org.ericlps.fornadacerta.dto.FechamentoResponse;
import org.ericlps.fornadacerta.entity.Fechamento;
import org.ericlps.fornadacerta.entity.Produto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class FechamentoMapper {

    public Fechamento toEntity(FechamentoItemRequest item, Produto produto, LocalDate data, BigDecimal custoUnitario) {
        Fechamento fechamento = new Fechamento();
        fechamento.setProduto(produto);
        fechamento.setData(data);
        fechamento.setQtdSobra(item.qtdSobra());
        fechamento.setQtdDoada(item.doadaOuZero());
        fechamento.setQtdReaproveitada(item.reaproveitadaOuZero());
        fechamento.setCustoUnitario(custoUnitario);
        fechamento.setHorarioEsgotou(item.horarioEsgotou());
        return fechamento;
    }

    public FechamentoResponse toResponse(Fechamento fechamento) {
        int descartada = fechamento.getQtdSobra() - fechamento.getQtdDoada() - fechamento.getQtdReaproveitada();
        BigDecimal custoDescarte = fechamento.getCustoUnitario().multiply(BigDecimal.valueOf(descartada));

        return new FechamentoResponse(
                fechamento.getId(),
                fechamento.getProduto().getId(),
                fechamento.getProduto().getNome(),
                fechamento.getData(),
                fechamento.getQtdSobra(),
                fechamento.getQtdDoada(),
                fechamento.getQtdReaproveitada(),
                descartada,
                fechamento.getCustoUnitario(),
                custoDescarte,
                fechamento.getHorarioEsgotou());
    }

    public List<FechamentoResponse> toResponseList(List<Fechamento> fechamentos) {
        return fechamentos.stream().map(this::toResponse).toList();
    }
}

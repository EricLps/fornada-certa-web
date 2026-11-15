package org.ericlps.fornadacerta.mapper;

import org.ericlps.fornadacerta.dto.FornadaItemRequest;
import org.ericlps.fornadacerta.dto.FornadaResponse;
import org.ericlps.fornadacerta.entity.Fornada;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.entity.enums.Turno;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class FornadaMapper {

    public Fornada toEntity(FornadaItemRequest item, Produto produto, LocalDate data, Turno turno){

        Fornada fornada = new Fornada();
        fornada.setProduto(produto);
        fornada.setData(data);
        fornada.setTurno(turno);
        fornada.setQtdProduzida(item.qtdProduzida());
        fornada.setPacotesUsados(item.pacotesUsados());
        return fornada;

    }

    public FornadaResponse toResponse(Fornada fornada){

        return new FornadaResponse(
                fornada.getId(),
                fornada.getProduto().getId(),
                fornada.getProduto().getNome(),
                fornada.getData(),
                fornada.getTurno(),
                fornada.getQtdProduzida(),
                fornada.getPacotesUsados()
        );

    }

    public List<FornadaResponse> toResponseList(List<Fornada> fornadas){

        return fornadas.stream().map(this::toResponse).toList();

    }


}

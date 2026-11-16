package org.ericlps.fornadacerta.mapper;

import org.ericlps.fornadacerta.dto.InsumoRequest;
import org.ericlps.fornadacerta.dto.InsumoResponse;
import org.ericlps.fornadacerta.entity.Insumo;
import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InsumoMapper {

    public Insumo toEntity(InsumoRequest request) {
        Insumo insumo = new Insumo();
        insumo.setNome(request.nome());
        insumo.setUnidade(request.unidade());
        insumo.setCustoUnitario(request.custoUnitario());
        insumo.setItensPorSaco(request.unidade() == UnidadeMedida.SACO ? request.itensPorSaco() : null);
        return insumo;
    }

    public InsumoResponse toResponse(Insumo insumo) {
        return new InsumoResponse(insumo.getId(), insumo.getNome(), insumo.getUnidade(), insumo.getCustoUnitario(),
                insumo.getItensPorSaco());
    }

    public List<InsumoResponse> toResponseList(List<Insumo> insumos) {
        return insumos.stream().map(this::toResponse).toList();
    }
}

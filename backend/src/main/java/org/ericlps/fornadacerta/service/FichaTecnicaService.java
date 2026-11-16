package org.ericlps.fornadacerta.service;

import lombok.RequiredArgsConstructor;
import org.ericlps.fornadacerta.calculo.CalculadoraCusto;
import org.ericlps.fornadacerta.dto.ItemReceitaRequest;
import org.ericlps.fornadacerta.entity.Insumo;
import org.ericlps.fornadacerta.entity.ItemReceita;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.entity.enums.OrigemProduto;
import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.repository.InsumoRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class FichaTecnicaService {

    private final InsumoRepository insumoRepository;
    private final CalculadoraCusto calculadoraCusto;

    public void aplicar(Produto produto, OrigemProduto origem, Integer rendimento, Integer fatias,
                        List<ItemReceitaRequest> itens) {

        Set<UUID> ids = new LinkedHashSet<>();
        for (ItemReceitaRequest item : itens) {
            if (!ids.add(item.insumoId())) {
                throw new RegraDeNegocioException("Um mesmo insumo aparece mais de uma vez na ficha técnica.");
            }
        }

        Map<UUID, Insumo> insumos = new HashMap<>();
        for (Insumo insumo : insumoRepository.findAllById(ids)) {
            insumos.put(insumo.getId(), insumo);
        }
        for (UUID id : ids) {
            if (!insumos.containsKey(id)) {
                throw new RecursoNaoEncontradoException("Insumo não encontrado: " + id);
            }
        }

        if (origem == OrigemProduto.FORNECEDOR) {
            Insumo saco = insumos.get(itens.getFirst().insumoId());
            if (itens.size() != 1 || saco.getUnidade() != UnidadeMedida.SACO) {
                throw new RegraDeNegocioException("Produto de fornecedor usa exatamente um insumo com unidade SACO.");
            }
            if (saco.getItensPorSaco() == null) {
                throw new RegraDeNegocioException("Informe na tela de Insumos quantos itens vêm no " + saco.getNome() + ".");
            }
            rendimento = saco.getItensPorSaco();
        } else if (rendimento == null) {
            throw new RegraDeNegocioException("Informe quantos itens a receita rende.");
        }

        Map<UUID, ItemReceita> atuais = new HashMap<>();
        for (ItemReceita item : produto.getReceita()) {
            atuais.put(item.getInsumo().getId(), item);
        }
        produto.getReceita().removeIf(item -> !ids.contains(item.getInsumo().getId()));

        for (ItemReceitaRequest pedido : itens) {
            ItemReceita existente = atuais.get(pedido.insumoId());
            if (existente != null) {
                existente.setQuantidade(pedido.quantidade());
            } else {
                produto.getReceita().add(new ItemReceita(produto, insumos.get(pedido.insumoId()), pedido.quantidade()));
            }
        }

        produto.setOrigem(origem);
        produto.setRendimento(rendimento);
        produto.setFatias(fatias);
        recalcularCusto(produto);
    }

    public void recalcularCusto(Produto produto) {

        if (produto.getReceita().isEmpty()) {
            return;
        }

        if (produto.getOrigem() == OrigemProduto.FORNECEDOR) {
            Integer itensPorSaco = produto.getReceita().getFirst().getInsumo().getItensPorSaco();
            if (itensPorSaco != null) {
                produto.setRendimento(itensPorSaco);
            }
        }

        if (produto.getRendimento() == null) {
            return;
        }

        int fatias = (produto.getFatias() != null) ? produto.getFatias() : 1;

        List<CalculadoraCusto.Componente> componentes = produto.getReceita().stream()
                .map(item -> new CalculadoraCusto.Componente(item.getQuantidade(), item.getInsumo().getCustoUnitario()))
                .toList();

        produto.setCusto(calculadoraCusto.custoUnitario(componentes, produto.getRendimento() * fatias));
    }
}

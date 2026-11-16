package org.ericlps.fornadacerta.service;

import lombok.RequiredArgsConstructor;
import org.ericlps.fornadacerta.dto.InsumoPatchRequest;
import org.ericlps.fornadacerta.dto.InsumoRequest;
import org.ericlps.fornadacerta.dto.InsumoResponse;
import org.ericlps.fornadacerta.entity.Insumo;
import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;
import org.ericlps.fornadacerta.exception.ConflitoException;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.mapper.InsumoMapper;
import org.ericlps.fornadacerta.repository.InsumoRepository;
import org.ericlps.fornadacerta.repository.ItemReceitaRepository;
import org.ericlps.fornadacerta.repository.ProdutoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class InsumoService {

    private final InsumoRepository insumoRepository;
    private final ItemReceitaRepository itemReceitaRepository;
    private final ProdutoRepository produtoRepository;
    private final FichaTecnicaService fichaTecnicaService;
    private final InsumoMapper insumoMapper;

    private static final String SEM_ITENS_POR_SACO = "Informe quantos itens vêm em cada saco.";
    private static final String SEM_FORNADAS_POR_BOTIJAO = "Informe quantas fornadas um botijão rende.";

    @Transactional
    public InsumoResponse cadastrar(InsumoRequest request) {

        if (insumoRepository.existsByNomeIgnoreCase(request.nome())) {
            throw new ConflitoException("Já existe um insumo cadastrado como: " + request.nome());
        }

        if (request.unidade() == UnidadeMedida.SACO && request.itensPorSaco() == null) {
            throw new RegraDeNegocioException(SEM_ITENS_POR_SACO);
        }

        if (request.unidade() == UnidadeMedida.GAS && request.fornadasPorBotijao() == null) {
            throw new RegraDeNegocioException(SEM_FORNADAS_POR_BOTIJAO);
        }

        return insumoMapper.toResponse(insumoRepository.save(insumoMapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public List<InsumoResponse> listar(Sort.Direction ordem) {

        return insumoMapper.toResponseList(insumoRepository.findAll(Sort.by(ordem, "nome")));
    }

    @Transactional
    public InsumoResponse atualizarParcialmente(UUID id, InsumoPatchRequest request) {

        Insumo insumo = insumoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Insumo não encontrado"));

        if (request.nome() != null) {
            if (insumoRepository.existsByNomeIgnoreCaseAndIdNot(request.nome(), id)) {
                throw new ConflitoException("Já existe um insumo cadastrado como: " + request.nome());
            }
            insumo.setNome(request.nome());
        }

        if (request.unidade() != null && request.unidade() != insumo.getUnidade()) {
            if (itemReceitaRepository.existsByInsumoId(id)) {
                throw new RegraDeNegocioException("A unidade de " + insumo.getNome()
                        + " não pode mudar porque ele já é usado em fichas técnicas.");
            }
            insumo.setUnidade(request.unidade());
        }

        boolean recalcular = false;

        if (request.itensPorSaco() != null && !request.itensPorSaco().equals(insumo.getItensPorSaco())) {
            insumo.setItensPorSaco(request.itensPorSaco());
            recalcular = true;
        }

        if (insumo.getUnidade() != UnidadeMedida.SACO) {
            insumo.setItensPorSaco(null);
        } else if (insumo.getItensPorSaco() == null) {
            throw new RegraDeNegocioException(SEM_ITENS_POR_SACO);
        }

        if (request.fornadasPorBotijao() != null && !request.fornadasPorBotijao().equals(insumo.getFornadasPorBotijao())) {
            insumo.setFornadasPorBotijao(request.fornadasPorBotijao());
            recalcular = true;
        }

        if (insumo.getUnidade() != UnidadeMedida.GAS) {
            insumo.setFornadasPorBotijao(null);
        } else if (insumo.getFornadasPorBotijao() == null) {
            throw new RegraDeNegocioException(SEM_FORNADAS_POR_BOTIJAO);
        }

        if (request.custoUnitario() != null && request.custoUnitario().compareTo(insumo.getCustoUnitario()) != 0) {
            insumo.setCustoUnitario(request.custoUnitario());
            recalcular = true;
        }

        if (recalcular) {
            produtoRepository.findUsandoInsumo(id).forEach(fichaTecnicaService::recalcularCusto);
        }

        return insumoMapper.toResponse(insumo);
    }
}

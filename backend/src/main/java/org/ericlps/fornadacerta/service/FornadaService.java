package org.ericlps.fornadacerta.service;

import lombok.RequiredArgsConstructor;
import org.ericlps.fornadacerta.dto.FornadaItemRequest;
import org.ericlps.fornadacerta.dto.FornadaLoteRequest;
import org.ericlps.fornadacerta.dto.FornadaPatchRequest;
import org.ericlps.fornadacerta.dto.FornadaResponse;
import org.ericlps.fornadacerta.entity.Fornada;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.entity.enums.Turno;
import org.ericlps.fornadacerta.exception.ConflitoException;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.mapper.FornadaMapper;
import org.ericlps.fornadacerta.repository.FornadaRepository;
import org.ericlps.fornadacerta.repository.ProdutoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@RequiredArgsConstructor
@Service
public class FornadaService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Sort POR_NOME_DO_PRODUTO = Sort.by("produto.nome");


    private final FornadaRepository fornadaRepository;
    private final ProdutoRepository produtoRepository;
    private final FornadaMapper fornadaMapper;

    @Transactional
    public List<FornadaResponse> registrarLote(FornadaLoteRequest request) {

        LocalDate hoje = LocalDate.now(FUSO);
        LocalDate data = (request.data() != null) ? request.data() : hoje;

        if (data.isAfter(hoje.plusDays(1))) {

            throw new RegraDeNegocioException("Só é possível lançar fornadas até o dia de amanhã.");

        }

        Set<UUID> produtosNoLote = new HashSet<>();
        for (FornadaItemRequest item : request.itens()) {

            if (!produtosNoLote.add(item.produtoId())) {

                throw new RegraDeNegocioException("Um mesmo produto aparece mais de uma vez no lote.");

            }

        }

        List<Fornada> fornadas = new ArrayList<>();
        for (FornadaItemRequest item : request.itens()) {

            Produto produto = produtoRepository.findById(item.produtoId()).orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado: " + item.produtoId()));

            if(!produto.getStatus()){

                throw new RegraDeNegocioException("O produto: " + produto.getNome() + " está desativado");

            }

            if(fornadaRepository.existsByDataAndProdutoIdAndTurno(data, produto.getId(), request.turno())){

                throw new ConflitoException("A fornada de " + produto.getNome() + " no turno " + request.turno() + "de " + data + " já foi registrada.");

            }

            fornadas.add(fornadaMapper.toEntity(item, produto,data,request.turno()));

        }

        return fornadaMapper.toResponseList(fornadaRepository.saveAll(fornadas));

    }

    @Transactional(readOnly = true)
    public List<FornadaResponse> listar(LocalDate data, Turno turno){

        LocalDate dia = (data != null) ? data : LocalDate.now(FUSO);

        List<Fornada> fornadas = (turno == null)
                ? fornadaRepository.findByData(dia, POR_NOME_DO_PRODUTO)
                : fornadaRepository.findByDataAndTurno(dia, turno, POR_NOME_DO_PRODUTO);

        return fornadaMapper.toResponseList(fornadas);

    }

    @Transactional
    public FornadaResponse atualizarParcialmente(UUID id, FornadaPatchRequest request){

        Fornada fornada = buscar(id);

        if(request.qtdProduzida() != null){

            fornada.setQtdProduzida(request.qtdProduzida());

        }

        if(request.pacotesUsados() != null){

            fornada.setPacotesUsados(request.pacotesUsados());

        }

        return fornadaMapper.toResponse(fornada);

    }

    @Transactional
    public void excluir(UUID id){

        fornadaRepository.delete(buscar(id));

    }

    private Fornada buscar(UUID id){

        return fornadaRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Fornada não encontrada"));

    }

}

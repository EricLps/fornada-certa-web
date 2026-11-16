package org.ericlps.fornadacerta.service;

import lombok.RequiredArgsConstructor;
import org.ericlps.fornadacerta.dto.FechamentoItemRequest;
import org.ericlps.fornadacerta.dto.FechamentoLoteRequest;
import org.ericlps.fornadacerta.dto.FechamentoResponse;
import org.ericlps.fornadacerta.entity.Fechamento;
import org.ericlps.fornadacerta.entity.Fornada;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.entity.enums.OrigemProduto;
import org.ericlps.fornadacerta.exception.ConflitoException;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.mapper.FechamentoMapper;
import org.ericlps.fornadacerta.repository.FechamentoRepository;
import org.ericlps.fornadacerta.repository.FornadaRepository;
import org.ericlps.fornadacerta.repository.ProdutoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class FechamentoService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Sort POR_NOME_DO_PRODUTO = Sort.by("produto.nome");

    private final FechamentoRepository fechamentoRepository;
    private final FornadaRepository fornadaRepository;
    private final ProdutoRepository produtoRepository;
    private final FechamentoMapper fechamentoMapper;

    @Transactional
    public List<FechamentoResponse> registrarLote(FechamentoLoteRequest request) {

        LocalDate hoje = LocalDate.now(FUSO);
        LocalDate data = (request.data() != null) ? request.data() : hoje;

        if (data.isAfter(hoje)) {
            throw new RegraDeNegocioException("Não é possível fechar um dia que ainda não aconteceu.");
        }

        Set<UUID> produtosNoLote = new HashSet<>();
        for (FechamentoItemRequest item : request.itens()) {
            if (!produtosNoLote.add(item.produtoId())) {
                throw new RegraDeNegocioException("Um mesmo produto aparece mais de uma vez no fechamento.");
            }
        }

        List<Fechamento> fechamentos = new ArrayList<>();
        for (FechamentoItemRequest item : request.itens()) {

            Produto produto = produtoRepository.findById(item.produtoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado: " + item.produtoId()));

            validarItem(item, produto);

            if (fechamentoRepository.existsByDataAndProdutoId(data, produto.getId())) {
                throw new ConflitoException("O dia " + data + " já foi fechado para " + produto.getNome() + ".");
            }

            long produzido = fornadaRepository.somarProducaoDoDia(produto.getId(), data);

            if (produzido == 0) {
                throw new RegraDeNegocioException("Não houve fornada de " + produto.getNome() + " em " + data + ".");
            }

            if (item.qtdSobra() > produzido) {
                throw new RegraDeNegocioException("A sobra de " + produto.getNome() + " (" + item.qtdSobra()
                        + ") não pode passar do total produzido no dia (" + produzido + ").");
            }

            fechamentos.add(fechamentoMapper.toEntity(item, produto, data, custoDoDia(produto, data, produzido)));
        }

        return fechamentoMapper.toResponseList(fechamentoRepository.saveAll(fechamentos));
    }

    @Transactional(readOnly = true)
    public List<FechamentoResponse> listar(LocalDate data) {

        LocalDate dia = (data != null) ? data : LocalDate.now(FUSO);

        return fechamentoMapper.toResponseList(fechamentoRepository.findByData(dia, POR_NOME_DO_PRODUTO));
    }

    private BigDecimal custoDoDia(Produto produto, LocalDate data, long produzido) {

        if (produto.getOrigem() != OrigemProduto.FORNECEDOR || produto.getReceita().isEmpty()) {
            return produto.getCusto();
        }

        BigDecimal custoDoSaco = produto.getReceita().stream()
                .map(item -> item.getQuantidade().multiply(item.getInsumo().custoPorUnidadeDeUso()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = BigDecimal.ZERO;

        for (Fornada fornada : fornadaRepository.findByProdutoIdAndData(produto.getId(), data)) {
            BigDecimal custoDaFornada = (fornada.getPacotesUsados() != null)
                    ? custoDoSaco.multiply(BigDecimal.valueOf(fornada.getPacotesUsados()))
                    : produto.getCusto().multiply(BigDecimal.valueOf(fornada.getQtdProduzida()));
            total = total.add(custoDaFornada);
        }

        return total.divide(BigDecimal.valueOf(produzido), 2, RoundingMode.HALF_UP);
    }

    private void validarItem(FechamentoItemRequest item, Produto produto) {

        int destinado = item.doadaOuZero() + item.reaproveitadaOuZero();

        if (destinado > item.qtdSobra()) {
            throw new RegraDeNegocioException("Em " + produto.getNome() + ", doado + reaproveitado (" + destinado
                    + ") não pode passar da sobra (" + item.qtdSobra() + ").");
        }

        if (item.horarioEsgotou() != null && item.qtdSobra() > 0) {
            throw new RegraDeNegocioException("Em " + produto.getNome()
                    + ", o horário em que esgotou só pode ser informado quando não há sobra.");
        }
    }
}

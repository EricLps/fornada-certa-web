package org.ericlps.fornadacerta.service;

import lombok.RequiredArgsConstructor;
import org.ericlps.fornadacerta.calculo.CalculadoraSugestao;
import org.ericlps.fornadacerta.calculo.ResultadoSugestao;
import org.ericlps.fornadacerta.calculo.VendaDiaria;
import org.ericlps.fornadacerta.dto.SugestaoResponse;
import org.ericlps.fornadacerta.entity.Fechamento;
import org.ericlps.fornadacerta.entity.Fornada;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.repository.FechamentoRepository;
import org.ericlps.fornadacerta.repository.FornadaRepository;
import org.ericlps.fornadacerta.repository.ProdutoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class SugestaoService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final ProdutoRepository produtoRepository;
    private final FornadaRepository fornadaRepository;
    private final FechamentoRepository fechamentoRepository;
    private final CalculadoraSugestao calculadora;

    @Transactional(readOnly = true)
    public List<SugestaoResponse> sugerir(LocalDate data) {

        LocalDate alvo = (data != null) ? data : LocalDate.now(FUSO).plusDays(1);
        LocalDate inicio = alvo.minusWeeks(CalculadoraSugestao.SEMANAS_DE_HISTORICO);
        LocalDate fim = alvo.minusDays(1);

        Map<UUID, List<VendaDiaria>> vendasPorProduto = montarVendas(inicio, fim);
        String diaDaSemana = alvo.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR);

        List<SugestaoResponse> sugestoes = new ArrayList<>();
        for (Produto produto : produtoRepository.findByStatus(true, Sort.by("nome"))) {

            List<VendaDiaria> historico = vendasPorProduto.getOrDefault(produto.getId(), List.of());
            ResultadoSugestao resultado = calculadora.calcular(alvo, historico);

            sugestoes.add(new SugestaoResponse(
                    produto.getId(),
                    produto.getNome(),
                    alvo,
                    diaDaSemana,
                    resultado.criterio(),
                    resultado.amostras(),
                    resultado.mediaVendida(),
                    resultado.qtdSugerida(),
                    resultado.aviso(),
                    produto.getFatias()));
        }
        return sugestoes;
    }

    private Map<UUID, List<VendaDiaria>> montarVendas(LocalDate inicio, LocalDate fim) {

        Map<UUID, Map<LocalDate, Integer>> producao = new HashMap<>();
        for (Fornada fornada : fornadaRepository.findByDataBetween(inicio, fim)) {
            producao.computeIfAbsent(fornada.getProduto().getId(), id -> new HashMap<>())
                    .merge(fornada.getData(), fornada.getQtdProduzida(), Integer::sum);
        }

        Map<UUID, List<VendaDiaria>> vendas = new HashMap<>();
        for (Fechamento fechamento : fechamentoRepository.findByDataBetween(inicio, fim)) {
            UUID produtoId = fechamento.getProduto().getId();
            int produzido = producao.getOrDefault(produtoId, Map.of()).getOrDefault(fechamento.getData(), 0);
            int vendido = produzido - fechamento.getQtdSobra();
            boolean esgotou = fechamento.getHorarioEsgotou() != null;

            vendas.computeIfAbsent(produtoId, id -> new ArrayList<>())
                    .add(new VendaDiaria(fechamento.getData(), vendido, esgotou));
        }
        return vendas;
    }
}

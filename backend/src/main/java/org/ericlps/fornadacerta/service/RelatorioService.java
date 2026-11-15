package org.ericlps.fornadacerta.service;

import lombok.RequiredArgsConstructor;
import org.ericlps.fornadacerta.dto.RelatorioResponse;
import org.ericlps.fornadacerta.entity.Fechamento;
import org.ericlps.fornadacerta.entity.Fornada;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.repository.FechamentoRepository;
import org.ericlps.fornadacerta.repository.FornadaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class RelatorioService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final int DIAS_PADRAO = 7;

    private final FornadaRepository fornadaRepository;
    private final FechamentoRepository fechamentoRepository;

    @Transactional(readOnly = true)
    public RelatorioResponse gerar(LocalDate inicio, LocalDate fim) {

        LocalDate ultimoDia = (fim != null) ? fim : LocalDate.now(FUSO).minusDays(1);
        LocalDate primeiroDia = (inicio != null) ? inicio : ultimoDia.minusDays(DIAS_PADRAO - 1);

        if (primeiroDia.isAfter(ultimoDia)) {
            throw new RegraDeNegocioException("A data inicial não pode ser depois da data final.");
        }

        Map<UUID, Map<LocalDate, Integer>> producao = new HashMap<>();
        for (Fornada fornada : fornadaRepository.findByDataBetween(primeiroDia, ultimoDia)) {
            producao.computeIfAbsent(fornada.getProduto().getId(), id -> new HashMap<>())
                    .merge(fornada.getData(), fornada.getQtdProduzida(), Integer::sum);
        }

        Acumulador total = new Acumulador();
        Map<UUID, Acumulador> porProduto = new LinkedHashMap<>();
        Map<UUID, String> nomes = new HashMap<>();
        Map<LocalDate, Acumulador> porDia = new LinkedHashMap<>();
        Set<LocalDate> diasFechados = new HashSet<>();

        for (LocalDate dia = primeiroDia; !dia.isAfter(ultimoDia); dia = dia.plusDays(1)) {
            porDia.put(dia, new Acumulador());
        }

        for (Fechamento fechamento : fechamentoRepository.findByDataBetween(primeiroDia, ultimoDia)) {
            UUID produtoId = fechamento.getProduto().getId();
            int produzido = producao.getOrDefault(produtoId, Map.of()).getOrDefault(fechamento.getData(), 0);

            nomes.put(produtoId, fechamento.getProduto().getNome());
            diasFechados.add(fechamento.getData());

            total.somar(fechamento, produzido);
            porProduto.computeIfAbsent(produtoId, id -> new Acumulador()).somar(fechamento, produzido);
            porDia.get(fechamento.getData()).somar(fechamento, produzido);
        }

        List<RelatorioResponse.PorProduto> produtos = new ArrayList<>();
        porProduto.forEach((produtoId, a) -> produtos.add(new RelatorioResponse.PorProduto(
                produtoId, nomes.get(produtoId), a.produzido, a.vendido(), a.descartado, a.custo, a.taxa())));
        produtos.sort(Comparator.comparing(RelatorioResponse.PorProduto::custoDescarte).reversed());

        List<RelatorioResponse.PorDia> dias = new ArrayList<>();
        porDia.forEach((dia, a) -> dias.add(new RelatorioResponse.PorDia(
                dia, dia.getDayOfWeek().getDisplayName(TextStyle.SHORT, PT_BR), a.produzido, a.vendido(), a.descartado)));

        RelatorioResponse.Totais totais = new RelatorioResponse.Totais(
                diasFechados.size(), total.produzido, total.vendido(), total.sobra, total.doado,
                total.reaproveitado, total.descartado, total.custo, total.taxa());

        return new RelatorioResponse(primeiroDia, ultimoDia, totais, produtos, dias);
    }

    private static class Acumulador {

        private int produzido;
        private int sobra;
        private int doado;
        private int reaproveitado;
        private int descartado;
        private BigDecimal custo = BigDecimal.ZERO;

        void somar(Fechamento fechamento, int produzidoNoDia) {
            int descarteDoDia = fechamento.getQtdSobra() - fechamento.getQtdDoada() - fechamento.getQtdReaproveitada();

            produzido += produzidoNoDia;
            sobra += fechamento.getQtdSobra();
            doado += fechamento.getQtdDoada();
            reaproveitado += fechamento.getQtdReaproveitada();
            descartado += descarteDoDia;
            custo = custo.add(fechamento.getCustoUnitario().multiply(BigDecimal.valueOf(descarteDoDia)));
        }

        int vendido() {
            return produzido - sobra;
        }

        BigDecimal taxa() {
            if (produzido == 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(descartado).divide(BigDecimal.valueOf(produzido), 4, RoundingMode.HALF_UP);
        }
    }
}

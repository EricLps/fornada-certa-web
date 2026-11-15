package org.ericlps.fornadacerta.controller;

import org.ericlps.fornadacerta.dto.RelatorioResponse;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.service.RelatorioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RelatorioController.class)
class RelatorioControllerTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 8);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 14);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RelatorioService relatorioService;

    private RelatorioResponse relatorio() {
        return new RelatorioResponse(INICIO, FIM,
                new RelatorioResponse.Totais(7, 1000, 940, 60, 20, 10, 30, new BigDecimal("15.00"), new BigDecimal("0.0300")),
                List.of(new RelatorioResponse.PorProduto(UUID.randomUUID(), "Pão francês", 1000, 940, 30,
                        new BigDecimal("15.00"), new BigDecimal("0.0300"))),
                List.of(new RelatorioResponse.PorDia(FIM, "sáb.", 150, 140, 5)));
    }

    @Test
    @DisplayName("com início e fim: converte as datas e devolve totais, produtos e dias")
    void comDatas() throws Exception {
        when(relatorioService.gerar(INICIO, FIM)).thenReturn(relatorio());

        mockMvc.perform(get("/relatorio").param("inicio", "2026-11-08").param("fim", "2026-11-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value("2026-11-08"))
                .andExpect(jsonPath("$.fim").value("2026-11-14"))
                .andExpect(jsonPath("$.total.diasFechados").value(7))
                .andExpect(jsonPath("$.total.descartado").value(30))
                .andExpect(jsonPath("$.total.custoDescarte").value(15.0))
                .andExpect(jsonPath("$.total.taxaDesperdicio").value(0.03))
                .andExpect(jsonPath("$.porProduto[0].produtoNome").value("Pão francês"))
                .andExpect(jsonPath("$.porDia[0].diaDaSemana").value("sáb."));
    }

    @Test
    @DisplayName("sem datas: repassa nulos (o service assume os últimos 7 dias)")
    void semDatas() throws Exception {
        when(relatorioService.gerar(null, null)).thenReturn(relatorio());

        mockMvc.perform(get("/relatorio"))
                .andExpect(status().isOk());

        verify(relatorioService).gerar(null, null);
    }

    @Test
    @DisplayName("data em formato errado: 400 sem chamar o service")
    void dataInvalida() throws Exception {
        mockMvc.perform(get("/relatorio").param("inicio", "08/11/2026"))
                .andExpect(status().isBadRequest());

        verify(relatorioService, never()).gerar(any(), any());
    }

    @Test
    @DisplayName("início depois do fim: 422 com a mensagem do service")
    void periodoInvertido() throws Exception {
        when(relatorioService.gerar(FIM, INICIO))
                .thenThrow(new RegraDeNegocioException("A data inicial não pode ser depois da data final."));

        mockMvc.perform(get("/relatorio").param("inicio", "2026-11-14").param("fim", "2026-11-08"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.detail").value("A data inicial não pode ser depois da data final."));
    }
}

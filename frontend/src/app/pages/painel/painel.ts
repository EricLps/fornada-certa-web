import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Relatorio } from '../../models/relatorio';
import { Sugestao } from '../../models/sugestao';
import { tratarErroApi } from '../../services/erro-api';
import { RelatorioService } from '../../services/relatorio.service';
import { SugestaoService } from '../../services/sugestao.service';
import { hojeIso, somarDias } from '../../utils/datas';

@Component({
  selector: 'app-painel',
  host: { class: 'pagina' },
  imports: [RouterLink, CurrencyPipe, DatePipe, DecimalPipe, PercentPipe],
  templateUrl: './painel.html',
  styleUrl: './painel.css',
})
export class Painel implements OnInit {

  private readonly relatorioService = inject(RelatorioService);
  private readonly sugestaoService = inject(SugestaoService);

  readonly hoje = hojeIso();
  readonly amanha = somarDias(this.hoje, 1);
  private readonly fim = somarDias(this.hoje, -1);
  private readonly inicio = somarDias(this.fim, -6);

  readonly semana = signal<Relatorio | null>(null);
  readonly anterior = signal<Relatorio | null>(null);
  readonly sugestoes = signal<Sugestao[]>([]);
  readonly carregando = signal(true);
  readonly erro = signal<string | null>(null);

  readonly variacaoTaxa = computed(() => {
    const atual = this.semana()?.total;
    const antes = this.anterior()?.total;
    if (!atual || !antes || antes.produzido === 0) {
      return null;
    }
    return (atual.taxaDesperdicio - antes.taxaDesperdicio) * 100;
  });

  readonly variacaoVendas = computed(() => {
    const atual = this.semana()?.total;
    const antes = this.anterior()?.total;
    if (!atual || !antes || antes.vendido === 0) {
      return null;
    }
    return (atual.vendido - antes.vendido) / antes.vendido;
  });

  readonly maiorProducao = computed(() =>
    Math.max(1, ...(this.semana()?.porDia ?? []).map(d => d.produzido)));

  readonly totalSugerido = computed(() =>
    this.sugestoes().reduce((soma, s) => soma + (s.qtdSugerida ?? 0), 0));

  ngOnInit(): void {
    forkJoin({
      semana: this.relatorioService.gerar(this.inicio, this.fim),
      anterior: this.relatorioService.gerar(somarDias(this.inicio, -7), somarDias(this.fim, -7)),
      sugestoes: this.sugestaoService.sugerir(this.amanha),
    }).subscribe({
      next: ({ semana, anterior, sugestoes }) => {
        this.semana.set(semana);
        this.anterior.set(anterior);
        this.sugestoes.set(sugestoes);
        this.carregando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.erro.set(tratarErroApi(e).mensagem);
        this.carregando.set(false);
      },
    });
  }

  altura(valor: number): string {
    return `${(valor / this.maiorProducao()) * 85}%`;
  }
}

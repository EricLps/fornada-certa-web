import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { Fechamento as FechamentoRegistrado, FechamentoItemRequest } from '../../models/fechamento';
import { Fornada } from '../../models/fornada';
import { Produto } from '../../models/produto';
import { tratarErroApi } from '../../services/erro-api';
import { FechamentoService } from '../../services/fechamento.service';
import { FornadaService } from '../../services/fornada.service';
import { ProdutoService } from '../../services/produto.service';
import { hojeIso, somarDias } from '../../utils/datas';

interface Linha {
  produtoId: string;
  nome: string;
  custo: number;
  produzido: number;
  fechado: FechamentoRegistrado | null;
  sobra: number | null;
  doada: number | null;
  reaproveitada: number | null;
  esgotou: string | null;
  erro: string | null;
}

@Component({
  selector: 'app-fechamento',
  imports: [CurrencyPipe, DatePipe, PercentPipe],
  templateUrl: './fechamento.html',
  styleUrl: './fechamento.css',
})
export class Fechamento implements OnInit {

  private readonly produtoService = inject(ProdutoService);
  private readonly fornadaService = inject(FornadaService);
  private readonly fechamentoService = inject(FechamentoService);

  readonly hoje = hojeIso();

  readonly data = signal(new Date().getHours() < 6 ? somarDias(this.hoje, -1) : this.hoje);
  readonly linhas = signal<Linha[]>([]);
  readonly carregando = signal(false);
  readonly salvando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly sucesso = signal<string | null>(null);

  readonly pendentes = computed(() => this.linhas().filter(l => l.fechado === null));

  readonly resumo = computed(() => {
    let produzido = 0;
    let sobra = 0;
    let descartado = 0;
    let custo = 0;

    for (const l of this.linhas()) {
      const sobraLinha = l.fechado?.qtdSobra ?? l.sobra;
      if (sobraLinha === null) {
        continue;
      }
      const descarte = this.descarte(l) ?? 0;
      produzido += l.produzido;
      sobra += sobraLinha;
      descartado += descarte;
      custo += l.fechado?.custoDescarte ?? descarte * l.custo;
    }

    return {
      produzido,
      vendido: produzido - sobra,
      sobra,
      descartado,
      custo,
      taxa: produzido > 0 ? descartado / produzido : 0,
    };
  });

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);

    forkJoin({
      produtos: this.produtoService.listar(),
      fornadas: this.fornadaService.listar(this.data()),
      fechamentos: this.fechamentoService.listar(this.data()),
    }).subscribe({
      next: ({ produtos, fornadas, fechamentos }) => {
        this.linhas.set(this.montarLinhas(produtos, fornadas, fechamentos));
        this.carregando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.erro.set(tratarErroApi(e).mensagem);
        this.carregando.set(false);
      },
    });
  }

  mudarData(data: string): void {
    if (!data) {
      return;
    }
    this.data.set(data);
    this.sucesso.set(null);
    this.carregar();
  }

  definir(produtoId: string, campo: 'sobra' | 'doada' | 'reaproveitada', valor: number): void {
    const mudancas: Partial<Linha> = { [campo]: Number.isNaN(valor) ? null : valor, erro: null };
    if (campo === 'sobra' && valor !== 0) {
      mudancas.esgotou = null;
    }
    this.atualizarLinha(produtoId, mudancas);
  }

  definirEsgotou(produtoId: string, horario: string): void {
    this.atualizarLinha(produtoId, { esgotou: horario || null, erro: null });
  }

  descarte(linha: Linha): number | null {
    if (linha.fechado) {
      return linha.fechado.qtdDescartada;
    }
    if (linha.sobra === null) {
      return null;
    }
    return Math.max(0, linha.sobra - (linha.doada ?? 0) - (linha.reaproveitada ?? 0));
  }

  fecharDia(): void {
    const preenchidas = this.pendentes().filter(l => l.sobra !== null);

    if (preenchidas.length === 0) {
      this.erro.set('Informe a sobra de pelo menos um produto.');
      return;
    }

    const comErro = preenchidas.filter(l => this.validar(l));
    if (comErro.length > 0) {
      this.erro.set('Corrija os produtos marcados antes de fechar o dia.');
      return;
    }

    const itens: FechamentoItemRequest[] = preenchidas.map(l => ({
      produtoId: l.produtoId,
      qtdSobra: l.sobra!,
      qtdDoada: l.doada,
      qtdReaproveitada: l.reaproveitada,
      horarioEsgotou: l.esgotou,
    }));

    this.salvando.set(true);
    this.erro.set(null);
    this.sucesso.set(null);

    this.fechamentoService.registrarLote({ data: this.data(), itens }).subscribe({
      next: registrados => {
        this.sucesso.set(`Dia fechado para ${registrados.length} produto(s).`);
        this.salvando.set(false);
        this.carregar();
      },
      error: (e: HttpErrorResponse) => {
        const tratado = tratarErroApi(e);
        this.erro.set(tratado.mensagem);
        for (const [caminho, mensagem] of Object.entries(tratado.campos)) {
          const linha = preenchidas[Number(caminho.match(/^itens\[(\d+)\]/)?.[1])];
          if (linha) {
            this.atualizarLinha(linha.produtoId, { erro: mensagem });
          }
        }
        this.salvando.set(false);
      },
    });
  }

  private validar(linha: Linha): boolean {
    const sobra = linha.sobra ?? 0;
    const destinado = (linha.doada ?? 0) + (linha.reaproveitada ?? 0);
    let erro: string | null = null;

    if (sobra > linha.produzido) {
      erro = `A sobra não pode passar do que foi produzido (${linha.produzido}).`;
    } else if (destinado > sobra) {
      erro = `Doado + reaproveitado (${destinado}) não pode passar da sobra (${sobra}).`;
    } else if (linha.esgotou && sobra > 0) {
      erro = 'O horário em que esgotou só vale quando não há sobra.';
    }

    this.atualizarLinha(linha.produtoId, { erro });
    return erro !== null;
  }

  private montarLinhas(produtos: Produto[], fornadas: Fornada[], fechamentos: FechamentoRegistrado[]): Linha[] {
    const producao = new Map<string, number>();
    for (const f of fornadas) {
      producao.set(f.produtoId, (producao.get(f.produtoId) ?? 0) + f.qtdProduzida);
    }

    return [...producao.entries()]
      .map(([produtoId, produzido]) => {
        const produto = produtos.find(p => p.id === produtoId);
        return {
          produtoId,
          nome: produto?.nome ?? fornadas.find(f => f.produtoId === produtoId)!.produtoNome,
          custo: produto?.custo ?? 0,
          produzido,
          fechado: fechamentos.find(f => f.produtoId === produtoId) ?? null,
          sobra: null,
          doada: null,
          reaproveitada: null,
          esgotou: null,
          erro: null,
        };
      })
      .sort((a, b) => a.nome.localeCompare(b.nome));
  }

  private atualizarLinha(produtoId: string, mudancas: Partial<Linha>): void {
    this.linhas.update(linhas => linhas.map(l => l.produtoId === produtoId ? { ...l, ...mudancas } : l));
  }
}

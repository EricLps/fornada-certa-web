import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { Fornada as FornadaRegistrada, FornadaItemRequest, Turno } from '../../models/fornada';
import { Produto } from '../../models/produto';
import { tratarErroApi } from '../../services/erro-api';
import { FornadaService } from '../../services/fornada.service';
import { ProdutoService } from '../../services/produto.service';
import { SugestaoService } from '../../services/sugestao.service';
import { hojeIso, somarDias } from '../../utils/datas';

interface Linha {
  produto: Produto;
  sugestaoDoDia: number | null;
  aviso: string | null;
  produzidoNoDia: number;
  lancadaNoTurno: number | null;
  quantidade: number | null;
  pacotes: number | null;
  erro: string | null;
}

@Component({
  selector: 'app-fornada',
  host: { class: 'pagina' },
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './fornada.html',
  styleUrl: './fornada.css',
})
export class Fornada implements OnInit {

  private readonly produtoService = inject(ProdutoService);
  private readonly fornadaService = inject(FornadaService);
  private readonly sugestaoService = inject(SugestaoService);

  readonly turnos: { valor: Turno; rotulo: string }[] = [
    { valor: 'MANHA', rotulo: 'Manhã (7h)' },
    { valor: 'TARDE', rotulo: 'Tarde (16h/18h)' },
  ];

  readonly hoje = hojeIso();
  readonly amanha = somarDias(this.hoje, 1);

  readonly data = signal(this.hoje);
  readonly turno = signal<Turno>('TARDE');
  readonly linhas = signal<Linha[]>([]);
  readonly carregando = signal(false);
  readonly salvando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly sucesso = signal<string | null>(null);

  readonly pendentes = computed(() => this.linhas().filter(l => l.lancadaNoTurno === null));
  readonly totalItens = computed(() => this.pendentes().reduce((soma, l) => soma + (l.quantidade ?? 0), 0));
  readonly totalSugerido = computed(() => this.pendentes().reduce((soma, l) => soma + (this.restante(l) ?? 0), 0));
  readonly custoTotal = computed(() => this.pendentes().reduce((soma, l) => soma + (l.quantidade ?? 0) * l.produto.custo, 0));
  readonly receitaTotal = computed(() => this.pendentes().reduce((soma, l) => soma + (l.quantidade ?? 0) * l.produto.preco, 0));

  ngOnInit(): void {
    this.sugerirMomento();
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);

    forkJoin({
      produtos: this.produtoService.listar(true),
      sugestoes: this.sugestaoService.sugerir(this.data()),
      fornadas: this.fornadaService.listar(this.data()),
    }).subscribe({
      next: ({ produtos, sugestoes, fornadas }) => {
        this.linhas.set(produtos.map(produto => this.montarLinha(produto, sugestoes, fornadas)));
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

  mudarTurno(turno: Turno): void {
    this.turno.set(turno);
    this.sucesso.set(null);
    this.carregar();
  }

  restante(linha: Linha): number | null {
    if (linha.sugestaoDoDia === null) {
      return null;
    }
    return Math.max(0, linha.sugestaoDoDia - linha.produzidoNoDia);
  }

  definirQuantidade(produtoId: string, valor: number): void {
    this.atualizarLinha(produtoId, { quantidade: Number.isNaN(valor) ? null : valor, erro: null });
  }

  definirPacotes(produtoId: string, valor: number): void {
    this.atualizarLinha(produtoId, { pacotes: Number.isNaN(valor) ? null : valor });
  }

  aplicarSugestoes(): void {
    this.linhas.update(linhas => linhas.map(l =>
      l.lancadaNoTurno === null && this.restante(l) !== null
        ? { ...l, quantidade: this.restante(l), erro: null }
        : l));
  }

  registrar(): void {
    const aEnviar = this.pendentes().filter(l => (l.quantidade ?? 0) > 0);

    if (aEnviar.length === 0) {
      this.erro.set('Informe a quantidade de pelo menos um produto.');
      return;
    }

    const itens: FornadaItemRequest[] = aEnviar.map(l => ({
      produtoId: l.produto.id,
      qtdProduzida: l.quantidade!,
      pacotesUsados: l.pacotes,
    }));

    this.salvando.set(true);
    this.erro.set(null);
    this.sucesso.set(null);

    this.fornadaService.registrarLote({ data: this.data(), turno: this.turno(), itens }).subscribe({
      next: registradas => {
        const total = registradas.reduce((soma, f) => soma + f.qtdProduzida, 0);
        this.sucesso.set(`Fornada registrada: ${total} itens de ${registradas.length} produto(s).`);
        this.salvando.set(false);
        this.carregar();
      },
      error: (e: HttpErrorResponse) => {
        const tratado = tratarErroApi(e);
        this.erro.set(tratado.mensagem);
        this.marcarErrosNasLinhas(tratado.campos, aEnviar);
        this.salvando.set(false);
      },
    });
  }

  private montarLinha(produto: Produto, sugestoes: { produtoId: string; qtdSugerida: number | null; aviso: string | null }[], fornadas: FornadaRegistrada[]): Linha {
    const sugestao = sugestoes.find(s => s.produtoId === produto.id);
    const doProduto = fornadas.filter(f => f.produtoId === produto.id);
    const noTurno = doProduto.find(f => f.turno === this.turno());

    return {
      produto,
      sugestaoDoDia: sugestao?.qtdSugerida ?? null,
      aviso: sugestao?.aviso ?? null,
      produzidoNoDia: doProduto.reduce((soma, f) => soma + f.qtdProduzida, 0),
      lancadaNoTurno: noTurno?.qtdProduzida ?? null,
      quantidade: null,
      pacotes: null,
      erro: null,
    };
  }

  private atualizarLinha(produtoId: string, mudancas: Partial<Linha>): void {
    this.linhas.update(linhas => linhas.map(l => l.produto.id === produtoId ? { ...l, ...mudancas } : l));
  }

  private marcarErrosNasLinhas(campos: Record<string, string>, enviadas: Linha[]): void {
    for (const [caminho, mensagem] of Object.entries(campos)) {
      const indice = Number(caminho.match(/^itens\[(\d+)\]/)?.[1]);
      const linha = enviadas[indice];
      if (linha) {
        this.atualizarLinha(linha.produto.id, { erro: mensagem });
      }
    }
  }

  private sugerirMomento(): void {
    const hora = new Date().getHours();
    if (hora >= 18) {
      this.data.set(this.amanha);
      this.turno.set('MANHA');
    } else if (hora < 12) {
      this.turno.set('MANHA');
    }
  }
}

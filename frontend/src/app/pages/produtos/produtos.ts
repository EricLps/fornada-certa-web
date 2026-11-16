import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { Insumo, siglaDaUnidade } from '../../models/insumo';
import { ItemReceita, OrigemProduto, Produto, ProdutoDetalhe, ProdutoPatch, ProdutoRequest } from '../../models/produto';
import { tratarErroApi } from '../../services/erro-api';
import { InsumoService } from '../../services/insumo.service';
import { ProdutoService } from '../../services/produto.service';

type Filtro = 'ativos' | 'inativos' | 'todos';
type Modo = 'fechado' | 'novo' | 'editando';

interface LinhaReceita {
  insumo: Insumo;
  quantidade: number | null;
}

@Component({
  selector: 'app-produtos',
  host: { class: 'pagina' },
  imports: [ReactiveFormsModule, CurrencyPipe, RouterLink],
  templateUrl: './produtos.html',
  styleUrl: './produtos.css',
})
export class Produtos implements OnInit {

  private readonly produtoService = inject(ProdutoService);
  private readonly insumoService = inject(InsumoService);
  private readonly fb = inject(FormBuilder);

  private readonly modal = viewChild.required<ElementRef<HTMLDialogElement>>('modal');

  readonly sigla = siglaDaUnidade;

  readonly filtros: { valor: Filtro; rotulo: string }[] = [
    { valor: 'ativos', rotulo: 'Ativos' },
    { valor: 'inativos', rotulo: 'Inativos' },
    { valor: 'todos', rotulo: 'Todos' },
  ];

  readonly origens: { valor: OrigemProduto; rotulo: string }[] = [
    { valor: 'PRODUCAO_PROPRIA', rotulo: 'Produção própria' },
    { valor: 'FORNECEDOR', rotulo: 'Fornecedor' },
  ];

  readonly filtro = signal<Filtro>('ativos');
  readonly modo = signal<Modo>('fechado');
  readonly produtos = signal<Produto[]>([]);
  readonly insumos = signal<Insumo[]>([]);
  readonly busca = signal('');
  readonly selecionado = signal<Produto | null>(null);
  readonly carregando = signal(false);
  readonly carregandoFicha = signal(false);
  readonly salvando = signal(false);
  readonly tentouSalvar = signal(false);
  readonly erro = signal<string | null>(null);
  readonly errosCampo = signal<Record<string, string>>({});
  readonly sucesso = signal<string | null>(null);

  readonly origem = signal<OrigemProduto>('PRODUCAO_PROPRIA');
  readonly receita = signal<LinhaReceita[]>([]);
  readonly sacoId = signal<string | null>(null);
  readonly semFicha = signal(false);
  readonly emFatias = signal(false);

  readonly buscaModal = signal('');
  readonly rascunho = signal<Record<string, number | null>>({});

  readonly visiveis = computed(() => {
    const termo = this.busca().trim().toLowerCase();
    return this.produtos().filter(p => p.nome.toLowerCase().includes(termo));
  });

  readonly sacos = computed(() => this.insumos().filter(i => i.unidade === 'SACO'));

  readonly insumosDoModal = computed(() => {
    const termo = this.buscaModal().trim().toLowerCase();
    return this.insumos().filter(i => i.nome.toLowerCase().includes(termo));
  });

  readonly selecionadosNoModal = computed(() => Object.keys(this.rascunho()).length);

  readonly sacoEscolhido = computed(() => this.sacos().find(s => s.id === this.sacoId()) ?? null);

  readonly custoDaReceita = computed(() => {
    if (this.origem() === 'FORNECEDOR') {
      return this.sacos().find(s => s.id === this.sacoId())?.custoUnitario ?? null;
    }
    const linhas = this.receita();
    if (linhas.length === 0) {
      return null;
    }
    return linhas.reduce((total, l) => total + (l.quantidade ?? 0) * l.insumo.custoUnitario, 0);
  });

  readonly form = this.fb.group({
    nome: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(100)]),
    preco: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    rendimento: this.fb.control<number | null>(null, [Validators.min(1), Validators.pattern(/^\d+$/)]),
    fatias: this.fb.control<number | null>(null, [Validators.min(2), Validators.pattern(/^\d+$/)]),
  });

  ngOnInit(): void {
    this.carregar();
    this.insumoService.listar().subscribe({
      next: insumos => this.insumos.set(insumos),
      error: (e: HttpErrorResponse) => this.erro.set(tratarErroApi(e).mensagem),
    });
  }

  carregar(): void {
    const status = { ativos: true, inativos: false, todos: undefined }[this.filtro()];
    this.carregando.set(true);

    this.produtoService.listar(status).subscribe({
      next: produtos => {
        this.produtos.set(produtos);
        this.carregando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.erro.set(tratarErroApi(e).mensagem);
        this.carregando.set(false);
      },
    });
  }

  escolherFiltro(filtro: Filtro): void {
    this.filtro.set(filtro);
    this.carregar();
  }

  novo(): void {
    this.selecionado.set(null);
    this.form.reset();
    this.limparFicha();
    this.limparMensagens();
    this.modo.set('novo');
  }

  editar(produto: Produto): void {
    this.selecionado.set(produto);
    this.form.setValue({ nome: produto.nome, preco: produto.preco, rendimento: produto.rendimento, fatias: produto.fatias });
    this.emFatias.set(produto.fatias !== null);
    this.limparFicha();
    this.limparMensagens();
    this.modo.set('editando');
    this.carregandoFicha.set(true);

    this.produtoService.detalhar(produto.id).subscribe({
      next: detalhe => {
        this.carregarFicha(detalhe);
        this.carregandoFicha.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.erro.set(tratarErroApi(e).mensagem);
        this.carregandoFicha.set(false);
      },
    });
  }

  fechar(): void {
    this.selecionado.set(null);
    this.form.reset();
    this.limparFicha();
    this.errosCampo.set({});
    this.erro.set(null);
    this.modo.set('fechado');
  }

  escolherOrigem(origem: OrigemProduto): void {
    this.origem.set(origem);
  }

  alternarFatias(evento: Event): void {
    const marcado = (evento.target as HTMLInputElement).checked;
    this.emFatias.set(marcado);
    if (!marcado) {
      this.form.controls.fatias.setValue(null);
    }
  }

  rendimentoEfetivo(): number | null {
    if (this.origem() === 'FORNECEDOR') {
      return this.sacoEscolhido()?.itensPorSaco ?? null;
    }
    return this.form.controls.rendimento.value;
  }

  divisor(): number | null {
    const rendimento = this.rendimentoEfetivo();
    if (!rendimento || rendimento < 1) {
      return null;
    }
    const fatias = this.emFatias() ? this.form.controls.fatias.value : 1;
    return fatias && fatias >= 1 ? rendimento * fatias : null;
  }

  escolherSaco(evento: Event): void {
    const valor = (evento.target as HTMLSelectElement).value;
    this.sacoId.set(valor || null);
  }

  alterarQuantidade(insumoId: string, evento: Event): void {
    const quantidade = this.numero(evento);
    this.receita.update(linhas => linhas.map(l => l.insumo.id === insumoId ? { ...l, quantidade } : l));
  }

  removerLinha(insumoId: string): void {
    this.receita.update(linhas => linhas.filter(l => l.insumo.id !== insumoId));
  }

  abrirModal(): void {
    const rascunho: Record<string, number | null> = {};
    for (const linha of this.receita()) {
      rascunho[linha.insumo.id] = linha.quantidade;
    }
    this.rascunho.set(rascunho);
    this.buscaModal.set('');
    this.modal().nativeElement.showModal();
  }

  fecharModal(): void {
    this.modal().nativeElement.close();
  }

  marcadoNoModal(insumoId: string): boolean {
    return insumoId in this.rascunho();
  }

  alternarNoModal(insumo: Insumo): void {
    this.rascunho.update(atual => {
      const novo = { ...atual };
      if (insumo.id in novo) {
        delete novo[insumo.id];
      } else {
        novo[insumo.id] = null;
      }
      return novo;
    });
  }

  quantidadeNoModal(insumoId: string, evento: Event): void {
    const quantidade = this.numero(evento);
    this.rascunho.update(atual => ({ ...atual, [insumoId]: quantidade }));
  }

  aplicarModal(): void {
    const rascunho = this.rascunho();
    const mantidas = this.receita()
      .filter(l => l.insumo.id in rascunho)
      .map(l => ({ ...l, quantidade: rascunho[l.insumo.id] }));
    const jaNaReceita = new Set(mantidas.map(l => l.insumo.id));
    const novas = this.insumos()
      .filter(i => i.id in rascunho && !jaNaReceita.has(i.id))
      .map(i => ({ insumo: i, quantidade: rascunho[i.id] }));

    this.receita.set([...mantidas, ...novas]);
    this.fecharModal();
  }

  custoPorItem(): number | null {
    const total = this.custoDaReceita();
    const divisor = this.divisor();
    if (total === null || divisor === null) {
      return this.fichaVazia() && this.selecionado() ? this.selecionado()!.custo : null;
    }
    return Math.round((total / divisor) * 100) / 100;
  }

  margem(): number | null {
    const preco = this.form.controls.preco.value;
    const custo = this.custoPorItem();
    return preco != null && custo != null ? preco - custo : null;
  }

  problemaNaFicha(): string | null {
    if (this.modo() === 'editando' && this.semFicha() && this.fichaVazia()) {
      return null;
    }
    const rendimento = this.form.controls.rendimento.value;
    if (this.origem() === 'FORNECEDOR' && !this.sacoId()) {
      return 'Escolha o saco do fornecedor.';
    }
    if (this.origem() === 'FORNECEDOR' && !this.sacoEscolhido()?.itensPorSaco) {
      return 'Esse saco ainda não tem a quantidade de itens. Informe na tela de Insumos.';
    }
    const fatias = this.form.controls.fatias.value;
    if (this.emFatias() && (!fatias || fatias < 2 || this.form.controls.fatias.invalid)) {
      return 'Informe em quantas fatias cada unidade é cortada (pelo menos 2).';
    }
    if (this.origem() === 'PRODUCAO_PROPRIA' && this.receita().length === 0) {
      return 'Adicione ao menos um insumo à receita.';
    }
    if (this.receita().some(l => !l.quantidade || l.quantidade <= 0) && this.origem() === 'PRODUCAO_PROPRIA') {
      return 'Preencha uma quantidade maior que zero para cada insumo.';
    }
    if (this.origem() === 'PRODUCAO_PROPRIA' && (!rendimento || rendimento < 1 || this.form.controls.rendimento.invalid)) {
      return this.emFatias()
        ? 'Informe quantas unidades inteiras a receita rende.'
        : 'Informe quantos itens a receita rende.';
    }
    return this.errosCampo()['itens'] ?? null;
  }

  salvar(): void {
    this.tentouSalvar.set(true);
    if (this.form.invalid || this.problemaNaFicha()) {
      this.form.markAllAsTouched();
      return;
    }

    this.limparMensagens();
    this.salvando.set(true);

    const { nome, preco } = this.form.getRawValue();
    const atual = this.selecionado();
    const mandaFicha = !(atual && this.semFicha() && this.fichaVazia());
    const ficha = {
      origem: this.origem(),
      rendimento: this.origem() === 'PRODUCAO_PROPRIA' ? this.form.controls.rendimento.value : null,
      itens: this.itens(),
      fatias: this.emFatias() ? this.form.controls.fatias.value : null,
    };

    let requisicao: Observable<Produto>;
    if (atual) {
      const alteracoes: ProdutoPatch = mandaFicha ? { nome, preco: preco!, ...ficha } : { nome, preco: preco! };
      requisicao = this.produtoService.atualizar(atual.id, alteracoes);
    } else {
      const dados: ProdutoRequest = { nome, preco: preco!, ...ficha };
      requisicao = this.produtoService.cadastrar(dados);
    }

    requisicao.subscribe({
      next: produto => {
        this.salvando.set(false);
        this.fechar();
        this.sucesso.set(atual ? `Alterações em ${produto.nome} salvas.` : `${produto.nome} cadastrado.`);
        this.carregar();
      },
      error: (e: HttpErrorResponse) => {
        this.salvando.set(false);
        const tratado = tratarErroApi(e);
        this.erro.set(tratado.mensagem);
        this.errosCampo.set(tratado.campos);
      },
    });
  }

  alternarStatus(produto: Produto): void {
    this.produtoService.atualizar(produto.id, { status: !produto.ativo }).subscribe({
      next: atualizado => {
        if (this.selecionado()?.id === atualizado.id) {
          this.selecionado.set(atualizado);
        }
        this.carregar();
      },
      error: (e: HttpErrorResponse) => this.erro.set(tratarErroApi(e).mensagem),
    });
  }

  erroDoCampo(campo: 'nome' | 'preco' | 'rendimento' | 'fatias'): string | null {
    const doServidor = this.errosCampo()[campo];
    if (doServidor) {
      return doServidor;
    }

    const controle = this.form.controls[campo];
    if (!controle.touched || controle.valid) {
      return null;
    }
    if (controle.hasError('required')) {
      return 'Campo obrigatório.';
    }
    if (controle.hasError('min')) {
      if (campo === 'fatias') {
        return 'Use pelo menos 2 fatias.';
      }
      return campo === 'rendimento' ? 'Precisa render pelo menos 1 item.' : 'Precisa ser maior que zero.';
    }
    if (controle.hasError('pattern')) {
      return 'Use um número inteiro.';
    }
    if (controle.hasError('maxlength')) {
      return 'Use no máximo 100 caracteres.';
    }
    return 'Valor inválido.';
  }

  private itens(): ItemReceita[] {
    if (this.origem() === 'FORNECEDOR') {
      return [{ insumoId: this.sacoId()!, quantidade: 1 }];
    }
    return this.receita().map(l => ({ insumoId: l.insumo.id, quantidade: l.quantidade! }));
  }

  private fichaVazia(): boolean {
    return this.origem() === 'FORNECEDOR' ? !this.sacoId() : this.receita().length === 0;
  }

  private carregarFicha(detalhe: ProdutoDetalhe): void {
    this.origem.set(detalhe.origem);
    this.emFatias.set(detalhe.fatias !== null);
    this.form.controls.fatias.setValue(detalhe.fatias);
    this.semFicha.set(detalhe.itens.length === 0);

    if (detalhe.origem === 'FORNECEDOR') {
      this.sacoId.set(detalhe.itens[0]?.insumoId ?? null);
      return;
    }

    this.receita.set(detalhe.itens.map(i => ({
      insumo: { id: i.insumoId, nome: i.insumoNome, unidade: i.unidade, custoUnitario: i.custoUnitario, itensPorSaco: null },
      quantidade: i.quantidade,
    })));
  }

  private limparFicha(): void {
    this.origem.set('PRODUCAO_PROPRIA');
    this.receita.set([]);
    this.sacoId.set(null);
    this.semFicha.set(false);
    this.emFatias.set(false);
    this.tentouSalvar.set(false);
  }

  private numero(evento: Event): number | null {
    const valor = (evento.target as HTMLInputElement).valueAsNumber;
    return Number.isNaN(valor) ? null : valor;
  }

  private limparMensagens(): void {
    this.erro.set(null);
    this.sucesso.set(null);
    this.errosCampo.set({});
  }
}

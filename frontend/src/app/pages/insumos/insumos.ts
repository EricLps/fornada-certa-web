import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Insumo, InsumoRequest, UNIDADES, UnidadeMedida, siglaDaUnidade } from '../../models/insumo';
import { tratarErroApi } from '../../services/erro-api';
import { InsumoService } from '../../services/insumo.service';

type Filtro = 'todos' | 'ingredientes' | 'sacos';
type Modo = 'fechado' | 'novo' | 'editando';

@Component({
  selector: 'app-insumos',
  host: { class: 'pagina' },
  imports: [ReactiveFormsModule, CurrencyPipe],
  templateUrl: './insumos.html',
  styleUrl: './insumos.css',
})
export class Insumos implements OnInit {

  private readonly insumoService = inject(InsumoService);
  private readonly fb = inject(FormBuilder);

  readonly unidades = UNIDADES;
  readonly sigla = siglaDaUnidade;

  readonly filtros: { valor: Filtro; rotulo: string }[] = [
    { valor: 'todos', rotulo: 'Todos' },
    { valor: 'ingredientes', rotulo: 'Ingredientes' },
    { valor: 'sacos', rotulo: 'Sacos de fornecedor' },
  ];

  readonly filtro = signal<Filtro>('todos');
  readonly modo = signal<Modo>('fechado');
  readonly insumos = signal<Insumo[]>([]);
  readonly busca = signal('');
  readonly selecionado = signal<Insumo | null>(null);
  readonly carregando = signal(false);
  readonly salvando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly errosCampo = signal<Record<string, string>>({});
  readonly sucesso = signal<string | null>(null);

  readonly visiveis = computed(() => {
    const termo = this.busca().trim().toLowerCase();
    const filtro = this.filtro();
    return this.insumos()
      .filter(i => filtro === 'todos' || (filtro === 'sacos') === (i.unidade === 'SACO'))
      .filter(i => i.nome.toLowerCase().includes(termo));
  });

  readonly form = this.fb.group({
    nome: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(100)]),
    unidade: this.fb.control<UnidadeMedida | null>(null, Validators.required),
    custoUnitario: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    itensPorSaco: this.fb.control<number | null>(null, [Validators.min(1), Validators.pattern(/^\d+$/)]),
  });

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);

    this.insumoService.listar().subscribe({
      next: insumos => {
        this.insumos.set(insumos);
        this.carregando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.erro.set(tratarErroApi(e).mensagem);
        this.carregando.set(false);
      },
    });
  }

  novo(): void {
    this.selecionado.set(null);
    this.form.reset();
    this.limparMensagens();
    this.modo.set('novo');
  }

  editar(insumo: Insumo): void {
    this.selecionado.set(insumo);
    this.form.setValue({
      nome: insumo.nome,
      unidade: insumo.unidade,
      custoUnitario: insumo.custoUnitario,
      itensPorSaco: insumo.itensPorSaco,
    });
    this.limparMensagens();
    this.modo.set('editando');
  }

  fechar(): void {
    this.selecionado.set(null);
    this.form.reset();
    this.errosCampo.set({});
    this.erro.set(null);
    this.modo.set('fechado');
  }

  salvar(): void {
    if (this.form.invalid || this.faltaItensPorSaco()) {
      this.form.markAllAsTouched();
      return;
    }

    this.limparMensagens();
    this.salvando.set(true);

    const valores = this.form.getRawValue();
    const dados = {
      ...valores,
      itensPorSaco: valores.unidade === 'SACO' ? valores.itensPorSaco : null,
    } as InsumoRequest;
    const atual = this.selecionado();
    const requisicao = atual
      ? this.insumoService.atualizar(atual.id, dados)
      : this.insumoService.cadastrar(dados);

    requisicao.subscribe({
      next: insumo => {
        this.salvando.set(false);
        this.fechar();
        this.sucesso.set(atual
          ? `Alterações em ${insumo.nome} salvas. O custo dos produtos que usam esse insumo foi atualizado.`
          : `${insumo.nome} cadastrado.`);
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

  rotuloDoCusto(): string {
    const unidade = this.form.controls.unidade.value;
    return unidade ? `Custo por ${this.sigla(unidade)} (R$)` : 'Custo por unidade (R$)';
  }

  erroDoCampo(campo: 'nome' | 'unidade' | 'custoUnitario' | 'itensPorSaco'): string | null {
    const doServidor = this.errosCampo()[campo];
    if (doServidor) {
      return doServidor;
    }

    const controle = this.form.controls[campo];
    if (campo === 'itensPorSaco' && controle.touched && this.faltaItensPorSaco()) {
      return 'Informe quantos itens vêm em cada saco.';
    }
    if (controle.hasError('pattern')) {
      return 'Use um número inteiro.';
    }
    if (!controle.touched || controle.valid) {
      return null;
    }
    if (controle.hasError('required')) {
      return 'Campo obrigatório.';
    }
    if (controle.hasError('min')) {
      return 'Precisa ser maior que zero.';
    }
    if (controle.hasError('maxlength')) {
      return 'Use no máximo 100 caracteres.';
    }
    return 'Valor inválido.';
  }

  private faltaItensPorSaco(): boolean {
    const { unidade, itensPorSaco } = this.form.getRawValue();
    return unidade === 'SACO' && !itensPorSaco;
  }

  private limparMensagens(): void {
    this.erro.set(null);
    this.sucesso.set(null);
    this.errosCampo.set({});
  }
}

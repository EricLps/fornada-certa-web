import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Produto, ProdutoRequest } from '../../models/produto';
import { tratarErroApi } from '../../services/erro-api';
import { ProdutoService } from '../../services/produto.service';

type Filtro = 'ativos' | 'inativos' | 'todos';

@Component({
  selector: 'app-produtos',
  imports: [ReactiveFormsModule, CurrencyPipe],
  templateUrl: './produtos.html',
  styleUrl: './produtos.css',
})
export class Produtos implements OnInit {

  private readonly produtoService = inject(ProdutoService);
  private readonly fb = inject(FormBuilder);

  readonly filtros: { valor: Filtro; rotulo: string }[] = [
    { valor: 'ativos', rotulo: 'Ativos' },
    { valor: 'inativos', rotulo: 'Inativos' },
    { valor: 'todos', rotulo: 'Todos' },
  ];

  readonly filtro = signal<Filtro>('ativos');
  readonly produtos = signal<Produto[]>([]);
  readonly busca = signal('');
  readonly selecionado = signal<Produto | null>(null);
  readonly carregando = signal(false);
  readonly salvando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly errosCampo = signal<Record<string, string>>({});
  readonly sucesso = signal<string | null>(null);

  readonly visiveis = computed(() => {
    const termo = this.busca().trim().toLowerCase();
    return this.produtos().filter(p => p.nome.toLowerCase().includes(termo));
  });

  readonly form = this.fb.group({
    nome: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(100)]),
    preco: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    custo: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
  });

  ngOnInit(): void {
    this.carregar();
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
    this.limparMensagens();
  }

  editar(produto: Produto): void {
    this.selecionado.set(produto);
    this.form.setValue({ nome: produto.nome, preco: produto.preco, custo: produto.custo });
    this.limparMensagens();
  }

  salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.limparMensagens();
    this.salvando.set(true);

    const dados = this.form.getRawValue() as ProdutoRequest;
    const atual = this.selecionado();
    const requisicao = atual
      ? this.produtoService.atualizar(atual.id, dados)
      : this.produtoService.cadastrar(dados);

    requisicao.subscribe({
      next: produto => {
        this.salvando.set(false);
        this.sucesso.set(atual ? 'Alterações salvas.' : `${produto.nome} cadastrado.`);
        this.selecionado.set(produto);
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

  margem(): number | null {
    const { preco, custo } = this.form.getRawValue();
    return preco != null && custo != null ? preco - custo : null;
  }

  erroDoCampo(campo: 'nome' | 'preco' | 'custo'): string | null {
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
      return 'Precisa ser maior que zero.';
    }
    if (controle.hasError('maxlength')) {
      return 'Use no máximo 100 caracteres.';
    }
    return 'Valor inválido.';
  }

  private limparMensagens(): void {
    this.erro.set(null);
    this.sucesso.set(null);
    this.errosCampo.set({});
  }
}

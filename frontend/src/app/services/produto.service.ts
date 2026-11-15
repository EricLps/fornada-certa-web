import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api';
import { Produto, ProdutoDetalhe, ProdutoPatch, ProdutoRequest } from '../models/produto';

@Injectable({ providedIn: 'root' })
export class ProdutoService {

  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/produto`;

  listar(status?: boolean, ordem: 'ASC' | 'DESC' = 'ASC'): Observable<Produto[]> {
    let params = new HttpParams().set('ordem', ordem);
    if (status !== undefined) {
      params = params.set('status', status);
    }
    return this.http.get<Produto[]>(this.url, { params });
  }

  detalhar(id: string): Observable<ProdutoDetalhe> {
    return this.http.get<ProdutoDetalhe>(`${this.url}/${id}`);
  }

  cadastrar(produto: ProdutoRequest): Observable<Produto> {
    return this.http.post<Produto>(this.url, produto);
  }

  atualizar(id: string, alteracoes: ProdutoPatch): Observable<Produto> {
    return this.http.patch<Produto>(`${this.url}/${id}`, alteracoes);
  }
}

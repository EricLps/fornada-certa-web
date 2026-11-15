import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api';
import { Fornada, FornadaLoteRequest, Turno } from '../models/fornada';

@Injectable({ providedIn: 'root' })
export class FornadaService {

  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/fornada`;

  listar(data: string, turno?: Turno): Observable<Fornada[]> {
    let params = new HttpParams().set('data', data);
    if (turno) {
      params = params.set('turno', turno);
    }
    return this.http.get<Fornada[]>(this.url, { params });
  }

  registrarLote(lote: FornadaLoteRequest): Observable<Fornada[]> {
    return this.http.post<Fornada[]>(this.url, lote);
  }

  atualizar(id: string, alteracoes: { qtdProduzida?: number; pacotesUsados?: number }): Observable<Fornada> {
    return this.http.patch<Fornada>(`${this.url}/${id}`, alteracoes);
  }

  excluir(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}

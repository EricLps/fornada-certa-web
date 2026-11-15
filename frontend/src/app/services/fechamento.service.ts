import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api';
import { Fechamento, FechamentoLoteRequest } from '../models/fechamento';

@Injectable({ providedIn: 'root' })
export class FechamentoService {

  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/fechamento`;

  listar(data: string): Observable<Fechamento[]> {
    return this.http.get<Fechamento[]>(this.url, { params: new HttpParams().set('data', data) });
  }

  registrarLote(lote: FechamentoLoteRequest): Observable<Fechamento[]> {
    return this.http.post<Fechamento[]>(this.url, lote);
  }
}

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api';
import { Insumo, InsumoPatch, InsumoRequest } from '../models/insumo';

@Injectable({ providedIn: 'root' })
export class InsumoService {

  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/insumo`;

  listar(ordem: 'ASC' | 'DESC' = 'ASC'): Observable<Insumo[]> {
    return this.http.get<Insumo[]>(this.url, { params: new HttpParams().set('ordem', ordem) });
  }

  cadastrar(insumo: InsumoRequest): Observable<Insumo> {
    return this.http.post<Insumo>(this.url, insumo);
  }

  atualizar(id: string, alteracoes: InsumoPatch): Observable<Insumo> {
    return this.http.patch<Insumo>(`${this.url}/${id}`, alteracoes);
  }
}

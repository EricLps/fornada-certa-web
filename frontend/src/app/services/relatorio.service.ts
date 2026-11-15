import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api';
import { Relatorio } from '../models/relatorio';

@Injectable({ providedIn: 'root' })
export class RelatorioService {

  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/relatorio`;

  gerar(inicio: string, fim: string): Observable<Relatorio> {
    const params = new HttpParams().set('inicio', inicio).set('fim', fim);
    return this.http.get<Relatorio>(this.url, { params });
  }
}

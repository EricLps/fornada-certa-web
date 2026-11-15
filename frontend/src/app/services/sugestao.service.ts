import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api';
import { Sugestao } from '../models/sugestao';

@Injectable({ providedIn: 'root' })
export class SugestaoService {

  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/sugestao`;

  sugerir(data?: string): Observable<Sugestao[]> {
    const params = data ? new HttpParams().set('data', data) : undefined;
    return this.http.get<Sugestao[]>(this.url, { params });
  }
}

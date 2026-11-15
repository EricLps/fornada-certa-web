import { HttpErrorResponse } from '@angular/common/http';
import { Problema } from '../models/problema';

export interface ErroTratado {
  mensagem: string;
  campos: Record<string, string>;
}

export function tratarErroApi(erro: HttpErrorResponse): ErroTratado {
  if (erro.status === 0) {
    return {
      mensagem: 'Não foi possível falar com o servidor. Confira se o backend está rodando.',
      campos: {},
    };
  }

  const problema = erro.error as Problema | null;

  return {
    mensagem: problema?.detail ?? 'Algo deu errado. Tente de novo.',
    campos: problema?.erros ?? {},
  };
}

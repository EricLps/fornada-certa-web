export type Turno = 'MANHA' | 'TARDE';

export interface Fornada {
  id: string;
  produtoId: string;
  produtoNome: string;
  data: string;
  turno: Turno;
  qtdProduzida: number;
  pacotesUsados: number | null;
}

export interface FornadaItemRequest {
  produtoId: string;
  qtdProduzida: number;
  pacotesUsados?: number | null;
}

export interface FornadaLoteRequest {
  data: string;
  turno: Turno;
  itens: FornadaItemRequest[];
}

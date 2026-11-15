export interface Fechamento {
  id: string;
  produtoId: string;
  produtoNome: string;
  data: string;
  qtdSobra: number;
  qtdDoada: number;
  qtdReaproveitada: number;
  qtdDescartada: number;
  custoUnitario: number;
  custoDescarte: number;
  horarioEsgotou: string | null;
}

export interface FechamentoItemRequest {
  produtoId: string;
  qtdSobra: number;
  qtdDoada?: number | null;
  qtdReaproveitada?: number | null;
  horarioEsgotou?: string | null;
}

export interface FechamentoLoteRequest {
  data: string;
  itens: FechamentoItemRequest[];
}

export interface Produto {
  id: string;
  nome: string;
  preco: number;
  custo: number;
  ativo: boolean;
}

export interface ProdutoRequest {
  nome: string;
  preco: number;
  custo: number;
}

export interface ProdutoPatch {
  nome?: string;
  preco?: number;
  custo?: number;
  status?: boolean;
}

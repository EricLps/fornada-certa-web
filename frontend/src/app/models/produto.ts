import { UnidadeMedida } from './insumo';

export type OrigemProduto = 'PRODUCAO_PROPRIA' | 'FORNECEDOR';

export interface Produto {
  id: string;
  nome: string;
  preco: number;
  custo: number;
  ativo: boolean;
  origem: OrigemProduto;
  rendimento: number | null;
  fatias: number | null;
}

export interface ItemReceita {
  insumoId: string;
  quantidade: number;
}

export interface ItemReceitaDetalhe {
  insumoId: string;
  insumoNome: string;
  unidade: UnidadeMedida;
  quantidade: number;
  custoUnitario: number;
  custoParcial: number;
}

export interface ProdutoDetalhe extends Produto {
  itens: ItemReceitaDetalhe[];
}

export interface ProdutoRequest {
  nome: string;
  preco: number;
  origem: OrigemProduto;
  rendimento: number | null;
  itens: ItemReceita[];
  fatias: number | null;
}

export interface ProdutoPatch {
  nome?: string;
  preco?: number;
  status?: boolean;
  origem?: OrigemProduto;
  rendimento?: number | null;
  itens?: ItemReceita[];
  fatias?: number | null;
}

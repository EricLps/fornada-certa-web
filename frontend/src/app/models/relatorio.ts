export interface Totais {
  diasFechados: number;
  produzido: number;
  vendido: number;
  sobra: number;
  doado: number;
  reaproveitado: number;
  descartado: number;
  custoDescarte: number;
  taxaDesperdicio: number;
}

export interface RelatorioPorProduto {
  produtoId: string;
  produtoNome: string;
  produzido: number;
  vendido: number;
  descartado: number;
  custoDescarte: number;
  taxaDesperdicio: number;
}

export interface RelatorioPorDia {
  data: string;
  diaDaSemana: string;
  produzido: number;
  vendido: number;
  descartado: number;
}

export interface Relatorio {
  inicio: string;
  fim: string;
  total: Totais;
  porProduto: RelatorioPorProduto[];
  porDia: RelatorioPorDia[];
}

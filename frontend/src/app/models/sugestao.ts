export type CriterioSugestao = 'MESMO_DIA_DA_SEMANA' | 'ULTIMOS_DIAS' | 'SEM_HISTORICO';

export interface Sugestao {
  produtoId: string;
  produtoNome: string;
  data: string;
  diaDaSemana: string;
  criterio: CriterioSugestao;
  amostras: number;
  mediaVendida: number | null;
  qtdSugerida: number | null;
  aviso: string | null;
}

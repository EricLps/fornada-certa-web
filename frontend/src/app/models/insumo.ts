export type UnidadeMedida = 'KG' | 'L' | 'UN' | 'SACO' | 'GAS';

export interface Insumo {
  id: string;
  nome: string;
  unidade: UnidadeMedida;
  custoUnitario: number;
  itensPorSaco: number | null;
  fornadasPorBotijao: number | null;
}

export interface InsumoRequest {
  nome: string;
  unidade: UnidadeMedida;
  custoUnitario: number;
  itensPorSaco: number | null;
  fornadasPorBotijao: number | null;
}

export interface InsumoPatch {
  nome?: string;
  unidade?: UnidadeMedida;
  custoUnitario?: number;
  itensPorSaco?: number | null;
  fornadasPorBotijao?: number | null;
}

export const UNIDADES: { valor: UnidadeMedida; rotulo: string; sigla: string }[] = [
  { valor: 'KG', rotulo: 'Quilo (kg)', sigla: 'kg' },
  { valor: 'L', rotulo: 'Litro (L)', sigla: 'L' },
  { valor: 'UN', rotulo: 'Unidade (un)', sigla: 'un' },
  { valor: 'SACO', rotulo: 'Saco de fornecedor', sigla: 'saco' },
  { valor: 'GAS', rotulo: 'Gás (botijão)', sigla: 'fornada' },
];

export function siglaDaUnidade(unidade: UnidadeMedida): string {
  return UNIDADES.find(u => u.valor === unidade)?.sigla ?? unidade;
}

export function custoPorUso(insumo: Insumo): number {
  if (insumo.unidade === 'GAS' && insumo.fornadasPorBotijao) {
    return insumo.custoUnitario / insumo.fornadasPorBotijao;
  }
  return insumo.custoUnitario;
}

export type UnidadeMedida = 'KG' | 'L' | 'UN' | 'SACO';

export interface Insumo {
  id: string;
  nome: string;
  unidade: UnidadeMedida;
  custoUnitario: number;
}

export interface InsumoRequest {
  nome: string;
  unidade: UnidadeMedida;
  custoUnitario: number;
}

export interface InsumoPatch {
  nome?: string;
  unidade?: UnidadeMedida;
  custoUnitario?: number;
}

export const UNIDADES: { valor: UnidadeMedida; rotulo: string; sigla: string }[] = [
  { valor: 'KG', rotulo: 'Quilo (kg)', sigla: 'kg' },
  { valor: 'L', rotulo: 'Litro (L)', sigla: 'L' },
  { valor: 'UN', rotulo: 'Unidade (un)', sigla: 'un' },
  { valor: 'SACO', rotulo: 'Saco de fornecedor', sigla: 'saco' },
];

export function siglaDaUnidade(unidade: UnidadeMedida): string {
  return UNIDADES.find(u => u.valor === unidade)?.sigla ?? unidade;
}

export interface Problema {
  status: number;
  title: string;
  detail: string;
  erros?: Record<string, string>;
}

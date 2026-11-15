import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'painel' },
  { path: 'painel', title: 'Painel · Fornada Certa', loadComponent: () => import('./pages/painel/painel').then(m => m.Painel) },
  { path: 'fornada', title: 'Fornada do dia · Fornada Certa', loadComponent: () => import('./pages/fornada/fornada').then(m => m.Fornada) },
  { path: 'fechamento', title: 'Fechamento do dia · Fornada Certa', loadComponent: () => import('./pages/fechamento/fechamento').then(m => m.Fechamento) },
  { path: 'produtos', title: 'Produtos · Fornada Certa', loadComponent: () => import('./pages/produtos/produtos').then(m => m.Produtos) },
  { path: '**', redirectTo: 'painel' },
];

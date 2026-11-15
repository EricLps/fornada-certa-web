import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    })
      .compileComponents();
  });

  it('cria o layout', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('mostra a marca e os quatro itens do menu', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const tela = fixture.nativeElement as HTMLElement;
    expect(tela.querySelector('.marca')?.textContent).toContain('Fornada Certa');
    expect(tela.querySelectorAll('.menu a').length).toBe(4);
  });
});

# Fornada Certa

Sistema web para reduzir o desperdício em padarias. Ele registra o que foi produzido e o que sobrou no fim do dia, calcula o custo da perda e sugere quanto produzir no dia seguinte com base na média de vendas por dia da semana.

Projeto da disciplina de Análise e Desenvolvimento de Sistemas — Universidade Cruzeiro do Sul.

## Funcionalidades

- **Insumos:** ingredientes e sacos de massa de fornecedor, com custo por kg, L, unidade ou saco.
- **Produtos com ficha técnica:** o custo de cada item é calculado pela receita (insumos × quantidades ÷ rendimento).
- **Fornada do dia:** quanto foi produzido de cada produto, por turno.
- **Fechamento do dia:** sobras, doações e reaproveitamento, com o custo do que foi descartado.
- **Painel:** indicadores da semana, gráfico de vendas e descarte, ranking do desperdício e sugestão para amanhã.

## Tecnologias

| Parte | Stack |
| --- | --- |
| Backend | Java 21, Spring Boot 4 (Web MVC, Data JPA, Validation), Gradle Kotlin DSL, Lombok |
| Banco | PostgreSQL 17 (Docker Compose), Flyway |
| Frontend | Angular 22 (standalone, signals, formulários reativos), TypeScript |
| Testes | JUnit 5, Mockito, AssertJ, MockMvc |
| CI | GitHub Actions |

## Como rodar

Pré-requisitos: Java 21, Node.js 22+ e Docker Desktop aberto.

```bash
# backend: sobe o Postgres pelo Docker, aplica as migrations e abre a API em http://localhost:8080
cd backend
./gradlew bootRun        # Windows: .\gradlew bootRun

# frontend: abre http://localhost:4200
cd frontend
npm install
npm run dev
```

Testes do backend:

```bash
cd backend
./gradlew test           # Windows: .\gradlew test
```

Para apagar os dados e recomeçar com o banco vazio: `docker compose down -v` dentro de `backend/`.

## Estrutura

```
fornada-certa/
├── backend/    API REST em Spring Boot (controller, service, repository, calculo…)
├── frontend/   aplicação Angular (pages, services, models)
├── docs/       documentação completa
└── .github/    workflow de CI
```

## Documentação

A documentação completa, com o modelo de dados, as regras de negócio, a API e o porquê de cada decisão, está em [docs/documentacao.md](docs/documentacao.md).

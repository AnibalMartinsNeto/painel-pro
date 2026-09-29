# QA Panel Pro

Versão "profissional" do QA Panel, construída por etapas como projeto de estudo:

| Camada | Tecnologia | Pasta |
|---|---|---|
| Backend (API REST) | Java 21 + Spring Boot 4 | `backend/` |
| Banco de dados | PostgreSQL 17 (Docker) + Flyway + JPA | `backend/compose.yaml`, `backend/src/main/resources/db/migration` |
| Frontend | React 19 + TypeScript + Vite | `frontend/` |

## Etapas

1. ✅ **Ambiente:** Java 21, WSL2, Docker Desktop.
2. ✅ **Backend base:** camadas Controller → Service → Repository, DTOs, erros no formato Problem Details e testes.
3. ✅ **Banco:** PostgreSQL no Docker, migrações com Flyway, entidades JPA, importação do histórico do painel Node e testes com Testcontainers.
4. ⬜ **Execução dos testes:** o backend dispara Cypress, Playwright e k6, grava os resultados e transmite o log ao vivo.
5. 🟨 **Frontend:** a base está pronta (rotas, cliente de API, telas de projetos, testes). As telas crescem junto com cada etapa.
6. ⬜ **Integrações e qualidade:** triagem com IA, Azure DevOps e testes do próprio painel.

## Como rodar tudo

São dois processos, cada um num terminal:

```bash
# Terminal 1: API
cd backend
.\mvnw.cmd spring-boot:run      # http://localhost:8080

# Terminal 2: interface
cd frontend
npm install                     # só na primeira vez
npm run dev                     # http://localhost:5173  ← abra este no navegador
```

O front chama `/api/...` na própria porta 5173, e o Vite repassa ao backend na 8080 (proxy configurado em `frontend/vite.config.ts`). Assim o navegador não bloqueia as chamadas por CORS.

## Frontend

```
src/
├── main.tsx              ← entrada: provedores globais (cache de dados, roteador)
├── App.tsx               ← mapa de rotas (URL → página)
├── api/client.ts         ← único ponto que faz fetch; converte erros da API (ApiError)
├── components/           ← peças reutilizáveis: Layout, Badge, estados de carregando/erro
├── features/projetos/    ← uma funcionalidade completa
│   ├── api.ts              tipos (espelham os DTOs Java) + hooks de dados
│   ├── ProjetosPage.tsx    tela /projetos
│   ├── ProjetoDetalhePage  tela /projetos/:id
│   └── *.test.tsx          testes de componente
└── test/                 ← setup e utilitários de teste
```

```bash
npm test          # testes (Vitest + Testing Library), com a API simulada
npm run build     # checa tipos e gera a versão de produção em dist/
```

## Backend

```bash
cd backend
./mvnw test              # roda os testes
./mvnw spring-boot:run   # sobe a API em http://localhost:8080
```

| Endpoint | O que faz |
|---|---|
| `GET /api/projetos` | Lista os projetos de teste e se estão instalados |
| `GET /api/projetos/{id}` | Detalhe com specs, navegadores, scripts e URL base (404 se não existir) |
| `GET /api/execucoes?projeto=cypress` | Últimas 50 execuções do projeto |
| `GET /api/execucoes/resumo?projeto=cypress` | Números da Visão geral (mês, aprovação, falhas por módulo) |
| `GET /api/execucoes/{id}` | Execução com todos os resultados de teste |
| `POST /api/importacoes/painel-node` | Importa o histórico de `Painel/data/runs` (idempotente) |
| `GET /actuator/health` | Saúde da aplicação |

### Banco de dados

O `spring-boot:run` sobe o PostgreSQL do `backend/compose.yaml` automaticamente, com o Docker Desktop aberto. Para mexer no banco na mão:

```bash
docker compose up -d          # sobe o banco
docker compose down -v        # APAGA tudo e recomeça do zero
docker exec -it qapanel-postgres psql -U qapanel -d qapanel   # console SQL
```

As tabelas são criadas pelo **Flyway** a partir de `src/main/resources/db/migration/V*.sql`. Uma migração aplicada nunca é editada; mudanças viram um novo arquivo (`V2__...`).

```
execucao (1) ──< (N) resultado_teste
  id, projeto_id, script, status,          id, execucao_id, spec, titulo,
  iniciada_em, total, aprovados...         chave, status, mensagem_erro, tipo_erro
```

### Como uma requisição percorre o backend

```
GET /api/projetos/playwright
        │
        ▼
ProjetoController   ← traduz HTTP ↔ Java; nenhuma regra de negócio
        │
        ▼
ProjetoService      ← regras: projeto existe? está instalado? quais specs?
        │
        ▼
ProjetoRepository   ← de onde vêm os dados (hoje: application.yml)
        │
        ▼
ProjetoDetalheResponse (DTO) → JSON devolvido ao cliente
```

Erros lançados em qualquer camada sobem até o `ApiExceptionHandler`, que devolve sempre o mesmo formato JSON (RFC 9457).

### Testes (pirâmide)

| Tipo | Classe | Sobe o quê | Tempo |
|---|---|---|---|
| Unidade | `ProjetoServiceTest` | Nada: só a classe, com repositório falso | ~0,1s |
| Web | `ProjetoControllerTest` | Só a camada HTTP, com o Service mockado | ~1s |
| Dados | `ExecucaoRepositoryTest`, `ImportadorPainelNodeTest` | JPA + Flyway contra um PostgreSQL real (Testcontainers) | ~12s |
| Integração | `PainelBackendApplicationTests` | A aplicação inteira, com a configuração real e um PostgreSQL real | ~4s |

Os testes com banco exigem o Docker Desktop aberto: o Testcontainers cria um PostgreSQL descartável para cada execução, sem tocar no banco de desenvolvimento.

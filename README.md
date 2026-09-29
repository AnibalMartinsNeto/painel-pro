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
4. ✅ **Execução dos testes:** o backend dispara Cypress, Playwright e k6 (padrão Strategy), transmite o log ao vivo por SSE, grava resultados e log no banco, cancela a árvore de processos e aceita uma execução por vez (409).
5. 🟨 **Frontend:** a base está pronta (rotas, cliente de API, telas de projetos, testes). As telas crescem junto com cada etapa.
6. 🟨 **Integrações e qualidade:**
   - ✅ Configurações e segredos (AES-256-GCM, chave-mestra fora do banco), com Jira no lugar do Azure DevOps
   - ✅ Triagem com IA (Gemini ou Claude; fila por consulta nativa; rascunho editável; heurística sem chave)
   - ⬜ Jira (configuração e teste de conexão prontos; falta publicar bugs)
   - ⬜ Relatórios, screenshots, falha nova × recorrente
   - ⬜ CI no GitHub Actions

## Como rodar do zero

### 1. Pré-requisitos (uma vez)

```bash
winget install EclipseAdoptium.Temurin.21.JDK
winget install OpenJS.NodeJS.LTS
winget install Docker.DockerDesktop
```

O Docker Desktop pede o WSL2 e uma reinicialização na primeira vez.

### 2. Baixar os projetos lado a lado

```
QA_Estudos/
├── PainelPro/      ← git clone https://github.com/AnibalMartinsNeto/painel-pro.git PainelPro
└── serverest-qa/   ← git clone https://github.com/AnibalMartinsNeto/serverest-qa.git
```

O painel procura os projetos de teste em `../serverest-qa` (configurável em `backend/src/main/resources/application.yml`).

### 3. Iniciar

Dois cliques em **`iniciar.bat`**. Ele:

1. confere Java, Node e Docker;
2. abre o Docker Desktop se estiver fechado (o PostgreSQL roda nele);
3. instala as dependências do front na primeira vez;
4. empacota o backend **só se o código mudou** desde a última vez;
5. abre as janelas **"PainelPro - Backend"** (`java -jar`, ~8s) e **"PainelPro - Front"** (Vite);
6. espera a API responder e abre http://localhost:5173.

Para desligar tudo: **`parar.bat`** (fecha as janelas e para o banco, sem apagar dados).

| Situação | Tempo aproximado até abrir |
|---|---|
| Uso normal (código sem mudança, Docker aberto) | ~10 s |
| Depois de mudar o código do backend | ~25 s (empacota uma vez) |
| Docker Desktop fechado | + o tempo de o Docker subir (~30-60 s) |

> Dica: no Docker Desktop, ative *Settings → General → Start Docker Desktop when you sign in* para ele já estar pronto quando você ligar o computador.

### Manualmente (para desenvolvimento)

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
| `POST /api/execucoes` | Dispara uma execução → 202; 400 se inválida; 409 se já houver uma rodando |
| `GET /api/execucoes/em-andamento` | Execução rodando agora, ou 204 |
| `GET /api/execucoes/{id}/log` | Log ao vivo (Server-Sent Events) |
| `GET /api/execucoes/{id}/log.txt` | Log completo gravado |
| `POST /api/execucoes/{id}/cancelar` | Interrompe a execução (mata os processos filhos) |
| `POST /api/importacoes/painel-node` | Importa o histórico de `Painel/data/runs` (idempotente) |
| `GET /actuator/health` | Saúde da aplicação |

### Como uma execução acontece

```
POST /api/execucoes ─► OrquestradorExecucao.iniciar()
                          │ valida (specs existem? navegador suportado? já há execução?)
                          │ grava EM_ANDAMENTO e responde 202 na hora
                          ▼  thread "execucao-testes"
                 ExecutorFerramenta (Strategy)
        CypressExecutor │ PlaywrightExecutor │ K6Executor
                          │ etapas(): quais processos iniciar
                          ▼
                 ProcessBuilder → saída linha a linha ─► LogAoVivo ─SSE─► console da tela
                          │
                          ▼ ler(): relatório JSON da ferramenta → ResultadoTeste
                 ExecucaoGravacao.concluir() → status, resultados e log no PostgreSQL
```

### Configurações e segredos

`GET/PUT /api/configuracoes` guarda ambiente, IA e Jira na tabela `configuracao`. Chaves de API e o token do Jira são:

- **criptografados** com AES-256-GCM antes de ir para o banco (`v1:<base64>`);
- **somente escrita** pela API: o GET diz só se estão configurados, nunca devolve o valor;
- **mascarados** no `toString()` dos DTOs, para não vazarem em logs.

A **chave-mestra** que decifra os segredos fica fora do banco e do Git:

| Ambiente | Origem |
|---|---|
| Produção | variável `PAINEL_SEGURANCA_CHAVE_MESTRA` (32 bytes em Base64) |
| Desenvolvimento | arquivo `~/.qapanel/chave-mestra.key`, criado na primeira execução |

> **Faça backup** de `~/.qapanel/chave-mestra.key`. Sem ela, os segredos gravados não podem ser lidos; seria preciso cadastrá-los de novo.

### Triagem com IA

| Endpoint | O que faz |
|---|---|
| `GET /api/triagem?projeto=cypress` | Fila: testes cuja ocorrência MAIS RECENTE falhou, com a triagem de cada um |
| `POST /api/triagem/{resultadoId}/analisar` | Gera o rascunho do bug com a IA configurada (ou heurística, sem chave). 502 se a IA falhar |
| `PUT /api/triagem/{resultadoId}` | Grava a revisão do QA (classificação, severidade, textos, passos) |

- A triagem é por **teste** (projeto + "spec › título"), não por execução: a análise continua valendo se o teste falhar de novo.
- A classificação da IA fica como **sugestão** até o QA confirmar.
- A chamada à IA acontece **fora da transação do banco** (`TransactionTemplate`): esperar um serviço externo com uma conexão presa esgotaria o pool.
- Gemini e Claude fazem novas tentativas com espera crescente em 429/503; erros como 401 falham na hora.

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

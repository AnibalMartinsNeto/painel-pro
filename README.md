# QA Panel Pro

[![CI](https://github.com/AnibalMartinsNeto/painel-pro/actions/workflows/ci.yml/badge.svg)](https://github.com/AnibalMartinsNeto/painel-pro/actions/workflows/ci.yml)

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
   - ✅ Jira: publicar o bug da triagem ligado à demanda, e buscar a demanda para rodar os specs que a citam
   - 🟨 Relatórios (✅ totais do histórico, aprovação por execução, testes que mais falham/instáveis, CSV e JSON; ✅ histórico do projeto no Jira) · ⬜ screenshots, falha nova × recorrente
   - ✅ CI no GitHub Actions: testes do backend (com PostgreSQL via Testcontainers), lint, testes e build do front, só do lado que mudou em cada push ou PR ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)); os testes do serverest-qa têm o CI deles

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
├── painel-pro/     ← git clone https://github.com/AnibalMartinsNeto/painel-pro.git
└── serverest-qa/   ← git clone https://github.com/AnibalMartinsNeto/serverest-qa.git
```

O painel procura os projetos de teste em `../serverest-qa` (configurável em `backend/src/main/resources/application.yml`).

### 3. Iniciar

Dois cliques em **`iniciar.bat`**. Ele:

1. confere Java, Node e Docker;
2. abre o Docker Desktop se estiver fechado (o PostgreSQL roda nele);
3. sobe o PostgreSQL do painel e a **API local do ServeRest** (`../serverest-qa/compose.yaml`, porta 3000), usada pelos testes no lugar do servidor público;
4. instala as dependências do front na primeira vez;
5. empacota o backend **só se o código mudou** desde a última vez;
6. abre as janelas **"PainelPro - Backend"** (`java -jar`, ~8s) e **"PainelPro - Front"** (Vite);
7. espera a API responder e abre http://localhost:5173.

Para desligar tudo: **`parar.bat`** (fecha as janelas e para o banco, sem apagar dados). A API local do ServeRest continua no ar; para pará-la, rode `docker compose down` dentro de `serverest-qa`.

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

## Telas

O projeto de testes (Cypress, Playwright ou k6) é escolhido no seletor da barra lateral e vale para todas as telas.

| Tela | O que tem |
|---|---|
| **Visão geral** | Testes e aprovação do mês, falhas na fila de triagem, falhas por módulo e um botão para executar cada script do `package.json` |
| **Execuções** | Nova execução (specs, navegador, novas tentativas), console com o log ao vivo, histórico e detalhe de cada execução |
| **Triagem IA** | Fila de testes falhando; a IA (Gemini ou Claude) sugere título, classificação, severidade, passos e análise; o QA revisa, salva e publica no Jira |
| **Jira** | Busca por chave: numa **demanda** (ex.: DEV-1), lista os specs que a citam e executa; num **bug publicado pelo painel**, mostra o teste que o encontrou e o botão **Retestar**. Lista os bugs publicados e o **histórico do projeto no Jira** (todas as issues, com o status atual) |
| **Relatórios** | Totais de todo o histórico (execuções, testes únicos, que já falharam, instáveis, tempo total), aprovação por execução, testes que mais falham, exportação **CSV** e **JSON** |
| **Configurações** | Ambiente, chaves de IA e conexão com o Jira (com "Testar conexão") |

## Frontend

```
src/
├── main.tsx              ← entrada: provedores globais (cache de dados, roteador)
├── App.tsx               ← mapa de rotas (URL → página)
├── api/client.ts         ← único ponto que faz fetch; converte erros da API (ApiError)
├── components/           ← peças reutilizáveis: Layout, Badge, Gauge, estados de carregando/erro
├── features/             ← uma pasta por tela: api.ts (tipos que espelham os DTOs Java + hooks) e a página
│   ├── visao-geral/  execucoes/  triagem/  jira/  relatorios/  configuracoes/
│   ├── projetos/           projeto selecionado, compartilhado por todas as telas
│   └── paginas.test.tsx    testes de componente das telas, com a API simulada
├── lib/formato.ts        ← datas, durações, percentuais
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
| `GET /api/relatorios?projeto=cypress` | Números da tela Relatórios: todo o histórico, aprovação das últimas 24 execuções, testes que mais falham e instáveis |
| `GET /api/relatorios/execucoes.csv?projeto=cypress` | Todas as execuções do projeto em CSV (download) |
| `POST /api/importacoes/painel-node` | Importa o histórico do painel Node de `C:\QA_Estudos\Painel\data\runs` (idempotente; propriedade `painel.importacao.pasta-painel-node`) |
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
- Gemini e Claude fazem novas tentativas com espera crescente em 429/503; erros como 401 falham na hora. Cada tentativa vai para o log.

### Publicação no Jira

| Endpoint | O que faz |
|---|---|
| `POST /api/triagem/{resultadoId}/publicar` | Corpo `{"demanda":"DEV-1"}`. Cria o Bug no Jira a partir da triagem salva e o liga à demanda ("Relates"). 409 se já foi publicado |
| `GET /api/jira/demandas/{chave}?projeto=` | Dados da issue no Jira e os specs do projeto que citam a chave. Se a chave for um bug publicado pelo painel, traz também a `origem`: o teste que o encontrou, a demanda e se o spec ainda existe |
| `GET /api/jira/bugs?projeto=` | Bugs já publicados pelo painel (lidos do banco) |
| `GET /api/jira/issues?maximo=50` | Últimas issues do projeto no Jira, de qualquer origem (busca JQL na hora), com status e a marca `doPainel` (etiqueta `qa-panel`) |

- **Rastreabilidade pela chave:** o teste leva a chave da demanda no nome (`describe("Login - ServeRest [DEV-1]")`). O painel acha os specs que a citam e sugere a demanda na hora de publicar.
- A descrição vai em **ADF** (o formato de documento do Jira Cloud), com passos, esperado, encontrado, análise e o erro. A severidade vira prioridade (CRÍTICA→Highest ... BAIXA→Low), com as etiquetas `qa-panel` e o projeto.
- A chamada ao Jira fica **fora da transação**, como na IA. Se o vínculo falhar, o bug continua criado e a resposta traz um aviso.
- A migração V5 guarda na triagem a chave, o link, a demanda e a data da publicação.
- **Bugs publicados × histórico do Jira:** o primeiro é o que o painel registrou no banco; o segundo é o que de fato existe no Jira. Comparar os dois revela issues "órfãs" (criadas no Jira mas não registradas no painel).

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

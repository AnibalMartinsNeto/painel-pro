# QA Panel Pro

Versão "profissional" do QA Panel, construída por etapas como projeto de estudo:

| Camada | Tecnologia | Pasta |
|---|---|---|
| Backend (API REST) | Java 21 + Spring Boot 4 | `backend/` |
| Banco de dados | PostgreSQL 17 (Docker) | etapa 3 |
| Frontend | React + TypeScript | etapa 5 |

## Etapas

1. ✅ **Ambiente:** Java 21, WSL2, Docker Desktop.
2. ✅ **Backend base:** camadas Controller → Service → Repository, DTOs, erros no formato Problem Details e testes.
3. ⬜ **Banco:** PostgreSQL no Docker, migrações com Flyway, entidades JPA.
4. ⬜ **Execução dos testes:** o backend dispara Cypress, Playwright e k6, grava os resultados e transmite o log ao vivo.
5. ⬜ **Frontend:** React + TypeScript consumindo a API.
6. ⬜ **Integrações e qualidade:** triagem com IA, Azure DevOps e testes do próprio painel.

## Backend

```bash
cd backend
./mvnw test              # roda os testes
./mvnw spring-boot:run   # sobe a API em http://localhost:8080
```

| Endpoint | O que faz |
|---|---|
| `GET /api/projetos` | Lista os projetos de teste e se estão instalados |
| `GET /api/projetos/{id}` | Detalhe com specs e navegadores (404 se não existir) |
| `GET /actuator/health` | Saúde da aplicação |

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
| Integração | `PainelBackendApplicationTests` | A aplicação inteira, com a configuração real | ~4s |

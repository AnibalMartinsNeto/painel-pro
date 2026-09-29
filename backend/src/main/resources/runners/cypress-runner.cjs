// cypress-runner.cjs
// Ponte Java → Cypress. O backend copia este arquivo para uma pasta
// temporária e o executa com "node cypress-runner.cjs config.json".
// Usa a Module API do Cypress (cypress.run), que devolve os resultados
// estruturados (testes, estados, erros) em vez de só texto de console.
const fs = require("fs");
const path = require("path");

const cfg = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const escrever = (dados) => fs.writeFileSync(cfg.resultado, JSON.stringify(dados));

let cypress;
try {
  cypress = require(path.join(cfg.projeto, "node_modules", "cypress"));
} catch {
  console.error(`[painel] Cypress não encontrado em ${cfg.projeto}. Rode "npm install" no projeto.`);
  escrever({ ok: false, error: "Cypress não instalado no projeto." });
  process.exit(2);
}

const opcoes = {
  project: cfg.projeto,
  spec: cfg.specs.map((s) => path.join(cfg.projeto, s)).join(","),
  browser: cfg.navegador || "electron",
  headed: !!cfg.abrirNavegador,
  config: { video: false },
};
if (cfg.retentativas > 0) opcoes.config.retries = { runMode: cfg.retentativas };

cypress
  .run(opcoes)
  .then((results) => {
    escrever({ ok: true, results });
    process.exit(0);
  })
  .catch((err) => {
    console.error("[painel] Erro ao executar o Cypress:", err && err.message);
    escrever({ ok: false, error: String((err && err.message) || err) });
    process.exit(1);
  });

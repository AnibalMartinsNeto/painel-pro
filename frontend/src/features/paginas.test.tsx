import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import { apiFalsa, renderComApp } from '../test/render'

// Testes de componente: o React renderiza de verdade (num DOM simulado),
// mas a API é falsa. O front é testado sem depender do backend no ar.

const projetos = [
  { id: 'cypress', nome: 'Cypress', tipo: 'CYPRESS', encontrado: true, instalado: true },
  { id: 'k6', nome: 'k6 (desempenho)', tipo: 'K6', encontrado: true, instalado: false },
]
const cypress = {
  ...projetos[0],
  baseUrl: 'https://www.saucedemo.com',
  navegadores: ['electron', 'chrome'],
  specs: ['cypress/e2e/checkout.cy.js', 'cypress/e2e/login.cy.js', 'cypress/e2e/user-behavior-matrix.cy.js'],
  scripts: [
    { nome: 'test', comando: 'cypress run --spec ...', specs: ['cypress/e2e/checkout.cy.js', 'cypress/e2e/login.cy.js'], navegador: null },
    { nome: 'test:diagnostics', comando: 'cypress run --spec ...', specs: ['cypress/e2e/user-behavior-matrix.cy.js'], navegador: null },
  ],
}
const k6 = { ...projetos[1], baseUrl: null, navegadores: [], specs: ['tests/smoke.js'], scripts: [] }

const execucao = {
  id: 7, projetoId: 'cypress', script: 'test:diagnostics', navegador: 'electron', status: 'FALHOU',
  iniciadaEm: '2026-09-28T23:50:58Z', finalizadaEm: '2026-09-28T23:51:35Z',
  total: 2, aprovados: 1, reprovados: 1, pulados: 0, duracaoMs: 37036, importada: true,
}
const resumo = {
  mes: '2026-09', execucoes: 3, testes: 70, aprovados: 58, reprovados: 12, aprovacao: 82.9,
  falhasPorModulo: [{ modulo: 'Matriz de usuários', falhas: 12, percentual: 100 }],
  ultimaExecucao: execucao,
  ultimaPorScript: { 'test:diagnostics': execucao },
}
const vazio = { ...resumo, execucoes: 0, testes: 0, aprovados: 0, reprovados: 0, aprovacao: null, falhasPorModulo: [], ultimaExecucao: null, ultimaPorScript: {} }

let fetchFalso: ReturnType<typeof apiFalsa>

beforeEach(() => {
  localStorage.clear()
  fetchFalso = apiFalsa({
    '/actuator/health': { status: 'UP' },
    '/api/projetos': projetos,
    '/api/projetos/cypress': cypress,
    '/api/projetos/k6': k6,
    '/api/execucoes/resumo?projeto=cypress': resumo,
    '/api/execucoes/resumo?projeto=k6': vazio,
    '/api/execucoes?projeto=cypress': [execucao],
    '/api/execucoes?projeto=k6': [],
    '/api/execucoes/7': {
      execucao, versaoFerramenta: 'Cypress 15.19.0', erro: null,
      resultados: [
        { id: 1, spec: 'cypress/e2e/user-behavior-matrix.cy.js', titulo: 'Matriz › standard_user › login', status: 'PASSOU', duracaoMs: 2000, mensagemErro: null, tipoErro: null },
        { id: 2, spec: 'cypress/e2e/user-behavior-matrix.cy.js', titulo: 'Matriz › problem_user › imagens', status: 'FALHOU', duracaoMs: 66, mensagemErro: 'AssertionError: imagens duplicadas', tipoErro: 'Asserção' },
      ],
    },
    '/api/importacoes/painel-node': { importadas: 5, ignoradas: 0, erros: [] },
    '/api/execucoes/em-andamento': [204, null],
    '/api/execucoes': { ...execucao, id: 8, status: 'EM_ANDAMENTO' },
    '/api/configuracoes': configuracoes,
    '/api/triagem?projeto=cypress': [falhaPendente],
    '/api/triagem/2/analisar': rascunhoIa,
    '/api/triagem/2': { ...rascunhoIa, classificacao: 'BUG_APLICACAO' },
  })
})

const falhaPendente = {
  resultadoId: 2, execucaoId: 7, spec: 'cypress/e2e/user-behavior-matrix.cy.js', titulo: 'Matriz › problem_user › imagens',
  chave: 'x', mensagemErro: 'AssertionError: imagens duplicadas', tipoErro: 'Asserção',
  ocorridaEm: '2026-09-28T23:50:58Z', navegador: 'electron', triagem: null,
}
const rascunhoIa = {
  classificacao: null, classificacaoSugerida: 'BUG_APLICACAO', severidade: 'MEDIA', titulo: 'Imagens duplicadas para problem_user',
  esperado: 'Cada produto com sua imagem', encontrado: 'Imagens repetidas', passos: ['Logar com problem_user', 'Ver o inventário'],
  analise: 'Defeito conhecido do SauceDemo.', origemRascunho: 'IA', modelo: 'gemini-3.8-flash', observacoes: null,
  atualizadaEm: '2026-09-28T23:59:00Z',
}

const configuracoes = {
  ambiente: 'Homologação',
  ia: { provedor: 'gemini', modeloAnthropic: 'claude-sonnet-5', modeloGemini: 'gemini-flash-latest', anthropicConfigurada: false, geminiConfigurada: true, ativa: true },
  jira: { url: null, email: null, projeto: null, tipoIssue: 'Bug', tokenConfigurado: false, configurado: false },
}

/** Corpo JSON enviado na chamada ao endpoint (o fetch falso guarda os argumentos). */
function corpoEnviado(url: string) {
  // A mesma URL pode ter GET (sem corpo) e PUT/POST: pega a última chamada COM corpo.
  const chamadas = fetchFalso.mock.calls as unknown as [string, RequestInit | undefined][]
  const comCorpo = chamadas.filter(([u, init]) => u === url && init?.body !== undefined).at(-1)
  return comCorpo ? JSON.parse(String(comCorpo[1]!.body)) : undefined
}
afterEach(() => vi.unstubAllGlobals())

describe('Visão geral', () => {
  it('mostra uma linha de execução rápida por script do package.json', async () => {
    renderComApp(<App />)

    const linhas = await screen.findByRole('region', { name: 'Execuções rápidas' })
    expect(await within(linhas).findByText('npm run test')).toBeInTheDocument()
    expect(within(linhas).getByText('npm run test:diagnostics')).toBeInTheDocument()
    expect(within(linhas).getByText('2 specs')).toBeInTheDocument()
  })

  it('mostra os números do resumo vindos do banco', async () => {
    renderComApp(<App />)

    expect(await screen.findByText('82,9%', { selector: '.kpi-value' })).toBeInTheDocument()
    expect(screen.getByText('Suíte instável')).toBeInTheDocument()
    expect(screen.getByText('Matriz de usuários')).toBeInTheDocument()
    // A linha do script mostra o resultado da última execução dele.
    const linhas = screen.getByRole('region', { name: 'Execuções rápidas' })
    expect(within(linhas).getByText('1 falha')).toBeInTheDocument()
    expect(within(linhas).getByText('Nunca executado')).toBeInTheDocument() // "test" nunca rodou
  })

  it('mostra a URL do ambiente e a API online na barra lateral', async () => {
    renderComApp(<App />)

    expect(await screen.findByText('www.saucedemo.com')).toBeInTheDocument()
    expect(await screen.findByText('Online')).toBeInTheDocument()
  })
})

describe('Triagem', () => {
  it('mostra a falha pendente na fila, no menu e na Visão geral', async () => {
    renderComApp(<App />)

    expect(await screen.findByTitle('Falhas aguardando triagem')).toHaveTextContent('1') // menu
    expect(await screen.findByText('aguardando triagem')).toBeInTheDocument() // KPI da Visão geral
  })

  it('"Analisar com IA" chama a API e a sugestão aparece pré-selecionada', async () => {
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/triagem' })

    expect(await screen.findByText('Pendentes (1)')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Analisar com IA' }))

    await vi.waitFor(() => expect(fetchFalso).toHaveBeenCalledWith('/api/triagem/2/analisar', expect.objectContaining({ method: 'POST' })))
  })

  it('link para falha que não está na fila avisa em vez de abrir outra', async () => {
    renderComApp(<App />, { rota: '/triagem/999' })

    expect(await screen.findByRole('alert')).toHaveTextContent('Falha #999 fora da fila deste projeto')
    expect(screen.queryByText('Bug sugerido')).not.toBeInTheDocument()
  })

  it('com rascunho, salvar envia a revisão com os passos em lista', async () => {
    const fetchDoTeste = apiFalsa({
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': cypress,
      '/api/execucoes/em-andamento': [204, null],
      '/api/configuracoes': configuracoes,
      '/api/triagem?projeto=cypress': [{ ...falhaPendente, triagem: rascunhoIa }],
      '/api/triagem/2': { ...rascunhoIa, classificacao: 'BUG_APLICACAO' },
    })
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/triagem/2' })

    expect(await screen.findByDisplayValue('Imagens duplicadas para problem_user')).toBeInTheDocument()
    expect(screen.getByText(/sugestão da IA: Bug da aplicação/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Classificação/)).toHaveValue('BUG_APLICACAO') // sugestão já vem escolhida

    await user.click(screen.getByRole('button', { name: 'Salvar triagem' }))

    await vi.waitFor(() => {
      const put = (fetchDoTeste.mock.calls as unknown as [string, RequestInit | undefined][])
        .find(([u, init]) => u === '/api/triagem/2' && init?.method === 'PUT')
      expect(put).toBeDefined()
      expect(JSON.parse(String(put![1]!.body))).toMatchObject({
        classificacao: 'BUG_APLICACAO',
        severidade: 'MEDIA',
        passos: ['Logar com problem_user', 'Ver o inventário'],
      })
    })
  })
})

describe('Jira', () => {
  const comJira = {
    ...configuracoes,
    jira: { url: 'https://empresa.atlassian.net', email: 'qa@x.com', projeto: 'DEV', tipoIssue: 'Bug', tokenConfigurado: true, configurado: true },
  }

  it('publicar salva a revisão, sugere a demanda pelo título e mostra o link do bug', async () => {
    const falhaDaDemanda = { ...falhaPendente, titulo: 'Login - ServeRest [DEV-1] › deve logar', triagem: rascunhoIa }
    const fetchDoTeste = apiFalsa({
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': cypress,
      '/api/execucoes/em-andamento': [204, null],
      '/api/configuracoes': comJira,
      '/api/triagem?projeto=cypress': [falhaDaDemanda],
      '/api/triagem/2': rascunhoIa,
      '/api/triagem/2/publicar': { chave: 'DEV-2', url: 'https://empresa.atlassian.net/browse/DEV-2', demanda: 'DEV-1', aviso: null },
    })
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/triagem/2' })

    expect(await screen.findByLabelText(/Demanda testada/)).toHaveValue('DEV-1') // tirado do título do teste
    await user.click(screen.getByRole('button', { name: 'Publicar no Jira' }))

    await vi.waitFor(() => {
      const chamadas = fetchDoTeste.mock.calls as unknown as [string, RequestInit | undefined][]
      const indice = (url: string, metodo: string) => chamadas.findIndex(([u, i]) => u === url && i?.method === metodo)
      expect(indice('/api/triagem/2/publicar', 'POST')).toBeGreaterThan(indice('/api/triagem/2', 'PUT')) // salva ANTES
      expect(indice('/api/triagem/2', 'PUT')).toBeGreaterThanOrEqual(0)
    })
    const publicar = (fetchDoTeste.mock.calls as unknown as [string, RequestInit][]).find(([u]) => u === '/api/triagem/2/publicar')!
    expect(JSON.parse(String(publicar[1].body))).toEqual({ demanda: 'DEV-1' })
  })

  it('busca a demanda e lista os specs que a citam', async () => {
    apiFalsa({
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': cypress,
      '/api/execucoes/em-andamento': [204, null],
      '/api/configuracoes': comJira,
      '/api/jira/bugs?projeto=cypress': [],
      '/api/jira/demandas/DEV-1?projeto=cypress': {
        chave: 'DEV-1', erro: null, specs: ['cypress/e2e/login.cy.js'],
        issue: { chave: 'DEV-1', resumo: '[ServeRest] Login do administrador', tipo: 'Nova função', status: 'Aberto', url: 'https://x/browse/DEV-1' },
      },
    })
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/jira' })

    await user.type(await screen.findByLabelText('Chave da issue'), 'dev-1')
    await user.click(screen.getByRole('button', { name: 'Buscar' }))

    expect(await screen.findByText('[ServeRest] Login do administrador')).toBeInTheDocument()
    expect(screen.getByLabelText('Specs da demanda')).toHaveTextContent('cypress/e2e/login.cy.js')
    expect(screen.getByRole('button', { name: 'Executar 1 spec' })).toBeEnabled()
  })

  it('buscar um bug publicado mostra o teste que o encontrou e permite retestar', async () => {
    const fetchDoTeste = apiFalsa({
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': cypress,
      '/api/execucoes/em-andamento': [204, null],
      '/api/execucoes': { ...execucao, id: 8, status: 'EM_ANDAMENTO' },
      '/api/configuracoes': comJira,
      '/api/jira/bugs?projeto=cypress': [],
      '/api/jira/demandas/DEV-8?projeto=cypress': {
        chave: 'DEV-8', erro: null, specs: [],
        issue: { chave: 'DEV-8', resumo: 'Login quebrado', tipo: 'Bug', status: 'Aberto', url: 'https://x/browse/DEV-8' },
        origem: { spec: 'cypress/e2e/login.cy.js', teste: 'Login [DEV-1] › deve logar', demanda: 'DEV-1', publicadaEm: '2026-09-30T00:17:00Z', specExiste: true },
      },
    })
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/jira' })

    await user.type(await screen.findByLabelText('Chave da issue'), 'DEV-8')
    await user.click(screen.getByRole('button', { name: 'Buscar' }))

    const origem = await screen.findByLabelText('Origem do bug')
    expect(origem).toHaveTextContent('Login [DEV-1] › deve logar')
    expect(screen.queryByText(/Nenhum spec/)).not.toBeInTheDocument()
    await user.click(within(origem).getByRole('button', { name: 'Retestar' }))

    const post = (fetchDoTeste.mock.calls as unknown as [string, RequestInit | undefined][])
      .find(([url, init]) => url === '/api/execucoes' && init?.method === 'POST')!
    expect(JSON.parse(post[1]!.body as string)).toMatchObject({ specs: ['cypress/e2e/login.cy.js'] })
  })

  it('mostra o histórico do projeto no Jira e filtra as criadas pelo painel', async () => {
    apiFalsa({
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': cypress,
      '/api/execucoes/em-andamento': [204, null],
      '/api/configuracoes': comJira,
      '/api/jira/bugs?projeto=cypress': [],
      '/api/jira/issues?maximo=50': [
        { chave: 'DEV-3', resumo: 'Imagens duplicadas', tipo: 'Bug', status: 'Em andamento', categoriaStatus: 'indeterminate',
          prioridade: 'High', criadaEm: '2026-10-01T12:00:00Z', doPainel: true, url: 'https://x/browse/DEV-3' },
        { chave: 'DEV-2', resumo: 'Pedido aberto no portal', tipo: 'Task', status: 'Aberto', categoriaStatus: 'new',
          prioridade: null, criadaEm: '2026-09-30T12:00:00Z', doPainel: false, url: 'https://x/browse/DEV-2' },
      ],
    })
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/jira' })

    const historico = await screen.findByLabelText('Histórico do Jira')
    expect(await within(historico).findByText('Imagens duplicadas')).toBeInTheDocument()
    expect(within(historico).getByText('Pedido aberto no portal')).toBeInTheDocument()

    await user.click(screen.getByLabelText('só criadas pelo painel'))
    expect(within(historico).queryByText('Pedido aberto no portal')).not.toBeInTheDocument()
    expect(within(historico).getByText('Imagens duplicadas')).toBeInTheDocument()
  })
})

describe('Relatórios', () => {
  it('mostra os totais do histórico e os testes que mais falham, com link para a triagem', async () => {
    apiFalsa({
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': cypress,
      '/api/execucoes/em-andamento': [204, null],
      '/api/configuracoes': configuracoes,
      '/api/execucoes?projeto=cypress': [execucao],
      '/api/relatorios?projeto=cypress': {
        execucoes: 3, testesUnicos: 35, jaFalharam: 1, instaveis: 1, tempoTotalMs: 125_000,
        aprovacaoPorExecucao: [execucao],
        testesComFalha: [{
          chave: 'x', spec: 'cypress/e2e/user-behavior-matrix.cy.js', titulo: 'Matriz › problem_user › imagens',
          modulo: 'Matriz de usuários', tipoErro: 'Asserção', ultimaMensagem: 'AssertionError', falhas: 2, execucoes: 3,
          instavel: true, ultimaFalha: '2026-09-28T23:50:58Z', ultimoResultadoId: 2,
        }],
      },
    })
    renderComApp(<App />, { rota: '/relatorios' })

    const totais = await screen.findByLabelText('Totais')
    expect(await within(totais).findByText('35')).toBeInTheDocument()
    expect(within(totais).getByText('2min 5s')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Aprovação por execução' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'imagens' })).toHaveAttribute('href', '/triagem/2')
    expect(screen.getByText('instável')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Exportar CSV' })).toHaveAttribute('href', '/api/relatorios/execucoes.csv?projeto=cypress')
  })
})

describe('Configurações', () => {
  it('segredo configurado aparece só como selo: o campo vem vazio', async () => {
    renderComApp(<App />, { rota: '/configuracoes' })

    const campo = await screen.findByLabelText(/Chave da API Gemini/)
    expect(campo).toHaveValue('')
    expect(campo).toHaveAttribute('type', 'password')
    expect(campo).toHaveAttribute('placeholder', expect.stringContaining('deixe em branco para manter'))
    expect(screen.getByText('Gemini ativo')).toBeInTheDocument() // barra lateral
  })

  it('salvar envia PUT com a chave digitada e os campos do Jira', async () => {
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/configuracoes' })

    await user.type(await screen.findByLabelText(/Chave da API Gemini/), 'AQ.nova')
    await user.type(screen.getByLabelText('URL do Jira'), 'https://empresa.atlassian.net')
    await user.click(screen.getByRole('button', { name: 'Salvar configurações' }))

    await vi.waitFor(() => expect(corpoEnviado('/api/configuracoes')).toBeDefined())
    const corpo = corpoEnviado('/api/configuracoes')
    expect(corpo.ia).toMatchObject({ provedor: 'gemini', chaveGemini: 'AQ.nova' })
    expect(corpo.jira).toMatchObject({ url: 'https://empresa.atlassian.net', token: '' }) // token em branco = manter
  })

  it('"Testar conexão" mostra o usuário e o projeto retornados pelo Jira', async () => {
    apiFalsa({
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': cypress,
      '/api/execucoes/em-andamento': [204, null],
      '/api/configuracoes': {
        ...configuracoes,
        jira: { url: 'https://empresa.atlassian.net', email: 'qa@empresa.com', projeto: 'QA', tipoIssue: 'Bug', tokenConfigurado: true, configurado: true },
      },
      '/api/configuracoes/jira/testar': { usuario: 'Aníbal', email: 'qa@empresa.com', projeto: 'QA', nomeProjeto: 'Qualidade' },
    })
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/configuracoes' })

    await user.click(await screen.findByRole('button', { name: 'Testar conexão' }))

    expect(await screen.findByText(/Conectado como Aníbal ao projeto QA \(Qualidade\)/)).toBeInTheDocument()
    expect(screen.getByText('Projeto QA')).toBeInTheDocument() // barra lateral
  })

  it('"Remover chave" pede a remoção explícita do segredo', async () => {
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/configuracoes' })

    await user.click(await screen.findByRole('button', { name: 'Remover chave' }))

    await vi.waitFor(() => expect(corpoEnviado('/api/configuracoes')).toEqual({ remover: ['ia.gemini.chave'] }))
  })
})

describe('Seletor de projeto', () => {
  it('troca o projeto, avisa que o k6 não está instalado e lembra a escolha', async () => {
    const user = userEvent.setup()
    renderComApp(<App />)

    // Espera a lista chegar da API antes de escolher (senão só existe a opção atual).
    await screen.findByRole('option', { name: /k6/ })
    await user.selectOptions(screen.getByLabelText('Projeto de testes'), 'k6')

    expect(await screen.findByText(/Dependências do k6 \(desempenho\) não instaladas/)).toBeInTheDocument()
    expect(localStorage.getItem('qa-panel-pro:projeto')).toBe('k6')
  })
})

describe('Execuções', () => {
  it('a seleção rápida marca os specs do script escolhido', async () => {
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/execucoes' })

    await user.click(await screen.findByRole('button', { name: 'test:diagnostics' }))

    expect(screen.getByRole('checkbox', { name: /user-behavior-matrix/ })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /login/ })).not.toBeChecked()
    expect(screen.getByText('1 spec selecionado')).toBeInTheDocument()
  })

  it('lista o histórico e importa do painel Node com POST', async () => {
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/execucoes' })

    expect(await screen.findByRole('link', { name: /test:diagnostics/ })).toHaveTextContent('importada do painel Node')

    await user.click(screen.getByRole('button', { name: 'Importar do painel Node' }))

    expect(await screen.findByText(/5 importadas/)).toBeInTheDocument()
    expect(fetchFalso).toHaveBeenCalledWith('/api/importacoes/painel-node', expect.objectContaining({ method: 'POST' }))
  })

  it('detalhe agrupa por spec e mostra o erro da falha', async () => {
    renderComApp(<App />, { rota: '/execucoes/7' })

    expect(await screen.findByRole('heading', { name: 'test:diagnostics' })).toBeInTheDocument()
    expect(screen.getByText('AssertionError: imagens duplicadas')).toBeInTheDocument()
    expect(screen.getByText('Asserção')).toBeInTheDocument()
    expect(screen.getByText('1 falha')).toBeInTheDocument()
  })

  it('"Executar selecionados" envia o POST com os specs, o script e o navegador', async () => {
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/execucoes' })

    await user.click(await screen.findByRole('button', { name: 'test:diagnostics' }))
    await user.selectOptions(screen.getByLabelText('Navegador'), 'chrome')
    await user.click(screen.getByRole('button', { name: /Executar selecionados/ }))

    await vi.waitFor(() =>
      expect(corpoEnviado('/api/execucoes')).toEqual({
        projeto: 'cypress',
        script: 'test:diagnostics',
        specs: ['cypress/e2e/user-behavior-matrix.cy.js'],
        navegador: 'chrome',
        retentativas: 0,
        abrirNavegador: false,
      }),
    )
  })

  it('mexer num spec depois de escolher um script vira seleção manual (script null)', async () => {
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/execucoes' })

    await user.click(await screen.findByRole('button', { name: 'test' }))
    await user.click(screen.getByRole('checkbox', { name: /user-behavior-matrix/ }))
    await user.click(screen.getByRole('button', { name: /Executar selecionados/ }))

    await vi.waitFor(() => expect(corpoEnviado('/api/execucoes')?.script).toBeNull())
    expect(corpoEnviado('/api/execucoes').specs).toHaveLength(3)
  })

  it('mostra a mensagem do backend quando já há execução rodando (409)', async () => {
    apiFalsa({
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': cypress,
      '/api/execucoes/resumo?projeto=cypress': resumo,
      '/api/execucoes?projeto=cypress': [],
      '/api/execucoes/em-andamento': [204, null],
      '/api/execucoes': [409, { status: 409, title: 'Conflito', detail: 'Já existe uma execução em andamento.' }],
    })
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/execucoes' })

    await user.click(await screen.findByRole('button', { name: /Executar selecionados/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Já existe uma execução em andamento.')
  })

  it('"limpar" desmarca tudo e pede ao menos um spec', async () => {
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/execucoes' })

    await user.click(await screen.findByRole('button', { name: 'limpar' }))

    expect(screen.getByText('Selecione ao menos um spec')).toBeInTheDocument()
  })
})

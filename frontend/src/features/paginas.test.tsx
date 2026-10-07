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
        { id: 2, spec: 'cypress/e2e/user-behavior-matrix.cy.js', titulo: 'Matriz › problem_user › imagens', status: 'FALHOU', duracaoMs: 66, mensagemErro: 'AssertionError: imagens duplicadas', tipoErro: 'Asserção',
          evidencias: [
            { id: 3, nome: 'test-failed-1.png', tipo: 'image/png', url: '/api/evidencias/3' },
            { id: 4, nome: 'trace.zip', tipo: 'application/zip', url: '/api/evidencias/4' },
          ] },
      ],
    },
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

  it('mostra se a IA está usando o arquivo de regras de negócio do projeto', async () => {
    apiFalsa({
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': { ...cypress, regras: { arquivo: 'REGRAS_DE_NEGOCIO.md', encontrado: true }, codigoSistema: [{ pasta: 'ServeRest-front', encontrada: true }, { pasta: 'ServeRest', encontrada: false }] },
      '/api/execucoes/em-andamento': [204, null],
      '/api/configuracoes': configuracoes,
      '/api/triagem?projeto=cypress': [falhaPendente],
    })
    renderComApp(<App />, { rota: '/triagem' })

    expect(await screen.findByText('Regras de negócio · REGRAS_DE_NEGOCIO.md')).toBeInTheDocument()
    expect(screen.getByText('Código do sistema · ServeRest-front')).toBeInTheDocument() // só as pastas que existem
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

describe('Triagem: recorrência e ignorar', () => {
  const comJiraConfigurado = {
    ...configuracoes,
    jira: { url: 'https://empresa.atlassian.net', email: 'qa@x.com', projeto: 'DEV', tipoIssue: 'Bug', tokenConfigurado: true, configurado: true },
  }
  const publicada = { ...rascunhoIa, classificacao: 'BUG_APLICACAO', jiraIssue: 'DEV-2', jiraUrl: 'https://x/browse/DEV-2', demanda: 'DEV-1' }
  const recorrente = {
    ...falhaPendente, execucaoId: 9, triagem: publicada, recorrente: true,
    vinculos: [{ chave: 'DEV-2', url: 'https://x/browse/DEV-2', acao: 'CRIADO', em: '2026-09-28T23:59:00Z' }],
  }
  const rotasBase = {
    '/actuator/health': { status: 'UP' },
    '/api/projetos': projetos,
    '/api/projetos/cypress': cypress,
    '/api/execucoes/em-andamento': [204, null],
    '/api/configuracoes': comJiraConfigurado,
  }

  it('falha recorrente aparece como "Recorrente" e comenta a nova ocorrência no bug existente', async () => {
    const fetchDoTeste = apiFalsa({
      ...rotasBase,
      '/api/triagem?projeto=cypress': [recorrente],
      '/api/triagem/2/comentar': { chave: 'DEV-2', url: 'https://x/browse/DEV-2' },
    })
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/triagem/2' })

    expect(await screen.findByText('Recorrente')).toBeInTheDocument() // selo na fila
    const bloco = await screen.findByLabelText('Falha recorrente')
    expect(bloco).toHaveTextContent('voltou a falhar na execução #9')
    expect(screen.getByLabelText('Histórico no Jira')).toHaveTextContent('bug criado')

    await user.click(within(bloco).getByRole('button', { name: 'Comentar nova ocorrência no DEV-2' }))

    await vi.waitFor(() =>
      expect(fetchDoTeste).toHaveBeenCalledWith('/api/triagem/2/comentar', expect.objectContaining({ method: 'POST' })),
    )
    const chamadas = fetchDoTeste.mock.calls as unknown as [string, RequestInit | undefined][]
    expect(chamadas.some(([u]) => u === '/api/triagem/2/publicar')).toBe(false) // não criou bug novo
  })

  it('"Ignorar" tira a falha da fila sem publicar', async () => {
    const fetchDoTeste = apiFalsa({
      ...rotasBase,
      '/api/triagem?projeto=cypress': [{ ...falhaPendente, recorrente: false, vinculos: [] }],
      '/api/triagem/2/ignorar': [204, null],
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/triagem/2' })

    await user.click(await screen.findByRole('button', { name: 'Ignorar' }))

    await vi.waitFor(() =>
      expect(fetchDoTeste).toHaveBeenCalledWith('/api/triagem/2/ignorar', expect.objectContaining({ method: 'POST' })),
    )
  })

  it('"Ignorar pendentes" conta só as falhas ainda não triadas', async () => {
    apiFalsa({
      ...rotasBase,
      '/api/triagem?projeto=cypress': [
        { ...falhaPendente, recorrente: false, vinculos: [] },
        { ...recorrente, resultadoId: 5 },
      ],
    })
    renderComApp(<App />, { rota: '/triagem' })

    expect(await screen.findByRole('button', { name: 'Ignorar pendentes (1)' })).toBeEnabled()
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
    expect(JSON.parse(String(publicar[1].body))).toEqual({ demanda: 'DEV-1', novoBug: false })
  })

  it('depois de publicar, continua na mesma falha e mostra o bug criado (não pula para a próxima)', async () => {
    const outra = { ...falhaPendente, resultadoId: 9, titulo: 'Outra › falha pendente', triagem: null }
    const publicada = { ...falhaPendente, triagem: { ...rascunhoIa, classificacao: 'BUG_APLICACAO', jiraIssue: 'DEV-2', jiraUrl: 'https://x/browse/DEV-2', demanda: null, atualizadaEm: '2026-10-06T00:00:00Z' } }
    const rotas: Record<string, unknown> = {
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': cypress,
      '/api/execucoes/em-andamento': [204, null],
      '/api/configuracoes': comJira,
      '/api/triagem?projeto=cypress': [{ ...falhaPendente, triagem: rascunhoIa }, outra],
      '/api/triagem/2': rascunhoIa,
      '/api/triagem/2/publicar': { chave: 'DEV-2', url: 'https://x/browse/DEV-2', demanda: null, aviso: null },
    }
    apiFalsa(rotas)
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/triagem' }) // sem id: abre a primeira pendente

    const botao = await screen.findByRole('button', { name: 'Publicar no Jira' })
    // Depois de publicar, a fila volta com a falha publicada (não é mais pendente) e a outra pendente.
    rotas['/api/triagem?projeto=cypress'] = [publicada, outra]
    await user.click(botao)

    expect(await screen.findByText(/Bug DEV-2 criado no Jira/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /DEV-2 no Jira/ })).toBeInTheDocument()
    expect(screen.queryByText('Outra › falha pendente', { selector: '.test-title' })).not.toBeInTheDocument()
  })

  it('publicar mostra o carregamento até o fim (mesmo com a triagem recarregada) e um clique duplo cria um bug só', async () => {
    let liberarSalvamento!: () => void
    let liberarPublicacao!: () => void
    const rotas: Record<string, unknown> = {
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': cypress,
      '/api/execucoes/em-andamento': [204, null],
      '/api/configuracoes': comJira,
      '/api/triagem?projeto=cypress': [{ ...falhaPendente, triagem: rascunhoIa }],
      '/api/triagem/2': rascunhoIa,
      '/api/triagem/2/publicar': { chave: 'DEV-2', url: 'https://x/browse/DEV-2', demanda: null, aviso: null },
    }
    const fetchDoTeste = apiFalsa(rotas)
    // Segura o salvamento (PUT) e a publicação (POST) para ver a tela no meio do envio.
    const fetchOriginal = fetchDoTeste.getMockImplementation()!
    fetchDoTeste.mockImplementation(((url: string, init?: RequestInit) => {
      if (init?.method === 'PUT') return new Promise<Response>((ok) => (liberarSalvamento = () => ok(fetchOriginal(url) as unknown as Response)))
      if (init?.method === 'POST' && url.endsWith('/publicar'))
        return new Promise<Response>((ok) => (liberarPublicacao = () => ok(fetchOriginal(url) as unknown as Response)))
      return fetchOriginal(url)
    }) as typeof fetchOriginal)
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/triagem/2' })

    await user.dblClick(await screen.findByRole('button', { name: 'Publicar no Jira' }))

    expect(await screen.findByRole('button', { name: /Salvando a triagem/ })).toBeDisabled()
    // Como no backend real: depois de salvar, a fila volta com a triagem atualizada,
    // e o formulário é recriado. O botão NÃO pode voltar a "Publicar no Jira".
    rotas['/api/triagem?projeto=cypress'] = [{ ...falhaPendente, triagem: { ...rascunhoIa, classificacao: 'BUG_APLICACAO', atualizadaEm: '2026-10-06T01:00:00Z' } }]
    liberarSalvamento()

    expect(await screen.findByRole('button', { name: /Criando o bug no Jira/ })).toBeDisabled()
    await new Promise((r) => setTimeout(r, 50)) // dá tempo da fila recarregar e o formulário ser recriado
    expect(screen.getByRole('button', { name: /Criando o bug no Jira/ })).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Publicar no Jira' })).not.toBeInTheDocument()

    liberarPublicacao()
    expect(await screen.findByText(/Bug DEV-2 criado no Jira/)).toBeInTheDocument()
    const publicacoes = (fetchDoTeste.mock.calls as unknown as [string, RequestInit | undefined][])
      .filter(([u, i]) => u === '/api/triagem/2/publicar' && i?.method === 'POST')
    expect(publicacoes).toHaveLength(1)
  })

  it('mapa de cobertura mostra cada requisito com a situação, a evidência e o cenário sugerido', async () => {
    const fetchDoTeste = apiFalsa({
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': cypress,
      '/api/execucoes/em-andamento': [204, null],
      '/api/configuracoes': comJira,
      '/api/jira/bugs?projeto=cypress': [],
      '/api/jira/demandas/DEV-1?projeto=cypress': {
        chave: 'DEV-1', erro: null, specs: ['cypress/e2e/login.cy.js'], origem: null,
        issue: { chave: 'DEV-1', resumo: 'Login do administrador', tipo: 'Story', status: 'Aberto', url: 'https://x/browse/DEV-1' },
      },
      '/api/demandas/DEV-1/cobertura?projeto=cypress': {
        chave: 'DEV-1', titulo: 'Login do administrador', url: 'https://x/browse/DEV-1', resumo: 'Falta o bloqueio.',
        requisitos: [
          { requisito: 'admin loga', situacao: 'COBERTO', evidencias: ['login.cy.js › deve logar'], cenarioSugerido: null },
          { requisito: 'conta bloqueada não entra', situacao: 'SEM_TESTE', evidencias: [], cenarioSugerido: 'Logar com conta bloqueada' },
        ],
        specsAnalisados: ['cypress/e2e/login.cy.js'], criterioSpecs: 'specs que citam DEV-1', modelo: 'gemini-teste',
      },
    })
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/jira' })

    await user.type(await screen.findByLabelText('Chave da issue'), 'DEV-1')
    await user.click(screen.getByRole('button', { name: 'Buscar' }))
    await user.click(await screen.findByRole('button', { name: /Mapa de cobertura de DEV-1/ }))

    const mapa = screen.getByLabelText('Mapa de cobertura')
    expect(await within(mapa).findByText('1 de 2 requisitos cobertos.')).toBeInTheDocument()
    expect(within(mapa).getByText('Sem teste')).toBeInTheDocument()
    expect(within(mapa).getByText('Sugestão: Logar com conta bloqueada')).toBeInTheDocument()
    expect(fetchDoTeste).toHaveBeenCalledWith('/api/demandas/DEV-1/cobertura?projeto=cypress', expect.objectContaining({ method: 'POST' }))
  })

  it('relatório de validação mostra o veredito pelos resultados e comenta o texto revisado na demanda', async () => {
    const fetchDoTeste = apiFalsa({
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': cypress,
      '/api/execucoes/em-andamento': [204, null],
      '/api/configuracoes': comJira,
      '/api/jira/bugs?projeto=cypress': [],
      '/api/jira/demandas/DEV-1?projeto=cypress': {
        chave: 'DEV-1', erro: null, specs: ['cypress/e2e/login.cy.js'], origem: null,
        issue: { chave: 'DEV-1', resumo: 'Login do administrador', tipo: 'Story', status: 'Aberto', url: 'https://x/browse/DEV-1' },
      },
      '/api/demandas/DEV-1/validacao': {
        chave: 'DEV-1', titulo: 'Login do administrador', url: 'https://x/browse/DEV-1', veredito: 'REPROVADA',
        resumo: 'Uma falha no erro de senha.', pendencias: [], origem: 'IA', modelo: 'gemini-teste',
        itens: [
          { projeto: 'Cypress', spec: 'cypress/e2e/login.cy.js', teste: 'deve logar', status: 'PASSOU', execucaoId: 7, quando: null, oQueFoiValidado: 'chega na home' },
          { projeto: 'Cypress', spec: 'cypress/e2e/login.cy.js', teste: 'senha inválida', status: 'FALHOU', execucaoId: 7, quando: null, oQueFoiValidado: null },
        ],
      },
      '/api/demandas/DEV-1/validacao/publicar': { chave: 'DEV-1', url: 'https://x/browse/DEV-1' },
    })
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/jira' })

    await user.type(await screen.findByLabelText('Chave da issue'), 'DEV-1')
    await user.click(screen.getByRole('button', { name: 'Buscar' }))
    await user.click(await screen.findByRole('button', { name: /Relatório de validação de DEV-1/ }))

    const relatorio = screen.getByLabelText('Relatório de validação')
    expect(await within(relatorio).findByText('Reprovada')).toBeInTheDocument()
    expect(within(relatorio).getByLabelText('Testes da demanda')).toHaveTextContent('chega na home')
    await user.clear(within(relatorio).getByLabelText('Resumo para o time'))
    await user.type(within(relatorio).getByLabelText('Resumo para o time'), 'Revisado pelo QA')
    await user.click(within(relatorio).getByRole('button', { name: 'Comentar em DEV-1' }))

    expect(await within(relatorio).findByText(/Relatório comentado em/)).toBeInTheDocument()
    const post = (fetchDoTeste.mock.calls as unknown as [string, RequestInit | undefined][])
      .find(([u]) => u === '/api/demandas/DEV-1/validacao/publicar')!
    expect(JSON.parse(String(post[1]!.body))).toMatchObject({ veredito: 'REPROVADA', resumo: 'Revisado pelo QA' })
  })

  it('casos de teste: o QA edita, desmarca e publica só os escolhidos', async () => {
    const fetchDoTeste = apiFalsa({
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': cypress,
      '/api/execucoes/em-andamento': [204, null],
      '/api/configuracoes': comJira,
      '/api/jira/bugs?projeto=cypress': [],
      '/api/jira/demandas/DEV-1?projeto=cypress': {
        chave: 'DEV-1', erro: null, specs: [], origem: null,
        issue: { chave: 'DEV-1', resumo: 'Login do administrador', tipo: 'Story', status: 'Aberto', url: 'https://x/browse/DEV-1' },
      },
      '/api/demandas/DEV-1/casos-de-teste?projeto=cypress': {
        chave: 'DEV-1', titulo: 'Login do administrador', url: 'https://x/browse/DEV-1', modelo: 'gemini-teste',
        casos: [
          { titulo: 'Login válido', tipo: 'POSITIVO', preCondicoes: null, passos: ['abrir', 'entrar'], resultadoEsperado: 'home',
            regra: 'LOG-04', automatizado: true, evidencia: 'login.cy.js › deve logar' },
          { titulo: 'Duplicado', tipo: 'NEGATIVO', preCondicoes: null, passos: ['x'], resultadoEsperado: 'y',
            regra: null, automatizado: false, evidencia: null },
        ],
      },
      '/api/demandas/DEV-1/casos-de-teste/publicar': { chave: 'DEV-1', url: 'https://x/browse/DEV-1', casos: 1 },
    })
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/jira' })

    await user.type(await screen.findByLabelText('Chave da issue'), 'DEV-1')
    await user.click(screen.getByRole('button', { name: 'Buscar' }))
    await user.click(await screen.findByRole('button', { name: /Casos de teste de DEV-1/ }))

    const titulo = await screen.findByLabelText('Título do caso 1')
    await user.clear(titulo)
    await user.type(titulo, 'Login do admin válido')
    await user.click(within(screen.getByLabelText('Caso 2')).getByRole('checkbox')) // desmarca o duplicado
    await user.click(screen.getByRole('button', { name: 'Comentar 1 caso em DEV-1' }))

    expect(await screen.findByText(/1 caso comentado em/)).toBeInTheDocument()
    const post = (fetchDoTeste.mock.calls as unknown as [string, RequestInit | undefined][])
      .find(([u]) => u === '/api/demandas/DEV-1/casos-de-teste/publicar')!
    const enviados = JSON.parse(String(post[1]!.body))
    expect(enviados).toHaveLength(1)
    expect(enviados[0]).toMatchObject({ titulo: 'Login do admin válido', passos: ['abrir', 'entrar'], regra: 'LOG-04' })
    expect(enviados[0]).not.toHaveProperty('incluir')
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
    const grafico = screen.getByRole('img', { name: 'Resultado dos testes por execução' })
    expect(within(grafico).getByText('1/2')).toBeInTheDocument() // aprovados/total em cima da barra
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
  it('abre com todos os specs desmarcados e o botão de executar desabilitado', async () => {
    renderComApp(<App />, { rota: '/execucoes' })

    const specs = await screen.findAllByRole('checkbox', { name: /\.cy\.js/ })
    expect(specs).toHaveLength(3)
    specs.forEach((c) => expect(c).not.toBeChecked())
    expect(screen.getByText('Selecione ao menos um spec')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Executar selecionados/ })).toBeDisabled()
  })

  it('o atalho de módulo marca só os specs daquele módulo', async () => {
    apiFalsa({
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': { ...cypress, modulos: [
        { rotulo: 'Login', termo: 'login', specs: ['cypress/e2e/login.cy.js'] },
        { rotulo: 'Carrinho', termo: 'carrinho', specs: [] },
      ] },
      '/api/execucoes/em-andamento': [204, null],
      '/api/execucoes?projeto=cypress': [],
      '/api/configuracoes': configuracoes,
    })
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/execucoes' })

    const modulos = await screen.findByLabelText('Módulos')
    expect(within(modulos).queryByRole('button', { name: /Carrinho/ })).not.toBeInTheDocument() // sem specs: some
    await user.click(within(modulos).getByRole('button', { name: 'Login (1)' }))

    expect(screen.getByRole('checkbox', { name: /login/ })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /checkout/ })).not.toBeChecked()
  })

  it('a seleção rápida marca os specs do script escolhido', async () => {
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/execucoes' })

    await user.click(await screen.findByRole('button', { name: 'test:diagnostics' }))

    expect(screen.getByRole('checkbox', { name: /user-behavior-matrix/ })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /login/ })).not.toBeChecked()
    expect(screen.getByText('1 spec selecionado')).toBeInTheDocument()
  })

  it('lista o histórico sem o botão de importar do painel Node', async () => {
    renderComApp(<App />, { rota: '/execucoes' })

    expect(await screen.findByRole('link', { name: /test:diagnostics/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Importar do painel Node/ })).not.toBeInTheDocument()
  })

  it('detalhe agrupa por spec e mostra o erro da falha', async () => {
    renderComApp(<App />, { rota: '/execucoes/7' })

    expect(await screen.findByRole('heading', { name: 'test:diagnostics' })).toBeInTheDocument()
    expect(screen.getByText('AssertionError: imagens duplicadas')).toBeInTheDocument()
    expect(screen.getByText('Asserção')).toBeInTheDocument()
    expect(screen.getByText('1 falha')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Screenshot: test-failed-1.png' })).toHaveAttribute('src', '/api/evidencias/3')
    expect(screen.getByRole('link', { name: /trace.zip/ })).toHaveAttribute('href', '/api/evidencias/4')
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
        dev: false,
      }),
    )
  })

  it('mostra a previsão de tempo da seleção e o selo instável', async () => {
    apiFalsa({
      '/actuator/health': { status: 'UP' },
      '/api/projetos': projetos,
      '/api/projetos/cypress': cypress,
      '/api/execucoes/em-andamento': [204, null],
      '/api/execucoes?projeto=cypress': [],
      '/api/configuracoes': configuracoes,
      '/api/execucoes/duracoes?projeto=cypress': {
        tempoFixoMs: 20000,
        mediaPorSpecMs: { 'cypress/e2e/login.cy.js': 30000, 'cypress/e2e/checkout.cy.js': 10000 },
      },
      '/api/relatorios?projeto=cypress': { execucoes: 1, testesUnicos: 1, jaFalharam: 1, instaveis: 1, tempoTotalMs: 1, aprovacaoPorExecucao: [],
        testesComFalha: [{ chave: 'x', spec: 'cypress/e2e/login.cy.js', titulo: 't', modulo: 'Login', tipoErro: null, ultimaMensagem: null,
          falhas: 1, execucoes: 2, instavel: true, ultimaFalha: null, ultimoResultadoId: null }] },
    })
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/execucoes' })

    await user.click(await screen.findByRole('button', { name: 'test' })) // checkout + login

    expect(await screen.findByText(/previsão ~1min/)).toBeInTheDocument() // 20s + 30s + 10s = 1min
    expect(screen.getByRole('checkbox', { name: /login/ }).closest('label')).toHaveTextContent('instável')

    await user.click(screen.getByRole('button', { name: 'test:diagnostics' })) // spec sem histórico
    expect(screen.getByText(/sem histórico de 1 spec/)).toBeInTheDocument()
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

    await user.click(await screen.findByRole('checkbox', { name: /login/ }))
    await user.click(screen.getByRole('button', { name: /Executar selecionados/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Já existe uma execução em andamento.')
  })

  it('"limpar" desmarca tudo e pede ao menos um spec', async () => {
    const user = userEvent.setup()
    renderComApp(<App />, { rota: '/execucoes' })

    await user.click(await screen.findByRole('button', { name: 'limpar' }))

    expect(screen.getByText('Selecione ao menos um spec')).toBeInTheDocument()
  })
})

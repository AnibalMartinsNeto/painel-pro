import { Navigate, Route, Routes } from 'react-router'
import { Layout } from './components/Layout'
import { ExecucaoDetalhePage } from './features/execucoes/ExecucaoDetalhePage'
import { ExecucoesPage } from './features/execucoes/ExecucoesPage'
import { AzurePage, RelatoriosPage, TriagemPage } from './features/outras/OutrasPaginas'
import { ProjetoAtualProvider } from './features/projetos/ProjetoAtual'
import { VisaoGeralPage } from './features/visao-geral/VisaoGeralPage'

/**
 * Mapa de rotas: cada URL aponta para um componente de página.
 * Todas ficam dentro do <Layout> (barra lateral) e do provedor do
 * projeto selecionado, compartilhado por todas as telas.
 */
export function App() {
  return (
    <ProjetoAtualProvider>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<VisaoGeralPage />} />
          <Route path="execucoes" element={<ExecucoesPage />} />
          <Route path="execucoes/:id" element={<ExecucaoDetalhePage />} />
          <Route path="triagem" element={<TriagemPage />} />
          <Route path="azure" element={<AzurePage />} />
          <Route path="relatorios" element={<RelatoriosPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </ProjetoAtualProvider>
  )
}

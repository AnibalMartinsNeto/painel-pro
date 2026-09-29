import { Navigate, Route, Routes } from 'react-router'
import { EmBreve } from './components/Estado'
import { Layout } from './components/Layout'
import { ProjetoDetalhePage } from './features/projetos/ProjetoDetalhePage'
import { ProjetosPage } from './features/projetos/ProjetosPage'

/**
 * Mapa de rotas: cada URL aponta para um componente de página.
 * Todas ficam dentro do <Layout>, que desenha a barra lateral.
 */
export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/projetos" replace />} />
        <Route path="projetos" element={<ProjetosPage />} />
        <Route path="projetos/:id" element={<ProjetoDetalhePage />} />
        <Route
          path="execucoes"
          element={<EmBreve titulo="Execuções" etapa={4} descricao="Disparar specs, log ao vivo e histórico gravado no PostgreSQL." />}
        />
        <Route
          path="triagem"
          element={<EmBreve titulo="Triagem IA" etapa={6} descricao="Análise das falhas com IA e rascunho do bug." />}
        />
        <Route
          path="azure"
          element={<EmBreve titulo="Azure DevOps" etapa={6} descricao="Publicação de bugs e busca de testes por demanda." />}
        />
        <Route
          path="relatorios"
          element={<EmBreve titulo="Relatórios" etapa={4} descricao="Aprovação por execução, testes instáveis e exportação." />}
        />
        <Route path="*" element={<EmBreve titulo="Página não encontrada" etapa={0} descricao="Essa rota não existe." />} />
      </Route>
    </Routes>
  )
}

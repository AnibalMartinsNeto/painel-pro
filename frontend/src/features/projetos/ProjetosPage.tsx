import { Carregando, ErroApi } from '../../components/Estado'
import { useProjetos } from './api'
import { ProjetoCard } from './ProjetoCard'

/**
 * Tela /projetos. Todo componente que busca dados trata os três estados
 * possíveis: carregando, erro e sucesso. Esquecer um deles é uma fonte
 * clássica de bug (tela em branco, spinner infinito).
 */
export function ProjetosPage() {
  const { data: projetos, isPending, error } = useProjetos()

  return (
    <>
      <header className="page-head">
        <div>
          <h1>Projetos de teste</h1>
          <p>Projetos que o painel sabe executar, lidos do backend em <code>GET /api/projetos</code>.</p>
        </div>
        {projetos && (
          <span className="pill ok">
            {projetos.filter((p) => p.instalado).length} de {projetos.length} prontos
          </span>
        )}
      </header>

      {isPending && <Carregando texto="Buscando projetos na API…" />}
      {error && <ErroApi erro={error} />}
      {projetos && (
        <section className="grid2" aria-label="Lista de projetos">
          {projetos.map((p) => (
            <ProjetoCard key={p.id} projeto={p} />
          ))}
        </section>
      )}
    </>
  )
}

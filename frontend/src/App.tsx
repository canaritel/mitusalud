import { useGet, type Agua, type Energia, type Peso, type Resultado } from './api'
import { RegistroAgua } from './RegistroAgua'

// 'es-ES' decide el formato ("27 sept 2026, 10:30"); la hora sale en la zona horaria del navegador.
const formatoMomento = new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium', timeStyle: 'short' })
const formatoKilos = new Intl.NumberFormat('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

function momento(observadoEn: string) {
  return formatoMomento.format(new Date(observadoEn))
}

// Mensaje de un apartado mientras carga, si falla o si está vacío. Con datos, no muestra nada.
function Aviso({ resultado }: { resultado: Resultado<unknown> }) {
  if (resultado.estado === 'cargando') return <p>Cargando…</p>
  if (resultado.estado === 'error') return <p className="error">No se ha podido cargar la lista. Comprueba la conexión y recarga la página.</p>
  if (resultado.datos.length === 0) return <p>Todavía no hay registros.</p>
  return null
}

export default function App() {
  const [pesos] = useGet<Peso>('/api/v1/pesos')
  const [agua, recargarAgua] = useGet<Agua>('/api/v1/agua')
  const [energia] = useGet<Energia>('/api/v1/energia')

  return (
    <main>
      <h1>
        mitusalud <small>prototipo local</small>
      </h1>

      <section>
        <h2>Peso</h2>
        <Aviso resultado={pesos} />
        {pesos.estado === 'ok' && (
          <ul>
            {pesos.datos.map((peso) => (
              <li key={peso.id}>
                <span>{momento(peso.observadoEn)}</span>
                <strong>{formatoKilos.format(peso.kilos)} kg</strong>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h2>Agua</h2>
        <RegistroAgua alConfirmar={recargarAgua} />
        <Aviso resultado={agua} />
        {agua.estado === 'ok' && (
          <ul>
            {agua.datos.map((registro) => (
              <li key={registro.id}>
                <span>{momento(registro.observadoEn)}</span>
                <strong>{registro.mililitros} ml</strong>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h2>Energía</h2>
        <Aviso resultado={energia} />
        {energia.estado === 'ok' && (
          <ul>
            {energia.datos.map((registro) => (
              <li key={registro.id}>
                <span>{momento(registro.observadoEn)}</span>
                <strong>{registro.nivel} / 5</strong>
                {/* React escapa el texto: una nota con "<" se muestra tal cual, nunca como HTML. */}
                {registro.nota && <p className="nota">{registro.nota}</p>}
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}

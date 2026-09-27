import { useState } from 'react'
import { registrarAgua, type Envio, type OperacionAgua } from './api'

// Una operación aún no confirmada: se está enviando, quedó sin confirmar o el servidor la rechazó.
type Pendiente = OperacionAgua & { estado: 'enviando' | Exclude<Envio, 'confirmado'> }

const formatoHora = new Intl.DateTimeFormat('es-ES', { timeStyle: 'short' })

// Registrar agua (docs/MODELO_DATOS.md, pieza 4).
// - Cada pulsación de "+250 ml" o "Añadir" es una operación nueva, con clave nueva: dos vasos, dos registros.
// - "Reintentar" repite una operación concreta con su misma clave y sus mismos datos, hora incluida:
//   si el servidor ya la guardó, devuelve ese registro en vez de crear otro.
// - Las operaciones sin confirmar viven solo en memoria: si se recarga la página ya no se pueden
//   reintentar desde aquí, aunque el servidor pudiera haberlas guardado.
export function RegistroAgua({ alConfirmar }: { alConfirmar: () => void }) {
  const [pendientes, setPendientes] = useState<Pendiente[]>([])
  const [otraCantidad, setOtraCantidad] = useState('')

  // Mientras hay un envío en curso, los botones se desactivan: evita el doble toque accidental.
  const enviando = pendientes.some((p) => p.estado === 'enviando')
  const cantidad = Number(otraCantidad)
  const cantidadValida = Number.isInteger(cantidad) && cantidad >= 1 && cantidad <= 5000

  function cambiarEstado(clave: string, estado: Pendiente['estado']) {
    setPendientes((actuales) => actuales.map((p) => (p.clave === clave ? { ...p, estado } : p)))
  }

  function quitar(clave: string) {
    setPendientes((actuales) => actuales.filter((p) => p.clave !== clave))
  }

  async function enviar(operacion: OperacionAgua) {
    cambiarEstado(operacion.clave, 'enviando')
    const resultado = await registrarAgua(operacion)
    if (resultado === 'confirmado') {
      quitar(operacion.clave)
      // Si falla la recarga, la lista muestra su propio error: el registro ya está confirmado.
      alConfirmar()
    } else {
      cambiarEstado(operacion.clave, resultado)
    }
  }

  // Operación nueva: clave nueva y datos fijados ahora, incluida la hora.
  function registrar(mililitros: number) {
    const operacion = { clave: crypto.randomUUID(), observadoEn: new Date().toISOString(), mililitros }
    setPendientes((actuales) => [{ ...operacion, estado: 'enviando' }, ...actuales])
    void enviar(operacion)
  }

  return (
    <div>
      <div className="acciones">
        <button type="button" onClick={() => registrar(250)} disabled={enviando}>
          +250 ml
        </button>
        <form
          onSubmit={(evento) => {
            evento.preventDefault()
            registrar(cantidad)
            setOtraCantidad('')
          }}
        >
          <input
            type="number"
            inputMode="numeric"
            min={1}
            max={5000}
            step={1}
            placeholder="ml"
            aria-label="Otra cantidad, en mililitros"
            value={otraCantidad}
            onChange={(evento) => setOtraCantidad(evento.target.value)}
          />
          <button type="submit" disabled={enviando || !cantidadValida}>
            Añadir
          </button>
        </form>
      </div>

      {pendientes.map((p) => (
        <p key={p.clave} className={`pendiente ${p.estado}`}>
          {p.mililitros} ml · {formatoHora.format(new Date(p.observadoEn))} ·{' '}
          {p.estado === 'enviando' && 'enviando…'}
          {p.estado === 'sin-confirmar' && (
            <>
              sin confirmar (reintentar no lo duplica){' '}
              <button type="button" onClick={() => void enviar(p)} disabled={enviando}>
                Reintentar
              </button>
            </>
          )}
          {p.estado === 'rechazado' && (
            <>
              rechazado por el servidor{' '}
              <button type="button" onClick={() => quitar(p.clave)}>
                Descartar
              </button>
            </>
          )}
        </p>
      ))}
    </div>
  )
}

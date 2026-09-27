import { useCallback, useEffect, useState } from 'react'

// Lo que devuelve la API de cada tipo, escrito a mano mientras la API sea pequeña.
// observadoEn llega en UTC como texto ISO, por ejemplo "2026-09-26T14:00:00Z".
export type Peso = { id: string; observadoEn: string; kilos: number }
export type Agua = { id: string; observadoEn: string; mililitros: number }
export type Energia = { id: string; observadoEn: string; nivel: number; nota: string | null }

// Estado de una petición: cargando, con datos o con error.
export type Resultado<T> =
  | { estado: 'cargando' }
  | { estado: 'ok'; datos: T[] }
  | { estado: 'error' }

// Pide una lista a la API. Devuelve su estado y una función para volver a pedirla.
export function useGet<T>(ruta: string): [Resultado<T>, () => void] {
  const [resultado, setResultado] = useState<Resultado<T>>({ estado: 'cargando' })
  // Cambiar "version" repite la petición. Mientras llega, se sigue viendo la lista anterior.
  const [version, setVersion] = useState(0)

  useEffect(() => {
    // Si el componente desaparece antes de que llegue la respuesta (o React repite el efecto
    // en desarrollo), la respuesta se ignora en vez de pintarse donde ya no toca.
    let ignorar = false

    fetch(ruta)
      .then((respuesta) => {
        // fetch solo falla si no hay conexión: un 404 o un 500 hay que comprobarlo a mano.
        if (!respuesta.ok) throw new Error(`HTTP ${respuesta.status}`)
        return respuesta.json() as Promise<T[]>
      })
      .then((datos) => {
        if (!ignorar) setResultado({ estado: 'ok', datos })
      })
      .catch(() => {
        if (!ignorar) setResultado({ estado: 'error' })
      })

    return () => {
      ignorar = true
    }
  }, [ruta, version])

  const recargar = useCallback(() => setVersion((v) => v + 1), [])
  return [resultado, recargar]
}

// Una operación de registro de agua. Su clave y sus datos se fijan al crearla y no cambian al reintentar.
export type OperacionAgua = { clave: string; observadoEn: string; mililitros: number }

// Cómo acabó un envío:
// - 'confirmado': el servidor lo ha guardado (201).
// - 'sin-confirmar': no se sabe si llegó (sin conexión, sin respuesta a tiempo o error 5xx). Se puede
//   reintentar con la misma clave y los mismos datos: el servidor no lo guardará dos veces.
// - 'rechazado': el servidor lo rechazó (400, 422). Reenviar lo mismo daría el mismo error.
export type Envio = 'confirmado' | 'sin-confirmar' | 'rechazado'

export async function registrarAgua(operacion: OperacionAgua): Promise<Envio> {
  try {
    const respuesta = await fetch('/api/v1/agua', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': operacion.clave },
      body: JSON.stringify({ observadoEn: operacion.observadoEn, mililitros: operacion.mililitros }),
      // Sin respuesta en 10 s se deja de esperar, para que la pantalla no se quede bloqueada.
      signal: AbortSignal.timeout(10_000),
    })
    if (respuesta.ok) return 'confirmado'
    return respuesta.status >= 500 ? 'sin-confirmar' : 'rechazado'
  } catch {
    // Sin conexión o sin respuesta a tiempo: puede que el servidor lo guardara y se perdiera la respuesta.
    return 'sin-confirmar'
  }
}

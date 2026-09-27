import { useEffect, useState } from 'react'

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

// Pide una lista a la API. Lo usan los tres apartados, cada uno con su propio estado.
export function useGet<T>(ruta: string): Resultado<T> {
  const [resultado, setResultado] = useState<Resultado<T>>({ estado: 'cargando' })

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
  }, [ruta])

  return resultado
}

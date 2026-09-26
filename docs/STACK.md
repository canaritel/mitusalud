# Stack tecnológico

Propuesta viva, iniciada el 26/09/2026. Donde este documento difiere de la documentación de análisis, manda este documento.

## Principios

- **No sobredimensionar.** Cada tecnología entra cuando existe una necesidad real, no por si acaso.
- **Sencillo antes que fácil.** Se prefieren pocas piezas y poco acopladas, aunque otra opción parezca más rápida al principio.
- **Comprensible para un junior.** El código lleva comentarios útiles que explican el porqué, no lo que ya dice el código.
- **DTOs mínimos.** Sin clase de dominio separada fuera de `ingesta`. Los DTOs son `record` de una línea y solo existen si aportan algo: el de entrada impide que el cliente imponga campos como `id` u `ownerId`; el de salida desacopla la API de la tabla. Si coinciden, un solo record. Conversión con un método estático pequeño, sin librerías de mapeo.
- **Paso a paso.** Se empieza por un esqueleto mínimo que funciona de punta a punta y crece con cada necesidad.
- **Guía al día.** Cada paso que cambie estructura, tecnología o forma de arrancar actualiza también [`docs/guia/index.html`](guia/index.html) en el mismo commit.

## Estado de cada pieza

- **Aprobada:** se usa desde que haga falta.
- **Propuesta:** candidata; se decide al llegar a la tarea que la necesite.

## Backend

| Tecnología | Estado | Motivo |
|---|---|---|
| Java 21 (LTS) | Aprobada | Versión más extendida en empresa junto a 17. Pasar a 25 es un cambio menor cuando convenga. |
| Spring Boot 4 | Aprobada | Web, datos y seguridad integrados; estándar en Java. |
| Maven | Aprobada | Gestión de dependencias y build habitual en proyectos Java. |
| springdoc-openapi | Aprobada | Genera el contrato OpenAPI y Swagger UI a partir de los controllers, sin mantener un YAML a mano. |
| Monolito modular (`diario`, `ingesta`, `analitica`) | Aprobada | Cada módulo expone solo su parte pública, es dueño de sus tablas y se comunica con los demás por su API pública o por eventos. Es lo que permite extraer un módulo a un servicio en el futuro. |
| Spring Modulith | Aprobada | Un test verifica esas fronteras en cada build. |
| Hexagonal completa en `ingesta` | Aprobada | Ahí hay dependencias intercambiables reales: proveedor de IA y almacenamiento. |
| Hexagonal ligera en el resto | Aprobada | Reglas en service y dominio, nunca en el controller; el controller no expone entidades JPA. Pasar a hexagonal completa queda como refactor local. |
| Spring Data JPA | Aprobada | Acceso a PostgreSQL; es lo habitual en empresa. |
| Bean Validation | Aprobada | Reglas declaradas con anotaciones en los records (`@NotNull`, `@Positive`). |
| ProblemDetail (RFC 9457) | Aprobada | Formato de error estándar en toda la API; viene con Spring. |
| Idiomas: español e inglés | Aprobada | La API responde según la cabecera `Accept-Language`; español por defecto y para cualquier idioma no admitido. Textos propios en `messages*.properties`. |
| Spring Boot DevTools | Aprobada | Reinicio automático al guardar, solo en desarrollo. |
| Actuator (solo `health`) | Aprobada | Estado de la aplicación y la base de datos para healthchecks. |
| Virtual threads | Aprobada | Concurrencia de Java 21 activada con una propiedad, sin cambiar el código. |
| `@WebMvcTest` | Aprobada | Prueba la capa web (rutas, validación, errores, idiomas) sin base de datos, con el service simulado. |
| Spring Security, RestClient, Spring AI | Propuesta | Se deciden al llegar a login, integraciones externas y G5. |
| WebFlux, Spring Cloud, Spring Batch, GraalVM native | Descartadas | Complejidad sin necesidad: los virtual threads cubren la concurrencia y no hay microservicios ni procesos por lotes. |
| ArchUnit | Propuesta | Reglas de arquitectura adicionales si Spring Modulith no basta. |

## Datos

| Tecnología | Estado | Motivo |
|---|---|---|
| PostgreSQL | Aprobada | Datos muy relacionados que exigen integridad: claves foráneas, restricciones y transacciones. |
| Flyway | Aprobada | Migraciones versionadas: cualquier instalación llega al mismo esquema. |
| Outbox en PostgreSQL | Propuesta | Para trabajos en segundo plano, cuando existan. |

## Identidad

| Tecnología | Estado | Motivo |
|---|---|---|
| Keycloak (OIDC + PKCE) | Propuesta | Login externo con MFA; estándar en empresa, pero pesado para quien despliegue su instancia. |
| Spring Security con sesión y passkeys | Propuesta | Alternativa más sencilla. Se compara con Keycloak antes del primer dato real. |

## Frontend

| Tecnología | Estado | Motivo |
|---|---|---|
| React + TypeScript | Aprobada | Interfaz por componentes con tipado estático. |
| Vite | Aprobada | Desarrollo y build del frontend. |
| SPA sin Next.js | Aprobada | El backend ya es Spring; un servidor Node adicional no aporta nada. |
| React Router | Propuesta | Cuando haya más de una pantalla. |
| TanStack Query | Propuesta | Cuando la caché y los reintentos contra la API se noten necesarios. |
| React Hook Form + Zod | Propuesta | Cuando los formularios crezcan. |
| Cliente generado desde OpenAPI | Propuesta | Mientras la API sea pequeña, los tipos se escriben a mano. |
| Tailwind CSS + shadcn/ui | Propuesta | Estilos y componentes accesibles, cuando la interfaz lo pida. |
| vite-plugin-pwa | Propuesta | Para instalar la aplicación en móvil y escritorio. |

## Pruebas

| Tecnología | Estado | Motivo |
|---|---|---|
| JUnit 5 | Aprobada | Viene con Spring Boot. |
| Tests de integración (`*IT`) contra PostgreSQL real | Aprobada | En desarrollo, base `mitusalud_test` en el servidor por el túnel, con `./mvnw verify -Pbd`. El `verify` normal no los ejecuta y no necesita túnel. La anotación `@UsaBaseDeDatosDeTest` impide que apunten a otra base: `@TestPropertySource` gana a las variables de entorno y un guardia comprueba la base antes de Flyway. |
| Testcontainers | Propuesta | PostgreSQL desechable en GitHub Actions, que ya trae Docker. |
| Vitest + Testing Library | Propuesta | Pruebas de componentes React. |
| Playwright | Propuesta | Pruebas de extremo a extremo en navegador. |

## Infraestructura

| Tecnología | Estado | Motivo |
|---|---|---|
| Docker + Docker Compose | Aprobada | Cualquiera levanta su instancia con `docker compose up`. |
| GitHub Actions | Propuesta | Build y pruebas en cada cambio. |
| Caddy | Propuesta | Proxy con HTTPS automático, al desplegar en el VPS. |
| Restic + Backblaze B2 | Propuesta | Copias cifradas fuera del servidor, antes del primer dato real. |

## Esqueleto provisional

El primer paso de código registra y lista pesos en una tabla `peso` simple (`V1__crear_tabla_peso.sql`). Sirve para validar la cadena completa: API, validación, errores, Flyway y PostgreSQL.

- No sigue todavía el modelo del backlog, donde el peso es una `Observation` con propietario, idempotencia, auditoría y revisiones (L-14 a L-20).
- Cuando se adopte el modelo definitivo, una migración de Flyway trasladará o descartará estos datos; solo hay datos inventados.
- **No se añade un segundo tipo de registro** (agua, energía…) hasta revisar y decidir ese modelo, pieza a pieza y con el mismo criterio de no sobredimensionar.

## Entorno de desarrollo

- Backend y frontend se ejecutan directamente en el equipo de desarrollo (`./mvnw spring-boot:run` y `npm run dev`); solo necesitan Java 21 y Node.
- PostgreSQL 17 corre en Docker en un servidor Linux de la red local, en un contenedor propio que no se comparte con otros proyectos.
- En ese servidor PostgreSQL escucha solo en `127.0.0.1:5433`. El equipo de desarrollo llega mediante un túnel SSH:

  ```
  ssh -N -L 5433:localhost:5433 usuario@servidor
  ```

  Con el túnel abierto, la aplicación ve la base de datos en `localhost:5433`.
- El `compose.yaml` llega al servidor clonando este repositorio. La contraseña va en un `.env` que solo existe en el servidor.

## Piezas descartadas por ahora

| Tecnología | Decisión | Cuándo revisarlo |
|---|---|---|
| Redis | No se usa. PostgreSQL responde de sobra y el límite de peticiones se resuelve dentro de Spring. | Solo si hay varias instancias del backend. |
| MinIO u otro almacenamiento S3 | No se usa hasta G3. Guardar archivos será un puerto de `ingesta`; el primer adaptador es una carpeta en disco. | Al llegar a G3, revisando el estado de MinIO y alternativas como Garage o Hetzner Object Storage. |

## Mensajería: sin broker

No se usa RabbitMQ, Kafka ni ningún otro broker. Con un único proceso y un único propietario, el volumen es de decenas de eventos al día.

Criterio para revisarlo:

- **Cola de trabajos (RabbitMQ):** solo si el procesamiento pesado (PDF, fotos, voz con IA; gates G4 y G5) pasa a un worker independiente.
- **Streaming (Kafka o similar):** no se prevé. Resuelve volúmenes altos, muchos consumidores y reprocesado del histórico, ninguno de los cuales existe en una instancia personal.

Añadir un broker obliga a cada persona que despliegue su instancia a operarlo, respaldarlo y reservarle memoria, así que solo entra con un consumidor real.

## Diferencias con la documentación de análisis

- Frontend en React en lugar de Angular.
- Arquitectura hexagonal completa solo en `ingesta`.
- Keycloak y el resto de piezas del documento técnico pasan a ser propuestas que se deciden al llegar a cada tarea.
- Proyecto publicado con licencia AGPL-3.0 y desplegable por terceros, con un propietario por instancia.

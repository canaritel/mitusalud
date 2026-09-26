# Stack tecnológico

Decisiones del 26/09/2026. Donde este documento difiere de la documentación de análisis, manda este documento.

## Visión general

```
PWA React ──login──► Keycloak (OIDC + PKCE, MFA)
    │
    │ petición + token
    ▼
  Caddy (TLS) ──► Spring Boot (monolito modular) ──► PostgreSQL
```

Solo el proxy publica puertos. PostgreSQL, la administración de Keycloak y Actuator nunca son accesibles desde fuera.

## Backend

| Tecnología | Motivo |
|---|---|
| Java 25 (LTS) | LTS vigente; soportada por Spring Boot 4. |
| Spring Boot 4 | Web, datos, seguridad y observabilidad integrados. |
| Maven | Gestión de dependencias y build habitual en proyectos Java. |
| Monolito modular (`diario`, `ingesta`, `analitica`) | Un único propietario y un servidor no justifican microservicios; los módulos mantienen fronteras claras. |
| Spring Modulith | Verifica las fronteras entre módulos y registra eventos de forma persistente. |
| Arquitectura hexagonal + ArchUnit | El dominio no depende de Spring, JPA ni proveedores externos; ArchUnit lo comprueba en cada build. |
| OpenAPI | Contrato publicable de `/api/v1` y fuente del cliente TypeScript. |

## Datos

| Tecnología | Motivo |
|---|---|
| PostgreSQL | Datos muy relacionados que exigen integridad: claves foráneas compuestas, restricciones y transacciones. |
| Flyway | Migraciones versionadas: cualquier instalación llega al mismo esquema. |
| Outbox en PostgreSQL | Los trabajos pendientes se guardan en la misma transacción que el dato; no se pierden ni se duplican. |

## Identidad

| Tecnología | Motivo |
|---|---|
| Keycloak | La aplicación no almacena contraseñas; MFA y gestión de sesiones incluidos. |
| OAuth2 / OIDC, Authorization Code + PKCE | Estándar para clientes en navegador. El backend actúa como Resource Server y solo valida tokens. |

## Frontend

| Tecnología | Motivo |
|---|---|
| React + TypeScript | Interfaz por componentes con tipado estático. |
| Vite | Desarrollo y build del frontend. |
| SPA sin Next.js | El backend ya es Spring; un servidor Node adicional no aporta nada. |
| React Router | Navegación entre pantallas. |
| TanStack Query | Caché, estados de carga, errores y reintentos contra la API. |
| React Hook Form + Zod | Formularios de registro con validación tipada. |
| Cliente generado desde OpenAPI (`orval` u `openapi-typescript`) | Frontend y backend comparten contrato; un cambio incompatible rompe la compilación. |
| react-oidc-context (`oidc-client-ts`) | Login con Keycloak. Los tokens se mantienen en memoria, nunca en `localStorage`. |
| Tailwind CSS + shadcn/ui | Estilos consistentes, modo claro/oscuro y componentes accesibles. |
| vite-plugin-pwa | Instalable en móvil y escritorio sin tiendas de aplicaciones. |

## Pruebas

| Tecnología | Motivo |
|---|---|
| JUnit 5 | Pruebas del backend. |
| Testcontainers | PostgreSQL y Keycloak reales en las pruebas de integración. |
| Vitest + Testing Library | Pruebas de componentes React. |
| Playwright | Pruebas de extremo a extremo en navegador. |

## Infraestructura

| Tecnología | Motivo |
|---|---|
| Docker + Docker Compose | Cualquiera levanta su instancia con `docker compose up`. |
| Caddy | Proxy con HTTPS y renovación automática de certificados. |
| GitHub Actions | Build, pruebas y escaneo de secretos y dependencias en cada cambio. |
| Restic + Backblaze B2 | Copias cifradas fuera del servidor y restauración demostrada. |

## Mensajería: sin broker

No se usa RabbitMQ, Kafka ni ningún otro broker. Con un único proceso y un único propietario, el volumen es de decenas de eventos al día y el outbox en PostgreSQL cubre la necesidad.

Criterio para revisarlo:

- **Cola de trabajos (RabbitMQ):** solo si el procesamiento pesado (PDF, fotos, voz con IA; gates G4 y G5) pasa a un worker independiente. Spring Modulith puede externalizar entonces los eventos ya registrados sin rehacer el dominio.
- **Streaming (Kafka o similar):** no se prevé. Resuelve volúmenes altos, muchos consumidores y reprocesado del histórico, ninguno de los cuales existe en una instancia personal.

Añadir un broker obliga a cada persona que despliegue su instancia a operarlo, respaldarlo y reservarle memoria, así que solo entra con un consumidor real.

## Diferencias con la documentación de análisis

- Frontend en React en lugar de Angular.
- Java 25 en lugar de Java 21.
- Proyecto publicado con licencia AGPL-3.0 y desplegable por terceros, con un propietario por instancia.

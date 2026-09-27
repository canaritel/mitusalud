# De cero al primer dato

Backlog v3 · 29/08/2026 · 41 tareas: 29 locales y 12 de servidor.

Los cinco documentos de análisis enumerados en el README son la referencia canónica. Este backlog traduce sus decisiones a trabajo ejecutable; no puede modificar su alcance por sí solo.

## Reglas que no se saltan

- S-01 y S-10 gastan dinero y permanecen bloqueadas hasta autorización expresa.
- Las demás tareas no están bloqueadas por gasto, pero conservan sus dependencias internas.
- La rama local solo utiliza datos sintéticos.
- Ningún dato personal real entra antes de completar L-29 y S-12.
- El P0 manual puede construirse antes del proveedor de IA. Voz, fotos, PDF y la pantalla Confirmar esperan sus gates.
- Las tablas de datos no llevan `ownerId` ni FKs compuestas con propietario: cada instalación tiene un solo propietario y `OwnerAccount` es la frontera de autorización. Las menciones a `ownerId` en las tareas se leen conforme a la pieza 3 de [`MODELO_DATOS.md`](MODELO_DATOS.md).
- `ContextPeriod` y `ObservationTag` son conceptos distintos: el primero es un periodo temporal; el segundo etiqueta una observación concreta.

## Rama local

### A. Esqueleto

| ID | Tarea | Depende | Criterio de aceptación |
|---|---|---|---|
| L-01 | Repositorio con licencia y README | — | Explica finalidad, límites, carácter no sanitario, estado, documentación canónica y licencia vigente. |
| L-02 | Esqueleto del monolito modular | L-01 | Módulos `diario`, `ingesta` y `analitica`; solo `diario` empieza con contenido. Una prueba impide importar internos de otro módulo. |
| L-03 | Compose local: aplicación y PostgreSQL | L-02 | `docker compose up` levanta ambos. PostgreSQL escucha en `127.0.0.1:5432`, nunca en `0.0.0.0`. |
| L-04 | Flyway y migración inicial | L-03 | Una base vacía crea `owner_account` y su índice singleton; ejecutar migraciones dos veces es seguro. |
| L-05 | Secretos fuera del repositorio | L-01 | `.env.example` no contiene valores, `.gitignore` cubre el real y el escaneo de secretos queda limpio. |
| L-06 | CI: compilar, probar y escanear | L-02, L-05 | Cada cambio ejecuta build, pruebas y escaneos de secretos y dependencias. **Parcial:** en cada push y pull request, build y pruebas del backend (con PostgreSQL) y compilación del frontend. **Pendiente:** escaneos de secretos y dependencias. |
| L-07 | Logs estructurados sin contenido personal | L-02 | Una prueba falla si valores o texto de observaciones aparecen en logs. **Parcial:** los errores de PostgreSQL ya no escriben la fila rechazada. En el log del backend, con `logServerErrorDetail=false`, lo vigila un test sobre la nota de la energía (se vio fallar sin el ajuste). En el del servidor, con `log_error_verbosity=terse`, se comprobó a mano el 27/09/2026 en el servidor de desarrollo: una nota inventada ya no aparece y el error sigue registrado. Cada instalación lo recibe con `compose.yaml`; la CI no lo aplica (solo datos inventados). **Pendiente:** logs estructurados. |

### B. Identidad

| ID | Tarea | Depende | Criterio de aceptación |
|---|---|---|---|
| L-08 | Keycloak con realm propio | L-03 | Registro público desactivado y comprobado automáticamente. |
| L-09 | Authorization Code + PKCE; aplicación como Resource Server | L-08 | **Revisada (pieza 3), pendiente de implementar.** Sin token, las rutas de datos responden 401. Solo la identidad `iss` + `sub` registrada en `OwnerAccount` accede; cualquier otra identidad autenticada es rechazada en todas las rutas de datos. |
| L-10 | MFA en la cuenta propietaria | L-08 | Segundo factor exigible y códigos de respaldo fuera del sistema. |

### C. Núcleo del diario

| ID | Tarea | Depende | Criterio de aceptación |
|---|---|---|---|
| L-11 | `OwnerAccount`, singleton y readiness guard | L-04, L-09 | Índice único sobre constante. Tras migrar, la aplicación exige exactamente un propietario y remite al ADR de RLS si hay cero o más de uno. |
| L-12 | Prueba: rechazo del segundo propietario | L-11 | La prueba de integración intenta insertar un segundo `owner_account` y debe fallar. |
| L-13 | Pruebas de acceso del propietario único | L-09, L-11 | **Revisada (pieza 3), pendiente de implementar.** Sustituye al perfil con dos propietarios: el propietario accede, otra identidad autenticada es rechazada y el acceso anónimo es rechazado. |
| L-14 | `Observation` y restricciones | L-11 | Forma del valor, obligatoriedad por tipo, energía 1–5, `symptomKind`, `source` y procedencia quedan protegidos. |
| L-15 | Idempotencia del camino directo | L-14 | **Revisada (pieza 4), pendiente de implementar.** Cabecera `Idempotency-Key` obligatoria y tabla `idempotency_record`, con el contrato y las 8 pruebas de la pieza 4 de [`MODELO_DATOS.md`](MODELO_DATOS.md). **Parcial (versión mínima):** creación de agua con clave obligatoria en una columna `UNIQUE`, probada con reintento, datos distintos, fallo al crear y concurrencia real. **Pendiente:** peso y energía, y lo necesario antes de editar o borrar. |
| L-16 | `AuditEvent` en la misma transacción | L-14 | **Revisada (pieza 5), pendiente de implementar.** Cada operación de negocio que modifica datos deja su evento de auditoría en la misma transacción. Las exportaciones y otras acciones de seguridad tienen eventos específicos. Sin contenido; ver reglas y pruebas de la pieza 5 de [`MODELO_DATOS.md`](MODELO_DATOS.md). |
| L-17 | `ObservationRevision` y edición versionada | L-16 | **Revisada (pieza 6), pendiente de implementar.** Editar guarda una foto JSON del estado anterior, en una sola transacción con el cambio y su evento; bloqueo optimista con `version` y 409. Ver reglas y pruebas de la pieza 6 de [`MODELO_DATOS.md`](MODELO_DATOS.md). |
| L-18 | Generador de datos sintéticos | L-17 | Genera entre 12 y 18 meses correlacionados sin datos personales y permite demostrar la interfaz y la restauración. |

### D. Dominio

| ID | Tarea | Depende | Criterio de aceptación |
|---|---|---|---|
| L-19 | Alta: zona horaria, unidades, peso y altura | L-14 | Zona horaria y una medición fechada de altura obligatorias; peso opcional. Peso y altura son observaciones, no campos sobrescritos del perfil. **Revisada (pieza 7):** en la fase actual solo unidades canónicas; la preferencia de unidades alternativas (lb) activa la regla de conservar lo declarado de la pieza 7 de [`MODELO_DATOS.md`](MODELO_DATOS.md). |
| L-20 | Registros directos: agua, peso y energía | L-15 | Se escriben confirmados; energía admite varias mediciones diarias con hora y nota. **Revisada (pieza 7):** en la fase actual, agua en mililitros enteros y peso en kilos con 2 decimales, sin conversiones. **Implementado:** peso, agua y energía con nota opcional (registrar y listar). **Pendiente:** el agua en vasos no se da por terminada hasta conservar cantidad declarada, unidad y tamaño aplicado (`ml_por_unidad`). |
| L-21 | `MedicationPlan` y `SupplementPlan` | L-14 | Tablas separadas; `indicatedBy` opcional en suplementos. Las pautas finalizadas no se borran. |
| L-22 | Tomas mediante `IntakeDetail` | L-21 | Exclusividad y FK compuestas; solo registra tomas reales, nunca la ausencia de una toma. |
| L-23 | `ContextPeriod` | L-14 | `type`, `startedAt`, `endedAt`, `note` y `source`; fin no anterior al inicio, fin nulo significa activo y se permiten periodos simultáneos. |
| L-24 | `ObservationTag` | L-14 | FK compuesta con `Observation` y unicidad `(ownerId, observationId, key, value)`; no se inventa relación directa con `ContextPeriod`. |
| L-25 | Detalles tipados: comida, sueño y actividad | L-14 | Relación 1:1 con la observación, `ownerId` y FK compuesta. Energía de comida bloqueada en la v1. |
| L-26 | Gimnasio: sesión y series | L-14, L-16 | Consulta por propietario y ejercicio la sesión anterior y la muestra al registrar. |

### E. Interfaz

| ID | Tarea | Depende | Criterio de aceptación |
|---|---|---|---|
| L-27 | PWA React instalable | L-09 | Instalable en móvil y escritorio, modos claro y oscuro, sin contenido sensible en `localStorage`. |
| L-28 | Pantalla Hoy, rápida y normal | L-19 a L-27 | Una pantalla colapsada o desplegada, atajos directos y resumen diario. Lee periodos abiertos de L-23 y etiquetas de L-24 como funciones separadas. Sin rachas ni porcentajes. |
| L-29 | Línea temporal, edición y resumen semanal | L-17, L-23, L-24, L-27 | Filtra por `ObservationTag`, superpone `ContextPeriod` por tiempo sin fingir relación directa, pagina de forma estable, muestra revisiones y produce un resumen descriptivo y honesto. |

## Rama servidor

### A. Host y red

| ID | Tarea | Depende | Criterio de aceptación |
|---|---|---|---|
| S-01 | Contratar Hetzner CX33 en NBG1 — BLOQUEADA | Autorización | Proyecto nuevo y aislado, cuenta con 2FA, servidor nunca reutilizado; precio y disponibilidad revalidados antes de contratar. |
| S-02 | Preparar y endurecer el host | S-01 | Usuario sin privilegios, SSH por clave, sin contraseña ni root directo, actualizaciones de seguridad y reloj sincronizado. |
| S-03 | Cloud Firewall de Hetzner | S-01 | Solo 80/443 desde IPv4 e IPv6; SSH únicamente desde el origen autorizado en ambas familias. |
| S-04 | Firewall del sistema y política Docker | S-02, S-03 | Solo el proxy publica puertos; reglas en `DOCKER-USER`, no únicamente `ufw`. |
| S-05 | DNS hacia el servidor | S-01 | Registros A y AAAA resueltos y propagados antes de solicitar certificado. |

### B. Despliegue

| ID | Tarea | Depende | Criterio de aceptación |
|---|---|---|---|
| S-06 | Despliegue reproducible | S-04, L-11 | Aplicación, PostgreSQL y Keycloak se levantan desde cero con configuración versionada y secretos inyectados. Repetir produce el mismo resultado. |
| S-07 | Proxy TLS y renovación automática | S-05, S-06 | HTTPS válido, HSTS, protocolos obsoletos desactivados y alerta previa al vencimiento. |
| S-08 | Escaneo externo | S-07 | Desde otra máquina, en IPv4 e IPv6, solo son públicos los puertos previstos; PostgreSQL, administración de Keycloak y Actuator quedan inaccesibles. |

### C. Copias y puerta de datos reales

| ID | Tarea | Depende | Criterio de aceptación |
|---|---|---|---|
| S-09 | Copia cifrada de lo necesario | S-06 | Incluye PostgreSQL, base y realm de Keycloak, migraciones y configuración. Periodicidad máxima de seis horas y cifrado antes de salir. |
| S-10 | Repositorio Backblaze B2 — BLOQUEADA | Autorización, S-09 | Requiere cuenta y método de pago. Clave independiente; retención de 7 diarias, 4 semanales y 12 mensuales. |
| S-11 | Custodia fría de claves y secretos | S-10 | Dos copias en dominios de fallo distintos; ninguna reside en el servidor ni en la cuenta de Hetzner. |
| S-12 | Restauración integral demostrada | S-11, L-18, L-29 | En entorno aislado y sin secretos vivos, recupera el artefacto inmutable exacto de L-29 —misma etiqueta y migraciones—, restaura base, realm y configuración, lee los datos sintéticos desde la interfaz y repite el escaneo externo de S-08. Registra duración y pasos manuales. |

## Puerta del primer dato real

Se necesitan las dos ramas completas:

- L-29 demuestra que existe un recorrido funcional terminado.
- S-12 demuestra que la misma versión desplegable puede recuperarse tras una pérdida total.

Hasta entonces solo se permiten datos sintéticos.

## Trabajo posterior a sus gates

| Gate | Desbloquea | Condición resumida |
|---|---|---|
| G2 | Captura sin conexión | Clave local y compatibilidad mínima decididas. |
| G3 | Fotos, audios y PDF almacenados | AES-GCM en backend, DEK/KEK/AAD y restauración completa del objeto. |
| G4 | Procesamiento de PDF | Parseador aislado, límites y fallo seguro. |
| G5 | Interpretación IA y voz online | Consentimiento, proveedor/DPA, región y retención, esquema estricto y presupuesto. |

## Estado inicial

- L-01: completada. Repositorio, README, límites y licencia AGPL-3.0 establecidos.
- L-02 a L-29: pendientes.
- Peso, agua y energía con el modelo definitivo (`observation` + detalle por tipo, UUID v7, `observed_at`): registrar y listar. Cumple parcialmente L-07, L-14 y L-20; idempotencia (L-15), auditoría (L-16) y revisiones (L-17) siguen pendientes como condiciones futuras de [`MODELO_DATOS.md`](MODELO_DATOS.md).
- Prototipo local en `frontend/` (React + Vite): lista peso, agua y energía y registra agua. No cumple L-27 ni L-28.
- S-01 y S-10: bloqueadas por gasto.
- Resto de tareas de servidor: pendientes de sus dependencias.

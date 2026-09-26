# Modelo de datos: revisión pieza a pieza

Iniciada el 26/09/2026. Revisa el modelo del diccionario de datos (v6) con los principios de [`STACK.md`](STACK.md): no sobredimensionar, sencillo antes que fácil y comprensible para un junior.

Cada pieza registra qué se decidió, por qué y qué alternativas se descartaron. Donde este documento difiere del diccionario, manda este documento. Lo que no aparece aquí sigue como dice el diccionario hasta que se revise.

Por ahora es **solo documentación**: el código sigue con la tabla `peso` provisional (ver "Esqueleto provisional" en `STACK.md`).

## Estado

| # | Pieza | Estado |
|---|---|---|
| 1 | Organización de las tablas: `observation` común + una tabla de detalle por tipo | **Decidida** |
| 1b | Mapeo Java: herencia `JOINED` o relación uno a uno | Pendiente: se decide al implementar, comparando ambas versiones sobre el peso |
| 2 | Identificadores: UUID v7 | **Decidida** |
| 3 | Sin `owner_id` en los datos; `OwnerAccount` como frontera de autorización | **Decidida**, pendiente de implementar con el login |
| 4 | Idempotencia: cabecera `Idempotency-Key` y tabla `idempotency_record` | **Decidida**, pendiente de implementar |
| 5 | `AuditEvent`: un evento por operación de negocio, sin contenido | **Decidida**, pendiente de implementar |
| 6 | `ObservationRevision` (historial de ediciones) | Pendiente |
| 7 | Valor o rango, unidad original y normalizada | Pendiente |
| 8 | Procedencia (`source`: directo, IA, importado) | Pendiente |
| 9 | `observedAt` (fecha y hora) en lugar de solo fecha | Pendiente |

## Pieza 1: `observation` común + tabla de detalle por tipo

### Decisión

- Tabla `observation` con lo común a todo registro: identificador, `type`, momento del hecho y procedencia.
- Una tabla de detalle por tipo (`peso`, `agua`, `energia`…) con sus valores y sus reglas, con nombres explícitos (`kilos`, `mililitros`, `nivel`).
- Línea temporal, etiquetas, revisiones y auditoría referencian `observation`.

**Sustituye al diccionario**, que guardaba los valores de los tipos simples en columnas genéricas de `observation` (`value`, `unit`, `valueLow`, `valueHigh`) y usaba tablas de detalle solo para los tipos complejos. Ahora todo tipo sigue un único patrón: `observation` + su tabla de detalle.

### Alternativas descartadas

| Opción | Motivo |
|---|---|
| A. `observation` con valores genéricos (diccionario) | Nombres genéricos y reglas condicionales por tipo, del estilo `CHECK (type <> 'energy' OR value BETWEEN 1 AND 5)`. Dos patrones distintos según el tipo sea simple o complejo. |
| B. Una tabla independiente por tipo, sin tabla común | Viable. La línea temporal se resolvería con `UNION ALL`, razonable con pocos tipos. Pero etiquetas, revisiones y auditoría tendrían que referenciar "cualquiera de N tablas". C conserva la claridad de B y una referencia común. |

### Tablas (ejemplo mínimo)

```sql
CREATE TABLE observation (
    id          UUID        PRIMARY KEY,
    type        TEXT        NOT NULL CHECK (type IN ('weight', 'water')),
    observed_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, type)                 -- permite que los detalles referencien (id, type)
);

CREATE TABLE peso (
    observation_id UUID PRIMARY KEY,
    type           TEXT NOT NULL DEFAULT 'weight' CHECK (type = 'weight'),
    kilos          NUMERIC(5,2) NOT NULL CHECK (kilos > 0 AND kilos < 500),
    FOREIGN KEY (observation_id, type) REFERENCES observation (id, type) ON DELETE CASCADE
);
```

El ejemplo se probó en PostgreSQL 17 (con `BIGINT` en lugar de UUID, antes de decidir la pieza 2):

| Caso | Resultado |
|---|---|
| Observación `weight` + detalle en `peso` | Se guarda |
| Detalle de peso colgado de una observación de agua | Rechazado por la FK `(observation_id, type)` |
| Detalle de peso con `type = 'water'` | Rechazado por el `CHECK` |
| Dos detalles de peso para la misma observación | Rechazado por la clave primaria |
| Observación `weight` sin detalle | **Permitido**: límite conocido, ver abajo |

### Qué garantiza cada parte

| Garantía | Responsable |
|---|---|
| `type` obligatorio y dentro de los valores permitidos | PostgreSQL (`NOT NULL` + `CHECK`) |
| Un detalle no puede pertenecer a una observación de otro tipo | PostgreSQL (FK compuesta + `CHECK` de tipo fijo en el detalle) |
| Como mucho un detalle de cada tabla por observación | PostgreSQL (clave primaria del detalle) |
| Reglas de cada valor | PostgreSQL (`CHECK` en cada tabla de detalle) |
| Borrar una observación borra su detalle | PostgreSQL (`ON DELETE CASCADE`) |
| **Toda observación tiene su detalle** | **La aplicación**: crea la observación y su detalle dentro de una misma transacción. Las pruebas verifican que los caminos implementados cumplen esa regla. |
| El `type` de una observación no cambia | La aplicación: una edición ordinaria no puede cambiarlo |

La transacción por sí sola no obliga a insertar ambas filas: si el código guardara solo la observación, se confirmaría igual. Y un test detecta errores en los caminos que ejecuta; no protege la base permanentemente. PostgreSQL podría garantizar la existencia del detalle con triggers diferidos, pero se descarta por ahora: añaden complejidad difícil de entender para un junior.

### Pruebas obligatorias al implementar

1. Camino válido: se crean la observación y su detalle.
2. Fallo al guardar el detalle: se comprueba que **tampoco queda la observación**.

## Pieza 2: identificadores UUID v7

### Decisión

- Identificadores UUID versión 7, generados por la aplicación con Hibernate: `@UuidGenerator(style = UuidGenerator.Style.VERSION_7)`, disponible en Hibernate 7.4.
- Se adopta a partir de `observation`, antes de que el modelo crezca y el cambio sea más caro.
- La cronología se ordena por `observed_at`, con `id` como desempate estable. El UUID no sustituye a la fecha del hecho.

**Motivo:** identificadores independientes de cada instalación y adopción barata ahora. **No** se adopta porque `BIGINT` impida funciones futuras.

### Lo que UUID v7 es y no es

| Propiedad | Realidad |
|---|---|
| Información que revela | Contiene la marca temporal de su generación. No es un identificador secreto. |
| Orden | Facilita ordenar por momento de generación, pero no garantiza el orden global de inserción o confirmación, especialmente entre dispositivos. |
| Unicidad | Las colisiones accidentales son extremadamente improbables. Siguen haciendo falta claves únicas y gestionar duplicados al importar. |
| Modo sin conexión (G2) | Generarlo en el servidor no resuelve la creación de identificadores en el móvil ni su aceptación por la API. Ese contrato se decide en G2. Con `BIGINT` también sería posible, con identificadores temporales o un UUID de sincronización aparte. |

Referencia: RFC 9562, sección 5.7.

### Alternativa descartada

`BIGINT` generado por PostgreSQL: más legible al depurar y más pequeño. Descartado porque cada tabla nueva encarece un cambio posterior y porque los identificadores dependerían de la instalación. Su legibilidad es el coste real de la decisión.

## Pieza 3: sin `owner_id` en los datos; `OwnerAccount` como frontera de autorización

### Contexto

El diccionario pone `owner_id NOT NULL` en todas las tablas, claves foráneas compuestas `(owner_id, id)` y un `OwnerAccount` con una sola fila. Los documentos vinculan esta protección a un segundo propietario, a la compartición o al acceso de terceros: el diccionario exige RLS "antes del segundo propietario, compartición o acceso de terceros" y el threat model (T12) habla de "gate RLS antes de abrir".

La decisión de producto es que **cada instalación pertenece a una sola persona**. Quien quiera usar mitusalud despliega su propia instancia; el multiusuario queda fuera del producto.

### Decisión

- Las tablas de datos **no** llevan `owner_id`: toda la instalación pertenece al propietario.
- `OwnerAccount` se mantiene con una sola fila. No es solo un perfil (zona horaria, preferencias): **determina quién puede acceder a todos los datos de la instalación**.

Quitar `owner_id` simplifica el modelo, pero **no elimina la autorización**: cambia dónde se aplica la protección, de cada tabla y cada consulta a una regla central de acceso.

### Condiciones

1. Solo el propietario configurado puede leer o escribir, en **todas** las rutas de datos. Estar autenticado no basta: cualquier otra identidad válida queda rechazada.
2. `OwnerAccount` admite una sola fila. Mientras no esté configurada, el acceso a los datos está bloqueado.
3. Con OIDC, la identidad del propietario se comprueba con **emisor e identificador (`iss` + `sub`)**, nunca solo con `sub` ni con el correo. El `sub` solo es único dentro de su emisor.
4. Cambiar o recuperar la cuenta propietaria requiere un procedimiento explícito; nunca se reasigna automáticamente a quien consiga iniciar sesión.
5. L-13 se sustituye por tres pruebas de acceso: propietario permitido, otra identidad autenticada rechazada y acceso anónimo rechazado. No hace falta guardar dos propietarios en la base.
6. Se implementa con el login. Hasta entonces el backend solo escucha en `127.0.0.1` y solo hay datos inventados.

### Fuera de esta decisión

- **Compartir datos con terceros** (por ejemplo, un médico). B no lo impide, pero es una política de acceso distinta de "solo accede el propietario". Antes de habilitarlo habrá que definir qué datos puede leer el tercero, cómo se concede y se revoca el permiso y qué pruebas lo verifican.
- **Multiusuario en una misma instalación.** Exigiría añadir `owner_id` a todas las tablas con una migración amplia. Está descartado por decisión de producto.

### Adenda al threat model

El threat model (documento canónico externo) sigue describiendo la protección por `owner_id`. Esta adenda indica cómo queda cada punto afectado; prevalece sobre él.

| Amenaza | Mitigación original | Con esta decisión |
|---|---|---|
| T12. Autorización rota entre propietarios | `ownerId`, FKs compuestas, singleton y gate RLS | El riesgo pasa a ser que **otra identidad autenticada acceda**. Mitigación: comprobar `iss` + `sub` contra `OwnerAccount` en todas las rutas de datos, con las pruebas de la condición 5. |
| T13. Registro público crea cuentas | Registro desactivado, alta administrativa | Sin cambios; gana peso junto a la comprobación de identidad. |
| T21. Consulta olvida `ownerId` | Repositorios por propietario, pruebas cruzadas, RLS | No aplica. Su equivalente es "una ruta de datos sin comprobación de propietario". Mitigación: regla central que exige al propietario en todo `/api/**` y pruebas por ruta. |
| T22. Detalle enlaza agregado de otro propietario | UNIQUE y FKs compuestas `(owner_id, id)` | No aplica: hay un solo propietario. |
| T34. Ciphertext o DEK intercambiados entre registros | AAD con `ownerId + artifactId + objectId` | **Abierta.** Eliminar `owner_id` obliga a revisar en G3 el contexto que usa el cifrado. No se fija todavía la solución. |
| Verificación: el segundo `OwnerAccount` falla | Prueba del singleton | Se mantiene. |

## Pieza 4: idempotencia de las creaciones

### Problema

Una respuesta puede perderse después de que el servidor haya guardado el registro (red móvil, navegador, doble toque). El cliente reintenta y, sin protección, se crea un duplicado. Desactivar el botón no cubre la respuesta perdida, y detectar duplicados por contenido es incorrecto: dos vasos de agua seguidos son dos registros legítimos.

### Decisión

- Toda operación de **creación de observaciones** exige la cabecera HTTP `Idempotency-Key`.
- **Una clave por operación**, no por pantalla: registrar un vaso y registrar otro son dos operaciones. Los reintentos de una misma operación reutilizan su clave y sus datos.
- Unicidad de la clave en la instalación.
- Las claves se guardan en una tabla propia, `idempotency_record`, separada de `observation`. Si vivieran en `observation`, desaparecerían al borrarla y un reintento antiguo podría recrear el registro.
- Se implementa **una sola vez**, como componente reutilizable para todas las creaciones de observaciones.

**Sustituye al diccionario**, que ponía `idempotencyKey` en `Observation` con `UNIQUE(ownerId, key)`. La clave de `Capture` se revisará al llegar a G3.

Contrato basado en el borrador IETF *The Idempotency-Key HTTP Header Field* (draft-ietf-httpapi-idempotency-key-header-07), con una desviación explícita: el reintento devuelve el estado actual y no la respuesta original exacta (ver "Conservación").

### Tabla (propuesta, a verificar al implementar)

```sql
CREATE TABLE idempotency_record (
    key                 TEXT        PRIMARY KEY,
    operation           TEXT        NOT NULL,            -- p. ej. 'crear-peso'
    fingerprint_version SMALLINT    NOT NULL,            -- formato de la huella; el hash no permite leerlo
    request_hash        TEXT,                            -- SHA-256 de la cadena canónica
    observation_id      UUID REFERENCES observation (id) ON DELETE RESTRICT,
    estado              TEXT        NOT NULL CHECK (estado IN ('en_curso', 'completada', 'borrada')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (
        (estado = 'en_curso') OR
        (estado = 'completada' AND request_hash IS NOT NULL AND observation_id IS NOT NULL) OR
        (estado = 'borrada'    AND request_hash IS NULL     AND observation_id IS NULL)
    )
);
```

- `ON DELETE RESTRICT` impide borrar una observación mientras un registro de idempotencia la referencie: obliga a actualizar el registro en la misma transacción.
- El `CHECK` impide en PostgreSQL una operación `completada` sin observación, o una `borrada` que conserve la huella.
- `en_curso` solo existe dentro de la transacción que crea la observación; otras transacciones nunca lo ven confirmado.

### Flujo de creación (una transacción)

1. Reservar la clave: `INSERT … ON CONFLICT (key) DO NOTHING` con `estado = 'en_curso'`, con un límite de espera (`SET LOCAL lock_timeout`).
2. **Solo si la reserva se insertó:** crear la observación y su detalle, y marcar la clave como `completada` con su huella y su `observation_id`.
3. Si hubo conflicto: leer la clave con **una consulta posterior**. Con el aislamiento por defecto de PostgreSQL (`READ COMMITTED`), la fila concurrente puede no ser visible en la misma sentencia del `INSERT`.

### Respuestas

| Caso | Respuesta |
|---|---|
| Falta la cabecera | 400 |
| Clave nueva | 201 con la observación creada |
| Misma clave, misma operación y misma huella | 201 con el **estado actual** de la observación; no crea nada |
| Misma clave con otra huella u otra operación | 422 |
| Operación borrada después de completarse | 410; no recrea nada |
| Se agota la espera por la reserva de **esa** clave (otra petición la tiene en curso) | 409, tras deshacer la transacción. Otros timeouts de PostgreSQL no se interpretan como "operación en curso". |
| La creación falla | Se deshace todo, reserva incluida; un reintento puede completar la operación |

Que `lock_timeout` corte la espera en el índice único se verificará con una prueba real antes de darlo por válido.

### Huella (fingerprint)

- Se calcula sobre los **campos ya validados**, no sobre el JSON recibido: el orden de los campos JSON no influye.
- Cadena canónica escrita a mano, con campos en orden fijo, números normalizados (`72.350` y `72.35` son iguales), fechas en ISO-8601 y opcionales vacíos explícitos. Ejemplo: `v1|crear-peso|fecha=2026-09-26|kilos=72.35`.
- La versión se guarda además en `fingerprint_version`. Mientras existan claves de una versión, se conserva su algoritmo.

### Conservación

- Las claves **no caducan** por ahora: un solo propietario genera un volumen trivial y así no hace falta ninguna tarea de limpieza. Se revisará si el volumen lo pidiera. El borrador pide publicar esta política; queda publicada aquí.
- **No se guarda el cuerpo de la respuesta.** Mientras la observación existe, un reintento con la petición original devuelve su estado actual. Tras borrarla, devuelve 410.
- **Borrado atómico:** en la misma transacción se borran la observación y su detalle, se eliminan `request_hash` y `observation_id` y la operación pasa a `borrada`.
- Tras el borrado se conservan `key`, `operation`, `fingerprint_version`, `estado` y `created_at`: **sin contenido de la observación**, aunque revelan qué operación se hizo y cuándo.
- La huella se elimina al borrar porque, con entradas previsibles (fechas y pesos acotados), se pueden probar combinaciones por fuerza bruta hasta encontrarla: conservarla equivaldría a conservar el dato.

### Pruebas obligatorias al implementar

1. Reintento seguido con la misma clave: misma respuesta, una sola fila.
2. Misma clave con contenido distinto: 422.
3. Sin cabecera: 400.
4. Dos peticiones simultáneas con la misma clave: una sola fila y respuestas coherentes.
5. Fallo durante la creación: no queda registro de idempotencia.
6. Reintento después de borrar: 410 y no se recrea.
7. Reintento después de editar: la petición original devuelve el estado actual; contenido distinto del original sigue dando 422.
8. La primera petición concurrente falla: la segunda completa la operación.

## Pieza 5: auditoría de operaciones (`audit_event`)

### Regla

> Cada operación de negocio que modifica datos deja su evento de auditoría en la misma transacción. Las exportaciones y otras acciones de seguridad tienen eventos específicos.

Las escrituras técnicas internas (por ejemplo, reservar una clave de idempotencia) no son operaciones de negocio y no se auditan por separado.

### Por qué una tabla propia

Se solapa en parte con otras tablas, pero cada una tiene un propósito distinto y no se sustituyen:

| Tabla | Propósito |
|---|---|
| `idempotency_record` | Evitar duplicados en los reintentos |
| `observation_revision` | Conservar el contenido anterior de cada edición (pieza 6) |
| `audit_event` | Seguridad y trazabilidad: qué operación se hizo, sobre qué, cuándo y por quién, incluidas exportaciones, borrados y cambios de la cuenta propietaria |

Usar `idempotency_record` como auditoría las acoplaría: un cambio en la política de claves eliminaría la auditoría sin que se notara.

### Decisión

- Tabla con `id`, `created_at`, `action`, `aggregate_type`, `aggregate_id` y `actor`. Sin `owner_id` (pieza 3).
- **Sin campo `metadata` libre.** Si una acción necesita más información, se añaden columnas concretas.
- **Sin contenido**: ni valores de la observación, ni valores anteriores, textos, prompts, respuestas del proveedor ni credenciales.
- Se escribe con una **llamada explícita desde el service**, en la misma transacción que la operación. Sin Hibernate Envers, sin AOP y sin eventos intermedios.
- Sin caducidad por ahora: volumen trivial.

**Sustituye al diccionario**, que tenía `ownerId` y `metadata jsonb`, y cuya regla (INV-05, L-16) solo cubría la creación de observaciones.

### Reglas

1. **Una creación, un evento.** El evento se escribe solo en el camino que realmente crea, tras reservar la clave de idempotencia. Un reintento no escribe otro. Si falla la auditoría, no se confirma la creación; si falla la creación, no queda evento.
2. **Exportar no modifica datos**, pero se audita expresamente. Solo se registra lo que el servidor puede comprobar (por ejemplo, "exportación generada"). Que el navegador haya recibido el archivo no se puede afirmar desde una transacción.
3. **El actor lo determina el servidor** a partir de la sesión, nunca un campo enviado por el cliente. Si la IA propone y el propietario confirma, el actor de la confirmación es el **propietario**; el proveedor forma parte de la procedencia de la propuesta (pieza 8).
4. **La auditoría sobrevive al borrado.** `aggregate_id` es un UUID sin clave foránea: no se borra en cascada ni impide eliminar la observación. Tras un borrado conserva qué operación se hizo, sobre qué identificador y cuándo, sin contenido; el UUID v7 revela además cuándo se generó.
5. **La aplicación solo inserta y lee** en `audit_event`, y los tests lo comprueban. Es una regla de la aplicación, **no una garantía de inmutabilidad** frente a quien tenga acceso a la base de datos.

### Alcance del "borrado verificable"

El threat model pide trazabilidad y borrado verificable. Un evento de borrado acredita que **la aplicación registró esa operación**; por sí solo no demuestra que hayan desaparecido todas las copias (por ejemplo, las copias de seguridad hasta que caduquen).

### Pruebas obligatorias al implementar

1. Crear una observación deja exactamente un evento; un reintento con la misma clave no añade otro.
2. Si falla la escritura del evento, no queda la observación; si falla la creación, no queda evento.
3. Borrar una observación deja su evento de borrado y los eventos anteriores siguen existiendo.
4. El actor sale de la sesión del servidor, no de la petición.
5. Ningún evento contiene valores de la observación.


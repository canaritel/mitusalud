# Modelo de datos: revisión pieza a pieza

Revisión del 26/09/2026 del modelo del diccionario de datos (v6), con los principios de [`STACK.md`](STACK.md): no sobredimensionar, sencillo antes que fácil y comprensible para un junior. Donde este documento difiere del diccionario, manda este documento. Lo que no aparece aquí sigue como dice el diccionario hasta que se revise.

## Cómo leer este documento

La revisión se detuvo en la pieza 8 al detectar que se estaba **sobrediseñando**: el código seguía siendo pequeño, pero crecía el compromiso de diseño para fases que aún no existen. Por eso cada decisión está en uno de estos dos grupos:

- **Se implementa en el próximo paso:** lo necesario para el recorrido pequeño del peso.
- **Condición futura, revisable:** el problema y sus límites están identificados y deben respetarse, pero el mecanismo descrito es una propuesta. Se revisará cuando aparezca la función que lo necesite, y solo entrará si simplifica ese código. Los requisitos del backlog asociados siguen vigentes y no se dan por cumplidos antes.

## Implementado

Peso (V2), agua (V3) y energía (V4) siguen estas decisiones.

| # | Decisión |
|---|---|
| 1 | Tablas: `observation` común + una tabla de detalle por tipo, con FK compuesta `(observation_id, type)` |
| 1b | Mapeo Java: **una sola implementación**, herencia `JOINED`. Validado: guarda ambas filas y Hibernate no envía `peso.type`. La comprobación de `@Version` sobre cambios solo del detalle se hará con la edición (pieza 6); las tablas son iguales con uno a uno, así que un cambio de mapeo no requeriría migración |
| 2 | Identificadores UUID v7 generados con Hibernate; cronología por `observed_at` con `id` de desempate |
| 7 | Solo unidades canónicas (`kilos` con 2 decimales, `mililitros` enteros, `nivel` entero de 1 a 5), sin redondeos silenciosos |
| 7b | Texto libre (nota de la energía): opcional y en la tabla de detalle de su tipo; hasta 500 unidades UTF-16 en la API y 500 caracteres en PostgreSQL; en blanco se guarda como `NULL`; el carácter nulo y las mitades sueltas de emoji se rechazan con 400, sin cambios silenciosos |
| 9 | `observed_at TIMESTAMPTZ` en lugar de solo fecha; la API exige zona horaria y devuelve UTC |

## Condiciones futuras, revisables

| # | Condición | Cuándo se revisa |
|---|---|---|
| 1b | Lógica común a todos los tipos (editar con revisión, borrar con auditoría, idempotencia) en un solo sitio. Hasta entonces, clases concretas por tipo y sin genéricos: se repite estructura, no lógica | Con la primera lógica que necesiten todos los tipos |
| 3 | Sin `owner_id` en los datos; `OwnerAccount` como frontera de autorización (`iss` + `sub`), con pruebas de acceso | Con el login |
| 4 | Idempotencia de las creaciones (`Idempotency-Key`) | Con el primer cliente real que pueda perder una respuesta |
| 5 | Auditoría de operaciones, sin contenido | Con la primera operación que deba auditarse (borrar, exportar…) |
| 6 | Historial de ediciones: foto del estado anterior | Con la edición |
| 7 | Conservar lo declarado cuando haya conversión (libras, vasos) | Con la primera unidad alternativa |
| 7b | Nota en otros tipos: decidir si pasa a `observation` | Cuando un segundo tipo necesite nota |
| 8 | Procedencia (`source`) | **Antes del primer camino de creación no directo** (importación o IA, lo que llegue antes) |

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

> **Condición futura, revisable.** El problema y sus límites se mantienen; el mecanismo y las pruebas descritas son una propuesta que se revisará al implementarse (ver "Cómo leer este documento").

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

> **Condición futura, revisable.** El problema y sus límites se mantienen; el mecanismo y las pruebas descritas son una propuesta que se revisará al implementarse (ver "Cómo leer este documento").

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

> **Condición futura, revisable.** El problema y sus límites se mantienen; el mecanismo y las pruebas descritas son una propuesta que se revisará al implementarse (ver "Cómo leer este documento").

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

## Pieza 6: historial de ediciones (`observation_revision`)

> **Condición futura, revisable.** El problema y sus límites se mantienen; el mecanismo y las pruebas descritas son una propuesta que se revisará al implementarse (ver "Cómo leer este documento").

### Propósito

En un historial de salud a largo plazo, corregir no debe borrar lo anterior. La auditoría (pieza 5) registra que hubo una edición, sin contenido; la revisión conserva **qué contenido había antes**.

Se decide ahora y se implementa cuando exista la edición (L-17).

### Decisión

Cada edición guarda una **foto del estado anterior** (observación + detalle) en JSON. El estado actual vive en las tablas normales. Todas las versiones de una observación son sus revisiones en orden más el estado actual.

| Opción | Motivo del descarte |
|---|---|
| `before` + `after` en cada revisión (diccionario) | Duplica: el `after` de una revisión es el `before` de la siguiente o el estado actual. |
| Foto de todas las versiones, incluida la creación | Duplica el estado actual. |
| Sin historial | Contradice la trazabilidad del proyecto. |
| Tablas de revisión por tipo (`peso_revision`…) | Multiplica tablas y clases; el historial solo se consulta. |
| Hibernate Envers | Mecanismo automático poco visible para un junior (mismo criterio que la pieza 5). |

### Qué representa cada fila

- La revisión *N* **conserva la versión *N***, sustituida al realizar la edición que produjo la versión *N+1*. Su fecha y su motivo describen esa edición.
- `UNIQUE (observation_id, revision_number)` impide numeraciones duplicadas.
- `snapshot_version` es otra cosa: identifica el **formato del JSON**. Cambiarlo obliga a seguir sabiendo leer los formatos anteriores; el número por sí solo no resuelve esa compatibilidad.
- El JSON se construye con **records explícitos** (por ejemplo, una foto de peso versión 1), **nunca serializando la entidad JPA**: así un cambio en la entidad no altera en silencio el formato guardado.
- Motivo opcional: texto libre escrito por el propietario.

### Reglas

1. **La edición completa es una sola transacción:** guardar la foto anterior, modificar observación y detalle, aumentar la versión y escribir el evento `EDITAR` (pieza 5). Si falla cualquier paso, se deshace todo, incluida la revisión.
2. **El `type` no se edita** (pieza 1).
3. **Borrar una observación borra sus revisiones** en cascada, motivos incluidos: contienen datos de salud y borrar es borrar el contenido (pieza 4). Solo sobrevive la auditoría, sin contenido.

### Ediciones simultáneas (bloqueo optimista)

- `observation` tiene una columna `version` con `@Version` de JPA.
- El cliente envía la versión que vio en un campo `version` del JSON (visible y fácil de probar en Swagger). La alternativa estándar HTTP, `If-Match` con ETag y respuesta 412, queda descartada por ahora; cambiarla no afecta a las tablas.
- **La versión recibida se compara, no se copia sobre la entidad:** el servidor lee la entidad, compara la versión recibida y, si no coincide, responde **409**. Si coincide, `@Version` detecta además los cambios concurrentes posteriores a esa lectura. Copiar la versión del cliente sobre la entidad anularía la protección.
- **`@Version` debe cubrir también los cambios del detalle.** Con herencia `JOINED`, observación y detalle son la misma entidad y cambiar solo `kilos` debería aumentar la versión; con dos entidades uno a uno, no ocurre por sí solo y habría que forzarlo (`OPTIMISTIC_FORCE_INCREMENT`). Es un criterio de la decisión 1b y se verificará con una prueba, no se da por hecho.

### Pruebas obligatorias al implementar

1. Una edición guarda la foto anterior con el número correcto y el estado actual refleja el cambio.
2. Rollback completo: si falla cualquier paso de la edición, no quedan revisión, cambio ni evento.
3. Dos ediciones simultáneas que cambian **solo el detalle** (por ejemplo, los kilos): la segunda recibe 409.
4. Una versión desactualizada enviada por el cliente recibe 409.
5. Borrar la observación elimina sus revisiones; la auditoría permanece.
6. Intentar cambiar el `type` se rechaza.

## Pieza 7: valores, unidades y rangos

### Contexto

El diccionario guardaba en cada observación el valor original con su unidad y el valor normalizado, y admitía punto o rango completo. Con la pieza 1, los valores viven en cada tabla de detalle con nombre propio (`kilos`, `mililitros`, `nivel`), así que esta pieza decide cuándo conservar la unidad declarada y qué tipos admiten rangos.

### Fase actual: solo unidades canónicas

- La API recibe **exclusivamente** unidades canónicas: `kilos`, `mililitros`, `nivel`. No admite libras ni vasos.
- Las tablas de detalle tienen solo la columna canónica.
- **La interfaz respeta la misma fase:** muestra y pide kg y ml explícitamente. No puede ofrecer "vasos" o "libras", convertirlos por su cuenta y enviar solo el resultado, porque se perdería lo declarado.
- **Precisión definida por tipo y sin redondeos silenciosos:** un valor con más decimales de los admitidos se rechaza con 400. El peso admite 2 decimales (`@Digits(integer = 3, fraction = 2)` en `PesoEntrada`); el agua, mililitros enteros. Ojo: por defecto Jackson convierte un decimal en entero truncándolo (`250.5` → `250`) sin avisar; se desactiva con `spring.jackson.deserialization.accept-float-as-int=false`. Ambos casos tienen test.

Mientras no existan conversiones, se conserva exactamente el valor canónico aceptado: no hay nada declarado que perder.

### Fase con conversiones: conservar lo declarado

> **Condición futura, revisable.** El problema y sus límites se mantienen; el mecanismo y las pruebas descritas son una propuesta que se revisará al implementarse (ver "Cómo leer este documento").

Se aplica cuando entren unidades alternativas o atajos (libras, vasos; L-19 y L-20). Queda decidida desde ahora:

> Si un valor llega en una unidad distinta de la canónica, se conserva lo declarado junto al valor normalizado.

| Entrada | Qué se guarda |
|---|---|
| `72,35 kg` | `kilos = 72,35`; sin duplicar |
| `160 lb` | `kilos` normalizado + `cantidad_declarada = 160` + `unidad_declarada = 'lb'` |
| `2 vasos` | `mililitros` + `cantidad_declarada = 2` + `unidad_declarada = 'vaso'` + `ml_por_unidad` (tamaño del vaso aplicado ese día) |
| Energía `3` | `nivel = 3`; no hay conversión |

Reglas de esa fase:

- El cliente declara cantidad y unidad; **el servidor calcula el valor normalizado**. El cliente no puede enviar ambos.
- `cantidad_declarada` y `unidad_declarada` van juntas o ninguna (`CHECK`).
- Cuando la conversión depende de una configuración (el tamaño del vaso), se conserva el factor aplicado.
- Cambiar una configuración **no recalcula registros antiguos**. Conservar lo declarado permite revisar una conversión equivocada, no reaplicar las preferencias actuales.
- Si se descartara el original, se aceptaría expresamente la pérdida: que una conversión concreta sea reversible depende de la precisión y el redondeo, no está garantizado en general.
- `Capture` (G3/G5) no sustituye esta procedencia estructurada: su conservación y borrado se deciden aparte.

### Rangos

Ningún registro directo actual (peso, agua, energía) los necesita. Se decidirán por tipo cuando exista una necesidad concreta (por ejemplo, estimaciones de calorías de una comida, bloqueadas en la v1), con la regla del diccionario: ambos límites o ninguno, y el mínimo no mayor que el máximo.

### Sustituye al diccionario

Las columnas genéricas `originalValue`, `originalUnit`, `value`, `valueLow`, `valueHigh` y `unit` de `Observation` desaparecen: cada tabla de detalle declara sus columnas canónicas y, cuando haya conversión, las declaradas.

### Pruebas obligatorias al implementar

1. Un valor con más decimales de los admitidos se rechaza con 400, sin redondear.
2. En la fase con conversiones: se guardan lo declarado y el normalizado calculado por el servidor, y un cambio posterior de configuración no altera registros existentes.

## Pieza 8: procedencia (`source`)

### Decisión

No se añade la columna `source` mientras solo exista el registro directo: todas las observaciones son directas por construcción, porque no hay otro camino para crearlas. Una columna que siempre vale lo mismo es lo que la pieza 3 retiró con `owner_id`.

**Regla:** la revisión de procedencia se hace **antes del primer camino de creación no directo**, sea importación o IA, lo que llegue antes. En ese momento una migración añade `source NOT NULL` y marca como `direct` las filas anteriores. Ese marcado es seguro, no una suposición, precisamente por la regla. INV-03 e INV-04 del diccionario son el punto de partida de esa revisión.

Lo que se guarde del proveedor de IA pertenece a la procedencia, no a la auditoría: el actor de una confirmación es el propietario (pieza 5).

### Alternativas descartadas

| Opción | Motivo |
|---|---|
| Diccionario completo ahora | Crea columnas y FKs hacia `capture` y `proposal`, que no existen. |
| Columna `source` limitada a `direct` | Un campo constante sin información. |

## Pieza 9: momento del hecho (`observed_at`)

### Decisión

- `observation.observed_at TIMESTAMPTZ NOT NULL` sustituye a la fecha sin hora: puedes pesarte por la mañana y por la noche, y la energía tendrá varias mediciones al día.
- La API recibe `observadoEn` **con zona horaria** (`2026-09-26T08:30:00+02:00` o `…Z`). Sin zona responde 400: "08:30" solo es ambiguo.
- La API devuelve el instante **en UTC** (`2026-09-26T06:30:00Z`). Mostrarlo en hora local es trabajo de la interfaz, hasta que exista la zona horaria del perfil (L-19).
- `TIMESTAMPTZ` guarda el **instante**, no la zona original (por ejemplo, `Europe/Madrid`). Si algún día hiciera falta la zona original de cada registro, sería una columna aparte.
- En Java: `OffsetDateTime` en la entrada y `Instant` en la entidad y la salida.

Implementada en `V2__peso_como_observacion.sql`, con pruebas de entrada `+02:00` → salida UTC y de rechazo sin zona.


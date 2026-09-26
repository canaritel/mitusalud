-- El peso pasa al modelo definitivo: observation (lo común a todo registro) + peso (su detalle).
-- Ver docs/MODELO_DATOS.md, piezas 1, 2 y 9.
--
-- La tabla peso de V1 era un esqueleto con datos inventados: se descarta, no se migra.
DROP TABLE peso;

-- Lo común a cualquier registro del diario (peso, agua, energía...).
CREATE TABLE observation (
    -- UUID v7: lo genera la aplicación (Hibernate) antes de insertar.
    id          UUID        PRIMARY KEY,
    -- Qué tipo de registro es. La lista crece con cada tipo nuevo, en su propia migración.
    type        TEXT        NOT NULL CHECK (type IN ('weight')),
    -- Cuándo ocurrió el hecho. TIMESTAMPTZ guarda el instante exacto, no la zona horaria original.
    observed_at TIMESTAMPTZ NOT NULL,
    -- Cuándo se registró en la aplicación.
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Permite que cada tabla de detalle referencie (id, type) y no solo id.
    UNIQUE (id, type)
);

-- Detalle de un registro de peso. Su clave es la de su observación.
CREATE TABLE peso (
    observation_id UUID          PRIMARY KEY,
    -- Tipo fijo: junto con la FK de abajo, impide colgar un peso de una observación de otro tipo.
    -- La aplicación no lo envía; lo rellena el DEFAULT.
    type           TEXT          NOT NULL DEFAULT 'weight' CHECK (type = 'weight'),
    kilos          NUMERIC(5, 2) NOT NULL CHECK (kilos > 0 AND kilos < 500),
    FOREIGN KEY (observation_id, type) REFERENCES observation (id, type) ON DELETE CASCADE
);

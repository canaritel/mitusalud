-- Primera tabla del módulo diario: registros de peso.
-- Flyway ejecuta este script una sola vez y anota que ya se aplicó.
-- Nunca se modifica un script ya aplicado: los cambios van en un V2, V3...

CREATE TABLE peso (
    id        BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Día al que corresponde el peso. Se permiten varios registros el mismo día.
    fecha     DATE          NOT NULL,
    -- NUMERIC guarda los decimales exactos (72.35), sin errores de redondeo.
    -- El CHECK hace que la propia base de datos rechace valores absurdos.
    kilos     NUMERIC(5, 2) NOT NULL CHECK (kilos > 0 AND kilos < 500),
    -- Momento en que se guardó el registro, con zona horaria.
    creado_en TIMESTAMPTZ   NOT NULL DEFAULT now()
);

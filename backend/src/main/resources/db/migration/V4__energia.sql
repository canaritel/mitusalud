-- Tercer tipo de registro: la energía. Mismo patrón que V3 (agua); V1 a V3 no se tocan.
-- Ver docs/MODELO_DATOS.md, piezas 1 y 7.

-- 1. observation admite ahora también 'energy'.
ALTER TABLE observation DROP CONSTRAINT observation_type_check;
ALTER TABLE observation ADD CONSTRAINT observation_type_check CHECK (type IN ('weight', 'water', 'energy'));

-- 2. Detalle de un registro de energía.
CREATE TABLE energia (
    observation_id UUID    PRIMARY KEY,
    -- Tipo fijo: junto con la FK de abajo, impide colgar una energía de una observación de otro tipo.
    type           TEXT    NOT NULL DEFAULT 'energy' CHECK (type = 'energy'),
    -- Cómo te sientes de energía: 1 = muy baja, 5 = muy alta.
    nivel          INTEGER NOT NULL CHECK (nivel BETWEEN 1 AND 5),
    -- Nota opcional. char_length cuenta caracteres; la API ya limita a 500 unidades UTF-16,
    -- que nunca son menos que los caracteres, así que este CHECK no rechaza nada que la API acepte.
    nota           TEXT    CHECK (char_length(nota) <= 500),
    FOREIGN KEY (observation_id, type) REFERENCES observation (id, type) ON DELETE CASCADE
);

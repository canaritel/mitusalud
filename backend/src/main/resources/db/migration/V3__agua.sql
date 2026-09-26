-- Segundo tipo de registro: el agua. V1 y V2 no se tocan: una migración aplicada nunca se modifica.
-- Ver docs/MODELO_DATOS.md, piezas 1 y 7.

-- 1. observation admite ahora también 'water'.
--    Un CHECK no se puede editar: se borra y se vuelve a crear con la lista ampliada.
--    El nombre observation_type_check lo puso PostgreSQL al crear la tabla en V2.
ALTER TABLE observation DROP CONSTRAINT observation_type_check;
ALTER TABLE observation ADD CONSTRAINT observation_type_check CHECK (type IN ('weight', 'water'));

-- 2. Detalle de un registro de agua, con el mismo patrón que peso.
CREATE TABLE agua (
    observation_id UUID    PRIMARY KEY,
    -- Tipo fijo: junto con la FK de abajo, impide colgar un agua de una observación de otro tipo.
    type           TEXT    NOT NULL DEFAULT 'water' CHECK (type = 'water'),
    -- Mililitros enteros, por registro (no por día). 5000 cubre una botella grande
    -- y rechaza errores como 50000.
    mililitros     INTEGER NOT NULL CHECK (mililitros > 0 AND mililitros <= 5000),
    FOREIGN KEY (observation_id, type) REFERENCES observation (id, type) ON DELETE CASCADE
);

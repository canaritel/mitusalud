-- Clave de idempotencia de la operación que creó cada observación (docs/MODELO_DATOS.md, pieza 4,
-- versión mínima). La envía el cliente en la cabecera Idempotency-Key; un reintento repite la misma.

-- Admite NULL: los registros anteriores no tienen clave, y peso y energía aún no la exigen.
ALTER TABLE observation ADD COLUMN idempotency_key UUID;

-- UNIQUE: nunca dos observaciones con la misma clave, tampoco con peticiones simultáneas.
-- El nombre lo usa AguaService para distinguir este choque de cualquier otro error.
ALTER TABLE observation ADD CONSTRAINT observation_idempotency_key_key UNIQUE (idempotency_key);

# mitusalud

[![CI](https://github.com/canaritel/mitusalud/actions/workflows/ci.yml/badge.svg)](https://github.com/canaritel/mitusalud/actions/workflows/ci.yml)

Nombre provisional del producto. No es una marca definitiva.

Aplicación web personal para registrar y relacionar alimentación, sueño, actividad, gimnasio, medicación, suplementos, analíticas y contexto vital. Su finalidad es conservar un historial longitudinal útil y trazable para el propio usuario.

## Despliegue

- El código se publica en un repositorio público de GitHub para que cualquiera pueda desplegar su propia instancia en un VPS o servidor similar.
- Cada instancia tiene un único propietario: no es un servicio compartido y cada persona guarda sus datos en su propio servidor.

## Límites

- No es un producto sanitario.
- No diagnostica, prescribe ni sustituye a profesionales sanitarios.
- No convierte estimaciones de IA en hechos sin confirmación humana.
- No usa datos personales reales en desarrollo, pruebas, demostraciones ni repositorios.
- El repositorio es público: no contiene datos personales, secretos ni configuración de una instancia concreta.

## Estado

- Análisis funcional, técnico, de datos y amenazas: cerrado.
- Backlog ejecutable: v3, 41 tareas.
- Código de producto: backend (Spring Boot + PostgreSQL) que registra y lista pesos, agua y energía con el modelo definitivo (`observation` + detalle), una pantalla local para consultarlos y registrar agua (prototipo en `frontend/`) y acceso del propietario con passkeys en el backend. Ver [`docs/MODELO_DATOS.md`](docs/MODELO_DATOS.md).
- Entorno de desarrollo: PostgreSQL en un servidor Linux local, solo con datos inventados.
- Infraestructura contratada: ninguna.
- Primer dato real: prohibido hasta completar L-29 y S-12.

## Documentación canónica

La documentación de análisis aprobada se mantiene fuera de este repositorio:

- `Auditoria_apps_salud_IA_open_source_2026.docx`
- `Documento_tecnico_diario_personal_IA_2026.docx` — v0.5
- `Diccionario_de_datos_diario_personal_v6.docx`
- `Wireframes_diario_personal_v9.docx`
- `Threat_model_diario_personal_2026.docx`

Si este repositorio contradice esos documentos, mandan los documentos canónicos, salvo en las decisiones recogidas en [`docs/STACK.md`](docs/STACK.md) (tecnología) y [`docs/MODELO_DATOS.md`](docs/MODELO_DATOS.md) (modelo de datos, incluida su adenda al threat model), que son posteriores.

## Ejecución

El stack está descrito en [`docs/STACK.md`](docs/STACK.md) y las decisiones del modelo de datos en [`docs/MODELO_DATOS.md`](docs/MODELO_DATOS.md). La [guía para juniors](docs/guia/index.html) explica cada pieza del proyecto y por qué está así. El trabajo se gobierna mediante [`docs/BACKLOG_V3.md`](docs/BACKLOG_V3.md). Las tareas locales pueden avanzar sin contratar servicios. S-01 y S-10 requieren autorización expresa de gasto.

## Licencia

Copyright © 2026 Antonio González Bonilla.

Publicado bajo la [GNU Affero General Public License v3.0](LICENSE). Quien lo modifique y lo ofrezca como servicio en red debe publicar también su código modificado.

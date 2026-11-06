# DonaTrack - API Gateway

Punto de entrada unico al sistema (Entrega 6). Recibe cada pedido y lo reenvia al modulo que
corresponde. Es un Spring Cloud Gateway (WebFlux): no tiene logica de negocio, solo rutas.

## Rutas

Cada modulo se publica bajo su propio prefijo, y el gateway lo saca antes de reenviar:

| Prefijo | Modulo | Variable de entorno |
|---|---|---|
| `/api/donaciones/**` | Donaciones | `DONACIONES_URL` |
| `/api/donadores/**` | Donadores y Entidades | `DONADORES_URL` |
| `/api/incentivos/**` | Incentivos | `INCENTIVOS_URL` |
| `/api/logistica/**` | Logistica | `LOGISTICA_URL` |
| `/api/consultas/**` | Servicio de Consultas | `CONSULTAS_URL` |

Ejemplos:

- `GET  /api/logistica/depositos` llega a Logistica como `GET /depositos`.
- `POST /api/donaciones/donaciones` llega a Donaciones como `POST /donaciones`.
- `POST /api/logistica/donaciones` llega a Logistica como `POST /donaciones`.

## Otros endpoints

- `GET /` lista los prefijos y a donde apunta cada uno.
- `GET /actuator/health` para el UptimeRobot.

## Traza

El gateway respeta el `X-Trace-Id` del pedido (1 a 64 caracteres entre letras, numeros, `.`, `-`
y `_`) o genera uno, lo reenvia al modulo y lo devuelve en la respuesta. Asi un mismo
identificador aparece en los logs del gateway y en los de cada modulo.

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

El prefijo hace falta porque hay rutas repetidas entre modulos: `POST /donaciones` existe en
Donaciones y en Logistica, y `DELETE /admin/db` en Donaciones e Incentivos. Con el prefijo,
nunca hay dudas de a quien le toca cada pedido.

Todo lo demas (metodo, cuerpo, parametros y encabezados) se reenvia sin cambios. Un pedido
sin prefijo, o con uno desconocido, responde 404.

## Otros endpoints

- `GET /` lista los prefijos y a donde apunta cada uno.
- `GET /actuator/health` para el UptimeRobot.

## Traza

El gateway respeta el `X-Trace-Id` del pedido (1 a 64 caracteres entre letras, numeros, `.`, `-`
y `_`) o genera uno, lo reenvia al modulo y lo devuelve en la respuesta. Asi un mismo
identificador aparece en los logs del gateway y en los de cada modulo.

## Tiempos de espera

Los modulos estan en el plan gratis de Render y se duermen. El primer pedido a un modulo
dormido puede tardar mas de 2 minutos, por eso el gateway espera hasta 3 minutos antes de
rendirse (`httpclient.response-timeout`). El gateway tambien se duerme: hay que sumarlo al
UptimeRobot (`/actuator/health`).

## Configuracion

Las URLs de los modulos se toman de variables de entorno y son obligatorias: sin
`DONACIONES_URL`, `DONADORES_URL`, `INCENTIVOS_URL` y `LOGISTICA_URL` el gateway no arranca.
`CONSULTAS_URL` apunta por defecto a `http://localhost:8090`, asi que en Render hay que
definirla con la URL real del Servicio de Consultas.

El puerto lo toma de `PORT` (Render lo define solo).

## Ejecutar y probar

```bash
./mvnw test
```

Para correrlo en local hay que definir antes las cuatro variables de los modulos.

Los tests levantan un servidor falso por modulo y verifican el reenvio de ruta, metodo,
cuerpo y parametros, las rutas repetidas, la traza y los 404.

## Despliegue en Render

Web Service con runtime Docker (el `Dockerfile` ya esta). Variables de entorno:
`DONACIONES_URL`, `DONADORES_URL`, `INCENTIVOS_URL`, `LOGISTICA_URL`, `CONSULTAS_URL`.

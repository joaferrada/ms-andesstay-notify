# ms-andesstay-notify

Consumidor RabbitMQ de AndesStay (puerto **8083**, sin base de datos). Procesa los comandos asincronos de email, tickets de housekeeping y vouchers/boletas con **ACK manual**, **idempotencia**, **reintentos controlados** y **DLQ**. Los "envios" se simulan con logs.

## Ejecutar

```powershell
docker compose -f ..\infra\mq\compose.yml up -d
.\mvnw.cmd spring-boot:run
# cluster: $env:RABBITMQ_ADDRESSES="localhost:5672,localhost:5673"
```

| Variable | Default | Uso |
|---|---|---|
| `RABBITMQ_ADDRESSES` | `localhost:5672` | nodos del broker |
| `NOTIFY_PREFETCH` | `5` | mensajes sin ACK por consumidor |
| `NOTIFY_MAX_ATTEMPTS` | `3` | intentos totales antes de la DLQ |
| `NOTIFY_RETRY_BACKOFF` | `1s` | espera base antes de republicar (x intento) |
| `NOTIFY_DLQ_LISTENER` | `false` | arranca el listener de DLQ (consume y loguea `x-death`) |
| `NOTIFY_SIMULATION_ENABLED` | `true` | habilita la simulacion de fallos |

Salud: `GET http://localhost:8083/actuator/health` (incluye el estado de RabbitMQ).

## Estructura

```
config/        MessagingProperties, NotifyProperties, RabbitTopologyConfig (misma topologia que reservations)
messaging/     EnvelopeParser, AckDecisionPolicy, ManualAckMessageProcessor, ProcessedEventRegistry, FailureSimulator
email/         EmailCommandListener      -> q.cmd.email
housekeeping/  HousekeepingTicketListener -> q.cmd.housekeeping
voucher/       VoucherCommandListener    -> q.cmd.voucher
deadletter/    DeadLetterListener        -> q.cmd.*.dlq (autoStartup configurable)
```

## Estrategia ACK / NACK

| Caso | Accion | Log |
|---|---|---|
| Exito | `basicAck` | `[ACK]` |
| `eventId` ya procesado | `basicAck` sin reprocesar | `[ACK-DUPLICADO]` |
| JSON invalido / campo obligatorio faltante | `basicNack(requeue=false)` -> DLX -> DLQ | `[NACK->DLQ]` con eventId, cola, motivo |
| Fallo transitorio, intento < `max-attempts` | republica copia con `x-retry-count+1` y `basicAck` del original | `[RETRY]` |
| Fallo transitorio en el ultimo intento | `basicNack(requeue=false)` -> DLQ | `[NACK->DLQ]` |
| Excepcion inesperada | igual que no recuperable | `[NACK->DLQ]` |

Nunca se usa `requeue=true`, asi que no hay loops infinitos. Ademas `default-requeue-rejected: false`.

Campos obligatorios del sobre: `type`, `eventId` (UUID), `timestamp`, `source`, `payload` (objeto). Por dominio:
- email: `payload.reservationId`, `payload.clienteUsername`
- housekeeping: `payload.reservationId`, `payload.alojamientoNombre`
- voucher: `payload.reservationId`, `payload.clienteUsername`

## Simular fallos (demo)

Se controlan desde el payload. Lo mas facil es publicarlos con ms-andesstay-rabbitadmin (`POST /api/admin/messages`, con `ANDESSTAY_SECURITY_ENABLED=false` en local):

```bash
# 1) OK -> [EMAIL] >>> Enviado ... y [ACK]
curl -X POST localhost:8084/api/admin/messages -H "Content-Type: application/json" -d '{
  "exchange":"cmd.direct","routingKey":"email.send","type":"EMAIL_RESERVA_CREADA",
  "payload":{"reservationId":1,"clienteUsername":"ana@andesstay.cl","alojamientoNombre":"Cabana Volcan"}}'

# 2) Duplicado: repetir el mismo eventId dos veces -> [ACK-DUPLICADO]
#    agregar "eventId":"11111111-2222-3333-4444-555555555555" al body anterior y enviarlo 2 veces

# 3) JSON invalido -> [NACK->DLQ] inmediato -> q.cmd.email.dlq
curl -X POST localhost:8084/api/admin/messages -H "Content-Type: application/json" -d '{
  "exchange":"cmd.direct","routingKey":"email.send","rawBody":"{esto no es json"}'

# 4) Campo obligatorio faltante (sin clienteUsername) -> DLQ
curl -X POST localhost:8084/api/admin/messages -H "Content-Type: application/json" -d '{
  "exchange":"cmd.direct","routingKey":"email.send","payload":{"reservationId":1}}'

# 5) Fallo transitorio que se recupera: falla 2 veces, el intento 3 funciona
curl -X POST localhost:8084/api/admin/messages -H "Content-Type: application/json" -d '{
  "exchange":"cmd.topic","routingKey":"housekeeping.ticket.preparacion","type":"HOUSEKEEPING_PREPARACION",
  "payload":{"reservationId":7,"alojamientoNombre":"Domo Andino","simulateFailure":"TRANSIENT","failTimes":2}}'

# 6) Fallo transitorio permanente: 3 intentos y luego DLQ
curl -X POST localhost:8084/api/admin/messages -H "Content-Type: application/json" -d '{
  "exchange":"cmd.direct","routingKey":"voucher.gen","type":"VOUCHER_GENERAR",
  "payload":{"reservationId":9,"clienteUsername":"ana@andesstay.cl","simulateFailure":"TRANSIENT"}}'

# 7) Excepcion inesperada -> DLQ sin reintentos
#    payload con "simulateFailure":"FATAL"
```

Despues revisar `http://localhost:15672` > Queues: los mensajes rechazados quedan en `q.cmd.*.dlq` (el listener de DLQ esta apagado por defecto). En *Get messages* se ve el header `x-death` (reason `rejected`, cola de origen) y `x-retry-count`.

Para procesar las DLQ: `NOTIFY_DLQ_LISTENER=true`, que loguea `[DLQ] x-death reason=... queue=...` y hace ACK.

## Tests

```powershell
.\mvnw.cmd clean package
```

`AckDecisionPolicyTest`, `EnvelopeParserTest` y `ManualAckMessageProcessorTest` (con `Channel` mock: ack, duplicado, nack, retry con header, ultimo intento a DLQ). Ninguno necesita broker.

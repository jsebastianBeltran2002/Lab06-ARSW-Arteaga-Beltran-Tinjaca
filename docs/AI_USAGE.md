# AI Usage Declaration

Declarar el uso de IA no reduce la nota. Debo poder explicar y validar cada decisión entregada.

Usamos Claude Code como apoyo puntual durante el desarrollo, no para diseñar la arquitectura ni tomar las decisiones del dominio. Algunas cosas concretas para las que lo usamos:

- Revisar ortografía y redacción de la documentación (README, ADR, contrato de la API).
- Resolver algunos errores de compilación que fueron apareciendo durante el desarrollo.
- Ayudar a diagnosticar y corregir fallos puntuales de tests.
- Dar una segunda opinión sobre pequeños detalles de implementación ya definidos por el enunciado del laboratorio.

Todo el código y texto sugerido fue revisado, probado (`mvn test`) y validado manualmente antes de aceptarlo.

## Lab 05 — Interactive Board

| Campo | Detalle |
|---|---|
| Herramienta | Claude Code (Anthropic), modelo Opus 5 |
| Actividad | (1) Backend: añadir la invariante de agregado en `Board` que valida que todo `CONNECTOR` referencie `sourceId`/`targetId` existentes en el propio `elements` al momento de construir/guardar el Board, más las pruebas de dominio y de contrato REST correspondientes. (2) Cliente: implementar desde cero los cuatro módulos ES Modules pedidos por el enunciado (`js/api/board-api-client.js`, `js/state/board-state.js`, `js/ui/board-view.js`, `js/app.js`), el `index.html`/`css/styles.css` de la interfaz SVG, y actualizar `docs/api-contract.md`, la vista de aplicación ArchiMate, el diagrama de clases/módulos y `docs/ADR-002-client-boundaries.md`. |
| Propósito | Completar el alcance funcional del Lab 5 (crear/cargar/agregar/mover/conectar/eliminar/guardar un Board vía REST) manteniendo el backend delgado y separando acceso HTTP, estado y vista en el cliente, según la arquitectura objetivo del enunciado. |
| Resultado | `mvn test` pasa (39 pruebas, incluyendo las nuevas de `CONNECTOR`); prueba manual de extremo a extremo con `curl` (crear → `PUT` con `RECTANGLE`+`TEXT`+`CONNECTOR` → `GET`) confirma que el estado se guarda y recarga correctamente; los cuatro módulos JS pasan `node --check` y las operaciones puras de `board-state.js` (agregar/mover/eliminar con cascada de conectores/flujo de conexión) se verificaron con aserciones en Node. |
| Validación | No se contó con navegador disponible en esta sesión para validar visualmente arrastre/click en el SVG (`board-view.js`/`app.js`); se dejó explícito este límite y se recomienda al equipo abrir `http://localhost:8080` y recorrer manualmente el flujo (crear, agregar rectángulo/texto, conectar, mover, guardar, recargar) antes de dar por cerrado el laboratorio. |
| Modificaciones del equipo | El equipo debe revisar y, si lo considera necesario, ajustar: los tamaños por defecto de los elementos creados (120×80 para `RECTANGLE`, 140×30 para `TEXT`), el uso de `window.prompt` para capturar el texto de un `TEXT`, y los textos/estilos del `status-banner` y `interaction-hint` en `index.html`/`styles.css`. |

## Lab 06 — Real-Time Collaboration

| Campo | Detalle |
|---|---|
| Herramienta | Claude Code (Anthropic) |
| Actividad | Partiendo del repositorio del Lab 05 y usando el ZIP del laboratorio solo como referencia: (1) Backend: configuración STOMP (`WebSocketConfig`), contrato `BoardEvent`/`BoardEventType`/`BoardEventPayload`, `BoardEventApplicationService` con validación por tipo de evento, transiciones puras en `Board` y `BoardWebSocketController` con canal privado de rechazos. (2) Cliente: `board-realtime-client.js`, `board-event.js`, `BoardState.applyEvent` y la barra "live" en `app.js`/`index.html`. (3) Pruebas unitarias, de contrato y de integración STOMP. (4) Borradores de `event-contract.md`, ADR-003, la vista ArchiMate y el diagrama de clases. |
| Propósito | Cumplir el alcance del Lab 06 (propagar en tiempo real los cambios entre navegadores del mismo `boardId`, aislar sesiones, mantener REST para la carga inicial y los snapshots) sin mezclar el protocolo con el dominio ni con la vista. |
| Resultado | `mvn test` pasa con 65 pruebas (20 nuevas para eventos y contrato, 4 del controlador STOMP y 2 de integración con clientes STOMP reales que verifican propagación, aislamiento entre boards y rechazo al emisor). Un script en Node ejecutó los módulos reales del cliente contra el servidor en marcha (3 participantes, 2 boards) y confirmó creación, movimiento, conector, edición, borrado en cascada, aislamiento y coincidencia con el snapshot REST. |
| Validación | Ejecución de `mvn test` y verificación de los límites entre módulos con `grep` (STOMP solo en `js/realtime`, `fetch` solo en `js/api`). No se contó con navegador en la sesión de apoyo: _el equipo debe completar aquí la prueba manual en dos/tres navegadores siguiendo la demostración mínima del enunciado (evidencia en `docs/evidence`)._ |
| Modificaciones del equipo | _Completar por el equipo: ajustes hechos tras la revisión y la prueba manual._ |

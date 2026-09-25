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

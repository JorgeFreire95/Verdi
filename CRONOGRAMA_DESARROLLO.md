# 📅 Cronograma de Desarrollo (Carta Gantt) - Verdi App

Este documento presenta la planificación temporal y el registro de hitos del desarrollo de la aplicación **Verdi**, estructurado en formato de Carta Gantt. Las fechas y actividades están mapeadas directamente a partir del historial real de confirmaciones de Git (desde el primer commit el 13 de junio de 2026).

---

## 📊 Vista General del Cronograma

A continuación se muestra la Carta Gantt en formato visual, representando las fases de desarrollo (Sprints):

```mermaid
gantt
    title Cronograma de Desarrollo - Verdi App
    dateFormat  YYYY-MM-DD
    axisFormat  %m-%d
    
    section Sprint 1: Presentación y Core
    "Estructura HTML5 / CSS Glassmorphism" :done, s1a, 2026-06-13, 2026-06-14
    "Motor de Cálculos de Rentabilidad (JS)" :done, s1b, 2026-06-13, 2026-06-15
    
    section Sprint 2: Lógica Nativa y Datos
    "Integración Capacitor Bridge" :done, s2a, 2026-06-15, 2026-06-20
    "Burbuja Flotante y WindowManager (Overlay UI)" :done, s2b, 2026-06-19, 2026-06-23
    "Persistencia de Parámetros (SharedPreferences)" :done, s2c, 2026-06-20, 2026-06-23
    
    section Sprint 3: Integración Reactiva
    "Servicio de Accesibilidad y OCR Local" :done, s3a, 2026-06-24, 2026-06-30
    "Detección de Apps de Conductor (Uber/DiDi/Cabify)" :done, s3b, 2026-06-24, 2026-06-28
    "Sincronización de Eventos en Segundo Plano" :done, s3c, 2026-06-28, 2026-07-01
    
    section Sprint 4: Estabilización y Depuración
    "Corrección de Registro de Plugin en Capacitor" :done, s4a, 2026-07-01, 2026-07-04
    "Debounce de Resets a 'Ninguna' y Badges de Instalación" :done, s4b, 2026-07-02, 2026-07-04
    
    section Sprint 5: Refactor, UI y Soporte
    "Remoción de Permisos Innecesarios (GPS/Uso)" :done, s5a, 2026-07-20, 2026-07-24
    "Asistente de Permisos Dinámico y Snap de Burbuja" :done, s5b, 2026-07-24, 2026-07-25
    "Pestaña de Ayuda Interactiva y Solución Ajustes Restringidos" :done, s5c, 2026-07-26, 2026-07-26
    "Corrección de Redibujado de Color de Burbuja y Visibilidad de Wizard" :done, s5d, 2026-07-26, 2026-07-30
    
    section Sprint 6: Corrección Definitiva del Color de Burbuja
    "Reemplazo de Broadcasts por Llamada Directa (companion object)" :done, s6a, 2026-08-01, 2026-08-01
    "Fix Race Condition en Actualización de Color (capture-before-post)" :done, s6b, 2026-08-01, 2026-08-01
    "Limpieza de instance en onDestroy y remoción de BroadcastReceiver" :done, s6c, 2026-08-01, 2026-08-01
```

---

## 📝 Desglose de Fases (Sprints)

### Sprint 1: Presentación & Motor de Cálculos (13 Jun - 15 Jun)
* **Objetivo:** Diseñar la interfaz del conductor y validar los cálculos de rentabilidad matemática.
* **Hitos alcanzados:**
  * Maquetación HTML5 y estilo CSS oscuro premium con efectos de desenfoque (`glassmorphism`).
  * Implementación del algoritmo de rentabilidad operacional restando costos estimados de combustible.

### Sprint 2: Lógica Nátiva y Persistencia (15 Jun - 23 Jun)
* **Objetivo:** Establecer la persistencia de datos y el comportamiento de la burbuja sobre otras apps.
* **Hitos alcanzados:**
  * Integración del puente Capacitor para coordinar peticiones nativas en Android.
  * Renderizado del componente visual flotante sobre el WindowManager nativo.
  * Configuración de la persistencia de datos de costos mediante `SharedPreferences` y `LocalStorage`.

### Sprint 3: Integración Reactiva (24 Jun - 01 Jul)
* **Objetivo:** Vincular la lectura automática de pantalla con los cálculos del semáforo.
* **Hitos alcanzados:**
  * Desarrollo del Accessibility Service seguro para extraer tarifas y distancias del árbol visual de Uber, DiDi y Cabify.
  * Detección activa en segundo plano de la aplicación del conductor actualmente en uso.

### Sprint 4: Estabilización y Depuración (01 Jul - 04 Jul)
* **Objetivo:** Resolver problemas de detección y mejorar la consistencia entre transiciones de apps.
* **Hitos alcanzados:**
  * Corrección en el registro de `VerdiPlugin` en `MainActivity`.
  * Habilitación de la cola de eventos en background con `retainUntilConsumed = true`.
  * Implementación de un debounce de 4 segundos para evitar falsos resets en transiciones rápidas de apps.

### Sprint 6: Corrección Definitiva del Color de Burbuja (01 Ago)
* **Objetivo:** Resolver de forma definitiva y robusta la falta de cambio de color en la burbuja flotante nativa, identificando la causa raíz en el sistema de comunicación entre servicios.
* **Hitos alcanzados:**
  * Identificación de la causa raíz: los broadcasts `UPDATE_BUBBLE` no se entregaban de forma confiable en Android 13+ con `RECEIVER_NOT_EXPORTED`, especialmente cuando la app estaba en background.
  * **Reemplazo total del mecanismo de broadcast** por llamada directa mediante patrón `companion object` con referencia `@Volatile instance` — el mismo enfoque ya probado en `VerdiPlugin.onTripCaptured`.
  * Corrección de una **race condition** en la captura del color: `stateColor` se captura antes del `Handler.post` para garantizar consistencia entre emoji y color de fondo.
  * `instance` ahora se asigna al final de `onCreate()` (post-inicialización de vistas) y se limpia a `null` en `onDestroy()`.
  * Eliminación completa del `BroadcastReceiver`, las `IntentFilter` y las importaciones `@SuppressLint` relacionadas de `FloatingBubbleService`.

### Sprint 5: Refactor, UI y Soporte (20 Jul - 30 Jul)
* **Objetivo:** Optimizar la experiencia de usuario (UX), simplificar permisos y proveer soporte integrado.
* **Hitos alcanzados:**
  * Eliminación de permisos intrusivos de Ubicación (GPS) y Estadísticas de Uso.
  * Snap magnético horizontal de la burbuja al borde de la pantalla (`x=0`).
  * Lanzador nativo `appDetails` para permitir que el conductor desbloquee **Ajustes Restringidos (Botón Gris)**.
  * Incorporación de la pestaña interactiva **Ayuda** con FAQs y rutas paso a paso por marca de teléfono (Xiaomi, Samsung, Realme, Motorola).
  * Solución definitiva de redibujado de color de la burbuja (mediante WindowManager layout update y mutación del drawable) y visualización del Wizard.

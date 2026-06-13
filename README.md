# Verdi - Asistente Inteligente de Rentabilidad

Verdi es una aplicación móvil web responsiva diseñada para ayudar a conductores a maximizar sus ganancias calculando y monitoreando la rentabilidad de sus viajes en tiempo real. Cuenta con un diseño premium con tema oscuro (neon/obsidian), animaciones dinámicas, menú de navegación lateral y gestión de perfiles de usuario.

## Características Principales

*   **Autenticación de Usuarios**: Registro e Inicio de sesión integrados con Supabase.
*   **Configuración de Rentabilidad**: Ajuste dinámico de costo de combustible, rendimiento de combustible y ganancia neta deseada.
*   **Semáforo de Rentabilidad**: Panel visual dinámico que indica si la operación del conductor cumple con sus metas de ganancia.
*   **Panel de Estadísticas**: Vista rápida del costo por distancia, rendimiento del auto y la meta configurada.
*   **Configuración de Perfil**:
    *   Selección dinámica del país de procedencia con conversión automática de unidades (km vs. mi) y divisas.
    *   Validación y actualización de número de celular con prefijos de países.
    *   Cambio de contraseña seguro mediante un flujo de confirmación de 3 campos (Contraseña anterior, Nueva contraseña y Confirmación).
*   **Modo Demo de Emergencia**: Si las credenciales de Supabase no están configuradas, la aplicación permite probar toda la interfaz y flujos mediante simulación interactiva local sin fallar.

## Tecnologías Utilizadas

*   **HTML5 & CSS3**: Estructura semántica y diseño visual premium moderno (glassmorphism, gradientes neon).
*   **JavaScript (ES6+)**: Lógica e interactividad del cliente.
*   **Supabase**: Servicio de Backend para la gestión de usuarios (Auth) y persistencia de perfiles en la base de datos (Database).
*   **Vite**: Entorno de desarrollo rápido y empaquetador de módulos.
*   **Lucide Icons**: Set de iconos modernos de alta calidad.

## Estructura del Proyecto

*   [index.html](file:///c:/Users/jorge/OneDrive/Escritorio/Proyectos/Verdi/index.html): Documento HTML principal con todas las vistas (Login, Registro, Dashboard, Configuración y Menú).
*   [style.css](file:///c:/Users/jorge/OneDrive/Escritorio/Proyectos/Verdi/style.css): Estilos del tema visual neon/obsidian, transiciones y adaptabilidad móvil.
*   [main.js](file:///c:/Users/jorge/OneDrive/Escritorio/Proyectos/Verdi/main.js): Controlador de la lógica de negocio, navegación entre vistas, validación de formularios y consumo de la API de Supabase.
*   [supabase.js](file:///c:/Users/jorge/OneDrive/Escritorio/Proyectos/Verdi/supabase.js): Inicialización del cliente Supabase y gestión de variables de entorno.
*   [schema.sql](file:///c:/Users/jorge/OneDrive/Escritorio/Proyectos/Verdi/schema.sql): Esquema SQL para inicializar la tabla `profiles` y sus políticas de seguridad RLS en la base de datos de Supabase.

## Requisitos Previos

*   [Node.js](https://nodejs.org/) (versión 18 o superior recomendada)
*   Una cuenta de [Supabase](https://supabase.com/) (para funcionamiento en la nube)

## Configuración y Ejecución

### 1. Clonar el repositorio y configurar dependencias
Instala los paquetes necesarios del proyecto:
```bash
npm install
```

### 2. Configurar Base de Datos
En tu panel de Supabase, ve al editor SQL y ejecuta el script del archivo [schema.sql](file:///c:/Users/jorge/OneDrive/Escritorio/Proyectos/Verdi/schema.sql) para crear la tabla de perfiles de usuario.

### 3. Configurar Variables de Entorno
Crea un archivo `.env` en la raíz del proyecto basándote en [.env.example](file:///c:/Users/jorge/OneDrive/Escritorio/Proyectos/Verdi/.env.example):
```env
VITE_SUPABASE_URL=tu_supabase_url
VITE_SUPABASE_ANON_KEY=tu_supabase_anon_key
```

*Nota: Si dejas las variables vacías, el sistema entrará en **Modo Demo**, simulando de forma interactiva el registro, inicio de sesión y guardado de perfiles en memoria local.*

### 4. Iniciar Servidor Local de Desarrollo
Levanta el servidor con Vite:
```bash
npm run dev
```
Abre en tu navegador la dirección que se muestre en consola (comúnmente `http://localhost:5173`).
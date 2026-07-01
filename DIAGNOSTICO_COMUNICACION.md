# 🔌 Diagnóstico: Comunicación entre Capas (AccessibilityService ↔ UI)

## Problema Identificado
✅ **La notificación "Verdi: Cabify activo" aparece** (sin Toast = problema solo en UI)  
❌ **Pero el semáforo sigue mostrando "Inactivo"** (evento no llega a JavaScript)

Esto significa que:
- ✅ AccessibilityService está funcionando
- ✅ VerdiPlugin.onAppConnected() está siendo llamado en Kotlin
- ❌ El evento NO está llegando a JavaScript

---

## 🔍 Flujo de Comunicación (Esperado)

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. AccessibilityService detecta cambio de app                   │
│    └─ Detecta: com.cabify.driver                                │
└───────────────────┬─────────────────────────────────────────────┘
                    │
                    ↓
┌─────────────────────────────────────────────────────────────────┐
│ 2. AccessibilityService llama: commitActiveApp("Cabify")        │
│    └─ VerdiPlugin.onAppConnected("Cabify")                      │
└───────────────────┬─────────────────────────────────────────────┘
                    │
                    ↓
┌─────────────────────────────────────────────────────────────────┐
│ 3. VerdiPlugin (Capacitor) emite: notifyListeners(...)          │
│    └─ if (instance != null) {                                   │
│        instance?.notifyListeners("onAppConnected", app)         │
│       }                                                          │
└───────────────────┬─────────────────────────────────────────────┘
                    │
                    ↓
┌─────────────────────────────────────────────────────────────────┐
│ 4. JavaScript recibe evento en listener:                         │
│    └─ VerdiPlugin.addListener('onAppConnected', (data) => {     │
│        updateAppConnectionUI(data.appName);                     │
│       })                                                         │
└───────────────────┬─────────────────────────────────────────────┘
                    │
                    ↓
┌─────────────────────────────────────────────────────────────────┐
│ 5. UI se actualiza: Cabify muestra "Activo"                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔎 Puntos de Fallo Posibles

### **Paso 1-2: ¿commitActiveApp se llama?**
```bash
adb logcat | grep "🔄 commitActiveApp"
```
Busca: `🔄 commitActiveApp: Changing state from 'Ninguna' to 'Cabify'`

Si NO aparece → El AccessibilityService no está detectando Cabify

---

### **Paso 2-3: ¿VerdiPlugin.onAppConnected se llama?**
```bash
adb logcat | grep "📞 Calling VerdiPlugin"
```
Busca: `📞 Calling VerdiPlugin.onAppConnected('Cabify')...`

Si NO aparece → commitActiveApp no se está ejecutando

---

### **Paso 3: ¿VerdiPlugin.instance es NULL?**
```bash
adb logcat | grep "VerdiPlugin.*instance"
```
Busca: 
```
🔌 onAppConnected called with appName=Cabify, instance=true
📢 Event emitted to JavaScript listeners for appName=Cabify
```

O el error:
```
⚠️  instance is NULL - cannot emit event! Plugin may not be loaded.
```

**Si instance es NULL** → El plugin no se ha inicializado. Capacitor no ha llamado a `load()` aún.

---

### **Paso 4: ¿JavaScript recibe el evento?**
```bash
adb logcat | grep "CONSOLE LOG" | grep "onAppConnected"
```

O abre la consola del navegador (F12) y busca:
```
[onAppConnected] 🔔 EVENT RECEIVED from plugin - appName: Cabify
```

Si NO aparece → El evento no está llegando a JavaScript

---

## 📋 Checklist de Diagnóstico Completo

Ejecuta estos comandos en orden cuando veas "Cabify activo":

```bash
# Terminal 1: Captura logs del servicio
adb logcat | grep -E "VERDI|VerdiPlugin|onAppConnected"

# En Terminal 2: Abre Cabify y espera...
# (El log debería mostrar el flujo completo)
```

Busca este patrón exacto:

```
🔄 commitActiveApp: Changing state from 'Ninguna' to 'Cabify'
  📞 Calling VerdiPlugin.onAppConnected('Cabify')...
🔌 onAppConnected called with appName=Cabify, instance=true
📢 Event emitted to JavaScript listeners for appName=Cabify
  ✅ VerdiPlugin.onAppConnected() returned
```

Si ves TODO esto, pero la UI no se actualiza → El problema está en JavaScript.

---

## 🛠️ Soluciones por Escenario

### **Escenario 1: instance es NULL**

**Problema**: VerdiPlugin no está cargado

**Solución**: Agregar delay en setupNativeListeners()

```javascript
// main.js
setTimeout(() => {
  setupNativeListeners();  // Esperar a que Capacitor cargue el plugin
}, 500);
```

---

### **Escenario 2: El evento se emite pero JavaScript no lo recibe**

**Problema**: El listener no está registrado correctamente

**Solución**: Verificar que `VerdiPlugin` está disponible

```javascript
console.log('VerdiPlugin available?', !!window.Verdi);
console.log('VerdiPlugin.addListener exists?', typeof VerdiPlugin.addListener);
```

---

### **Escenario 3: El evento llega pero la UI no se actualiza**

**Problema**: `updateAppConnectionUI()` tiene un error o los IDs del DOM no existen

**Solución**: Verificar en console del navegador

```javascript
// En F12 Console
const el = document.getElementById('status-app-cabify');
console.log('Element exists?', !!el);
console.log('Element classes:', el?.className);
```

---

## 📊 Logs Esperados (Completos)

### En adb logcat (Kotlin/Android):
```
🔄 commitActiveApp: Changing state from 'Ninguna' to 'Cabify'
  💾 Saved to SharedPreferences: lastConnectedApp=Cabify
  📞 Calling VerdiPlugin.onAppConnected('Cabify')...
🔌 onAppConnected called with appName=Cabify, instance=true
📢 Event emitted to JavaScript listeners for appName=Cabify
  ✅ VerdiPlugin.onAppConnected() returned
```

### En F12 Console (JavaScript):
```
[onAppConnected] 🔔 EVENT RECEIVED from plugin - appName: Cabify timestamp: 2026-07-01T...
[onAppConnected] 🚀 FORCING UI UPDATE for: Cabify
[updateAppConnectionUI] UPDATING - activeApp: Cabify
[updateAppConnectionUI] Setting Cabify to ACTIVE
[updateAppConnectionUI] ✅ Cabify element classes: status-app-item active cabify
[updateAppConnected] ✅ Cabify text content: Activo
[updateAppConnectionUI] DONE
[onAppConnected] 🔒 UI LOCKED for 10 seconds: Cabify
[onAppConnected] ✅ VERIFIED - UI shows Cabify as ACTIVE
```

---

## 🚀 Pasos para Reproducir + Diagnosticar

1. **Conecta dispositivo Android o emulador**

2. **Abre DOS terminales**

Terminal 1:
```bash
cd c:\Users\jorge\OneDrive\Escritorio\Proyectos\Verdi\android
.\gradlew.bat installDebug
```

Terminal 2:
```bash
adb logcat | grep -E "VERDI|VerdiPlugin|onAppConnected|CommitActive|EVENT"
```

3. **En el teléfono/emulador**: Abre Cabify Driver con una oferta visible

4. **Analiza los logs** según el checklist anterior

5. **Luego abre F12** en Verdi web y mira la consola

6. **Compara los logs** con los "Esperados" arriba

---

## 📞 Contacto / Reporte

Si después de seguir esto aún no funciona, reporta:

1. ✅ Logs completos de adb logcat (paso 2 arriba)
2. ✅ Logs completos de F12 console (paso 5 arriba)
3. ✅ Captura de pantalla mostrando "Cabify Inactivo" en UI
4. ✅ Captura de pantalla del logcat con "Conectado a Cabify"

Esto especificará exactamente en dónde se pierde la comunicación.

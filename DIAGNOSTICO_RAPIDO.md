# 🎯 Plan Rápido de Diagnóstico - Flujo de Comunicación

## Estado Actual
```
✅ Problema: "Verdi: Cabify activo" aparece en notificación
❌ Pero: El semáforo sigue mostrando "Inactivo" o "Detectando..."
```

---

## 🚀 Ejecución en 5 Pasos

### **Paso 1: Compilar e Instalar**
```bash
cd c:\Users\jorge\OneDrive\Escritorio\Proyectos\Verdi\android
.\gradlew.bat installDebug
```
*(Si dice "No connected devices", conecta un Android por USB o inicia emulador)*

---

### **Paso 2: Abrir Dos Terminales**

**Terminal A** (Kotlin/Android logs):
```bash
adb logcat | findstr "VerdiPlugin onAppConnected commitActive"
```

**Terminal B** (Browser console):
```
Abre el navegador donde está Verdi (Capacitor)
F12 → Console
Busca mensajes que empiezan con [onAppConnected]
```

---

### **Paso 3: Abrir Cabify Driver**
En el teléfono/emulador, abre Cabify Driver con una oferta de viaje visible

---

### **Paso 4: Revisar Logs**

#### En **Terminal A**, deberías ver:
```
🔄 commitActiveApp: Changing state from 'Ninguna' to 'Cabify'
📞 Calling VerdiPlugin.onAppConnected('Cabify')...
🔌 onAppConnected called with appName=Cabify, instance=true
📢 Event emitted to JavaScript listeners for appName=Cabify
✅ VerdiPlugin.onAppConnected() returned
```

#### En **Console (F12)**, deberías ver:
```
[onAppConnected] 🔔 EVENT RECEIVED from plugin - appName: Cabify
[onAppConnected] 🚀 FORCING UI UPDATE for: Cabify
[updateAppConnectionUI] ✅ Cabify element classes: status-app-item active cabify
[onAppConnected] ✅ VERIFIED - UI shows Cabify as ACTIVE
```

---

### **Paso 5: Análisis**

| Si ves en Terminal A | Si ves en Console | Conclusión |
|---|---|---|
| ✅ Todo | ✅ Todo | **✅ FUNCIONA** - El semáforo debería estar "Activo" |
| ✅ Todo | ❌ Nada | **❌ Problema**: JavaScript no recibe eventos |
| ❌ Nada | N/A | **❌ Problema**: AccessibilityService no detecta cambio |
| ✅ `instance=false` | N/A | **❌ Problema**: VerdiPlugin no está cargado |

---

## 📸 Qué Debería Pasar

### Antes (Antes de abrir Cabify):
```
┌─────────────────────┐
│ Uber      Segundo plano
│ DiDi      Segundo plano
│ Cabify    Segundo plano  ← "Detectando..."
└─────────────────────┘
```

### Después (Cuando abres Cabify):
```
┌─────────────────────┐
│ Uber      Segundo plano
│ DiDi      Segundo plano
│ Cabify    ✅ ACTIVO    ← Debe cambiar a "Activo"
└─────────────────────┘
```

---

## 🆘 Si No Funciona

### **A. Si ves `instance=false` o `instance=null`**

Problema: VerdiPlugin no está cargado por Capacitor

**Solución**: En main.js, agrega un delay:

```javascript
// Esperar a que Capacitor cargue el plugin
setTimeout(() => {
  console.log('🔌 Inicializando listeners de Verdi...');
  setupNativeListeners();
  checkAndroidPermissions();
}, 1000);
```

---

### **B. Si ves los logs de Kotlin pero NO los de JavaScript**

Problema: JavaScript no está escuchando

**Solución**: Verificar que el plugin está disponible

```javascript
// En F12 Console
console.log('VerdiPlugin:', window.VerdiPlugin || window.Verdi);
console.log('Has addListener?', !!window.VerdiPlugin?.addListener);
```

---

### **C. Si ves TODO los logs pero la UI no cambia**

Problema: updateAppConnectionUI() tiene error o DOM no existe

**Solución**: Verificar elementos del DOM

```javascript
// En F12 Console
const el = document.getElementById('status-app-cabify');
console.log('Elemento existe?', !!el);
console.log('Classes antes:', el?.className);
el?.classList.add('active');
console.log('Classes después:', el?.className);
```

---

## 📝 Plantilla para Reportar

Si aún no funciona, copia esto y pega los logs:

```
## Logs de Terminal A (adb logcat):
[PEGA AQUÍ]

## Logs de F12 Console:
[PEGA AQUÍ]

## Captura de pantalla de Verdi UI:
[DESCRIBE O ADJUNTA]

## Qué debería pasar vs qué pasó:
Esperado: Cabify debería mostrar "Activo"
Actual: [DESCRIBE]
```

---

## 💡 Tips

- **Limpia el cache**: `adb shell pm clear com.verdi.app`
- **Reinicia Verdi**: Cierra y reabre la aplicación
- **Resetea SharedPreferences**: Desinstala y reinstala
- **Prende verbose logging**: `adb logcat -v long | grep -i verdi`

---

Sigue estos pasos y **reporta qué ves en los logs** 🎯

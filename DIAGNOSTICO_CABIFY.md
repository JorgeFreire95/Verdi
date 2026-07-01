# 🔍 Guía de Diagnóstico: Problema de Detección de Cabify

## 📊 Cambios Implementados en VerdiAccessibilityService.kt

### 1. **Logging Mejorado de Eventos** 
```kotlin
🔋 onAccessibilityEvent [WINDOW_STATE_CHANGED] pkg=com.cabify.driver activeApp=Ninguna
```
- **Ahora se muestra el tipo de evento** (WINDOW_STATE_CHANGED, WINDOWS_CHANGED, WINDOW_CONTENT_CHANGED)
- Esto te ayuda a entender qué eventos está enviando Cabify

### 2. **Diagnóstico del Nodo Raíz**
```kotlin
🚗 Scanning active app package com.cabify.driver
  ├─ Root node packageName: com.cabify.driver
  ├─ Root node className: android.webkit.WebView  ← ⚠️ WebView = Texto difícil de extraer
  └─ Root node childCount: 15
```
- Si ves `className: android.webkit.WebView`, Cabify usa WebView y el OCR es más difícil
- Si `childCount` es bajo o 0, el árbol puede estar protegido

### 3. **Logs de Textos Recolectados**
```kotlin
🚗 Scanning active app package com.cabify.driver
  └─ Collected 8 text nodes from tree
    [Text 0]: Distancia: 5.2 km
    [Text 1]: Tarifa estimada: $8,500
    [Text 2]: Tiempo: 12 min
```
- Si aquí **no aparece nada**, el problema está en `findTextNodes()`
- Si aparecen textos **pero sin formato de precio/distancia**, el problema es con los regex

### 4. **Diagnóstico de Regex**
```kotlin
📋 parseAndEvaluateScreenTexts: Processing 8 text nodes
  💰 Price: text='Tarifa estimada: $8,500' -> $8500
  📍 Distance: text='Distancia: 5.2 km' -> 5.2 km
  ⏱️ Time: text='Tiempo: 12 min' -> 12 min
  ✅ Candidate trip: price=$8500 distance=5.2km time=12
```

---

## 🚀 Pasos para Diagnosticar

### **Paso 1: Recompila y despliega**
```bash
cd android
./gradlew build
./gradlew installDebug
```

### **Paso 2: Abre Cabify Driver**
```bash
# En la terminal, mientras Cabify está abierto
adb logcat | grep "VERDI\|VerdiAcc"
```

### **Paso 3: Analiza los logs**

#### **Caso 1: No aparece `🚗 Scanning active app package`**
→ **Problema**: No se detectó que Cabify está abierto  
→ **Solución**: Verifica el packageName exact de Cabify
```bash
adb shell pm list packages | grep cabify
# Debería mostrar algo como: package:com.cabify.driver
```

Luego actualiza `pkgToAppName()` en VerdiAccessibilityService:
```kotlin
private fun pkgToAppName(pkg: String): String? = when {
    pkg.contains("uber", ignoreCase = true)    -> "Uber"
    pkg.contains("didi", ignoreCase = true)    -> "DiDi"
    pkg.contains("cabify", ignoreCase = true)  -> "Cabify"  // Verifica que detecta
    // ...
}
```

---

#### **Caso 2: Aparece `🚗 Scanning` pero `⚠️ rootInActiveWindow is NULL`**
→ **Problema**: No se puede acceder al árbol de elementos  
→ **Solución**: Cabify tiene protección `FLAG_SECURE`  
→ **Workaround**: Detectar solo por `packageName`, sin analizar contenido

---

#### **Caso 3: Aparece `Collected 0 text nodes`**
→ **Problema**: El árbol está vacío o protegido  
→ **Solución**: 
- Verifica que Cabify esté en modo "Esperando oferta" (con ofertas visibles)
- Posible causa: Cabify usa WebView y los textos están renderizados en Canvas

---

#### **Caso 4: `Collected 15 text nodes` pero `❌ No valid trip found`**
→ **Problema**: Los textos se recolectaron pero NO coinciden con los regex  
→ **Acciones**:

1. **Busca en los logs qué textos se recolectaron:**
```
    [Text 0]: ...
    [Text 1]: ...
```

2. **Verifica el formato exacto de Cabify:**
   - ¿Precio está en `$8,500` o `8.500 CLP` o `8500`?
   - ¿Distancia en `5.2 km` o `5,2 km` o `5.2KM`?
   - ¿Tiempo en `12 min` o `12 minutes` o `12m`?

3. **Actualiza los regex en `parseAndEvaluateScreenTexts()`:**
```kotlin
// ACTUAL (puede ser incompleto)
val pricePattern = Pattern.compile("""[$€¥]\s*([0-9]+[.,]?[0-9]*[.,]?[0-9]*)""")

// MEJORADO (para Cabify específicamente)
val pricePattern = Pattern.compile("""(?:Tarifa|Precio|Estimada)[:\s]*[$]?\s*([0-9]+[.,][0-9]+(?:[.,][0-9]+)?)""", Pattern.CASE_INSENSITIVE)
val distPattern = Pattern.compile("""(?:Distancia|Recorrido)[:\s]*([0-9]+[.,][0-9]+)\s*(?:km|KM)""", Pattern.CASE_INSENSITIVE)
val timePattern = Pattern.compile("""(?:Tiempo|Duración)[:\s]*([0-9]+)\s*(?:min|minutos|m)""", Pattern.CASE_INSENSITIVE)
```

---

## 🎯 Checklist de Diagnóstico

- [ ] **Paso 1**: ¿Aparece "Conectado a Cabify" en el UI? 
  - ✅ SÍ → Continuar
  - ❌ NO → Problema en `commitActiveApp()` o `notifyAppConnected()`

- [ ] **Paso 2**: ¿Aparece `🚗 Scanning active app package com.cabify.driver` en logs?
  - ✅ SÍ → Continuar
  - ❌ NO → `pkgToAppName()` no reconoce el packageName de Cabify

- [ ] **Paso 3**: ¿Aparece `Collected N text nodes` (N > 0)?
  - ✅ SÍ → Continuar  
  - ❌ NO → El árbol está protegido o vacío

- [ ] **Paso 4**: ¿Aparecen los textos de la oferta (precio, distancia, tiempo)?
  - ✅ SÍ → Continuar
  - ❌ NO → Los regex no coinciden con el formato de Cabify

- [ ] **Paso 5**: ¿Aparece `🟢 Candidate trip` o `❌ No valid trip found`?
  - ✅ Candidate trip → El semáforo debe cambiar de color
  - ❌ No valid trip → Ajusta los regex

---

## 📝 Plantilla de Reporte

Cuando reportes el problema, incluye:

```
## Logs capturados (adb logcat | grep VERDI)
[Pega aquí todo lo que veas en los logs durante 5 segundos con Cabify abierto]

## Pantalla de Cabify  
- ¿Qué texto ves en la oferta de viaje?
- Ejemplo: "Tarifa: $8,500 | Distancia: 5.2 km | Tiempo: 12 min"

## ¿Qué debería pasar?
- El semáforo debería cambiar de color (rojo, amarillo, verde) dentro de <500ms

## ¿Qué está pasando?
- [Describe el comportamiento actual]
```

---

## 🔗 Referencias

- **Android Accessibility Docs**: https://developer.android.com/guide/topics/ui/accessibility
- **AccessibilityEvent Types**: https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent
- **WebView Text Extraction**: https://stackoverflow.com/q/50810975


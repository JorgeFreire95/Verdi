// Import Capacitor core utilities
import { registerPlugin } from '@capacitor/core';

// Reference the custom Verdi native plugin
// Safe wrapper in case of browser/non-native environment
const VerdiPlugin = registerPlugin('Verdi', {
  web: () => ({
    checkPermissions: async () => ({ overlay: false, accessibility: false }),
    requestPermissions: async (args) => ({ status: 'prompted' }),
    updateConfig: async (config) => { console.log('Mock Config Sended to Android:', config); return { status: 'ok' }; },
    toggleBubble: async (args) => ({ active: args.active })
  })
});

// App State Management
const STATE = {
  currency: 'CLP',
  distanceUnit: 'km',
  fuelUnit: 'L',
  consumptionUnit: 'km_l',
  fuelPrice: 1200,
  vehicleEfficiency: 12.0,
  minHourlyEarnings: 15000,
  minPerDistance: 350,
  lastCapturedTime: 0,
  stats: {
    total: 0,
    green: 0,
    red: 0
  },
  history: [],
  installations: {
    uber: false,
    didi: false,
    cabify: false
  },
  bubbleActive: false
};

// DOM Elements
const elements = {};

// Cache DOM references
function cacheDom() {
  elements.tabs = document.querySelectorAll('.tab-content');
  elements.navItems = document.querySelectorAll('.nav-item');
  elements.sliders = document.querySelectorAll('input[type="range"]');
  elements.costsForm = document.getElementById('costs-form');
  
  // Settings Inputs
  elements.currency = document.getElementById('currency');
  elements.unitDistance = document.getElementById('unit-distance');
  elements.unitFuel = document.getElementById('unit-fuel');
  elements.unitConsumption = document.getElementById('unit-consumption');
  elements.fuelPriceInput = document.getElementById('fuel-price');
  elements.efficiencyInput = document.getElementById('vehicle-efficiency');
  elements.minPerDistInput = document.getElementById('min-per-dist');
  
  // Dashboard Status Controls
  elements.serviceBadge = document.getElementById('service-badge');
  elements.serviceStatusText = document.getElementById('service-status-text');
  elements.btnToggleOverlay = document.getElementById('btn-toggle-overlay');
  elements.btnToggleAccessibility = document.getElementById('btn-toggle-accessibility');
  elements.statusOverlayDesc = document.getElementById('status-overlay-desc');
  elements.statusAccessibilityDesc = document.getElementById('status-accessibility-desc');
  
  // Live Screen Preview
  elements.liveTrafficLight = document.getElementById('live-traffic-light');
  elements.liveBubble = document.getElementById('live-bubble');
  elements.liveBubbleText = document.getElementById('live-bubble-text');
  elements.liveStatusTitle = document.getElementById('live-status-title');
  elements.liveStatusDesc = document.getElementById('live-status-desc');
  elements.liveMetricsContainer = document.getElementById('live-metrics-container');
  elements.liveMetricPrice = document.getElementById('live-metric-price');
  elements.liveMetricDist = document.getElementById('live-metric-dist');
  elements.liveMetricProfit = document.getElementById('live-metric-profit');
  
  // Stats Counters
  elements.statsTotal = document.getElementById('stats-total');
  elements.statsGreen = document.getElementById('stats-green');
  elements.statsRed = document.getElementById('stats-red');
  // App Status items in dashboard
  elements.appUber = document.getElementById('status-app-uber');
  elements.appDiDi = document.getElementById('status-app-didi');
  elements.appCabify = document.getElementById('status-app-cabify');
  elements.textUber = document.getElementById('status-text-uber');
  elements.textDiDi = document.getElementById('status-text-didi');
  elements.textCabify = document.getElementById('status-text-cabify');
  
  elements.installUber = document.getElementById('install-badge-uber');
  elements.installDiDi = document.getElementById('install-badge-didi');
  elements.installCabify = document.getElementById('install-badge-cabify');
  
  elements.cardBubble = document.getElementById('card-bubble');
  elements.statusBubbleDesc = document.getElementById('status-bubble-desc');
  elements.btnToggleBubble = document.getElementById('btn-toggle-bubble');
  
  // Labels
  elements.lblDistUnits = document.querySelectorAll('.lbl-dist-unit');
  elements.historyContainer = document.getElementById('history-container');
}

// Format Numbers to currency
function formatCurrency(value, currency) {
  const code = currency || STATE.currency;
  try {
    return new Intl.NumberFormat(code === 'CLP' || code === 'COP' ? 'es-CL' : 'en-US', {
      style: 'currency',
      currency: code,
      maximumFractionDigits: 0
    }).format(value);
  } catch (e) {
    return `$${Math.round(value)}`;
  }
}

// Load and Initialize Settings
function loadSettings() {
  const saved = localStorage.getItem('verdi_settings');
  if (saved) {
    try {
      const parsed = JSON.parse(saved);
      Object.assign(STATE, parsed);
    } catch(e) {
      console.error('Error parsing local storage settings', e);
    }
  }
  
  // Sync state to UI
  elements.currency.value = STATE.currency;
  elements.unitDistance.value = STATE.distanceUnit;
  elements.unitFuel.value = STATE.fuelUnit;
  elements.unitConsumption.value = STATE.consumptionUnit;
  
  elements.fuelPriceInput.value = STATE.fuelPrice;
  elements.efficiencyInput.value = STATE.vehicleEfficiency;
  elements.minPerDistInput.value = STATE.minPerDistance;
  
  // Update badges
  updateSliderBadges();
  updateLabels();
  syncConfigToNative();
}

function updateSliderBadges() {
  document.getElementById('val-fuel-price').innerText = formatCurrency(elements.fuelPriceInput.value);
  document.getElementById('val-efficiency').innerText = `${elements.efficiencyInput.value} ${STATE.distanceUnit}/${STATE.fuelUnit}`;
  document.getElementById('val-min-per-dist').innerText = `${formatCurrency(elements.minPerDistInput.value)}/${STATE.distanceUnit}`;
}

function updateLabels() {
  elements.lblDistUnits.forEach(el => el.innerText = STATE.distanceUnit);
}

// Save Settings Form
function saveSettings(e) {
  if (e) e.preventDefault();
  
  STATE.currency = elements.currency.value;
  STATE.distanceUnit = elements.unitDistance.value;
  STATE.fuelUnit = elements.unitFuel.value;
  STATE.consumptionUnit = elements.unitConsumption.value;
  
  STATE.fuelPrice = parseFloat(elements.fuelPriceInput.value);
  STATE.vehicleEfficiency = parseFloat(elements.efficiencyInput.value);
  STATE.minPerDistance = parseFloat(elements.minPerDistInput.value);
  
  localStorage.setItem('verdi_settings', JSON.stringify(STATE));
  updateLabels();
  updateSliderBadges();
  syncConfigToNative();
  
  // Trigger animations
  const btn = document.getElementById('btn-save-settings');
  const oldText = btn.innerHTML;
  btn.innerHTML = '⚡ Guardado y Sincronizado!';
  btn.style.background = '#059669';
  setTimeout(() => {
    btn.innerHTML = oldText;
    btn.style.background = '';
  }, 1500);
}

// Sync parameters to native Kotlin Layer
async function syncConfigToNative() {
  try {
    await VerdiPlugin.updateConfig({
      currency: STATE.currency,
      distanceUnit: STATE.distanceUnit,
      fuelUnit: STATE.fuelUnit,
      consumptionUnit: STATE.consumptionUnit,
      fuelPrice: STATE.fuelPrice,
      vehicleEfficiency: STATE.vehicleEfficiency,
      minHourlyEarnings: STATE.minHourlyEarnings,
      minPerDistance: STATE.minPerDistance
    });
  } catch (err) {
    console.warn('Native environment not loaded yet for configuration sync.', err);
  }
}

// Check native Android permissions
async function checkAndroidPermissions() {
  try {
    console.log("checkAndroidPermissions started");
    const res = await VerdiPlugin.checkPermissions();
    console.log("=== VERDI DEBUG ===");
    console.log("activeApp:", res.activeApp);
    console.log("lastConnectedApp:", res.lastConnectedApp);
    console.log("isServiceRunning:", res.isServiceRunning);
    console.log("cabifyInstalled:", res.cabifyInstalled);
    console.log("accessibility:", res.accessibility);
    console.log("overlay:", res.overlay);
    console.log("Full result:", res);
    console.log("=== END DEBUG ===");
    console.log("checkAndroidPermissions result:", res);
    
    updatePermissionUI('overlay', res.overlay);
    updatePermissionUI('accessibility', res.accessibility);
    
    // Save installation states locally in the state object
    STATE.installations = {
      uber: !!res.uberInstalled,
      didi: !!res.didiInstalled,
      cabify: !!res.cabifyInstalled
    };

    // If overlay permission is disabled, turn off active bubble state
    if (!res.overlay) {
      STATE.bubbleActive = false;
    }
    updateBubbleUI(STATE.bubbleActive);

    // Always refresh app connection UI from native state, even if bubble is not active
    updateAppConnectionUI(res.activeApp || 'Ninguna');

    // Check if the accessibility service is running in background
    const isServiceActive = res.isServiceRunning || (res.accessibility && res.overlay);

    if (isServiceActive) {
      const timeSinceCapture = Date.now() - (STATE.lastCapturedTime || 0);
      if (timeSinceCapture > 6000) {
        const currentApp = res.activeApp;
        if (currentApp && currentApp !== 'Ninguna' && currentApp !== 'Desconectado' && currentApp !== 'Verdi (Pruebas)') {
          elements.liveStatusTitle.innerText = `Conectado a ${currentApp}`;
          elements.liveStatusDesc.innerText = `Esperando viaje... Monitoreando pantalla de forma activa.`;
        } else {
          elements.liveStatusTitle.innerText = `Buscando Conexión...`;
          elements.liveStatusDesc.innerText = `Esperando la apertura de Uber, DiDi o Cabify en primer plano.`;
        }
        elements.liveTrafficLight.className = 'traffic-light-preview graphite';
        elements.liveMetricsContainer.style.display = 'none';
        elements.liveBubbleText.innerText = '🔘';
      }
    } else {
      const timeSinceCapture = Date.now() - (STATE.lastCapturedTime || 0);
      if (timeSinceCapture > 6000) {
        elements.liveStatusTitle.innerText = `Servicio Inactivo`;
        elements.liveStatusDesc.innerText = `Por favor, activa los permisos de lectura de pantalla y burbuja flotante para iniciar.`;
        elements.liveTrafficLight.className = 'traffic-light-preview graphite';
        elements.liveMetricsContainer.style.display = 'none';
        elements.liveBubbleText.innerText = '🔘';
      }
    }

    // Toggle active classes on global status badge
    if (res.accessibility && res.overlay) {
      elements.serviceBadge.classList.add('active');
      elements.serviceStatusText.innerText = 'Servicio Activo';
    } else {
      elements.serviceBadge.classList.remove('active');
      elements.serviceStatusText.innerText = 'Servicio Inactivo';
    }

    // If runtime permissions are missing, request them immediately (native dialog or settings)
    const locationFine = !!res.locationFine;
    const bluetoothScan = !!res.bluetoothScan;
    if (!locationFine || !bluetoothScan) {
      try {
        VerdiPlugin.requestPermissions({ type: 'runtime' }).then(r => console.log('requested runtime perms', r)).catch(e => console.warn('runtime perms error', e));
      } catch (err) {
        console.warn('Error requesting runtime permissions', err);
      }
      // recheck after a short delay
      setTimeout(checkAndroidPermissions, 1200);
    }
  } catch (err) {
    console.error('Error in checkAndroidPermissions:', err);
  }
}

// Update connection status list items in dashboard
function updateAppConnectionUI(activeApp) {
  try {
    if (!elements.appUber) return; // safety check
    
    // 1. Update installation badges in UI
    const inst = STATE.installations || { uber: false, didi: false, cabify: false };
    
    const updateBadge = (badgeEl, installed) => {
      if (!badgeEl) return;
      if (installed) {
        badgeEl.innerText = "Instalada";
        badgeEl.className = "app-install-badge installed";
      } else {
        badgeEl.innerText = "No detectada";
        badgeEl.className = "app-install-badge not-installed";
      }
    };

    updateBadge(elements.installUber, inst.uber);
    updateBadge(elements.installDiDi, inst.didi);
    updateBadge(elements.installCabify, inst.cabify);

    // 2. Clean classes and texts for connection states
    elements.appUber.classList.remove('active');
    elements.appUber.classList.remove('uber');
    elements.appDiDi.classList.remove('active');
    elements.appDiDi.classList.remove('didi');
    elements.appCabify.classList.remove('active');
    elements.appCabify.classList.remove('cabify');
    
    elements.textUber.innerText = inst.uber ? 'Segundo plano' : 'No detectada';
    elements.textDiDi.innerText = inst.didi ? 'Segundo plano' : 'No detectada';
    elements.textCabify.innerText = inst.cabify ? 'Segundo plano' : 'No detectada';

    // 3. Highlight active foreground app
    if (activeApp === 'Uber') {
      elements.appUber.classList.add('active');
      elements.appUber.classList.add('uber');
      elements.textUber.innerText = 'Activo';
    } else if (activeApp === 'DiDi') {
      elements.appDiDi.classList.add('active');
      elements.appDiDi.classList.add('didi');
      elements.textDiDi.innerText = 'Activo';
    } else if (activeApp === 'Cabify') {
      elements.appCabify.classList.add('active');
      elements.appCabify.classList.add('cabify');
      elements.textCabify.innerText = 'Activo';
    }
  } catch (e) {
    console.error("Error in updateAppConnectionUI:", e);
  }
}

// Update service bubble UI card status
function updateBubbleUI(active) {
  if (!elements.cardBubble) return;
  if (active) {
    elements.cardBubble.classList.add('active');
    elements.statusBubbleDesc.innerText = 'Burbuja Activa en pantalla';
    elements.btnToggleBubble.innerText = 'Detener';
  } else {
    elements.cardBubble.classList.remove('active');
    elements.statusBubbleDesc.innerText = 'Burbuja desactivada';
    elements.btnToggleBubble.innerText = 'Iniciar';
  }
}

// Toggle bubble service natively
async function toggleBubbleService() {
  try {
    const res = await VerdiPlugin.checkPermissions();
    if (!res.overlay) {
      alert("Por favor, activa primero el permiso de Burbuja Flotante (Dibujar sobre otras apps).");
      return;
    }
    
    STATE.bubbleActive = !STATE.bubbleActive;
    
    // Persist active state in localStorage settings
    localStorage.setItem('verdi_settings', JSON.stringify(STATE));
    
    // Call the native plugin method to start/stop the service
    await VerdiPlugin.toggleBubble({ active: STATE.bubbleActive });
    
    updateBubbleUI(STATE.bubbleActive);
  } catch (err) {
    console.error("Error toggling bubble service:", err);
    // Mock simulation toggle in browser
    STATE.bubbleActive = !STATE.bubbleActive;
    updateBubbleUI(STATE.bubbleActive);
  }
}


function updatePermissionUI(type, granted) {
  if (type === 'overlay') {
    const card = document.getElementById('card-overlay');
    const desc = elements.statusOverlayDesc;
    const btn = elements.btnToggleOverlay;
    if (granted) {
      card.classList.add('active');
      desc.innerText = 'Permiso Activo';
      btn.innerText = 'Desactivar';
    } else {
      card.classList.remove('active');
      desc.innerText = 'Requiere permiso de dibujo';
      btn.innerText = 'Otorgar';
    }
  } else if (type === 'accessibility') {
    const card = document.getElementById('card-accessibility');
    const desc = elements.statusAccessibilityDesc;
    const btn = elements.btnToggleAccessibility;
    if (granted) {
      card.classList.add('active');
      desc.innerText = 'Lectura Activa';
      btn.innerText = 'Desactivar';
    } else {
      card.classList.remove('active');
      desc.innerText = 'Requiere servicio accesibilidad';
      btn.innerText = 'Otorgar';
    }
  }
}

// Request permission natively
async function toggleAndroidPermission(type) {
  try {
    if (type === 'overlay') {
      const active = !document.getElementById('card-overlay').classList.contains('active');
      await VerdiPlugin.requestPermissions({ type: 'overlay', value: active });
    } else if (type === 'accessibility') {
      const active = !document.getElementById('card-accessibility').classList.contains('active');
      await VerdiPlugin.requestPermissions({ type: 'accessibility', value: active });
    }
    // Recheck state after requesting
    setTimeout(checkAndroidPermissions, 1000);
  } catch (err) {
    // Simulator mock mode toggle for testing UI
    if (type === 'overlay') {
      const active = !document.getElementById('card-overlay').classList.contains('active');
      updatePermissionUI('overlay', active);
    } else if (type === 'accessibility') {
      const active = !document.getElementById('card-accessibility').classList.contains('active');
      updatePermissionUI('accessibility', active);
    }
    
    // Update master service status badge
    const ovActive = document.getElementById('card-overlay').classList.contains('active');
    const acActive = document.getElementById('card-accessibility').classList.contains('active');
    if (ovActive && acActive) {
      elements.serviceBadge.classList.add('active');
      elements.serviceStatusText.innerText = 'Servicio Activo';
    } else {
      elements.serviceBadge.classList.remove('active');
      elements.serviceStatusText.innerText = 'Servicio Inactivo';
    }
  }
}

// Profitability core math formula
function calculateProfitability(price, distance, timeMins) {
  // Fuel Cost calculation
  // Distance divided by efficiency = fuel amount needed (applicable to both KM/L and MPG)
  const fuelUsed = distance / STATE.vehicleEfficiency;
  
  const fuelCost = fuelUsed * STATE.fuelPrice;
  const netProfit = price - fuelCost;
  
  // Hourly rate projection
  const hours = timeMins / 60;
  const hourlyRate = hours > 0 ? (netProfit / hours) : 0;
  
  // Distance rate projection
  const distanceRate = distance > 0 ? (netProfit / distance) : 0;
  
  // Decide Traffic Light Color
  let decision = 'RED';
  if (netProfit > 0) {
    const pctDist = distanceRate / STATE.minPerDistance;
    
    // Decidir color basado únicamente en ganancia por distancia
    if (pctDist >= 1.0) {
      decision = 'GREEN';
    } else if (pctDist >= 0.7) {
      decision = 'YELLOW';
    }
  }
  
  return {
    fuelCost,
    netProfit,
    hourlyRate,
    decision
  };
}



function updateStatsCounters() {
  elements.statsTotal.innerText = STATE.stats.total;
  elements.statsGreen.innerText = STATE.stats.green;
  elements.statsRed.innerText = STATE.stats.red;
}

// Add analyzed item to logs
function addTripToHistory(trip) {
  STATE.history.unshift(trip);
  if (STATE.history.length > 30) STATE.history.pop();
  
  renderHistory();
}

function renderHistory() {
  if (STATE.history.length === 0) {
    elements.historyContainer.innerHTML = '<li class="empty-history">No hay viajes capturados aún en este turno.</li>';
    return;
  }
  
  elements.historyContainer.innerHTML = STATE.history.map(trip => {
    const dotColor = trip.decision.toLowerCase();
    const netClass = trip.netProfit >= 0 ? 'green' : 'red';
    const profitSign = trip.netProfit >= 0 ? '+' : '';
    
    return `
      <li class="history-item">
        <div class="hist-left">
          <div class="hist-dot ${dotColor}"></div>
          <div class="hist-details">
            <span class="hist-price">${formatCurrency(trip.price)}</span>
            <span class="hist-sub">${trip.distance} ${STATE.distanceUnit} • ${trip.time} min</span>
          </div>
        </div>
        <div class="hist-right">
          <span class="hist-profit ${netClass}">${profitSign}${formatCurrency(trip.netProfit)}</span>
          <span class="hist-time">${trip.timestamp}</span>
        </div>
      </li>
    `;
  }).join('');
}

// Handle native callbacks from screen readers
function setupNativeListeners() {
  try {
    // Listen for trips captured by background OCR/Accessibility service
    VerdiPlugin.addListener('onTripCaptured', (trip) => {
      // Record capture time to prevent immediate override by connection status
      STATE.lastCapturedTime = Date.now();

      // trip keys: price, distance, timeMins
      const results = calculateProfitability(trip.price, trip.distance, trip.timeMins);
      
      // Update dashboard real-time view
      elements.liveMetricsContainer.style.display = 'flex';
      elements.liveMetricPrice.innerText = formatCurrency(trip.price);
      elements.liveMetricDist.innerText = `${trip.distance} ${STATE.distanceUnit}`;
      elements.liveMetricProfit.innerText = `${formatCurrency(results.netProfit)} netos`;
      
      let borderClass = 'graphite';
      let title = 'Oferta Grafito';
      let desc = 'Servicio activo. Buscando viajes en primer plano...';
      let emoji = '🔘';
      
      if (results.decision === 'GREEN') {
        borderClass = 'green';
        title = '🟢 Oferta Rentable!';
        desc = `Viaje altamente rentable detectado. Ganancia horaria estimada: ${formatCurrency(results.hourlyRate)}/hr.`;
        emoji = '🟢';
        STATE.stats.green++;
      } else if (results.decision === 'YELLOW') {
        borderClass = 'yellow';
        title = '🟡 Oferta Marginal';
        desc = `Viaje aceptable. Margen ajustado. Ganancia horaria estimada: ${formatCurrency(results.hourlyRate)}/hr.`;
        emoji = '🟡';
      } else if (results.decision === 'RED') {
        borderClass = 'red';
        title = '🔴 Oferta NO Recomendable';
        desc = `Viaje poco rentable o a pérdida. Combustible estimado: ${formatCurrency(results.fuelCost)}. Tasa: ${formatCurrency(results.hourlyRate)}/hr.`;
        emoji = '🔴';
        STATE.stats.red++;
      }
      
      STATE.stats.total++;
      elements.liveStatusTitle.innerText = title;
      elements.liveStatusDesc.innerText = desc;
      elements.liveBubbleText.innerText = emoji;
      elements.liveTrafficLight.className = `traffic-light-preview ${borderClass}`;
      
      // Save to history
      addTripToHistory({
        price: trip.price,
        distance: trip.distance,
        time: trip.timeMins,
        decision: results.decision,
        netProfit: results.netProfit,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
      });
      
      // Refresh stats
      updateStatsCounters();
    });

    // Listen for driver app foreground activity notifications
    VerdiPlugin.addListener('onAppConnected', (data) => {
      const appName = data.appName || 'Ninguna';
      updateAppConnectionUI(appName);
      
      const timeSinceCapture = Date.now() - (STATE.lastCapturedTime || 0);
      // Wait at least 6 seconds after a trip check before returning to graphite searching state
      if (timeSinceCapture > 6000) {
        if (appName !== 'Ninguna' && appName !== 'Desconectado' && appName !== 'Verdi (Pruebas)') {
          elements.liveStatusTitle.innerText = `Conectado a ${appName}`;
          elements.liveStatusDesc.innerText = `Esperando viaje... Monitoreando pantalla de forma activa.`;
        } else {
          elements.liveStatusTitle.innerText = `Buscando Conexión...`;
          elements.liveStatusDesc.innerText = `Esperando la apertura de Uber, DiDi o Cabify en primer plano.`;
        }
        elements.liveTrafficLight.className = 'traffic-light-preview graphite';
        elements.liveMetricsContainer.style.display = 'none';
        elements.liveBubbleText.innerText = '🔘';
      }
    });
  } catch(err) {
    console.warn('Cannot register native callbacks, browser simulation running.');
  }
}

// Navigation Tab Switcher
function setupNavigation() {
  elements.navItems.forEach(item => {
    item.addEventListener('click', () => {
      const targetTab = item.dataset.tab;
      
      // Remove active from nav elements
      elements.navItems.forEach(n => n.classList.remove('active'));
      item.classList.add('active');
      
      // Switch active class on sections
      elements.tabs.forEach(tab => {
        if (tab.id === targetTab) {
          tab.classList.add('active');
        } else {
          tab.classList.remove('active');
        }
      });
    });
  });
}

// Event Listeners Initialization
function initEvents() {
  // Navigation tabs
  setupNavigation();
  
  // Sliders value updates
  elements.sliders.forEach(slider => {
    slider.addEventListener('input', updateSliderBadges);
  });
  
  // Forms submit
  elements.costsForm.addEventListener('submit', saveSettings);
  
  // Permission Toggles
  elements.btnToggleOverlay.addEventListener('click', () => toggleAndroidPermission('overlay'));
  elements.btnToggleAccessibility.addEventListener('click', () => toggleAndroidPermission('accessibility'));
  
  // Service Bubble Toggle
  elements.btnToggleBubble.addEventListener('click', toggleBubbleService);
  
  // Unit change updates
  elements.unitDistance.addEventListener('change', (e) => {
    STATE.distanceUnit = e.target.value;
    updateLabels();
    updateSliderBadges();
  });
  elements.unitFuel.addEventListener('change', (e) => {
    STATE.fuelUnit = e.target.value;
    updateSliderBadges();
  });
  elements.currency.addEventListener('change', (e) => {
    STATE.currency = e.target.value;
    updateSliderBadges();
  });
}

// Bootstrap Application
window.addEventListener('DOMContentLoaded', () => {
  cacheDom();
  initEvents();
  loadSettings();
  setupNativeListeners();
  checkAndroidPermissions();
  
  // Auto-launch bubble service if active
  if (STATE.bubbleActive) {
    VerdiPlugin.toggleBubble({ active: true }).catch(err => console.warn("Failed starting service on load", err));
  }
  
  // Refrescar permisos y conexión de apps periódicamente cada 2 segundos
  setInterval(checkAndroidPermissions, 2000);

  // Initialize Lucide icons
  if (window.lucide) {
    window.lucide.createIcons();
  }
});

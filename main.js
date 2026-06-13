import { supabase, isConfigured } from './supabase.js';

// --- Country Mapping Config ---
const countryConfig = {
  AR: { currency: 'ARS', currencySymbol: '$', currencyName: 'Peso Argentino', unit: 'km', unitLabel: 'Kilómetros', prefix: '+54' },
  BR: { currency: 'BRL', currencySymbol: 'R$', currencyName: 'Real Brasileño', unit: 'km', unitLabel: 'Kilómetros', prefix: '+55' },
  CA: { currency: 'CAD', currencySymbol: '$', currencyName: 'Dólar Canadiense', unit: 'km', unitLabel: 'Kilómetros', prefix: '+1' },
  CL: { currency: 'CLP', currencySymbol: '$', currencyName: 'Peso Chileno', unit: 'km', unitLabel: 'Kilómetros', prefix: '+56' },
  CO: { currency: 'COP', currencySymbol: '$', currencyName: 'Peso Colombiano', unit: 'km', unitLabel: 'Kilómetros', prefix: '+57' },
  ES: { currency: 'EUR', currencySymbol: '€', currencyName: 'Euro', unit: 'km', unitLabel: 'Kilómetros', prefix: '+34' },
  MX: { currency: 'MXN', currencySymbol: '$', currencyName: 'Peso Mexicano', unit: 'km', unitLabel: 'Kilómetros', prefix: '+52' },
  PE: { currency: 'PEN', currencySymbol: 'S/.', currencyName: 'Sol Peruano', unit: 'km', unitLabel: 'Kilómetros', prefix: '+51' },
  UK: { currency: 'GBP', currencySymbol: '£', currencyName: 'Libra Esterlina', unit: 'mi', unitLabel: 'Millas', prefix: '+44' },
  US: { currency: 'USD', currencySymbol: '$', currencyName: 'Dólar Estadounidense', unit: 'mi', unitLabel: 'Millas', prefix: '+1' }
};

// --- Active Session Info ---
let currentSessionUser = null;
let userProfile = null; // Store full DB row
let currentProfileData = {
  full_name: 'Conductor',
  phone: '',
  country: 'CL',
  currencySymbol: '$',
  currency: 'CLP',
  distanceUnit: 'km',
  distanceUnitLabel: 'Kilómetros',
  fuel_cost: 0,
  fuel_efficiency: 0,
  desired_net_profit: 0,
  semaforo_active: true,
  connected_apps: [],
  active_app: ''
};

// --- DOM Elements ---
document.addEventListener('DOMContentLoaded', () => {
  // Initialize Lucide Icons
  if (window.lucide) {
    window.lucide.createIcons();
  }

  const appHeader = document.querySelector('.app-header');
  const loginView = document.getElementById('loginView');
  const registerView = document.getElementById('registerView');
  const profitabilityView = document.getElementById('profitabilityView');
  const dashboardView = document.getElementById('dashboardView');
  const settingsView = document.getElementById('settingsView');
  const appsView = document.getElementById('appsView');
  const profitActiveApp = document.getElementById('profitActiveApp');
  const dashActiveAppContainer = document.getElementById('dashActiveAppContainer');
  const dashActiveAppName = document.getElementById('dashActiveAppName');

  // Switch views buttons
  const switchToRegisterBtn = document.getElementById('switchToRegisterBtn');
  const switchToLoginBtn = document.getElementById('switchToLoginBtn');
  
  // Forms
  const loginForm = document.getElementById('loginForm');
  const registerForm = document.getElementById('registerForm');
  const profitabilityForm = document.getElementById('profitabilityForm');
  const settingsForm = document.getElementById('settingsForm');
  
  // Register inputs
  const countrySelect = document.getElementById('registerCountry');
  const autoConfigPanel = document.getElementById('autoConfigPanel');
  const currencySymbol = document.getElementById('currencySymbol');
  const currencyName = document.getElementById('currencyName');
  const distanceUnit = document.getElementById('distanceUnit');
  const distanceUnitLabel = document.getElementById('distanceUnitLabel');
  const phonePrefix = document.getElementById('phonePrefix');
  const registerPhone = document.getElementById('registerPhone');

  // Suffix elements for Profitability View
  const fuelCostSuffix = document.getElementById('fuelCostSuffix');
  const efficiencySuffix = document.getElementById('efficiencySuffix');
  const netGoalSuffix = document.getElementById('netGoalSuffix');

  // Dashboard Stats Elements
  const dashNetGoal = document.getElementById('dashNetGoal');
  const dashNetGoalUnit = document.getElementById('dashNetGoalUnit');
  const dashFuelCost = document.getElementById('dashFuelCost');
  const dashFuelCostUnit = document.getElementById('dashFuelCostUnit');
  const dashEfficiency = document.getElementById('dashEfficiency');
  const dashEfficiencyUnit = document.getElementById('dashEfficiencyUnit');
  const avatarName = document.getElementById('avatarName');

  // Side Menu Drawer Elements
  const sideMenu = document.getElementById('sideMenu');
  const menuBackdrop = document.getElementById('menuBackdrop');
  const openMenuBtn = document.getElementById('openMenuBtn');
  const closeMenuBtn = document.getElementById('closeMenuBtn');
  const menuAvatar = document.getElementById('menuAvatar');
  const menuUserName = document.getElementById('menuUserName');
  const menuUserEmail = document.getElementById('menuUserEmail');

  const menuHomeBtn = document.getElementById('menuHomeBtn');
  const menuSettingsBtn = document.getElementById('menuSettingsBtn');
  const menuAppsBtn = document.getElementById('menuAppsBtn');
  const menuLogoutBtn = document.getElementById('menuLogoutBtn');

  // Settings inputs
  const closeSettingsBtn = document.getElementById('closeSettingsBtn');
  const closeAppsBtn = document.getElementById('closeAppsBtn');
  const settingsCountry = document.getElementById('settingsCountry');
  const settingsCurrencySymbol = document.getElementById('settingsCurrencySymbol');
  const settingsCurrencyName = document.getElementById('settingsCurrencyName');
  const settingsDistanceUnit = document.getElementById('settingsDistanceUnit');
  const settingsDistanceUnitLabel = document.getElementById('settingsDistanceUnitLabel');
  const settingsPhonePrefix = document.getElementById('settingsPhonePrefix');
  const settingsPhone = document.getElementById('settingsPhone');
  const settingsPassword = document.getElementById('settingsPassword');

  // Toggle view function
  const switchView = (targetView) => {
    // Show/hide main landing header (only for Login and Register views)
    if (targetView === loginView || targetView === registerView) {
      appHeader.style.display = 'block';
    } else {
      appHeader.style.display = 'none';
    }

    // Hide all views
    [loginView, registerView, profitabilityView, dashboardView, settingsView, appsView].forEach(view => {
      view.classList.remove('active');
      view.style.display = 'none';
    });
    
    // Show target view
    targetView.style.display = 'block';
    setTimeout(() => targetView.classList.add('active'), 50);
  };

  // Side Menu Open/Close logic
  const toggleMenu = (open) => {
    if (open) {
      sideMenu.classList.add('open');
      menuBackdrop.classList.add('open');
    } else {
      sideMenu.classList.remove('open');
      menuBackdrop.classList.remove('open');
    }
  };

  openMenuBtn.addEventListener('click', () => toggleMenu(true));
  
  // Add listener for the second menu button in Profitability View
  const openMenuBtnProfit = document.getElementById('openMenuBtnProfit');
  if (openMenuBtnProfit) {
    openMenuBtnProfit.addEventListener('click', () => toggleMenu(true));
  }

  closeMenuBtn.addEventListener('click', () => toggleMenu(false));
  menuBackdrop.addEventListener('click', () => toggleMenu(false));

  // Switch menu items
  menuHomeBtn.addEventListener('click', () => {
    menuHomeBtn.classList.add('active');
    menuSettingsBtn.classList.remove('active');
    menuAppsBtn.classList.remove('active');
    toggleMenu(false);
    if (currentProfileData.settings_configured) {
      switchView(dashboardView);
    } else {
      switchView(profitabilityView);
    }
  });

  menuSettingsBtn.addEventListener('click', () => {
    menuSettingsBtn.classList.add('active');
    menuHomeBtn.classList.remove('active');
    menuAppsBtn.classList.remove('active');
    toggleMenu(false);
    loadProfileIntoSettings();
    switchView(settingsView);
  });

  menuAppsBtn.addEventListener('click', () => {
    menuAppsBtn.classList.add('active');
    menuHomeBtn.classList.remove('active');
    menuSettingsBtn.classList.remove('active');
    toggleMenu(false);
    switchView(appsView);
  });

  closeSettingsBtn.addEventListener('click', () => {
    menuHomeBtn.classList.add('active');
    menuSettingsBtn.classList.remove('active');
    menuAppsBtn.classList.remove('active');
    if (currentProfileData.settings_configured) {
      switchView(dashboardView);
    } else {
      switchView(profitabilityView);
    }
  });

  closeAppsBtn.addEventListener('click', () => {
    menuHomeBtn.classList.add('active');
    menuSettingsBtn.classList.remove('active');
    menuAppsBtn.classList.remove('active');
    if (currentProfileData.settings_configured) {
      switchView(dashboardView);
    } else {
      switchView(profitabilityView);
    }
  });

  menuLogoutBtn.addEventListener('click', async () => {
    toggleMenu(false);
    const confirmLogout = confirm('¿Estás seguro de que deseas cerrar sesión?');
    if (confirmLogout) {
      if (isConfigured) {
        await supabase.auth.signOut();
      }
      currentSessionUser = null;
      userProfile = null;
      switchView(loginView);
    }
  });

  // Toggle Semáforo (Turn Off / Turn On) Event Listener
  const toggleSemaforoBtn = document.getElementById('toggleSemaforoBtn');
  toggleSemaforoBtn.addEventListener('click', async () => {
    const newState = !currentProfileData.semaforo_active;
    currentProfileData.semaforo_active = newState;
    
    const originalHtml = toggleSemaforoBtn.innerHTML;
    toggleSemaforoBtn.disabled = true;
    toggleSemaforoBtn.innerHTML = `<span>Procesando...</span>`;

    if (isConfigured && currentSessionUser) {
      try {
        const updatePayload = { semaforo_active: newState };
        if (!newState) {
          updatePayload.settings_configured = false;
        }
        const { error } = await supabase
          .from('profiles')
          .update(updatePayload)
          .eq('id', currentSessionUser.id);
        if (error) throw error;
      } catch (err) {
        console.error(err);
        alert('Error al guardar el estado del semáforo: ' + err.message);
      }
    }

    setTimeout(() => {
      toggleSemaforoBtn.disabled = false;
      toggleSemaforoBtn.innerHTML = originalHtml;
      
      if (!newState) {
        // Al apagar, marcamos como no configurado y redirigimos al formulario inicial
        if (userProfile) userProfile.settings_configured = false;
        
        // Pre-rellenamos el formulario con los datos que ya tenía guardados
        document.getElementById('profitFuelCost').value = currentProfileData.fuel_cost || '';
        document.getElementById('profitEfficiency').value = currentProfileData.fuel_efficiency || '';
        document.getElementById('profitNetGoal').value = currentProfileData.desired_net_profit || '';
        
        menuHomeBtn.classList.add('active');
        menuSettingsBtn.classList.remove('active');
        setupProfitabilityLabels();
        switchView(profitabilityView);
      } else {
        updateDashboardStats();
      }
    }, 400);
  });

  // Event Listeners for switching views on landing page
  switchToRegisterBtn.addEventListener('click', () => switchView(registerView));
  switchToLoginBtn.addEventListener('click', () => switchView(loginView));

  // Toggle Password Visibility (Safe Selection for Lucide SVG conversion)
  const togglePasswordBtns = document.querySelectorAll('.toggle-password-btn');
  togglePasswordBtns.forEach(btn => {
    btn.addEventListener('click', (e) => {
      const input = btn.previousElementSibling;
      const icon = btn.querySelector('i, svg');
      if (input.type === 'password') {
        input.type = 'text';
        if (icon) icon.setAttribute('data-lucide', 'eye-off');
      } else {
        input.type = 'password';
        if (icon) icon.setAttribute('data-lucide', 'eye');
      }
      if (window.lucide) {
        window.lucide.createIcons();
      }
    });
  });

  // Country Selection Event (Register Form)
  countrySelect.addEventListener('change', (e) => {
    const code = e.target.value;
    const config = countryConfig[code];
    if (config) {
      currencySymbol.textContent = config.currencySymbol;
      currencyName.textContent = `${config.currency} - ${config.currencyName}`;
      distanceUnit.textContent = config.unit;
      distanceUnitLabel.textContent = config.unitLabel;
      phonePrefix.textContent = config.prefix;
      autoConfigPanel.classList.remove('hidden');
      clearError('registerCountry');
    } else {
      autoConfigPanel.classList.add('hidden');
      phonePrefix.textContent = '+--';
    }
  });

  // Country Selection Event (Settings Form)
  settingsCountry.addEventListener('change', (e) => {
    const code = e.target.value;
    const config = countryConfig[code];
    if (config) {
      settingsCurrencySymbol.textContent = config.currencySymbol;
      settingsCurrencyName.textContent = `${config.currency} - ${config.currencyName}`;
      settingsDistanceUnit.textContent = config.unit;
      settingsDistanceUnitLabel.textContent = config.unitLabel;
      settingsPhonePrefix.textContent = config.prefix;
    }
  });

  // --- Connected Apps Handling ---
  const renderConnectedAppsUI = () => {
    const connectBtns = document.querySelectorAll('.app-connect-btn');
    connectBtns.forEach(btn => {
      const appName = btn.getAttribute('data-app');
      const isConnected = currentProfileData.connected_apps.includes(appName);
      if (isConnected) {
        btn.classList.add('connected');
        btn.textContent = 'Desconectar';
      } else {
        btn.classList.remove('connected');
        btn.textContent = 'Conectar';
      }
    });
  };

  const populateActiveAppSelect = () => {
    if (!profitActiveApp) return;
    
    // Save current selection to restore it if possible
    const currentSelected = profitActiveApp.value;
    
    // Clear options except first
    profitActiveApp.innerHTML = '<option value="" disabled selected>Selecciona una aplicación</option>';
    
    // If no apps are connected, list all of them but show a generic option
    const appsToRender = currentProfileData.connected_apps.length > 0 
      ? currentProfileData.connected_apps 
      : ['Uber Driver', 'DiDi Conductor', 'Cabify Conductores', 'inDrive', 'Yango Pro'];
      
    appsToRender.forEach(app => {
      const opt = document.createElement('option');
      opt.value = app;
      opt.textContent = app;
      profitActiveApp.appendChild(opt);
    });

    // Add independent work option
    const indepOpt = document.createElement('option');
    indepOpt.value = 'Trabajo Independiente';
    indepOpt.textContent = 'Trabajo Independiente / Otra';
    profitActiveApp.appendChild(indepOpt);
    
    // Restore selection or select default
    if (currentSelected && [...profitActiveApp.options].some(o => o.value === currentSelected)) {
      profitActiveApp.value = currentSelected;
    } else if (currentProfileData.active_app && [...profitActiveApp.options].some(o => o.value === currentProfileData.active_app)) {
      profitActiveApp.value = currentProfileData.active_app;
    } else {
      profitActiveApp.value = '';
    }
  };

  // Delegate click for connect buttons
  document.addEventListener('click', async (e) => {
    if (e.target && e.target.classList.contains('app-connect-btn')) {
      const btn = e.target;
      const appName = btn.getAttribute('data-app');
      let connected = [...currentProfileData.connected_apps];
      
      if (connected.includes(appName)) {
        connected = connected.filter(a => a !== appName);
      } else {
        connected.push(appName);
      }
      
      currentProfileData.connected_apps = connected;
      renderConnectedAppsUI();
      populateActiveAppSelect();
      
      // Update profile database if logged in
      if (isConfigured && currentSessionUser) {
        try {
          const { error } = await supabase
            .from('profiles')
            .update({ connected_apps: connected })
            .eq('id', currentSessionUser.id);
          
          if (error) {
            if (error.message && error.message.includes('connected_apps')) {
              console.warn('connected_apps column does not exist on profiles table. Working in offline/demo mode for apps list.');
            } else {
              throw error;
            }
          }
        } catch (err) {
          console.error('Error saving connected apps:', err);
        }
      }
    }
  });

  // Form Validation Utilities
  const setError = (id, message) => {
    const element = document.getElementById(id);
    if (!element) return;
    const group = element.closest('.input-group');
    const errorSpan = document.getElementById(`${id}Error`);
    if (group && errorSpan) {
      group.classList.add('invalid');
      errorSpan.textContent = message;
    }
  };

  const clearError = (id) => {
    const element = document.getElementById(id);
    if (!element) return;
    const group = element.closest('.input-group');
    const errorSpan = document.getElementById(`${id}Error`);
    if (group && errorSpan) {
      group.classList.remove('invalid');
      errorSpan.textContent = '';
    }
  };

  const validateEmail = (email) => {
    const re = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    return re.test(String(email).toLowerCase());
  };

  // Setup dynamic labels based on active user settings
  const updateProfileDataLocally = (profile) => {
    userProfile = profile;
    
    currentProfileData.full_name = profile.full_name || 'Conductor';
    currentProfileData.phone = profile.phone || '';
    currentProfileData.country = profile.country || 'CL';
    currentProfileData.currency = profile.currency || 'CLP';
    
    const matchedConfig = countryConfig[currentProfileData.country];
    currentProfileData.currencySymbol = matchedConfig ? matchedConfig.currencySymbol : '$';
    currentProfileData.distanceUnit = profile.distance_unit || 'km';
    currentProfileData.distanceUnitLabel = profile.distance_unit === 'mi' ? 'Millas' : 'Kilómetros';

    currentProfileData.fuel_cost = profile.fuel_cost || 0;
    currentProfileData.fuel_efficiency = profile.fuel_efficiency || 0;
    currentProfileData.desired_net_profit = profile.desired_net_profit || 0;
    currentProfileData.semaforo_active = profile.semaforo_active !== false; // defaults to true
    currentProfileData.settings_configured = profile.settings_configured === true;
    currentProfileData.connected_apps = profile.connected_apps || [];
    currentProfileData.active_app = profile.active_app || '';

    // Render UI states
    renderConnectedAppsUI();
    populateActiveAppSelect();
  };

  const setupProfitabilityLabels = () => {
    fuelCostSuffix.textContent = `${currentProfileData.currencySymbol}/L`;
    efficiencySuffix.textContent = `${currentProfileData.distanceUnit}/L`;
    netGoalSuffix.textContent = `${currentProfileData.currencySymbol}/${currentProfileData.distanceUnit}`;

    document.getElementById('profitFuelCost').placeholder = `ej. 1.20 (${currentProfileData.currencySymbol})`;
    document.getElementById('profitEfficiency').placeholder = `ej. 12.5 (${currentProfileData.distanceUnit}/L)`;
    document.getElementById('profitNetGoal').placeholder = `ej. 0.80 (${currentProfileData.currencySymbol}/${currentProfileData.distanceUnit})`;
  };

  // Load profile values into the settings form
  const loadProfileIntoSettings = () => {
    settingsCountry.value = currentProfileData.country;
    
    // Trigger the country change display updates
    const config = countryConfig[currentProfileData.country];
    if (config) {
      settingsCurrencySymbol.textContent = config.currencySymbol;
      settingsCurrencyName.textContent = `${config.currency} - ${config.currencyName}`;
      settingsDistanceUnit.textContent = config.unit;
      settingsDistanceUnitLabel.textContent = config.unitLabel;
      settingsPhonePrefix.textContent = config.prefix;
    }

    // Strip prefix from phone number if present to fill the input correctly
    let rawPhone = currentProfileData.phone;
    if (config && rawPhone.startsWith(config.prefix)) {
      rawPhone = rawPhone.replace(config.prefix, '').trim();
    }
    settingsPhone.value = rawPhone;
    document.getElementById('settingsOldPassword').value = '';
    document.getElementById('settingsPassword').value = '';
    document.getElementById('settingsConfirmPassword').value = '';

    // Set side menu profile metadata
    menuUserName.textContent = currentProfileData.full_name;
    menuUserEmail.textContent = currentSessionUser ? currentSessionUser.email : 'correo@ejemplo.com';
    
    const initials = currentProfileData.full_name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);
    avatarName.textContent = initials || 'U';
    menuAvatar.textContent = initials || 'U';
    
    const avatarNameProfit = document.getElementById('avatarNameProfit');
    if (avatarNameProfit) {
      avatarNameProfit.textContent = initials || 'U';
    }
  };

  // Update Dashboard View statistics
  const updateDashboardStats = () => {
    dashNetGoal.textContent = `${currentProfileData.currencySymbol} ${parseFloat(currentProfileData.desired_net_profit).toFixed(2)}`;
    dashNetGoalUnit.textContent = `/ ${currentProfileData.distanceUnit}`;

    dashFuelCost.textContent = `${currentProfileData.currencySymbol} ${parseFloat(currentProfileData.fuel_cost).toFixed(2)}`;
    dashFuelCostUnit.textContent = `/ Litro`;

    dashEfficiency.textContent = `${parseFloat(currentProfileData.fuel_efficiency).toFixed(1)}`;
    dashEfficiencyUnit.textContent = `${currentProfileData.distanceUnit} / L`;

    // Update semaforo UI elements based on state
    const statusCard = document.getElementById('statusCard');
    const statusDot = document.getElementById('statusDot');
    const statusDesc = document.getElementById('statusDesc');

    if (currentProfileData.semaforo_active) {
      statusCard.classList.remove('inactive');
      statusDot.classList.remove('inactive');
      statusDot.classList.add('green');
      statusDesc.textContent = 'Operación Altamente Rentable';
      toggleSemaforoBtn.classList.remove('inactive');
      toggleSemaforoBtn.classList.add('active');
      toggleSemaforoBtn.querySelector('span').textContent = 'Apagar Semáforo';
    } else {
      statusCard.classList.add('inactive');
      statusDot.classList.add('inactive');
      statusDot.classList.remove('green');
      statusDesc.textContent = 'Semáforo Apagado / Inactivo';
      toggleSemaforoBtn.classList.add('inactive');
      toggleSemaforoBtn.classList.remove('active');
      toggleSemaforoBtn.querySelector('span').textContent = 'Encender Semáforo';
    }

    // Render active app status if semaforo is active and active_app is set
    if (currentProfileData.semaforo_active && currentProfileData.active_app) {
      dashActiveAppContainer.style.display = 'flex';
      dashActiveAppName.textContent = currentProfileData.active_app;
    } else {
      dashActiveAppContainer.style.display = 'none';
    }

    if (window.lucide) {
      window.lucide.createIcons();
    }

    loadProfileIntoSettings();
  };

  // --- Login Form Submit ---
  loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    let isValid = true;

    const email = document.getElementById('loginEmail');
    const password = document.getElementById('loginPassword');

    // Validation
    if (!email.value.trim()) {
      setError('loginEmail', 'El correo electrónico es requerido.');
      isValid = false;
    } else if (!validateEmail(email.value.trim())) {
      setError('loginEmail', 'Ingresa un correo electrónico válido.');
      isValid = false;
    } else {
      clearError('loginEmail');
    }

    if (!password.value) {
      setError('loginPassword', 'La contraseña es requerida.');
      isValid = false;
    } else if (password.value.length < 8) {
      setError('loginPassword', 'La contraseña debe tener al menos 8 caracteres.');
      isValid = false;
    } else {
      clearError('loginPassword');
    }

    if (isValid) {
      const submitBtn = document.getElementById('loginSubmitBtn');
      const originalHtml = submitBtn.innerHTML;
      submitBtn.disabled = true;

      // Fallback Mode if Supabase is not configured
      if (!isConfigured) {
        submitBtn.innerHTML = `<span>Iniciando (Simulado)...</span> <div class="spinner"></div>`;
        setTimeout(() => {
          submitBtn.disabled = false;
          submitBtn.innerHTML = originalHtml;

          currentSessionUser = { id: 'demo-user-123', email: email.value.trim() };
          updateProfileDataLocally({
            full_name: 'Jorge Freire (Demo)',
            country: 'CL',
            phone: '+56 975333778',
            currency: 'CLP',
            distance_unit: 'km',
            fuel_cost: 0,
            fuel_efficiency: 0,
            desired_net_profit: 0,
            semaforo_active: true,
            settings_configured: false
          });

          setupProfitabilityLabels();
          switchView(profitabilityView);
        }, 1200);
        return;
      }

      submitBtn.innerHTML = `<span>Iniciando sesión...</span> <div class="spinner"></div>`;
      try {
        const { data, error } = await supabase.auth.signInWithPassword({
          email: email.value.trim(),
          password: password.value,
        });

        if (error) throw error;
        if (!data.user) throw new Error('Usuario inválido.');

        currentSessionUser = data.user;

        // Fetch additional profile data
        const { data: profile, error: profileError } = await supabase
          .from('profiles')
          .select('*')
          .eq('id', data.user.id)
          .single();

        if (profileError) {
          updateProfileDataLocally({
            id: data.user.id,
            full_name: 'Usuario Verdi',
            country: 'CL',
            currency: 'CLP',
            distance_unit: 'km'
          });
        } else {
          updateProfileDataLocally(profile);
        }

        setupProfitabilityLabels();

        // Redirect to profitability configuration if not done yet, otherwise dashboard
        if (userProfile && userProfile.settings_configured) {
          updateDashboardStats();
          switchView(dashboardView);
        } else {
          switchView(profitabilityView);
        }

      } catch (err) {
        console.error(err);
        setError('loginPassword', err.message || 'Error al iniciar sesión.');
      } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalHtml;
      }
    }
  });

  // --- Register Form Submit ---
  registerForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    let isValid = true;

    const name = document.getElementById('registerName');
    const email = document.getElementById('registerEmail');
    const country = document.getElementById('registerCountry');
    const phone = document.getElementById('registerPhone');
    const password = document.getElementById('registerPassword');
    const confirmPassword = document.getElementById('registerConfirmPassword');

    // Validation
    if (!name.value.trim()) {
      setError('registerName', 'El nombre completo es requerido.');
      isValid = false;
    } else {
      clearError('registerName');
    }

    if (!email.value.trim()) {
      setError('registerEmail', 'El correo electrónico es requerido.');
      isValid = false;
    } else if (!validateEmail(email.value.trim())) {
      setError('registerEmail', 'Ingresa un correo electrónico válido.');
      isValid = false;
    } else {
      clearError('registerEmail');
    }

    if (!country.value) {
      setError('registerCountry', 'Por favor selecciona tu país de procedencia.');
      isValid = false;
    } else {
      clearError('registerCountry');
    }

    if (!phone.value.trim()) {
      setError('registerPhone', 'El número de celular es requerido.');
      isValid = false;
    } else if (!/^\d{7,14}$/.test(phone.value.replace(/[\s-]/g, ''))) {
      setError('registerPhone', 'Ingresa un número celular válido (solo números, entre 7 y 14 dígitos).');
      isValid = false;
    } else {
      clearError('registerPhone');
    }

    if (!password.value) {
      setError('registerPassword', 'La contraseña es requerida.');
      isValid = false;
    } else if (password.value.length < 8) {
      setError('registerPassword', 'La contraseña debe tener al menos 8 caracteres.');
      isValid = false;
    } else {
      clearError('registerPassword');
    }

    if (!confirmPassword.value) {
      setError('registerConfirmPassword', 'Debes confirmar la contraseña.');
      isValid = false;
    } else if (confirmPassword.value !== password.value) {
      setError('registerConfirmPassword', 'Las contraseñas no coinciden.');
      isValid = false;
    } else {
      clearError('registerConfirmPassword');
    }

    if (isValid) {
      const selectedConfig = countryConfig[country.value];
      const fullPhone = `${selectedConfig.prefix} ${phone.value.trim()}`;
      
      const submitBtn = document.getElementById('registerSubmitBtn');
      const originalHtml = submitBtn.innerHTML;
      submitBtn.disabled = true;

      // Fallback Mode if Supabase is not configured
      if (!isConfigured) {
        submitBtn.innerHTML = `<span>Registrando (Simulado)...</span> <div class="spinner"></div>`;
        setTimeout(() => {
          alert(`¡Modo Demo Activo (Supabase no configurado)!\nRegistro simulado con éxito.\nAhora inicia sesión con tus credenciales.`);
          submitBtn.disabled = false;
          submitBtn.innerHTML = originalHtml;
          
          registerForm.reset();
          autoConfigPanel.classList.add('hidden');
          phonePrefix.textContent = '+--';
          switchView(loginView);
        }, 1200);
        return;
      }

      submitBtn.innerHTML = `<span>Creando cuenta...</span> <div class="spinner"></div>`;
      try {
        const { data, error: signUpError } = await supabase.auth.signUp({
          email: email.value.trim(),
          password: password.value,
        });

        if (signUpError) throw signUpError;
        if (!data.user) throw new Error('No se pudo crear el usuario.');

        const { error: profileError } = await supabase
          .from('profiles')
          .insert([
            {
              id: data.user.id,
              full_name: name.value.trim(),
              country: country.value,
              currency: selectedConfig.currency,
              distance_unit: selectedConfig.unit,
              phone: fullPhone,
            }
          ]);

        if (profileError) throw profileError;

        alert(`¡Registro Exitoso!\nPor favor, verifica tu correo electrónico si tienes activada la confirmación de email.`);
        
        registerForm.reset();
        autoConfigPanel.classList.add('hidden');
        phonePrefix.textContent = '+--';
        switchView(loginView);

      } catch (err) {
        console.error(err);
        setError('registerPassword', err.message || 'Error al registrar el usuario.');
      } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalHtml;
      }
    }
  });

  // --- Profitability Settings Form Submit ---
  profitabilityForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    let isValid = true;

    const fuelCost = document.getElementById('profitFuelCost');
    const efficiency = document.getElementById('profitEfficiency');
    const netGoal = document.getElementById('profitNetGoal');
    const activeApp = document.getElementById('profitActiveApp');

    // Validation
    if (!fuelCost.value || parseFloat(fuelCost.value) <= 0) {
      setError('profitFuelCost', 'Por favor ingresa un costo de combustible válido mayor a 0.');
      isValid = false;
    } else {
      clearError('profitFuelCost');
    }

    if (!efficiency.value || parseFloat(efficiency.value) <= 0) {
      setError('profitEfficiency', 'Por favor ingresa un rendimiento de auto válido mayor a 0.');
      isValid = false;
    } else {
      clearError('profitEfficiency');
    }

    if (!netGoal.value || parseFloat(netGoal.value) <= 0) {
      setError('profitNetGoal', 'La ganancia neta deseada es obligatoria y debe ser mayor a 0.');
      isValid = false;
    } else {
      clearError('profitNetGoal');
    }

    if (!activeApp.value) {
      setError('profitActiveApp', 'Por favor selecciona la aplicación en la que trabajarás hoy.');
      isValid = false;
    } else {
      clearError('profitActiveApp');
    }

    if (isValid && currentSessionUser) {
      const submitBtn = document.getElementById('profitSubmitBtn');
      const originalHtml = submitBtn.innerHTML;
      submitBtn.disabled = true;

      // Fallback Mode if Supabase is not configured
      if (!isConfigured) {
        submitBtn.innerHTML = `<span>Guardando y activando...</span> <div class="spinner"></div>`;
        setTimeout(() => {
          submitBtn.disabled = false;
          submitBtn.innerHTML = originalHtml;

          // Update local variables
          currentProfileData.fuel_cost = parseFloat(fuelCost.value);
          currentProfileData.fuel_efficiency = parseFloat(efficiency.value);
          currentProfileData.desired_net_profit = parseFloat(netGoal.value);
          currentProfileData.active_app = activeApp.value;
          currentProfileData.semaforo_active = true;
          currentProfileData.settings_configured = true;
          if (userProfile) userProfile.settings_configured = true;

          alert(
            `¡Parámetros de Rentabilidad Configurados (Modo Demo)!\n\n` +
            `- Aplicación Activa: ${currentProfileData.active_app}\n` +
            `- Costo Combustible: ${currentProfileData.currencySymbol}${fuelCost.value}/L\n` +
            `- Rendimiento: ${efficiency.value} ${currentProfileData.distanceUnit}/L\n` +
            `- Ganancia Neta Deseada: ${currentProfileData.currencySymbol}${netGoal.value}/${currentProfileData.distanceUnit}\n\n` +
            `¡Listo! Tu asistente de rentabilidad y el semáforo están activos.`
          );
          
          profitabilityForm.reset();
          updateDashboardStats();
          switchView(dashboardView);
        }, 1200);
        return;
      }

      submitBtn.innerHTML = `<span>Activando semáforo...</span> <div class="spinner"></div>`;
      try {
        let updatePayload = {
          fuel_cost: parseFloat(fuelCost.value),
          fuel_efficiency: parseFloat(efficiency.value),
          desired_net_profit: parseFloat(netGoal.value),
          settings_configured: true,
          semaforo_active: true,
          active_app: activeApp.value
        };

        let { error } = await supabase
          .from('profiles')
          .update(updatePayload)
          .eq('id', currentSessionUser.id);

        // Graceful retry if active_app column doesn't exist in Supabase profiles yet
        if (error && error.message && error.message.includes('active_app')) {
          console.warn('active_app column does not exist on profiles table, retrying without it...');
          delete updatePayload.active_app;
          const { error: retryError } = await supabase
            .from('profiles')
            .update(updatePayload)
            .eq('id', currentSessionUser.id);
          error = retryError;
        }

        if (error) throw error;

        // Update local session properties
        currentProfileData.fuel_cost = parseFloat(fuelCost.value);
        currentProfileData.fuel_efficiency = parseFloat(efficiency.value);
        currentProfileData.desired_net_profit = parseFloat(netGoal.value);
        currentProfileData.active_app = activeApp.value;
        currentProfileData.semaforo_active = true;
        currentProfileData.settings_configured = true;
        if (userProfile) userProfile.settings_configured = true;

        alert(`¡Configuración guardada y semáforo de rentabilidad activado con éxito!\nTrabajando en: ${activeApp.value}`);
        
        profitabilityForm.reset();
        updateDashboardStats();
        switchView(dashboardView);
      } catch (err) {
        console.error(err);
        setError('profitNetGoal', err.message || 'Error al guardar la configuración.');
      } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalHtml;
      }
    }
  });

  // --- Profile Settings Form Submit ---
  settingsForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    let isValid = true;

    const country = settingsCountry;
    const phone = settingsPhone;
    const oldPassword = document.getElementById('settingsOldPassword');
    const password = document.getElementById('settingsPassword');
    const confirmPassword = document.getElementById('settingsConfirmPassword');

    // Validation
    if (!country.value) {
      setError('settingsCountry', 'Por favor selecciona tu país de procedencia.');
      isValid = false;
    } else {
      clearError('settingsCountry');
    }

    if (!phone.value.trim()) {
      setError('settingsPhone', 'El número de celular es requerido.');
      isValid = false;
    } else if (!/^\d{7,14}$/.test(phone.value.replace(/[\s-]/g, ''))) {
      setError('settingsPhone', 'Ingresa un número celular válido (solo números, entre 7 y 14 dígitos).');
      isValid = false;
    } else {
      clearError('settingsPhone');
    }

    // Three-field Password validation
    const hasPasswordChange = oldPassword.value || password.value || confirmPassword.value;
    if (hasPasswordChange) {
      if (!oldPassword.value) {
        setError('settingsOldPassword', 'Debes ingresar tu contraseña anterior para realizar el cambio.');
        isValid = false;
      } else {
        clearError('settingsOldPassword');
      }

      if (!password.value) {
        setError('settingsPassword', 'Ingresa la nueva contraseña.');
        isValid = false;
      } else if (password.value.length < 8) {
        setError('settingsPassword', 'La nueva contraseña debe tener al menos 8 caracteres.');
        isValid = false;
      } else {
        clearError('settingsPassword');
      }

      if (!confirmPassword.value) {
        setError('settingsConfirmPassword', 'Repite la nueva contraseña.');
        isValid = false;
      } else if (confirmPassword.value !== password.value) {
        setError('settingsConfirmPassword', 'Las contraseñas nuevas no coinciden.');
        isValid = false;
      } else {
        clearError('settingsConfirmPassword');
      }
    } else {
      clearError('settingsOldPassword');
      clearError('settingsPassword');
      clearError('settingsConfirmPassword');
    }

    if (isValid && currentSessionUser) {
      const submitBtn = document.getElementById('settingsSubmitBtn');
      const originalHtml = submitBtn.innerHTML;
      submitBtn.disabled = true;
      submitBtn.innerHTML = `<span>Guardando cambios...</span> <div class="spinner"></div>`;

      const selectedConfig = countryConfig[country.value];
      const fullPhone = `${selectedConfig.prefix} ${phone.value.trim()}`;

      // Fallback Mode if Supabase is not configured
      if (!isConfigured) {
        setTimeout(() => {
          submitBtn.disabled = false;
          submitBtn.innerHTML = originalHtml;

          // Update local details
          currentProfileData.country = country.value;
          currentProfileData.phone = fullPhone;
          currentProfileData.currency = selectedConfig.currency;
          currentProfileData.currencySymbol = selectedConfig.currencySymbol;
          currentProfileData.distanceUnit = selectedConfig.unit;
          currentProfileData.distanceUnitLabel = selectedConfig.unitLabel;

          let passChangeMsg = '';
          if (password.value) {
            passChangeMsg = '\n- Contraseña actualizada exitosamente (Simulado).';
          }

          alert(`¡Configuración de Perfil Actualizada (Modo Demo)!\n\n- País: ${country.value}\n- Celular: ${fullPhone}\n- Divisa: ${selectedConfig.currency}\n- Unidad: ${selectedConfig.unitLabel}${passChangeMsg}`);
          
          updateDashboardStats();
          
          // Pre-fill the profitability form with current values and update units
          document.getElementById('profitFuelCost').value = currentProfileData.fuel_cost || '';
          document.getElementById('profitEfficiency').value = currentProfileData.fuel_efficiency || '';
          document.getElementById('profitNetGoal').value = currentProfileData.desired_net_profit || '';
          menuHomeBtn.classList.add('active');
          menuSettingsBtn.classList.remove('active');
          menuAppsBtn.classList.remove('active');
          setupProfitabilityLabels();
          
          switchView(profitabilityView);
        }, 1200);
        return;
      }

      try {
        // 1. Verify old password first
        if (password.value) {
          const { error: verifyError } = await supabase.auth.signInWithPassword({
            email: currentSessionUser.email,
            password: oldPassword.value
          });
          if (verifyError) {
            setError('settingsOldPassword', 'La contraseña anterior es incorrecta.');
            throw new Error('La contraseña anterior es incorrecta.');
          }
        }

        // 2. Update Profile Fields in Database
        const { error: profileError } = await supabase
          .from('profiles')
          .update({
            country: country.value,
            phone: fullPhone,
            currency: selectedConfig.currency,
            distance_unit: selectedConfig.unit
          })
          .eq('id', currentSessionUser.id);

        if (profileError) throw profileError;

        // 3. Update Password if specified
        if (password.value) {
          const { error: passwordError } = await supabase.auth.updateUser({
            password: password.value
          });
          if (passwordError) throw passwordError;
        }

        // Update local session details
        currentProfileData.country = country.value;
        currentProfileData.phone = fullPhone;
        currentProfileData.currency = selectedConfig.currency;
        currentProfileData.currencySymbol = selectedConfig.currencySymbol;
        currentProfileData.distanceUnit = selectedConfig.unit;
        currentProfileData.distanceUnitLabel = selectedConfig.unitLabel;

        alert('¡Cambios guardados con éxito en Supabase!');
        
        updateDashboardStats();
        
        // Pre-fill the profitability form with current values and update units
        document.getElementById('profitFuelCost').value = currentProfileData.fuel_cost || '';
        document.getElementById('profitEfficiency').value = currentProfileData.fuel_efficiency || '';
        document.getElementById('profitNetGoal').value = currentProfileData.desired_net_profit || '';
        menuHomeBtn.classList.add('active');
        menuSettingsBtn.classList.remove('active');
        menuAppsBtn.classList.remove('active');
        setupProfitabilityLabels();
        
        switchView(profitabilityView);

      } catch (err) {
        console.error(err);
        if (err.message !== 'La contraseña anterior es incorrecta.') {
          setError('settingsPassword', err.message || 'Error al guardar cambios.');
        }
      } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalHtml;
      }
    }
  });

  // Real-time input validation clearers
  const inputsToTrack = [
    'loginEmail', 'loginPassword', 
    'registerName', 'registerEmail', 'registerPhone', 'registerPassword', 'registerConfirmPassword',
    'profitFuelCost', 'profitEfficiency', 'profitNetGoal', 'profitActiveApp',
    'settingsPhone', 'settingsOldPassword', 'settingsPassword', 'settingsConfirmPassword'
  ];
  inputsToTrack.forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('input', () => clearError(id));
      el.addEventListener('change', () => clearError(id));
    }
  });
});

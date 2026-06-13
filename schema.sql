-- 1. Crear la tabla de perfiles (profiles)
CREATE TABLE IF NOT EXISTS public.profiles (
  id UUID REFERENCES auth.users ON DELETE CASCADE PRIMARY KEY,
  full_name TEXT NOT NULL,
  country TEXT NOT NULL,
  currency TEXT NOT NULL,
  distance_unit TEXT NOT NULL,
  phone TEXT,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL,
  
  -- Campos de Rentabilidad
  fuel_cost NUMERIC DEFAULT 0,
  fuel_efficiency NUMERIC DEFAULT 0,
  desired_net_profit NUMERIC DEFAULT 0,
  settings_configured BOOLEAN DEFAULT FALSE,
  semaforo_active BOOLEAN DEFAULT TRUE,
  connected_apps TEXT[] DEFAULT '{}',
  active_app TEXT
);

-- 2. Habilitar la seguridad a nivel de fila (Row Level Security - RLS)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- 3. Crear políticas de seguridad
-- Permitir que los usuarios lean su propio perfil
CREATE POLICY "Permitir lectura individual" 
  ON public.profiles 
  FOR SELECT 
  USING (auth.uid() = id);

-- Permitir que los usuarios inserten su propio perfil
CREATE POLICY "Permitir inserción individual" 
  ON public.profiles 
  FOR INSERT 
  WITH CHECK (auth.uid() = id);

-- Permitir que los usuarios actualicen su propio perfil
CREATE POLICY "Permitir actualización individual" 
  ON public.profiles 
  FOR UPDATE 
  USING (auth.uid() = id);

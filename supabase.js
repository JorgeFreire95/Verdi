import { createClient } from '@supabase/supabase-js';

const rawUrl = import.meta.env.VITE_SUPABASE_URL || '';
const rawKey = import.meta.env.VITE_SUPABASE_ANON_KEY || '';

const isConfigured = rawUrl && rawKey && !rawUrl.includes('your-project-id');

if (!isConfigured) {
  console.warn(
    'Supabase credentials are not configured correctly. ' +
    'Please copy .env.example to .env and populate it with your real Supabase URL and Anon Key.'
  );
}

// Use placeholders if not configured to prevent startup crashes
const supabaseUrl = isConfigured ? rawUrl : 'https://placeholder-project-id.supabase.co';
const supabaseAnonKey = isConfigured ? rawKey : 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.placeholder';

export const supabase = createClient(supabaseUrl, supabaseAnonKey);
export { isConfigured };

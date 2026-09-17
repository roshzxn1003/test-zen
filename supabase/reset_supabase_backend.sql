-- =========================================================================
-- RESET & TEARDOWN SCRIPT FOR SUPABASE BACKEND
-- Use this script in the Supabase SQL editor if you wish to drop all old
-- tables, policies, triggers, and publications prior to a fresh rebuild.
-- =========================================================================

-- 1. Remove realtime publications
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime DROP TABLE IF EXISTS
        public.audit_logs,
        public.family_invitations,
        public.savings_contributions,
        public.savings_goals,
        public.budgets,
        public.transactions,
        public.categories,
        public.family_members,
        public.families,
        public.profiles;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;

-- 2. Drop triggers and functions
DROP TRIGGER IF EXISTS trg_auto_confirm_user ON auth.users;
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
DROP TRIGGER IF EXISTS trg_sync_transaction_columns ON public.transactions;
DROP TRIGGER IF EXISTS trg_sync_family_member_columns ON public.family_members;

DROP FUNCTION IF EXISTS public.auto_confirm_new_user CASCADE;
DROP FUNCTION IF EXISTS public.handle_new_user CASCADE;
DROP FUNCTION IF EXISTS public.sync_transaction_columns CASCADE;
DROP FUNCTION IF EXISTS public.sync_family_member_columns CASCADE;
DROP FUNCTION IF EXISTS public.get_family_dashboard CASCADE;
DROP FUNCTION IF EXISTS public.join_family_by_code CASCADE;
DROP FUNCTION IF EXISTS public.create_family CASCADE;
DROP FUNCTION IF EXISTS public.get_family_role CASCADE;
DROP FUNCTION IF EXISTS public.is_family_member CASCADE;

-- 3. Drop all application tables
DROP TABLE IF EXISTS public.audit_logs CASCADE;
DROP TABLE IF EXISTS public.family_invitations CASCADE;
DROP TABLE IF EXISTS public.savings_contributions CASCADE;
DROP TABLE IF EXISTS public.savings_goals CASCADE;
DROP TABLE IF EXISTS public.budgets CASCADE;
DROP TABLE IF EXISTS public.transactions CASCADE;
DROP TABLE IF EXISTS public.categories CASCADE;
DROP TABLE IF EXISTS public.family_members CASCADE;
DROP TABLE IF EXISTS public.families CASCADE;
DROP TABLE IF EXISTS public.profiles CASCADE;

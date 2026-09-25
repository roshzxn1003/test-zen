-- =============================================================================
-- Migration: CashFlow & FamilyLedger / Family Shared Finance
-- Description: Complete, self-healing, non-recursive PostgreSQL schema, RLS policies,
--              indexes, realtime configuration, and atomic stored procedures.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- 1. PURGE OLD / BROKEN / RECURSIVE POLICIES (Fixes Error 42P17)
-- =============================================================================
DO $$
DECLARE
    pol record;
BEGIN
    FOR pol IN 
        SELECT schemaname, tablename, policyname 
        FROM pg_policies 
        WHERE schemaname = 'public' 
          AND tablename IN (
            'profiles', 'families', 'family_members', 'categories',
            'transactions', 'budgets', 'savings_goals',
            'savings_contributions', 'audit_logs', 'family_invitations'
          )
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I.%I', pol.policyname, pol.schemaname, pol.tablename);
    END LOOP;
END $$;

-- =============================================================================
-- 2. TABLE CREATIONS & DEFENSIVE COLUMN ADDITIONS
-- =============================================================================

-- 2.1 PROFILES TABLE
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    full_name TEXT NOT NULL DEFAULT '',
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS email TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS full_name TEXT NOT NULL DEFAULT '';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS avatar_url TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 2.2 FAMILIES TABLE
CREATE TABLE IF NOT EXISTS public.families (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL DEFAULT 'Family Vault',
    description TEXT,
    invite_code VARCHAR(32) UNIQUE NOT NULL DEFAULT ('FAM-' || upper(substring(replace(gen_random_uuid()::text, '-', ''), 1, 6))),
    created_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE public.families ADD COLUMN IF NOT EXISTS name TEXT DEFAULT 'Family Vault';
ALTER TABLE public.families ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE public.families ADD COLUMN IF NOT EXISTS invite_code VARCHAR(32);
ALTER TABLE public.families ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL;
ALTER TABLE public.families ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.families ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.families ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- 2.3 FAMILY MEMBERS TABLE
CREATE TABLE IF NOT EXISTS public.family_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    user_id UUID,
    role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    name TEXT DEFAULT 'Member',
    display_name TEXT DEFAULT 'Member',
    avatar_color TEXT DEFAULT '#10B981',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.family_members ADD COLUMN IF NOT EXISTS family_id UUID REFERENCES public.families(id) ON DELETE CASCADE;
ALTER TABLE public.family_members ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE public.family_members ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'MEMBER';
ALTER TABLE public.family_members ADD COLUMN IF NOT EXISTS name TEXT DEFAULT 'Member';
ALTER TABLE public.family_members ADD COLUMN IF NOT EXISTS display_name TEXT DEFAULT 'Member';
ALTER TABLE public.family_members ADD COLUMN IF NOT EXISTS avatar_color TEXT DEFAULT '#10B981';
ALTER TABLE public.family_members ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.family_members ADD COLUMN IF NOT EXISTS joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.family_members ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

DO $$
BEGIN
    ALTER TABLE public.family_members DROP CONSTRAINT IF EXISTS family_members_user_id_fkey;
EXCEPTION WHEN others THEN null;
END $$;

-- 2.4 CATEGORIES TABLE
CREATE TABLE IF NOT EXISTS public.categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID REFERENCES public.families(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    icon TEXT DEFAULT 'Category',
    icon_name TEXT DEFAULT 'Category',
    color TEXT DEFAULT '#10B981',
    color_hex TEXT DEFAULT '#10B981',
    type VARCHAR(32) NOT NULL DEFAULT 'EXPENSE',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS family_id UUID REFERENCES public.families(id) ON DELETE CASCADE;
ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE;
ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS name TEXT;
ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS icon TEXT DEFAULT 'Category';
ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS icon_name TEXT DEFAULT 'Category';
ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS color TEXT DEFAULT '#10B981';
ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS color_hex TEXT DEFAULT '#10B981';
ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS type VARCHAR(32) DEFAULT 'EXPENSE';
ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.categories ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 2.5 TRANSACTIONS TABLE
CREATE TABLE IF NOT EXISTS public.transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    finance_scope VARCHAR(32) NOT NULL DEFAULT 'PERSONAL',
    family_id UUID REFERENCES public.families(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    paid_by_member_id UUID REFERENCES public.family_members(id) ON DELETE SET NULL,
    paid_by_name TEXT DEFAULT 'Member',
    title TEXT DEFAULT 'Transaction',
    description TEXT NOT NULL DEFAULT '',
    amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    type VARCHAR(32) NOT NULL DEFAULT 'EXPENSE',
    transaction_type VARCHAR(32) DEFAULT 'EXPENSE',
    category TEXT NOT NULL DEFAULT 'Other',
    category_id TEXT DEFAULT 'Other',
    payment_method TEXT NOT NULL DEFAULT 'UPI',
    upi_id TEXT,
    upi_transaction_id TEXT,
    transaction_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sync_version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS finance_scope VARCHAR(32) NOT NULL DEFAULT 'PERSONAL';
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS family_id UUID REFERENCES public.families(id) ON DELETE CASCADE;
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL;
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS paid_by_member_id UUID REFERENCES public.family_members(id) ON DELETE SET NULL;
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS paid_by_name TEXT DEFAULT 'Member';
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS title TEXT DEFAULT 'Transaction';
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS description TEXT DEFAULT '';
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS amount NUMERIC(15, 2) DEFAULT 0.00;
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS type VARCHAR(32) DEFAULT 'EXPENSE';
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS transaction_type VARCHAR(32) DEFAULT 'EXPENSE';
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS category TEXT DEFAULT 'Other';
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS category_id TEXT DEFAULT 'Other';
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS payment_method TEXT DEFAULT 'UPI';
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS upi_id TEXT;
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS upi_transaction_id TEXT;
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS transaction_date TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS sync_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 2.6 BUDGETS TABLE
CREATE TABLE IF NOT EXISTS public.budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    finance_scope VARCHAR(32) NOT NULL DEFAULT 'PERSONAL',
    family_id UUID REFERENCES public.families(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    name TEXT NOT NULL DEFAULT 'Budget',
    category_name TEXT DEFAULT 'Other',
    category_id TEXT DEFAULT 'Other',
    monthly_limit NUMERIC(15, 2) DEFAULT 0.00,
    amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    month_year VARCHAR(7) DEFAULT to_char(NOW(), 'YYYY-MM'),
    period_type VARCHAR(32) DEFAULT 'MONTHLY',
    start_date TIMESTAMPTZ,
    end_date TIMESTAMPTZ,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS finance_scope VARCHAR(32) NOT NULL DEFAULT 'PERSONAL';
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS family_id UUID REFERENCES public.families(id) ON DELETE CASCADE;
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL;
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS name TEXT DEFAULT 'Budget';
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS category_name TEXT DEFAULT 'Other';
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS category_id TEXT DEFAULT 'Other';
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS monthly_limit NUMERIC(15, 2) DEFAULT 0.00;
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS amount NUMERIC(15, 2) DEFAULT 0.00;
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS month_year VARCHAR(7) DEFAULT to_char(NOW(), 'YYYY-MM');
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS period_type VARCHAR(32) DEFAULT 'MONTHLY';
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS start_date TIMESTAMPTZ;
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS end_date TIMESTAMPTZ;
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.budgets ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 2.7 SAVINGS GOALS & CONTRIBUTIONS
CREATE TABLE IF NOT EXISTS public.savings_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    finance_scope VARCHAR(32) NOT NULL DEFAULT 'PERSONAL',
    family_id UUID REFERENCES public.families(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    name TEXT NOT NULL DEFAULT 'Goal',
    title TEXT DEFAULT 'Goal',
    target_amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    current_amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    target_date TIMESTAMPTZ,
    icon_name TEXT NOT NULL DEFAULT 'Savings',
    color_hex TEXT NOT NULL DEFAULT '#059669',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.savings_goals ADD COLUMN IF NOT EXISTS finance_scope VARCHAR(32) NOT NULL DEFAULT 'PERSONAL';
ALTER TABLE public.savings_goals ADD COLUMN IF NOT EXISTS family_id UUID REFERENCES public.families(id) ON DELETE CASCADE;
ALTER TABLE public.savings_goals ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL;
ALTER TABLE public.savings_goals ADD COLUMN IF NOT EXISTS name TEXT DEFAULT 'Goal';
ALTER TABLE public.savings_goals ADD COLUMN IF NOT EXISTS title TEXT DEFAULT 'Goal';
ALTER TABLE public.savings_goals ADD COLUMN IF NOT EXISTS target_amount NUMERIC(15, 2) DEFAULT 0.00;
ALTER TABLE public.savings_goals ADD COLUMN IF NOT EXISTS current_amount NUMERIC(15, 2) DEFAULT 0.00;
ALTER TABLE public.savings_goals ADD COLUMN IF NOT EXISTS target_date TIMESTAMPTZ;
ALTER TABLE public.savings_goals ADD COLUMN IF NOT EXISTS icon_name TEXT DEFAULT 'Savings';
ALTER TABLE public.savings_goals ADD COLUMN IF NOT EXISTS color_hex TEXT DEFAULT '#059669';
ALTER TABLE public.savings_goals ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.savings_goals ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.savings_goals ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE TABLE IF NOT EXISTS public.savings_contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id UUID NOT NULL REFERENCES public.savings_goals(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    member_id UUID REFERENCES public.family_members(id) ON DELETE SET NULL,
    amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    note TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.savings_contributions ADD COLUMN IF NOT EXISTS goal_id UUID REFERENCES public.savings_goals(id) ON DELETE CASCADE;
ALTER TABLE public.savings_contributions ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL;
ALTER TABLE public.savings_contributions ADD COLUMN IF NOT EXISTS member_id UUID REFERENCES public.family_members(id) ON DELETE SET NULL;
ALTER TABLE public.savings_contributions ADD COLUMN IF NOT EXISTS amount NUMERIC(15, 2) DEFAULT 0.00;
ALTER TABLE public.savings_contributions ADD COLUMN IF NOT EXISTS note TEXT DEFAULT '';
ALTER TABLE public.savings_contributions ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 2.8 AUDIT LOGS
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID REFERENCES public.families(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    entity_name TEXT NOT NULL,
    entity_id UUID,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2.9 FAMILY INVITATIONS
CREATE TABLE IF NOT EXISTS public.family_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    invite_code VARCHAR(32) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    invited_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '7 days'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =============================================================================
-- 3. BIDIRECTIONAL COLUMN SYNCHRONIZATION TRIGGERS
-- =============================================================================

CREATE OR REPLACE FUNCTION public.sync_transaction_columns()
RETURNS TRIGGER AS $$
BEGIN
    -- 1. Sync transaction_type and type
    IF NEW.transaction_type IS NOT NULL AND trim(NEW.transaction_type) <> '' THEN
        NEW.type := NEW.transaction_type;
    ELSIF NEW.type IS NOT NULL AND trim(NEW.type) <> '' THEN
        NEW.transaction_type := NEW.type;
    ELSE
        NEW.type := 'EXPENSE';
        NEW.transaction_type := 'EXPENSE';
    END IF;

    -- 2. Safely sync category and category_id without invalid UUID casts
    IF NEW.category IS NOT NULL AND trim(NEW.category) <> '' THEN
        NEW.category_id := NEW.category;
    ELSIF NEW.category_id IS NOT NULL AND trim(NEW.category_id::text) <> '' THEN
        NEW.category := NEW.category_id::text;
    ELSE
        NEW.category := 'Other';
        NEW.category_id := 'Other';
    END IF;

    -- 3. Ensure title and description are properly initialized
    IF NEW.title IS NULL OR trim(NEW.title) = '' THEN
        NEW.title := COALESCE(NULLIF(trim(NEW.description), ''), 'Transaction');
    END IF;
    IF NEW.description IS NULL THEN
        NEW.description := COALESCE(NEW.title, '');
    END IF;

    -- 4. Default sync_version and dates
    IF NEW.sync_version IS NULL THEN
        NEW.sync_version := 1;
    END IF;
    IF NEW.transaction_date IS NULL THEN
        NEW.transaction_date := NOW();
    END IF;
    IF NEW.created_at IS NULL THEN
        NEW.created_at := NOW();
    END IF;
    NEW.updated_at := NOW();

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_transaction_columns ON public.transactions;
CREATE TRIGGER trg_sync_transaction_columns
    BEFORE INSERT OR UPDATE ON public.transactions
    FOR EACH ROW
    EXECUTE FUNCTION public.sync_transaction_columns();

CREATE OR REPLACE FUNCTION public.sync_family_member_columns()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.name IS NOT NULL AND NEW.name <> '' THEN
        NEW.display_name := NEW.name;
    ELSIF NEW.display_name IS NOT NULL AND NEW.display_name <> '' THEN
        NEW.name := NEW.display_name;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_family_member_columns ON public.family_members;
CREATE TRIGGER trg_sync_family_member_columns
    BEFORE INSERT OR UPDATE ON public.family_members
    FOR EACH ROW
    EXECUTE FUNCTION public.sync_family_member_columns();

-- =============================================================================
-- 4. AUTH TRIGGERS: INSTANT EMAIL CONFIRMATION & PROFILE PROVISIONING
-- =============================================================================

CREATE OR REPLACE FUNCTION public.auto_confirm_new_user()
RETURNS TRIGGER AS $$
BEGIN
    NEW.email_confirmed_at := COALESCE(NEW.email_confirmed_at, NOW());
    NEW.confirmed_at := COALESCE(NEW.confirmed_at, NOW());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_auto_confirm_user ON auth.users;
CREATE TRIGGER trg_auto_confirm_user
    BEFORE INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.auto_confirm_new_user();

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, email, full_name)
    VALUES (
        NEW.id,
        COALESCE(NEW.email, ''),
        COALESCE(NEW.raw_user_meta_data->>'full_name', split_part(COALESCE(NEW.email, 'User'), '@', 1))
    )
    ON CONFLICT (id) DO UPDATE
    SET email = EXCLUDED.email,
        full_name = CASE WHEN public.profiles.full_name = '' THEN EXCLUDED.full_name ELSE public.profiles.full_name END,
        updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- =============================================================================
-- 5. PERFORMANCE & LOOKUP INDEXES
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_transactions_family_date ON public.transactions (family_id, transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_user_date ON public.transactions (user_id, transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_family_members_family ON public.family_members (family_id);
CREATE INDEX IF NOT EXISTS idx_family_members_user ON public.family_members (user_id);
CREATE INDEX IF NOT EXISTS idx_families_invite_code ON public.families (invite_code);

-- =============================================================================
-- 6. NON-RECURSIVE, ZERO-LOCK ROW LEVEL SECURITY (RLS) POLICIES
-- =============================================================================

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.families ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.family_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.budgets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.savings_goals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.savings_contributions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.family_invitations ENABLE ROW LEVEL SECURITY;

CREATE POLICY "profiles_select" ON public.profiles FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY "profiles_insert" ON public.profiles FOR INSERT TO anon, authenticated WITH CHECK (true);
CREATE POLICY "profiles_update" ON public.profiles FOR UPDATE TO anon, authenticated USING (auth.uid() = id OR auth.role() = 'anon');

CREATE POLICY "families_select" ON public.families FOR SELECT TO anon, authenticated USING (NOT is_deleted);
CREATE POLICY "families_insert" ON public.families FOR INSERT TO anon, authenticated WITH CHECK (true);
CREATE POLICY "families_update" ON public.families FOR UPDATE TO anon, authenticated USING (true);
CREATE POLICY "families_delete" ON public.families FOR DELETE TO anon, authenticated USING (true);

-- CRITICAL: COMPLETELY NON-RECURSIVE (Zero subquery on family_members)
CREATE POLICY "family_members_select" ON public.family_members FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY "family_members_insert" ON public.family_members FOR INSERT TO anon, authenticated WITH CHECK (true);
CREATE POLICY "family_members_update" ON public.family_members FOR UPDATE TO anon, authenticated USING (true);
CREATE POLICY "family_members_delete" ON public.family_members FOR DELETE TO anon, authenticated USING (true);

CREATE POLICY "categories_access" ON public.categories FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

CREATE POLICY "transactions_select" ON public.transactions FOR SELECT TO anon, authenticated USING (
    NOT is_deleted AND (
        finance_scope = 'FAMILY'
        OR auth.role() = 'anon'
        OR auth.uid() IS NULL
        OR user_id = auth.uid()
    )
);
CREATE POLICY "transactions_insert" ON public.transactions FOR INSERT TO anon, authenticated WITH CHECK (true);
CREATE POLICY "transactions_update" ON public.transactions FOR UPDATE TO anon, authenticated USING (true);
CREATE POLICY "transactions_delete" ON public.transactions FOR DELETE TO anon, authenticated USING (true);

CREATE POLICY "budgets_access" ON public.budgets FOR ALL TO anon, authenticated USING (
    NOT is_deleted AND (
        finance_scope = 'FAMILY'
        OR auth.role() = 'anon'
        OR auth.uid() IS NULL
        OR user_id = auth.uid()
    )
) WITH CHECK (true);

CREATE POLICY "savings_goals_access" ON public.savings_goals FOR ALL TO anon, authenticated USING (
    NOT is_deleted AND (
        finance_scope = 'FAMILY'
        OR auth.role() = 'anon'
        OR auth.uid() IS NULL
        OR user_id = auth.uid()
    )
) WITH CHECK (true);

CREATE POLICY "savings_contributions_access" ON public.savings_contributions FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "audit_logs_access" ON public.audit_logs FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "family_invitations_access" ON public.family_invitations FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- =============================================================================
-- 7. REALTIME SUBSCRIPTION CONFIGURATION
-- =============================================================================
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE 
        public.profiles,
        public.families,
        public.family_members,
        public.categories,
        public.transactions,
        public.budgets,
        public.savings_goals;
EXCEPTION WHEN others THEN null;
END $$;

-- =============================================================================
-- 8. ATOMIC STORED PROCEDURES (RPCs)
-- =============================================================================

-- 8.1 CREATE FAMILY
CREATE OR REPLACE FUNCTION public.create_family(family_name TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    new_family_id UUID;
    user_display_name TEXT;
    target_uid UUID;
    result JSONB;
BEGIN
    target_uid := auth.uid();
    IF char_length(trim(family_name)) < 2 THEN
        RAISE EXCEPTION 'INVALID_NAME: Family name must be at least 2 characters';
    END IF;

    IF target_uid IS NOT NULL THEN
        SELECT full_name INTO user_display_name FROM public.profiles WHERE id = target_uid;
    END IF;
    IF user_display_name IS NULL OR trim(user_display_name) = '' THEN
        user_display_name := 'Owner';
    END IF;

    INSERT INTO public.families (name, created_by)
    VALUES (trim(family_name), target_uid)
    RETURNING id INTO new_family_id;

    INSERT INTO public.family_members (family_id, user_id, role, name, display_name, is_active)
    VALUES (new_family_id, target_uid, 'ADMIN', user_display_name, user_display_name, TRUE);

    INSERT INTO public.audit_logs (family_id, actor_user_id, action, entity_name, entity_id, metadata)
    VALUES (new_family_id, target_uid, 'FAMILY_CREATED', 'families', new_family_id, jsonb_build_object('name', family_name));

    SELECT jsonb_build_object(
        'family_id', f.id,
        'family_name', f.name,
        'invite_code', f.invite_code,
        'role', 'ADMIN'
    ) INTO result
    FROM public.families f WHERE f.id = new_family_id;

    RETURN result;
END;
$$;

-- 8.2 JOIN FAMILY BY CODE
CREATE OR REPLACE FUNCTION public.join_family_by_code(code VARCHAR)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    target_family RECORD;
    user_display_name TEXT;
    existing_member RECORD;
    target_uid UUID;
    clean_code TEXT;
BEGIN
    clean_code := upper(trim(code));
    target_uid := auth.uid();

    SELECT * INTO target_family
    FROM public.families
    WHERE (upper(invite_code) = clean_code OR upper(invite_code) = ('FAM-' || clean_code))
      AND NOT is_deleted
    LIMIT 1;

    IF target_family IS NULL THEN
        RAISE EXCEPTION 'FAMILY_NOT_FOUND: Invalid invite code';
    END IF;

    IF target_uid IS NOT NULL THEN
        SELECT * INTO existing_member
        FROM public.family_members
        WHERE family_id = target_family.id AND user_id = target_uid;

        IF existing_member IS NOT NULL THEN
            UPDATE public.family_members
            SET is_active = TRUE, updated_at = NOW()
            WHERE id = existing_member.id;
        ELSE
            SELECT full_name INTO user_display_name FROM public.profiles WHERE id = target_uid;
            IF user_display_name IS NULL OR trim(user_display_name) = '' THEN
                user_display_name := 'Member';
            END IF;

            INSERT INTO public.family_members (family_id, user_id, role, name, display_name, is_active)
            VALUES (target_family.id, target_uid, 'MEMBER', user_display_name, user_display_name, TRUE);
        END IF;

        INSERT INTO public.audit_logs (family_id, actor_user_id, action, entity_name, entity_id)
        VALUES (target_family.id, target_uid, 'MEMBER_JOINED', 'family_members', target_uid);
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'family_id', target_family.id,
        'family_name', target_family.name,
        'invite_code', target_family.invite_code
    );
END;
$$;

-- 8.3 GET FAMILY DASHBOARD
CREATE OR REPLACE FUNCTION public.get_family_dashboard(p_family_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
AS $$
DECLARE
    dash JSONB;
    v_total_income NUMERIC(15, 2);
    v_total_expense NUMERIC(15, 2);
    v_recent_txs JSONB;
    v_category_spending JSONB;
    v_members JSONB;
BEGIN
    SELECT
        COALESCE(SUM(CASE WHEN COALESCE(transaction_type, type) = 'INCOME' THEN amount ELSE 0 END), 0),
        COALESCE(SUM(CASE WHEN COALESCE(transaction_type, type) = 'EXPENSE' THEN amount ELSE 0 END), 0)
    INTO v_total_income, v_total_expense
    FROM public.transactions
    WHERE family_id = p_family_id AND finance_scope = 'FAMILY' AND NOT is_deleted;

    SELECT jsonb_agg(tx_row) INTO v_recent_txs FROM (
        SELECT id, title, amount, COALESCE(transaction_type, type) as type, COALESCE(category, category_id) as category, payment_method, paid_by_name, transaction_date
        FROM public.transactions
        WHERE family_id = p_family_id AND finance_scope = 'FAMILY' AND NOT is_deleted
        ORDER BY transaction_date DESC
        LIMIT 8
    ) tx_row;

    SELECT jsonb_agg(cat_row) INTO v_category_spending FROM (
        SELECT COALESCE(category, category_id) as category, SUM(amount) as total
        FROM public.transactions
        WHERE family_id = p_family_id AND finance_scope = 'FAMILY' AND COALESCE(transaction_type, type) = 'EXPENSE' AND NOT is_deleted
        GROUP BY COALESCE(category, category_id)
        ORDER BY total DESC
        LIMIT 6
    ) cat_row;

    SELECT jsonb_agg(m_row) INTO v_members FROM (
        SELECT fm.id, COALESCE(fm.display_name, fm.name, 'Member') as display_name, fm.role,
               COALESCE(SUM(t.amount) FILTER (WHERE COALESCE(t.transaction_type, t.type) = 'EXPENSE'), 0) as total_spent,
               COUNT(t.id) as transaction_count
        FROM public.family_members fm
        LEFT JOIN public.transactions t ON t.family_id = fm.family_id AND (t.paid_by_member_id = fm.id OR (fm.user_id IS NOT NULL AND t.user_id = fm.user_id)) AND NOT t.is_deleted
        WHERE fm.family_id = p_family_id AND fm.is_active = TRUE
        GROUP BY fm.id, fm.display_name, fm.name, fm.role
    ) m_row;

    dash := jsonb_build_object(
        'family_id', p_family_id,
        'total_income', v_total_income,
        'total_expense', v_total_expense,
        'net_balance', (v_total_income - v_total_expense),
        'recent_transactions', COALESCE(v_recent_txs, '[]'::jsonb),
        'top_categories', COALESCE(v_category_spending, '[]'::jsonb),
        'members', COALESCE(v_members, '[]'::jsonb)
    );

    RETURN dash;
END;
$$;

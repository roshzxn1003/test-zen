-- =========================================================================
-- ZENITH FINANCE V2: SUPABASE POSTGRESQL SCHEMA & REALTIME SYNC ENGINE
-- (Self-Healing & Auto-Migrating for Existing / New Databases)
-- =========================================================================

-- 1. PROFILES TABLE & COLUMNS
CREATE TABLE IF NOT EXISTS profiles (
    id TEXT PRIMARY KEY
);
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS full_name TEXT NOT NULL DEFAULT 'Zenith Member';
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS email TEXT;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS avatar_url TEXT;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now());
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now());

-- 2. FAMILIES TABLE & COLUMNS
CREATE TABLE IF NOT EXISTS families (
    id TEXT PRIMARY KEY
);
ALTER TABLE families ADD COLUMN IF NOT EXISTS name TEXT NOT NULL DEFAULT 'Family Ledger';
ALTER TABLE families ADD COLUMN IF NOT EXISTS invite_code TEXT;
ALTER TABLE families ADD COLUMN IF NOT EXISTS created_by TEXT;
ALTER TABLE families ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now());
ALTER TABLE families ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now());

-- 3. FAMILY MEMBERS TABLE & COLUMNS
CREATE TABLE IF NOT EXISTS family_members (
    id TEXT PRIMARY KEY
);
ALTER TABLE family_members ADD COLUMN IF NOT EXISTS family_id TEXT NOT NULL DEFAULT '';
ALTER TABLE family_members ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT '';
ALTER TABLE family_members ADD COLUMN IF NOT EXISTS name TEXT;
ALTER TABLE family_members ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'MEMBER';
ALTER TABLE family_members ADD COLUMN IF NOT EXISTS joined_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now());
ALTER TABLE family_members ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now());

-- 4. CATEGORIES TABLE & COLUMNS
CREATE TABLE IF NOT EXISTS categories (
    id TEXT PRIMARY KEY
);
ALTER TABLE categories ADD COLUMN IF NOT EXISTS name TEXT NOT NULL DEFAULT 'Category';
ALTER TABLE categories ADD COLUMN IF NOT EXISTS icon TEXT NOT NULL DEFAULT 'Category';
ALTER TABLE categories ADD COLUMN IF NOT EXISTS color TEXT NOT NULL DEFAULT '#10B981';
ALTER TABLE categories ADD COLUMN IF NOT EXISTS user_id TEXT;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS family_id TEXT;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS is_default BOOLEAN DEFAULT false NOT NULL;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now());
ALTER TABLE categories ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now());

-- 5. TRANSACTIONS TABLE & COLUMNS
CREATE TABLE IF NOT EXISTS transactions (
    id TEXT PRIMARY KEY
);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT '';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS family_id TEXT;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS finance_scope TEXT NOT NULL DEFAULT 'PERSONAL';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS transaction_type TEXT NOT NULL DEFAULT 'EXPENSE';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS category_id TEXT;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS description TEXT NOT NULL DEFAULT '';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS payment_method TEXT NOT NULL DEFAULT 'UPI';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS upi_id TEXT;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS upi_transaction_id TEXT;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS transaction_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT timezone('utc'::text, now());
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now());
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now());
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT false NOT NULL;

-- 6. BUDGETS TABLE & COLUMNS
CREATE TABLE IF NOT EXISTS budgets (
    id TEXT PRIMARY KEY
);
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT '';
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS family_id TEXT;
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS finance_scope TEXT NOT NULL DEFAULT 'PERSONAL';
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS name TEXT NOT NULL DEFAULT 'Budget';
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS category_id TEXT;
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS period_type TEXT NOT NULL DEFAULT 'MONTHLY';
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS start_date TIMESTAMP WITH TIME ZONE;
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS end_date TIMESTAMP WITH TIME ZONE;
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now());
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now());
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT false NOT NULL;

-- 7. SAVINGS GOALS TABLE & COLUMNS
CREATE TABLE IF NOT EXISTS savings_goals (
    id TEXT PRIMARY KEY
);
ALTER TABLE savings_goals ADD COLUMN IF NOT EXISTS user_id TEXT NOT NULL DEFAULT '';
ALTER TABLE savings_goals ADD COLUMN IF NOT EXISTS family_id TEXT;
ALTER TABLE savings_goals ADD COLUMN IF NOT EXISTS finance_scope TEXT NOT NULL DEFAULT 'PERSONAL';
ALTER TABLE savings_goals ADD COLUMN IF NOT EXISTS name TEXT NOT NULL DEFAULT 'Goal';
ALTER TABLE savings_goals ADD COLUMN IF NOT EXISTS target_amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE savings_goals ADD COLUMN IF NOT EXISTS current_amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE savings_goals ADD COLUMN IF NOT EXISTS target_date TIMESTAMP WITH TIME ZONE;
ALTER TABLE savings_goals ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now());
ALTER TABLE savings_goals ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now());
ALTER TABLE savings_goals ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT false NOT NULL;

-- 8. PERFORMANCE INDEXES (Created safely)
CREATE INDEX IF NOT EXISTS idx_transactions_user ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_family ON transactions(family_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(transaction_date);
CREATE INDEX IF NOT EXISTS idx_family_members_family ON family_members(family_id);
CREATE INDEX IF NOT EXISTS idx_family_members_user ON family_members(user_id);
CREATE INDEX IF NOT EXISTS idx_families_invite_code ON families(invite_code);

-- 9. ROW LEVEL SECURITY (RLS) POLICIES
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE families ENABLE ROW LEVEL SECURITY;
ALTER TABLE family_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE budgets ENABLE ROW LEVEL SECURITY;
ALTER TABLE savings_goals ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    DROP POLICY IF EXISTS "Public access profiles" ON profiles;
    CREATE POLICY "Public access profiles" ON profiles FOR ALL USING (true) WITH CHECK (true);

    DROP POLICY IF EXISTS "Public access families" ON families;
    CREATE POLICY "Public access families" ON families FOR ALL USING (true) WITH CHECK (true);

    DROP POLICY IF EXISTS "Public access family_members" ON family_members;
    CREATE POLICY "Public access family_members" ON family_members FOR ALL USING (true) WITH CHECK (true);

    DROP POLICY IF EXISTS "Public access categories" ON categories;
    CREATE POLICY "Public access categories" ON categories FOR ALL USING (true) WITH CHECK (true);

    DROP POLICY IF EXISTS "Public access transactions" ON transactions;
    CREATE POLICY "Public access transactions" ON transactions FOR ALL USING (true) WITH CHECK (true);

    DROP POLICY IF EXISTS "Public access budgets" ON budgets;
    CREATE POLICY "Public access budgets" ON budgets FOR ALL USING (true) WITH CHECK (true);

    DROP POLICY IF EXISTS "Public access savings_goals" ON savings_goals;
    CREATE POLICY "Public access savings_goals" ON savings_goals FOR ALL USING (true) WITH CHECK (true);
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;

-- 10. REALTIME REPLICATION (Safe & Idempotent)
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE profiles, families, family_members, categories, transactions, budgets, savings_goals;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;

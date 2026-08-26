-- =========================================================================
-- ZENITH FINANCE V2: SUPABASE POSTGRESQL SCHEMA & REALTIME SYNC ENGINE
-- =========================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. PROFILES TABLE
CREATE TABLE IF NOT EXISTS profiles (
    id UUID REFERENCES auth.users(id) ON DELETE CASCADE PRIMARY KEY,
    full_name TEXT NOT NULL,
    email TEXT NOT NULL,
    avatar_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 2. FAMILIES TABLE (Supports Vault Invite Codes e.g. FAM-8921)
CREATE TABLE IF NOT EXISTS families (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    name TEXT NOT NULL,
    invite_code TEXT UNIQUE,
    created_by UUID REFERENCES profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 3. FAMILY MEMBERS TABLE
CREATE TABLE IF NOT EXISTS family_members (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    family_id UUID REFERENCES families(id) ON DELETE CASCADE NOT NULL,
    user_id UUID REFERENCES profiles(id) ON DELETE CASCADE NOT NULL,
    name TEXT,
    role TEXT NOT NULL CHECK (role IN ('ADMIN', 'MEMBER', 'VIEWER')),
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    UNIQUE(family_id, user_id)
);

-- 4. CATEGORIES TABLE
CREATE TABLE IF NOT EXISTS categories (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    name TEXT NOT NULL,
    icon TEXT NOT NULL,
    color TEXT NOT NULL,
    user_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
    family_id UUID REFERENCES families(id) ON DELETE CASCADE,
    is_default BOOLEAN DEFAULT false NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 5. TRANSACTIONS TABLE (Personal & Family Ledger)
CREATE TABLE IF NOT EXISTS transactions (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES profiles(id) ON DELETE CASCADE NOT NULL,
    family_id UUID REFERENCES families(id) ON DELETE CASCADE,
    finance_scope TEXT NOT NULL CHECK (finance_scope IN ('PERSONAL', 'FAMILY')),
    amount NUMERIC(15, 2) NOT NULL,
    transaction_type TEXT NOT NULL CHECK (transaction_type IN ('INCOME', 'EXPENSE')),
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    description TEXT NOT NULL,
    payment_method TEXT NOT NULL,
    upi_id TEXT,
    upi_transaction_id TEXT,
    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    is_deleted BOOLEAN DEFAULT false NOT NULL
);

-- 6. BUDGETS TABLE
CREATE TABLE IF NOT EXISTS budgets (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES profiles(id) ON DELETE CASCADE NOT NULL,
    family_id UUID REFERENCES families(id) ON DELETE CASCADE,
    finance_scope TEXT NOT NULL CHECK (finance_scope IN ('PERSONAL', 'FAMILY')),
    name TEXT NOT NULL,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    amount NUMERIC(15, 2) NOT NULL,
    period_type TEXT NOT NULL,
    start_date TIMESTAMP WITH TIME ZONE,
    end_date TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    is_deleted BOOLEAN DEFAULT false NOT NULL
);

-- 7. SAVINGS GOALS TABLE
CREATE TABLE IF NOT EXISTS savings_goals (
    id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID REFERENCES profiles(id) ON DELETE CASCADE NOT NULL,
    family_id UUID REFERENCES families(id) ON DELETE CASCADE,
    finance_scope TEXT NOT NULL CHECK (finance_scope IN ('PERSONAL', 'FAMILY')),
    name TEXT NOT NULL,
    target_amount NUMERIC(15, 2) NOT NULL,
    current_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    target_date TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    is_deleted BOOLEAN DEFAULT false NOT NULL
);

-- 8. PERFORMANCE INDEXES
CREATE INDEX IF NOT EXISTS idx_transactions_user ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_family ON transactions(family_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(transaction_date);
CREATE INDEX IF NOT EXISTS idx_family_members_family ON family_members(family_id);
CREATE INDEX IF NOT EXISTS idx_family_members_user ON family_members(user_id);
CREATE INDEX IF NOT EXISTS idx_families_invite_code ON families(invite_code);

-- 9. ROW LEVEL SECURITY (RLS)
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE families ENABLE ROW LEVEL SECURITY;
ALTER TABLE family_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE budgets ENABLE ROW LEVEL SECURITY;
ALTER TABLE savings_goals ENABLE ROW LEVEL SECURITY;

-- Profiles Policies
DROP POLICY IF EXISTS "Users can view their own profile" ON profiles;
CREATE POLICY "Users can view their own profile" ON profiles FOR SELECT USING (auth.uid() = id);
DROP POLICY IF EXISTS "Users can update their own profile" ON profiles;
CREATE POLICY "Users can update their own profile" ON profiles FOR UPDATE USING (auth.uid() = id);
DROP POLICY IF EXISTS "Users can insert their own profile" ON profiles;
CREATE POLICY "Users can insert their own profile" ON profiles FOR INSERT WITH CHECK (auth.uid() = id);

-- Families Policies (Invite Code Lookups Allowed)
DROP POLICY IF EXISTS "Users can view families they belong to or by code" ON families;
CREATE POLICY "Users can view families they belong to or by code" ON families FOR SELECT USING (
    id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()) OR invite_code IS NOT NULL
);
DROP POLICY IF EXISTS "Users can create families" ON families;
CREATE POLICY "Users can create families" ON families FOR INSERT WITH CHECK (auth.uid() = created_by OR created_by IS NULL);
DROP POLICY IF EXISTS "Admins can update families" ON families;
CREATE POLICY "Admins can update families" ON families FOR UPDATE USING (
    id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid() AND role = 'ADMIN')
);

-- Family Members Policies
DROP POLICY IF EXISTS "Users can view family members" ON family_members;
CREATE POLICY "Users can view family members" ON family_members FOR SELECT USING (
    family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()) OR user_id = auth.uid()
);
DROP POLICY IF EXISTS "Users can join or insert family members" ON family_members;
CREATE POLICY "Users can join or insert family members" ON family_members FOR INSERT WITH CHECK (
    user_id = auth.uid() OR auth.uid() IN (SELECT user_id FROM family_members WHERE family_id = family_members.family_id AND role = 'ADMIN')
);
DROP POLICY IF EXISTS "Admins can update family members" ON family_members;
CREATE POLICY "Admins can update family members" ON family_members FOR UPDATE USING (
    auth.uid() IN (SELECT user_id FROM family_members WHERE family_id = family_members.family_id AND role = 'ADMIN') OR user_id = auth.uid()
);

-- Transactions Policies (Personal & Family)
DROP POLICY IF EXISTS "Users can manage personal transactions" ON transactions;
CREATE POLICY "Users can manage personal transactions" ON transactions FOR ALL USING (
    user_id = auth.uid() AND finance_scope = 'PERSONAL'
);
DROP POLICY IF EXISTS "Users can view and manage family transactions" ON transactions;
CREATE POLICY "Users can view and manage family transactions" ON transactions FOR ALL USING (
    finance_scope = 'FAMILY' AND (
        family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid())
        OR user_id = auth.uid()
    )
);

-- Budgets & Goals Policies
DROP POLICY IF EXISTS "Users can manage personal and family budgets" ON budgets;
CREATE POLICY "Users can manage personal and family budgets" ON budgets FOR ALL USING (
    user_id = auth.uid() OR (finance_scope = 'FAMILY' AND family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()))
);
DROP POLICY IF EXISTS "Users can manage personal and family goals" ON savings_goals;
CREATE POLICY "Users can manage personal and family goals" ON savings_goals FOR ALL USING (
    user_id = auth.uid() OR (finance_scope = 'FAMILY' AND family_id IN (SELECT family_id FROM family_members WHERE user_id = auth.uid()))
);

-- 10. ENABLE SUPABASE REALTIME REPLICATION
ALTER PUBLICATION supabase_realtime ADD TABLE transactions, families, family_members, budgets, savings_goals, profiles;

import {
  Transaction,
  Category,
  Budget,
  SavingsGoal,
  FamilyVault,
  FamilyMember,
  UserProfile
} from '../types';

const STORAGE_KEYS = {
  TRANSACTIONS: 'zenith_transactions_v2',
  CATEGORIES: 'zenith_categories_v2',
  BUDGETS: 'zenith_budgets_v2',
  SAVINGS_GOALS: 'zenith_savings_goals_v2',
  FAMILY_VAULT: 'zenith_family_vault_v2',
  FAMILY_MEMBERS: 'zenith_family_members_v2',
  USER_PROFILE: 'zenith_user_profile_v2',
  CURRENCY: 'zenith_currency_v2',
  LAST_SYNC: 'zenith_last_sync_v2'
};

export const DEFAULT_CATEGORIES: Category[] = [
  { id: 'cat-1', name: 'Food & Dining', iconName: 'Utensils', colorHex: '#EF4444', type: 'EXPENSE', isDefault: true },
  { id: 'cat-2', name: 'Shopping', iconName: 'ShoppingBag', colorHex: '#EC4899', type: 'EXPENSE', isDefault: true },
  { id: 'cat-3', name: 'Housing & Rent', iconName: 'Home', colorHex: '#8B5CF6', type: 'EXPENSE', isDefault: true },
  { id: 'cat-4', name: 'Transportation', iconName: 'Car', colorHex: '#3B82F6', type: 'EXPENSE', isDefault: true },
  { id: 'cat-5', name: 'Bills & Utilities', iconName: 'Receipt', colorHex: '#06B6D4', type: 'EXPENSE', isDefault: true },
  { id: 'cat-6', name: 'Entertainment', iconName: 'Film', colorHex: '#F59E0B', type: 'EXPENSE', isDefault: true },
  { id: 'cat-7', name: 'Healthcare', iconName: 'HeartPulse', colorHex: '#10B981', type: 'EXPENSE', isDefault: true },
  { id: 'cat-8', name: 'Salary & Income', iconName: 'Briefcase', colorHex: '#059669', type: 'INCOME', isDefault: true },
  { id: 'cat-9', name: 'Freelance / Business', iconName: 'Laptop', colorHex: '#D97706', type: 'INCOME', isDefault: true },
  { id: 'cat-10', name: 'Investments', iconName: 'TrendingUp', colorHex: '#6366F1', type: 'INCOME', isDefault: true }
];

export const INITIAL_USER: UserProfile = {
  id: 'usr_me',
  fullName: 'You',
  email: 'user@zenith.app',
  isGuest: true
};

export const INITIAL_FAMILY: FamilyVault = {
  id: 'fam_vault_default',
  name: 'Family Vault',
  createdByUserId: 'usr_me',
  createdAt: Date.now(),
  inviteCode: 'FAM-VAULT1'
};

export const INITIAL_FAMILY_MEMBERS: FamilyMember[] = [
  { id: 'mem_1', familyId: 'fam_vault_default', userId: 'usr_me', name: 'You', role: 'ADMIN', joinedAt: Date.now() }
];

export const INITIAL_TRANSACTIONS: Transaction[] = [];

export const INITIAL_BUDGETS: Budget[] = [];

export const INITIAL_SAVINGS_GOALS: SavingsGoal[] = [];

export class StorageService {
  static loadTransactions(): Transaction[] {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.TRANSACTIONS);
      if (data) return JSON.parse(data);
    } catch (e) {}
    this.saveTransactions(INITIAL_TRANSACTIONS);
    return INITIAL_TRANSACTIONS;
  }

  static saveTransactions(transactions: Transaction[]) {
    try {
      localStorage.setItem(STORAGE_KEYS.TRANSACTIONS, JSON.stringify(transactions));
    } catch (e) {}
  }

  static loadCategories(): Category[] {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.CATEGORIES);
      if (data) return JSON.parse(data);
    } catch (e) {}
    this.saveCategories(DEFAULT_CATEGORIES);
    return DEFAULT_CATEGORIES;
  }

  static saveCategories(categories: Category[]) {
    try {
      localStorage.setItem(STORAGE_KEYS.CATEGORIES, JSON.stringify(categories));
    } catch (e) {}
  }

  static loadBudgets(): Budget[] {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.BUDGETS);
      if (data) return JSON.parse(data);
    } catch (e) {}
    this.saveBudgets(INITIAL_BUDGETS);
    return INITIAL_BUDGETS;
  }

  static saveBudgets(budgets: Budget[]) {
    try {
      localStorage.setItem(STORAGE_KEYS.BUDGETS, JSON.stringify(budgets));
    } catch (e) {}
  }

  static loadSavingsGoals(): SavingsGoal[] {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.SAVINGS_GOALS);
      if (data) return JSON.parse(data);
    } catch (e) {}
    this.saveSavingsGoals(INITIAL_SAVINGS_GOALS);
    return INITIAL_SAVINGS_GOALS;
  }

  static saveSavingsGoals(goals: SavingsGoal[]) {
    try {
      localStorage.setItem(STORAGE_KEYS.SAVINGS_GOALS, JSON.stringify(goals));
    } catch (e) {}
  }

  static loadFamilyVault(): FamilyVault {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.FAMILY_VAULT);
      if (data) return JSON.parse(data);
    } catch (e) {}
    this.saveFamilyVault(INITIAL_FAMILY);
    return INITIAL_FAMILY;
  }

  static saveFamilyVault(vault: FamilyVault) {
    try {
      localStorage.setItem(STORAGE_KEYS.FAMILY_VAULT, JSON.stringify(vault));
    } catch (e) {}
  }

  static loadFamilyMembers(): FamilyMember[] {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.FAMILY_MEMBERS);
      if (data) return JSON.parse(data);
    } catch (e) {}
    this.saveFamilyMembers(INITIAL_FAMILY_MEMBERS);
    return INITIAL_FAMILY_MEMBERS;
  }

  static saveFamilyMembers(members: FamilyMember[]) {
    try {
      localStorage.setItem(STORAGE_KEYS.FAMILY_MEMBERS, JSON.stringify(members));
    } catch (e) {}
  }

  static loadUserProfile(): UserProfile {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.USER_PROFILE);
      if (data) return JSON.parse(data);
    } catch (e) {}
    this.saveUserProfile(INITIAL_USER);
    return INITIAL_USER;
  }

  static saveUserProfile(profile: UserProfile) {
    try {
      localStorage.setItem(STORAGE_KEYS.USER_PROFILE, JSON.stringify(profile));
    } catch (e) {}
  }

  static loadCurrency(): string {
    return localStorage.getItem(STORAGE_KEYS.CURRENCY) || '₹';
  }

  static saveCurrency(curr: string) {
    localStorage.setItem(STORAGE_KEYS.CURRENCY, curr);
  }

  /**
   * Export all data as a backup JSON file
   */
  static exportBackupJson(): string {
    const backup = {
      version: 1,
      appName: 'Zenith CashFlow',
      exportedAt: new Date().toISOString(),
      user: this.loadUserProfile(),
      currency: this.loadCurrency(),
      familyVault: this.loadFamilyVault(),
      familyMembers: this.loadFamilyMembers(),
      categories: this.loadCategories(),
      budgets: this.loadBudgets(),
      savingsGoals: this.loadSavingsGoals(),
      transactions: this.loadTransactions()
    };
    return JSON.stringify(backup, null, 2);
  }

  /**
   * Export transactions as CSV
   */
  static exportTransactionsCsv(): string {
    const transactions = this.loadTransactions();
    const headers = ['ID', 'Date', 'Title', 'Type', 'Category', 'Amount', 'Payment Method', 'Scope', 'Created By', 'Note'];
    const rows = transactions.map(t => [
      t.id,
      new Date(t.dateMillis).toLocaleDateString(),
      `"${(t.title || '').replace(/"/g, '""')}"`,
      t.type,
      `"${(t.category || '').replace(/"/g, '""')}"`,
      t.amount,
      t.paymentMethod,
      t.financeScope,
      `"${(t.createdByName || '').replace(/"/g, '""')}"`,
      `"${(t.note || '').replace(/"/g, '""')}"`
    ]);
    return [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
  }

  /**
   * Import data from JSON backup
   */
  static importBackupJson(jsonString: string): boolean {
    try {
      const data = JSON.parse(jsonString);
      if (Array.isArray(data.transactions)) {
        this.saveTransactions(data.transactions);
      }
      if (Array.isArray(data.budgets)) {
        this.saveBudgets(data.budgets);
      }
      if (Array.isArray(data.savingsGoals)) {
        this.saveSavingsGoals(data.savingsGoals);
      }
      if (Array.isArray(data.categories)) {
        this.saveCategories(data.categories);
      }
      if (data.familyVault) {
        this.saveFamilyVault(data.familyVault);
      }
      if (Array.isArray(data.familyMembers)) {
        this.saveFamilyMembers(data.familyMembers);
      }
      if (data.currency) {
        this.saveCurrency(data.currency);
      }
      return true;
    } catch (e) {
      return false;
    }
  }

  /**
   * Reset all data back to factory defaults
   */
  static resetAllData() {
    localStorage.clear();
    this.saveTransactions(INITIAL_TRANSACTIONS);
    this.saveCategories(DEFAULT_CATEGORIES);
    this.saveBudgets(INITIAL_BUDGETS);
    this.saveSavingsGoals(INITIAL_SAVINGS_GOALS);
    this.saveFamilyVault(INITIAL_FAMILY);
    this.saveFamilyMembers(INITIAL_FAMILY_MEMBERS);
    this.saveUserProfile(INITIAL_USER);
    this.saveCurrency('₹');
  }
}

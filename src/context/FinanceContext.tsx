import React, { createContext, useContext, useState, useEffect, useMemo, useCallback } from 'react';
import {
  Transaction,
  Category,
  Budget,
  SavingsGoal,
  FamilyVault,
  FamilyMember,
  UserProfile,
  FinanceScope,
  TransactionType,
  PaymentMethod,
  FamilyRole,
  FamilySettlementSummary,
  ReceiptData
} from '../types';
import { StorageService } from '../services/storage';
import {
  auth,
  signInWithGoogle as fbSignInWithGoogle,
  signOutUser as fbSignOutUser,
  syncUserProfileToFirestore,
  syncTransactionToFirestore,
  deleteTransactionFromFirestore,
  syncBudgetToFirestore,
  syncSavingsGoalToFirestore,
  subscribeToUserTransactions,
  subscribeToUserBudgets,
  subscribeToUserSavingsGoals
} from '../services/firebase';
import { onAuthStateChanged } from 'firebase/auth';

interface FinanceContextType {
  // Data
  transactions: Transaction[];
  categories: Category[];
  budgets: Budget[];
  savingsGoals: SavingsGoal[];
  familyVault: FamilyVault;
  familyMembers: FamilyMember[];
  userProfile: UserProfile;
  currencySymbol: string;
  currentScope: FinanceScope;
  selectedTab: number;
  syncState: 'SYNCED' | 'SYNCING' | 'ERROR' | 'OFFLINE';
  isFirebaseAuthenticated: boolean;

  // Filters
  searchQuery: string;
  filterType: TransactionType | 'ALL';
  filterCategory: string | null;
  filterMember: string | null;
  filterStartDate: string | null;
  filterEndDate: string | null;

  // Modals
  isAddModalOpen: boolean;
  isVoiceModalOpen: boolean;
  isReceiptModalOpen: boolean;
  isUpiModalOpen: boolean;
  isUpiScanModalOpen: boolean;
  isFamilyModalOpen: boolean;
  selectedDetailTx: Transaction | null;
  editingTransaction: Transaction | null;
  isAuthModalOpen: boolean;

  // Computed Metrics
  displayedTransactions: Transaction[];
  totalIncome: number;
  totalExpense: number;
  netBalance: number;
  familySettlementSummary: FamilySettlementSummary;

  // Actions
  setCurrentScope: (scope: FinanceScope) => void;
  setSelectedTab: (tab: number) => void;
  setSearchQuery: (q: string) => void;
  setFilterType: (t: TransactionType | 'ALL') => void;
  setFilterCategory: (c: string | null) => void;
  setFilterMember: (m: string | null) => void;
  setFilterStartDate: (d: string | null) => void;
  setFilterEndDate: (d: string | null) => void;
  setDateRange: (start: string | null, end: string | null) => void;
  setCurrencySymbol: (curr: string) => void;

  openAddModal: (initial?: Partial<Transaction>) => void;
  closeAddModal: () => void;
  openVoiceModal: () => void;
  closeVoiceModal: () => void;
  openReceiptModal: () => void;
  closeReceiptModal: () => void;
  openUpiModal: () => void;
  closeUpiModal: () => void;
  openUpiScanModal: () => void;
  closeUpiScanModal: () => void;
  openFamilyModal: () => void;
  closeFamilyModal: () => void;
  openDetailModal: (tx: Transaction) => void;
  closeDetailModal: () => void;
  openAuthModal: () => void;
  closeAuthModal: () => void;

  addTransaction: (data: Omit<Transaction, 'id' | 'syncStatus'>) => void;
  updateTransaction: (tx: Transaction) => void;
  deleteTransaction: (id: string) => void;

  saveBudget: (budget: Omit<Budget, 'id'> & { id?: string }) => void;
  deleteBudget: (id: string) => void;

  saveSavingsGoal: (goal: Omit<SavingsGoal, 'id'> & { id?: string }) => void;
  deleteSavingsGoal: (id: string) => void;
  depositToGoal: (id: string, amount: number) => void;

  addFamilyMember: (name: string, role: FamilyRole) => void;
  joinFamilyVault: (code: string) => { success: boolean; message: string };
  syncNow: () => Promise<void>;
  updateUserProfile: (profile: Partial<UserProfile>) => void;
  resetAllData: () => void;

  signInWithGoogle: () => Promise<void>;
  signOutUser: () => Promise<void>;
}

const FinanceContext = createContext<FinanceContextType | undefined>(undefined);

export const FinanceProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [transactions, setTransactions] = useState<Transaction[]>(() => StorageService.loadTransactions());
  const [categories, setCategories] = useState<Category[]>(() => StorageService.loadCategories());
  const [budgets, setBudgets] = useState<Budget[]>(() => StorageService.loadBudgets());
  const [savingsGoals, setSavingsGoals] = useState<SavingsGoal[]>(() => StorageService.loadSavingsGoals());
  const [familyVault, setFamilyVault] = useState<FamilyVault>(() => StorageService.loadFamilyVault());
  const [familyMembers, setFamilyMembers] = useState<FamilyMember[]>(() => StorageService.loadFamilyMembers());
  const [userProfile, setUserProfile] = useState<UserProfile>(() => StorageService.loadUserProfile());
  const [currencySymbol, setCurrencySymbolState] = useState<string>(() => StorageService.loadCurrency());
  const [isFirebaseAuthenticated, setIsFirebaseAuthenticated] = useState<boolean>(false);

  const [currentScope, setCurrentScope] = useState<FinanceScope>('PERSONAL');
  const [selectedTab, setSelectedTab] = useState<number>(0);
  const [syncState, setSyncState] = useState<'SYNCED' | 'SYNCING' | 'ERROR' | 'OFFLINE'>('SYNCED');

  // Filter States
  const [searchQuery, setSearchQuery] = useState('');
  const [filterType, setFilterType] = useState<TransactionType | 'ALL'>('ALL');
  const [filterCategory, setFilterCategory] = useState<string | null>(null);
  const [filterMember, setFilterMember] = useState<string | null>(null);
  const [filterStartDate, setFilterStartDate] = useState<string | null>(null);
  const [filterEndDate, setFilterEndDate] = useState<string | null>(null);

  // Modals
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [editingTransaction, setEditingTransaction] = useState<Transaction | null>(null);
  const [isVoiceModalOpen, setIsVoiceModalOpen] = useState(false);
  const [isReceiptModalOpen, setIsReceiptModalOpen] = useState(false);
  const [isUpiModalOpen, setIsUpiModalOpen] = useState(false);
  const [isUpiScanModalOpen, setIsUpiScanModalOpen] = useState(false);
  const [isFamilyModalOpen, setIsFamilyModalOpen] = useState(false);
  const [selectedDetailTx, setSelectedDetailTx] = useState<Transaction | null>(null);
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);

  // Save changes
  useEffect(() => {
    StorageService.saveTransactions(transactions);
  }, [transactions]);

  useEffect(() => {
    StorageService.saveCategories(categories);
  }, [categories]);

  useEffect(() => {
    StorageService.saveBudgets(budgets);
  }, [budgets]);

  useEffect(() => {
    StorageService.saveSavingsGoals(savingsGoals);
  }, [savingsGoals]);

  useEffect(() => {
    StorageService.saveFamilyVault(familyVault);
  }, [familyVault]);

  useEffect(() => {
    StorageService.saveFamilyMembers(familyMembers);
  }, [familyMembers]);

  useEffect(() => {
    StorageService.saveUserProfile(userProfile);
  }, [userProfile]);

  // Listen for Firebase Auth changes
  useEffect(() => {
    const unsubscribeAuth = onAuthStateChanged(auth, async (fbUser) => {
      if (fbUser) {
        setIsFirebaseAuthenticated(true);
        const profile: UserProfile = {
          id: fbUser.uid,
          fullName: fbUser.displayName || 'You',
          email: fbUser.email || '',
          avatarUrl: fbUser.photoURL || undefined,
          isGuest: false
        };
        setUserProfile(profile);
        setSyncState('SYNCING');
        try {
          await syncUserProfileToFirestore(profile);
          setSyncState('SYNCED');
        } catch (err) {
          console.warn("Failed to sync user profile to Firestore:", err);
          setSyncState('OFFLINE');
        }
      } else {
        setIsFirebaseAuthenticated(false);
        setUserProfile(prev => ({
          ...prev,
          isGuest: true
        }));
      }
    });

    return () => unsubscribeAuth();
  }, []);

  // Listen for Firestore data changes when authenticated
  useEffect(() => {
    if (!isFirebaseAuthenticated || !auth.currentUser) return;
    const uid = auth.currentUser.uid;

    const unsubTx = subscribeToUserTransactions(uid, (remoteTxs) => {
      if (remoteTxs && remoteTxs.length > 0) {
        setTransactions(remoteTxs);
      }
    });

    const unsubBudgets = subscribeToUserBudgets(uid, (remoteBudgets) => {
      if (remoteBudgets && remoteBudgets.length > 0) {
        setBudgets(remoteBudgets);
      }
    });

    const unsubGoals = subscribeToUserSavingsGoals(uid, (remoteGoals) => {
      if (remoteGoals && remoteGoals.length > 0) {
        setSavingsGoals(remoteGoals);
      }
    });

    return () => {
      unsubTx();
      unsubBudgets();
      unsubGoals();
    };
  }, [isFirebaseAuthenticated]);

  const setCurrencySymbol = useCallback((curr: string) => {
    setCurrencySymbolState(curr);
    StorageService.saveCurrency(curr);
  }, []);

  const setDateRange = useCallback((start: string | null, end: string | null) => {
    setFilterStartDate(start);
    setFilterEndDate(end);
  }, []);

  // Filtered transactions for the current scope and search filters
  const displayedTransactions = useMemo(() => {
    return transactions.filter(tx => {
      // Scope match
      if (tx.financeScope !== currentScope) return false;

      // Search match (title, category, note, payment method, payee/creator, amount, or date)
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const matchesTitle = tx.title.toLowerCase().includes(q);
        const matchesCat = tx.category.toLowerCase().includes(q);
        const matchesNote = tx.note?.toLowerCase().includes(q) ?? false;
        const matchesMethod = tx.paymentMethod?.toLowerCase().includes(q) ?? false;
        const matchesMember = tx.createdByName?.toLowerCase().includes(q) ?? false;
        const matchesAmount = tx.amount.toString().includes(q);

        // Date text matching (e.g. "sep", "september", "2026-09", "26")
        const txDate = new Date(tx.dateMillis);
        const isoDate = `${txDate.getFullYear()}-${String(txDate.getMonth() + 1).padStart(2, '0')}-${String(txDate.getDate()).padStart(2, '0')}`;
        const monthShort = txDate.toLocaleString('default', { month: 'short' }).toLowerCase();
        const monthLong = txDate.toLocaleString('default', { month: 'long' }).toLowerCase();
        const formattedDate = `${monthShort} ${txDate.getDate()}, ${txDate.getFullYear()}`.toLowerCase();
        const matchesDate = isoDate.includes(q) || monthShort.includes(q) || monthLong.includes(q) || formattedDate.includes(q);

        if (!matchesTitle && !matchesCat && !matchesNote && !matchesMethod && !matchesMember && !matchesAmount && !matchesDate) {
          return false;
        }
      }

      // Filter Type match
      if (filterType !== 'ALL' && tx.type !== filterType) return false;

      // Filter Category match
      if (filterCategory && tx.category.toLowerCase() !== filterCategory.toLowerCase()) return false;

      // Filter Member match (only in Family scope)
      if (currentScope === 'FAMILY' && filterMember && tx.createdByUserId !== filterMember) return false;

      // Filter Date Range match (robust local calendar comparison)
      if (filterStartDate) {
        const [y, m, d] = filterStartDate.split('-').map(Number);
        if (y && m && d) {
          const startTimestamp = new Date(y, m - 1, d, 0, 0, 0, 0).getTime();
          if (tx.dateMillis < startTimestamp) return false;
        }
      }
      if (filterEndDate) {
        const [y, m, d] = filterEndDate.split('-').map(Number);
        if (y && m && d) {
          const endTimestamp = new Date(y, m - 1, d, 23, 59, 59, 999).getTime();
          if (tx.dateMillis > endTimestamp) return false;
        }
      }

      return true;
    }).sort((a, b) => b.dateMillis - a.dateMillis);
  }, [transactions, currentScope, searchQuery, filterType, filterCategory, filterMember, filterStartDate, filterEndDate]);

  // Overall financial totals
  const { totalIncome, totalExpense, netBalance } = useMemo(() => {
    const scopeTransactions = transactions.filter(tx => tx.financeScope === currentScope);
    let inc = 0;
    let exp = 0;
    for (const tx of scopeTransactions) {
      if (tx.type === 'INCOME') inc += tx.amount;
      else if (tx.type === 'EXPENSE') exp += tx.amount;
    }
    return {
      totalIncome: inc,
      totalExpense: exp,
      netBalance: inc - exp
    };
  }, [transactions, currentScope]);

  // Family settlement calculations
  const familySettlementSummary: FamilySettlementSummary = useMemo(() => {
    const familyExpenses = transactions.filter(tx => tx.financeScope === 'FAMILY' && tx.type === 'EXPENSE');
    const personalExpenses = transactions.filter(tx => tx.financeScope === 'PERSONAL' && tx.type === 'EXPENSE');

    const familyTotal = familyExpenses.reduce((sum, t) => sum + t.amount, 0);
    const personalTotal = personalExpenses.reduce((sum, t) => sum + t.amount, 0);
    const activeMemberCount = Math.max(familyMembers.length, 1);
    const fairShare = familyTotal / activeMemberCount;

    // What user paid for family
    const userPaid = familyExpenses
      .filter(t => t.createdByUserId === userProfile.id)
      .reduce((sum, t) => sum + t.amount, 0);

    const netSettlement = userPaid - fairShare;

    const memberContributions = familyMembers.map(member => {
      const paid = familyExpenses
        .filter(t => t.createdByUserId === member.userId)
        .reduce((sum, t) => sum + t.amount, 0);
      return {
        memberId: member.id,
        name: member.name,
        totalPaid: paid,
        shareDiff: paid - fairShare
      };
    });

    return {
      personalTotalExpense: personalTotal,
      familyTotalExpense: familyTotal,
      userPaidForFamily: userPaid,
      fairSharePerMember: fairShare,
      netSettlementBalance: netSettlement,
      memberCount: activeMemberCount,
      memberContributions
    };
  }, [transactions, familyMembers, userProfile.id]);

  // Modal open helpers
  const openAddModal = useCallback((initial?: Partial<Transaction>) => {
    if (initial && (initial as Transaction).id) {
      setEditingTransaction(initial as Transaction);
    } else {
      setEditingTransaction(null);
    }
    setIsAddModalOpen(true);
  }, []);

  const closeAddModal = useCallback(() => {
    setIsAddModalOpen(false);
    setEditingTransaction(null);
  }, []);

  const openVoiceModal = useCallback(() => setIsVoiceModalOpen(true), []);
  const closeVoiceModal = useCallback(() => setIsVoiceModalOpen(false), []);
  const openReceiptModal = useCallback(() => setIsReceiptModalOpen(true), []);
  const closeReceiptModal = useCallback(() => setIsReceiptModalOpen(false), []);
  const openUpiModal = useCallback(() => setIsUpiModalOpen(true), []);
  const closeUpiModal = useCallback(() => setIsUpiModalOpen(false), []);
  const openUpiScanModal = useCallback(() => setIsUpiScanModalOpen(true), []);
  const closeUpiScanModal = useCallback(() => setIsUpiScanModalOpen(false), []);
  const openFamilyModal = useCallback(() => setIsFamilyModalOpen(true), []);
  const closeFamilyModal = useCallback(() => setIsFamilyModalOpen(false), []);
  const openDetailModal = useCallback((tx: Transaction) => setSelectedDetailTx(tx), []);
  const closeDetailModal = useCallback(() => setSelectedDetailTx(null), []);
  const openAuthModal = useCallback(() => setIsAuthModalOpen(true), []);
  const closeAuthModal = useCallback(() => setIsAuthModalOpen(false), []);

  // CRUD for Transactions
  const addTransaction = useCallback((data: Omit<Transaction, 'id' | 'syncStatus'>) => {
    const newTx: Transaction = {
      ...data,
      id: `tx_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      syncStatus: 'SYNCED',
      createdByUserId: data.createdByUserId || userProfile.id,
      createdByName: data.createdByName || userProfile.fullName
    };
    setTransactions(prev => [newTx, ...prev]);

    if (auth.currentUser && !userProfile.isGuest) {
      syncTransactionToFirestore(userProfile.id, newTx).catch(err => {
        console.warn("Firestore transaction sync failed:", err);
      });
    }
  }, [userProfile]);

  const updateTransaction = useCallback((tx: Transaction) => {
    setTransactions(prev => prev.map(t => t.id === tx.id ? tx : t));
    if (selectedDetailTx?.id === tx.id) {
      setSelectedDetailTx(tx);
    }
    if (auth.currentUser && !userProfile.isGuest) {
      syncTransactionToFirestore(userProfile.id, tx).catch(err => {
        console.warn("Firestore transaction sync failed:", err);
      });
    }
  }, [selectedDetailTx, userProfile]);

  const deleteTransaction = useCallback((id: string) => {
    const toDelete = transactions.find(t => t.id === id);
    setTransactions(prev => prev.filter(t => t.id !== id));
    if (selectedDetailTx?.id === id) {
      setSelectedDetailTx(null);
    }
    if (auth.currentUser && toDelete && !userProfile.isGuest) {
      deleteTransactionFromFirestore(userProfile.id, toDelete).catch(err => {
        console.warn("Firestore transaction delete failed:", err);
      });
    }
  }, [transactions, selectedDetailTx, userProfile]);

  // CRUD for Budgets
  const saveBudget = useCallback((data: Omit<Budget, 'id'> & { id?: string }) => {
    const budgetId = data.id || `bgt_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;
    const newBudget: Budget = {
      ...data,
      id: budgetId
    };
    if (data.id) {
      setBudgets(prev => prev.map(b => b.id === data.id ? { ...b, ...data } : b));
    } else {
      setBudgets(prev => [...prev, newBudget]);
    }
    if (auth.currentUser && !userProfile.isGuest) {
      syncBudgetToFirestore(userProfile.id, newBudget).catch(err => console.warn(err));
    }
  }, [userProfile]);

  const deleteBudget = useCallback((id: string) => {
    setBudgets(prev => prev.filter(b => b.id !== id));
  }, []);

  // CRUD for Savings Goals
  const saveSavingsGoal = useCallback((data: Omit<SavingsGoal, 'id'> & { id?: string }) => {
    const goalId = data.id || `goal_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;
    const newGoal: SavingsGoal = {
      ...data,
      id: goalId
    };
    if (data.id) {
      setSavingsGoals(prev => prev.map(g => g.id === data.id ? { ...g, ...data } : g));
    } else {
      setSavingsGoals(prev => [...prev, newGoal]);
    }
    if (auth.currentUser && !userProfile.isGuest) {
      syncSavingsGoalToFirestore(userProfile.id, newGoal).catch(err => console.warn(err));
    }
  }, [userProfile]);

  const deleteSavingsGoal = useCallback((id: string) => {
    setSavingsGoals(prev => prev.filter(g => g.id !== id));
  }, []);

  const depositToGoal = useCallback((id: string, amount: number) => {
    setSavingsGoals(prev => prev.map(g => {
      if (g.id === id) {
        const updated = Math.max(0, g.currentAmount + amount);
        const updatedGoal = { ...g, currentAmount: updated };
        if (auth.currentUser && !userProfile.isGuest) {
          syncSavingsGoalToFirestore(userProfile.id, updatedGoal).catch(err => console.warn(err));
        }
        return updatedGoal;
      }
      return g;
    }));
  }, [userProfile]);

  // Family Members Management
  const addFamilyMember = useCallback((name: string, role: FamilyRole) => {
    const newMember: FamilyMember = {
      id: `mem_${Date.now()}`,
      familyId: familyVault.id,
      userId: `usr_${Date.now()}`,
      name,
      role,
      joinedAt: Date.now()
    };
    setFamilyMembers(prev => [...prev, newMember]);
  }, [familyVault.id]);

  const joinFamilyVault = useCallback((code: string) => {
    const clean = code.trim().toUpperCase();
    if (!clean) return { success: false, message: 'Invite code cannot be empty' };

    setFamilyVault(prev => ({
      ...prev,
      inviteCode: clean,
      name: `Joined Vault (${clean})`
    }));
    return { success: true, message: `Successfully connected to family vault ${clean}!` };
  }, []);

  // Real Cloud Sync
  const syncNow = useCallback(async () => {
    setSyncState('SYNCING');
    try {
      if (auth.currentUser && !userProfile.isGuest) {
        await syncUserProfileToFirestore(userProfile);
        for (const tx of transactions) {
          await syncTransactionToFirestore(userProfile.id, tx);
        }
        for (const b of budgets) {
          await syncBudgetToFirestore(userProfile.id, b);
        }
        for (const g of savingsGoals) {
          await syncSavingsGoalToFirestore(userProfile.id, g);
        }
      } else {
        await new Promise(resolve => setTimeout(resolve, 600));
      }
      setSyncState('SYNCED');
    } catch (e) {
      console.warn("Manual sync error:", e);
      setSyncState('ERROR');
    }
  }, [userProfile, transactions, budgets, savingsGoals]);

  const signInWithGoogle = useCallback(async () => {
    setSyncState('SYNCING');
    try {
      const fbUser = await fbSignInWithGoogle();
      if (fbUser) {
        setIsFirebaseAuthenticated(true);
        const profile: UserProfile = {
          id: fbUser.uid,
          fullName: fbUser.displayName || 'You',
          email: fbUser.email || '',
          avatarUrl: fbUser.photoURL || undefined,
          isGuest: false
        };
        setUserProfile(profile);
        await syncUserProfileToFirestore(profile);
        // Upload any existing local transactions to Firestore
        for (const tx of transactions) {
          await syncTransactionToFirestore(fbUser.uid, tx);
        }
        setSyncState('SYNCED');
      }
    } catch (err) {
      console.error("Firebase Google sign-in failed:", err);
      setSyncState('ERROR');
      throw err;
    }
  }, [transactions]);

  const signOutUser = useCallback(async () => {
    await fbSignOutUser();
    setIsFirebaseAuthenticated(false);
    setUserProfile({
      id: 'local_guest',
      fullName: 'You',
      email: 'user@zenith.app',
      isGuest: true
    });
    setSyncState('SYNCED');
  }, []);

  const updateUserProfile = useCallback((profile: Partial<UserProfile>) => {
    setUserProfile(prev => {
      const updated = { ...prev, ...profile };
      if (auth.currentUser && !updated.isGuest) {
        syncUserProfileToFirestore(updated).catch(err => console.warn(err));
      }
      return updated;
    });
  }, []);

  const resetAllData = useCallback(() => {
    StorageService.resetAllData();
    setTransactions(StorageService.loadTransactions());
    setCategories(StorageService.loadCategories());
    setBudgets(StorageService.loadBudgets());
    setSavingsGoals(StorageService.loadSavingsGoals());
    setFamilyVault(StorageService.loadFamilyVault());
    setFamilyMembers(StorageService.loadFamilyMembers());
    setUserProfile(StorageService.loadUserProfile());
    setCurrencySymbolState(StorageService.loadCurrency());
  }, []);

  return (
    <FinanceContext.Provider
      value={{
        transactions,
        categories,
        budgets,
        savingsGoals,
        familyVault,
        familyMembers,
        userProfile,
        currencySymbol,
        currentScope,
        selectedTab,
        syncState,
        isFirebaseAuthenticated,

        searchQuery,
        filterType,
        filterCategory,
        filterMember,
        filterStartDate,
        filterEndDate,

        isAddModalOpen,
        isVoiceModalOpen,
        isReceiptModalOpen,
        isUpiModalOpen,
        isUpiScanModalOpen,
        isFamilyModalOpen,
        selectedDetailTx,
        editingTransaction,
        isAuthModalOpen,

        displayedTransactions,
        totalIncome,
        totalExpense,
        netBalance,
        familySettlementSummary,

        setCurrentScope,
        setSelectedTab,
        setSearchQuery,
        setFilterType,
        setFilterCategory,
        setFilterMember,
        setFilterStartDate,
        setFilterEndDate,
        setDateRange,
        setCurrencySymbol,

        openAddModal,
        closeAddModal,
        openVoiceModal,
        closeVoiceModal,
        openReceiptModal,
        closeReceiptModal,
        openUpiModal,
        closeUpiModal,
        openUpiScanModal,
        closeUpiScanModal,
        openFamilyModal,
        closeFamilyModal,
        openDetailModal,
        closeDetailModal,
        openAuthModal,
        closeAuthModal,

        addTransaction,
        updateTransaction,
        deleteTransaction,

        saveBudget,
        deleteBudget,

        saveSavingsGoal,
        deleteSavingsGoal,
        depositToGoal,

        addFamilyMember,
        joinFamilyVault,
        syncNow,
        updateUserProfile,
        resetAllData,
        signInWithGoogle,
        signOutUser
      }}
    >
      {children}
    </FinanceContext.Provider>
  );
};

export const useFinance = () => {
  const context = useContext(FinanceContext);
  if (!context) {
    throw new Error('useFinance must be used within a FinanceProvider');
  }
  return context;
};

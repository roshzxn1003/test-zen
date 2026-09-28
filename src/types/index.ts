export type TransactionType = 'EXPENSE' | 'INCOME';
export type FinanceScope = 'PERSONAL' | 'FAMILY';
export type FamilyRole = 'ADMIN' | 'MEMBER' | 'VIEWER';
export type PaymentMethod = 'UPI' | 'Cash' | 'Credit Card' | 'Debit Card' | 'Bank Transfer';

export interface Category {
  id: string;
  name: string;
  iconName: string;
  colorHex: string;
  type: TransactionType;
  isDefault?: boolean;
}

export interface ReceiptItem {
  name: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
}

export interface ReceiptData {
  merchantName: string;
  receiptNumber?: string;
  receiptDate?: string;
  receiptTime?: string;
  subtotal: number;
  discount: number;
  tax: number;
  total: number;
  currency: string;
  paymentMethod: string;
  items: ReceiptItem[];
  category?: string;
  rawText?: string;
}

export interface Transaction {
  id: string;
  title: string;
  amount: number;
  type: TransactionType;
  category: string;
  categoryIconName?: string;
  note?: string;
  paymentMethod: PaymentMethod;
  dateMillis: number;
  financeScope: FinanceScope;
  familyId?: string;
  createdByUserId?: string;
  createdByName?: string;
  receiptImageUri?: string;
  receipt?: ReceiptData;
  upiId?: string;
  upiTransactionId?: string;
  syncStatus?: 'SYNCED' | 'PENDING_CREATE' | 'PENDING_UPDATE' | 'PENDING_DELETE';
}

export interface Budget {
  id: string;
  categoryName: string;
  monthlyLimit: number;
  monthYear: string; // e.g. "2026-09"
  periodType: 'MONTHLY' | 'WEEKLY' | 'YEARLY' | 'CUSTOM';
  customPeriodName?: string;
  financeScope: FinanceScope;
  familyId?: string;
}

export interface SavingsGoal {
  id: string;
  title: string;
  targetAmount: number;
  currentAmount: number;
  targetDateMillis: number;
  iconName: string;
  colorHex: string;
  financeScope: FinanceScope;
  familyId?: string;
}

export interface FamilyMember {
  id: string;
  familyId: string;
  userId: string;
  name: string;
  role: FamilyRole;
  joinedAt: number;
}

export interface FamilyVault {
  id: string;
  name: string;
  createdByUserId: string;
  createdAt: number;
  inviteCode: string;
}

export interface UserProfile {
  id: string;
  fullName: string;
  email: string;
  avatarUrl?: string;
  isGuest: boolean;
}

export interface ParsedVoiceExpense {
  title: string;
  amount: number;
  type: TransactionType;
  category: string;
  paymentMethod: PaymentMethod;
  note: string;
  scope: FinanceScope;
}

export interface VoiceChatMessage {
  id: string;
  sender: 'user' | 'assistant';
  text: string;
  timestamp: number;
  parsedExpense?: ParsedVoiceExpense;
}

export interface FamilySettlementSummary {
  personalTotalExpense: number;
  familyTotalExpense: number;
  userPaidForFamily: number;
  fairSharePerMember: number;
  netSettlementBalance: number; // positive = owed to user, negative = user owes
  memberCount: number;
  memberContributions: {
    memberId: string;
    name: string;
    totalPaid: number;
    shareDiff: number;
  }[];
}

import { initializeApp } from 'firebase/app';
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signOut,
  onAuthStateChanged,
  User as FirebaseUser
} from 'firebase/auth';
import {
  getFirestore,
  doc,
  getDoc,
  getDocFromServer,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  onSnapshot,
  query,
  where,
  getDocs,
  Unsubscribe
} from 'firebase/firestore';
import firebaseConfig from '../../firebase-applet-config.json';
import {
  Transaction,
  Budget,
  SavingsGoal,
  Category,
  FamilyVault,
  FamilyMember,
  UserProfile
} from '../types';

// Initialize Firebase App
export const app = initializeApp(firebaseConfig);

// CRITICAL: Initialize Firestore with explicit database ID from config
export const db = getFirestore(app, firebaseConfig.firestoreDatabaseId);

// Initialize Firebase Authentication
export const auth = getAuth(app);
export const googleProvider = new GoogleAuthProvider();

// Error Handling Infrastructure mandated by Firebase Integration Skill
export enum OperationType {
  CREATE = 'create',
  UPDATE = 'update',
  DELETE = 'delete',
  LIST = 'list',
  GET = 'get',
  WRITE = 'write',
}

export interface FirestoreErrorInfo {
  error: string;
  operationType: OperationType;
  path: string | null;
  authInfo: {
    userId?: string | null;
    email?: string | null;
    emailVerified?: boolean | null;
    isAnonymous?: boolean | null;
    tenantId?: string | null;
    providerInfo?: {
      providerId?: string | null;
      email?: string | null;
    }[];
  };
}

export function handleFirestoreError(error: unknown, operationType: OperationType, path: string | null): never {
  const errInfo: FirestoreErrorInfo = {
    error: error instanceof Error ? error.message : String(error),
    authInfo: {
      userId: auth.currentUser?.uid,
      email: auth.currentUser?.email,
      emailVerified: auth.currentUser?.emailVerified,
      isAnonymous: auth.currentUser?.isAnonymous,
      tenantId: auth.currentUser?.tenantId,
      providerInfo: auth.currentUser?.providerData?.map(provider => ({
        providerId: provider.providerId,
        email: provider.email,
      })) || []
    },
    operationType,
    path
  };
  console.error('Firestore Error: ', JSON.stringify(errInfo));
  throw new Error(JSON.stringify(errInfo));
}

// CRITICAL CONSTRAINT: Test connection on initial application boot
export async function testConnection(): Promise<boolean> {
  try {
    await getDocFromServer(doc(db, 'test', 'connection'));
    return true;
  } catch (error) {
    if (error instanceof Error && error.message.includes('the client is offline')) {
      console.warn("Firestore client is offline or network restricted.");
      return false;
    }
    // Permissions or non-existent document errors still mean server connection is alive
    return true;
  }
}

// Kick off test connection
testConnection().catch(() => {});

// Authentication Helpers
export async function signInWithGoogle(): Promise<FirebaseUser | null> {
  try {
    const result = await signInWithPopup(auth, googleProvider);
    return result.user;
  } catch (error) {
    console.error("Google Sign-In failed:", error);
    throw error;
  }
}

export async function signOutUser(): Promise<void> {
  try {
    await signOut(auth);
  } catch (error) {
    console.error("Sign-out failed:", error);
    throw error;
  }
}

// Firestore User Profile Sync
export async function syncUserProfileToFirestore(profile: UserProfile): Promise<void> {
  if (!auth.currentUser || profile.isGuest) return;
  const path = `users/${auth.currentUser.uid}`;
  try {
    await setDoc(doc(db, 'users', auth.currentUser.uid), {
      id: auth.currentUser.uid,
      fullName: profile.fullName || auth.currentUser.displayName || 'You',
      email: profile.email || auth.currentUser.email || '',
      avatarUrl: profile.avatarUrl || auth.currentUser.photoURL || '',
      updatedAt: new Date().toISOString()
    }, { merge: true });
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path);
  }
}

// Firestore Personal Transactions Sync
export async function syncTransactionToFirestore(userId: string, tx: Transaction): Promise<void> {
  if (!userId || !auth.currentUser) return;
  const path = tx.financeScope === 'FAMILY' && tx.familyId
    ? `familyVaults/${tx.familyId}/transactions/${tx.id}`
    : `users/${userId}/transactions/${tx.id}`;

  try {
    const docRef = tx.financeScope === 'FAMILY' && tx.familyId
      ? doc(db, 'familyVaults', tx.familyId, 'transactions', tx.id)
      : doc(db, 'users', userId, 'transactions', tx.id);

    // Filter out undefined values to satisfy Firestore serialization
    const cleanPayload: Record<string, unknown> = {
      id: tx.id,
      title: tx.title.slice(0, 140),
      amount: Number(tx.amount) || 0,
      type: tx.type,
      category: tx.category.slice(0, 60),
      paymentMethod: tx.paymentMethod,
      dateMillis: tx.dateMillis,
      financeScope: tx.financeScope,
      createdByUserId: tx.createdByUserId || userId
    };

    if (tx.note) cleanPayload.note = tx.note.slice(0, 500);
    if (tx.createdByName) cleanPayload.createdByName = tx.createdByName.slice(0, 120);
    if (tx.upiId) cleanPayload.upiId = tx.upiId.slice(0, 100);
    if (tx.upiTransactionId) cleanPayload.upiTransactionId = tx.upiTransactionId.slice(0, 100);
    if (tx.receiptImageUri) cleanPayload.receiptImageUri = tx.receiptImageUri.slice(0, 1000);
    if (tx.familyId) cleanPayload.familyId = tx.familyId;

    await setDoc(docRef, cleanPayload, { merge: true });
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path);
  }
}

export async function deleteTransactionFromFirestore(userId: string, tx: Transaction): Promise<void> {
  if (!userId || !auth.currentUser) return;
  const path = tx.financeScope === 'FAMILY' && tx.familyId
    ? `familyVaults/${tx.familyId}/transactions/${tx.id}`
    : `users/${userId}/transactions/${tx.id}`;

  try {
    const docRef = tx.financeScope === 'FAMILY' && tx.familyId
      ? doc(db, 'familyVaults', tx.familyId, 'transactions', tx.id)
      : doc(db, 'users', userId, 'transactions', tx.id);

    await deleteDoc(docRef);
  } catch (error) {
    handleFirestoreError(error, OperationType.DELETE, path);
  }
}

// Firestore Subscriptions
export function subscribeToUserTransactions(
  userId: string,
  onData: (transactions: Transaction[]) => void
): Unsubscribe {
  const collectionPath = `users/${userId}/transactions`;
  const q = collection(db, 'users', userId, 'transactions');

  return onSnapshot(
    q,
    (snapshot) => {
      const items: Transaction[] = [];
      snapshot.forEach((docSnap) => {
        items.push(docSnap.data() as Transaction);
      });
      onData(items);
    },
    (error) => {
      handleFirestoreError(error, OperationType.LIST, collectionPath);
    }
  );
}

export function subscribeToUserBudgets(
  userId: string,
  onData: (budgets: Budget[]) => void
): Unsubscribe {
  const collectionPath = `users/${userId}/budgets`;
  const q = collection(db, 'users', userId, 'budgets');

  return onSnapshot(
    q,
    (snapshot) => {
      const items: Budget[] = [];
      snapshot.forEach((docSnap) => {
        items.push(docSnap.data() as Budget);
      });
      onData(items);
    },
    (error) => {
      handleFirestoreError(error, OperationType.LIST, collectionPath);
    }
  );
}

export function subscribeToUserSavingsGoals(
  userId: string,
  onData: (goals: SavingsGoal[]) => void
): Unsubscribe {
  const collectionPath = `users/${userId}/savingsGoals`;
  const q = collection(db, 'users', userId, 'savingsGoals');

  return onSnapshot(
    q,
    (snapshot) => {
      const items: SavingsGoal[] = [];
      snapshot.forEach((docSnap) => {
        items.push(docSnap.data() as SavingsGoal);
      });
      onData(items);
    },
    (error) => {
      handleFirestoreError(error, OperationType.LIST, collectionPath);
    }
  );
}

// Sync Budgets & Savings Goals
export async function syncBudgetToFirestore(userId: string, budget: Budget): Promise<void> {
  if (!userId || !auth.currentUser) return;
  const path = budget.financeScope === 'FAMILY' && budget.familyId
    ? `familyVaults/${budget.familyId}/budgets/${budget.id}`
    : `users/${userId}/budgets/${budget.id}`;

  try {
    const docRef = budget.financeScope === 'FAMILY' && budget.familyId
      ? doc(db, 'familyVaults', budget.familyId, 'budgets', budget.id)
      : doc(db, 'users', userId, 'budgets', budget.id);

    const cleanPayload: Record<string, unknown> = {
      id: budget.id,
      categoryName: budget.categoryName.slice(0, 60),
      monthlyLimit: Number(budget.monthlyLimit) || 0,
      periodType: budget.periodType,
      financeScope: budget.financeScope
    };
    if (budget.monthYear) cleanPayload.monthYear = budget.monthYear.slice(0, 20);
    if (budget.customPeriodName) cleanPayload.customPeriodName = budget.customPeriodName.slice(0, 80);
    if (budget.familyId) cleanPayload.familyId = budget.familyId;

    await setDoc(docRef, cleanPayload, { merge: true });
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path);
  }
}

export async function syncSavingsGoalToFirestore(userId: string, goal: SavingsGoal): Promise<void> {
  if (!userId || !auth.currentUser) return;
  const path = goal.financeScope === 'FAMILY' && goal.familyId
    ? `familyVaults/${goal.familyId}/savingsGoals/${goal.id}`
    : `users/${userId}/savingsGoals/${goal.id}`;

  try {
    const docRef = goal.financeScope === 'FAMILY' && goal.familyId
      ? doc(db, 'familyVaults', goal.familyId, 'savingsGoals', goal.id)
      : doc(db, 'users', userId, 'savingsGoals', goal.id);

    const cleanPayload: Record<string, unknown> = {
      id: goal.id,
      title: goal.title.slice(0, 100),
      targetAmount: Number(goal.targetAmount) || 0,
      currentAmount: Number(goal.currentAmount) || 0,
      targetDateMillis: Number(goal.targetDateMillis) || Date.now(),
      financeScope: goal.financeScope
    };
    if (goal.iconName) cleanPayload.iconName = goal.iconName.slice(0, 50);
    if (goal.colorHex) cleanPayload.colorHex = goal.colorHex.slice(0, 20);
    if (goal.familyId) cleanPayload.familyId = goal.familyId;

    await setDoc(docRef, cleanPayload, { merge: true });
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, path);
  }
}

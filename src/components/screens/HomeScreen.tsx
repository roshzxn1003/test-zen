import React, { useState, useMemo } from 'react';
import {
  ArrowDownLeft,
  ArrowUpRight,
  QrCode,
  CreditCard,
  FileText,
  Mic,
  Plus,
  Sparkles,
  Copy,
  Share2,
  Users,
  UserPlus,
  Check,
  TrendingUp,
  Receipt
} from 'lucide-react';
import { useFinance } from '../../context/FinanceContext';
import { ScopeSwitcher } from '../ScopeSwitcher';
import { TransactionItemCard } from '../TransactionItemCard';

export const HomeScreen: React.FC = () => {
  const {
    currentScope,
    currencySymbol,
    totalIncome,
    totalExpense,
    netBalance,
    displayedTransactions,
    familyVault,
    familyMembers,
    familySettlementSummary,
    openAddModal,
    openVoiceModal,
    openReceiptModal,
    openUpiModal,
    openUpiScanModal,
    openFamilyModal,
    setSelectedTab
  } = useFinance();

  const [copiedInvite, setCopiedInvite] = useState(false);
  const [selectedMemberFilter, setSelectedMemberFilter] = useState<string | null>(null);

  const isFamily = currentScope === 'FAMILY';

  // Dynamic intelligent insight text
  const insightText = useMemo(() => {
    if (displayedTransactions.length === 0) {
      return isFamily
        ? 'Connect family members to share expenses, track settlement balances, and manage budgets together.'
        : 'Welcome to Zenith! Log your first expense or income to unlock real-time spending insights and analytics.';
    }

    const expenses = displayedTransactions.filter(t => t.type === 'EXPENSE');
    if (expenses.length > 0 && totalExpense > 0) {
      const catTotals: Record<string, number> = {};
      expenses.forEach(e => {
        catTotals[e.category] = (catTotals[e.category] || 0) + e.amount;
      });
      const topCat = Object.entries(catTotals).sort((a, b) => b[1] - a[1])[0];
      if (topCat) {
        const pct = Math.round((topCat[1] / totalExpense) * 100);
        return isFamily
          ? `${pct}% of total family vault spending is concentrated in ${topCat[0]}.`
          : `${pct}% of your spending is in ${topCat[0]}. Review your category budget to optimize cash flow.`;
      }
    }

    if (totalIncome > 0) {
      const savingsRate = Math.max(0, Math.round(((totalIncome - totalExpense) / totalIncome) * 100));
      return `Your net savings rate is ${savingsRate}% this period. Keep logging daily to stay financially resilient!`;
    }

    return 'Consistent expense tracking is the fastest path to long-term financial independence.';
  }, [displayedTransactions, totalExpense, totalIncome, isFamily]);

  const handleCopyInvite = () => {
    navigator.clipboard.writeText(familyVault.inviteCode);
    setCopiedInvite(true);
    setTimeout(() => setCopiedInvite(false), 2000);
  };

  const handleShareInvite = () => {
    if (navigator.share) {
      navigator.share({
        title: 'Join Family Vault on Zenith CashFlow',
        text: `Join our Family Vault on Zenith CashFlow! Use invite code: ${familyVault.inviteCode}`
      }).catch(() => {});
    } else {
      handleCopyInvite();
    }
  };

  const familyTransactions = useMemo(() => {
    if (!selectedMemberFilter) return displayedTransactions;
    return displayedTransactions.filter(t => t.createdByUserId === selectedMemberFilter);
  }, [displayedTransactions, selectedMemberFilter]);

  const recentTransactions = isFamily ? familyTransactions.slice(0, 6) : displayedTransactions.slice(0, 6);

  return (
    <div className="space-y-5 pb-24 md:pb-12">
      {/* 1. Mobile Scope Switcher (Desktop uses header toggle) */}
      <div className="md:hidden">
        <ScopeSwitcher />
      </div>

      {/* 2. Hero Presentation Card */}
      {isFamily ? (
        <div className="space-y-4">
          {/* Family Vault Invite Banner */}
          <div className="p-4 sm:p-5 rounded-2xl bg-slate-900/80 border border-white/[0.08] shadow-lg backdrop-blur-xl">
            <div className="flex items-center justify-between gap-3 mb-3">
              <div className="flex items-center gap-3 min-w-0">
                <div className="w-10 h-10 rounded-xl bg-cyan-500/15 border border-cyan-500/25 flex items-center justify-center text-cyan-400 flex-shrink-0">
                  <Users className="w-5 h-5" />
                </div>
                <div className="min-w-0">
                  <h3 className="text-sm sm:text-base font-bold text-white truncate">{familyVault.name}</h3>
                  <p className="text-xs text-slate-400">{familyMembers.length} Active Member{familyMembers.length === 1 ? '' : 's'}</p>
                </div>
              </div>

              <button
                onClick={openFamilyModal}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-cyan-500/10 border border-cyan-500/20 text-cyan-300 hover:bg-cyan-500/20 text-xs font-bold transition-all cursor-pointer flex-shrink-0"
              >
                <UserPlus className="w-3.5 h-3.5" />
                <span>Manage</span>
              </button>
            </div>

            {/* Invite Code Pill */}
            <div className="flex items-center justify-between px-3.5 py-2 rounded-xl bg-slate-950/80 border border-cyan-500/20">
              <div className="flex items-center gap-2">
                <span className="text-[10px] font-extrabold uppercase tracking-wider text-slate-400">Vault Invite Code:</span>
                <span className="font-mono text-xs sm:text-sm font-black text-cyan-300 tracking-wider">
                  {familyVault.inviteCode}
                </span>
              </div>

              <div className="flex items-center gap-1">
                <button
                  onClick={handleCopyInvite}
                  className="p-1.5 rounded-lg text-slate-300 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
                  title="Copy Invite Code"
                >
                  {copiedInvite ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
                <button
                  onClick={handleShareInvite}
                  className="p-1.5 rounded-lg text-cyan-400 hover:text-cyan-300 hover:bg-white/10 transition-colors cursor-pointer"
                  title="Share Invite Code"
                >
                  <Share2 className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>

          {/* Main Hero Vault Balance Card */}
          <div className="relative p-5 sm:p-7 rounded-3xl bg-gradient-to-br from-[#0c1427] via-[#090f1d] to-[#061722] border border-cyan-500/25 shadow-2xl overflow-hidden backdrop-blur-2xl">
            <div className="flex items-center justify-between text-xs font-bold text-cyan-300 tracking-wider uppercase">
              <span>Shared Vault Total Expenses</span>
              <span className="text-slate-400 normal-case font-medium">{familyMembers.length} Members Sharing</span>
            </div>

            <div className="mt-2 text-3xl sm:text-5xl font-black text-white tracking-tight tabular-nums">
              {currencySymbol}{familySettlementSummary.familyTotalExpense.toLocaleString('en-US', { minimumFractionDigits: 2 })}
            </div>

            {/* 3-Metric Interlock: Personal | You Paid | Fair Share */}
            <div className="grid grid-cols-3 gap-2 sm:gap-3 mt-5">
              <div className="p-3 sm:p-3.5 rounded-2xl bg-slate-900/80 border border-white/[0.08]">
                <p className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">Your Spending</p>
                <p className="text-xs sm:text-base font-bold text-indigo-300 mt-0.5 truncate tabular-nums">
                  {currencySymbol}{Math.round(familySettlementSummary.personalTotalExpense).toLocaleString()}
                </p>
              </div>
              <div className="p-3 sm:p-3.5 rounded-2xl bg-slate-900/80 border border-white/[0.08]">
                <p className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">You Paid For All</p>
                <p className="text-xs sm:text-base font-bold text-cyan-300 mt-0.5 truncate tabular-nums">
                  {currencySymbol}{Math.round(familySettlementSummary.userPaidForFamily).toLocaleString()}
                </p>
              </div>
              <div className="p-3 sm:p-3.5 rounded-2xl bg-slate-900/80 border border-white/[0.08]">
                <p className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">Fair Share Each</p>
                <p className="text-xs sm:text-base font-bold text-indigo-200 mt-0.5 truncate tabular-nums">
                  {currencySymbol}{Math.round(familySettlementSummary.fairSharePerMember).toLocaleString()}
                </p>
              </div>
            </div>

            {/* Settlement Standing Banner */}
            <div className={`mt-4 p-3.5 rounded-2xl border flex items-center justify-between ${
              familySettlementSummary.netSettlementBalance > 0
                ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
                : familySettlementSummary.netSettlementBalance < 0
                ? 'bg-rose-500/10 border-rose-500/30 text-rose-300'
                : 'bg-cyan-500/10 border-cyan-500/30 text-cyan-300'
            }`}>
              <div>
                <p className="text-xs font-semibold">
                  {familySettlementSummary.netSettlementBalance > 0
                    ? 'You are owed by other members'
                    : familySettlementSummary.netSettlementBalance < 0
                    ? 'You owe the shared pool'
                    : 'All member shares are balanced'}
                </p>
                <p className="text-base sm:text-lg font-black text-white mt-0.5 tabular-nums">
                  {familySettlementSummary.netSettlementBalance > 0
                    ? `+${currencySymbol}${Math.abs(familySettlementSummary.netSettlementBalance).toFixed(2)}`
                    : familySettlementSummary.netSettlementBalance < 0
                    ? `-${currencySymbol}${Math.abs(familySettlementSummary.netSettlementBalance).toFixed(2)}`
                    : 'Fully Settled'}
                </p>
              </div>

              {familySettlementSummary.netSettlementBalance < 0 && (
                <button
                  onClick={openUpiModal}
                  className="px-3.5 py-2 rounded-xl bg-rose-500 hover:bg-rose-600 text-white text-xs font-bold shadow-md transition-all cursor-pointer"
                >
                  Settle UPI
                </button>
              )}
            </div>
          </div>
        </div>
      ) : (
        /* Personal Mode Hero Card */
        <div className="relative p-5 sm:p-7 rounded-3xl bg-gradient-to-br from-[#0e1424] via-[#090e1a] to-[#070b14] border border-white/[0.09] shadow-2xl overflow-hidden backdrop-blur-2xl">
          <div className="flex items-center justify-between text-xs font-bold text-slate-400 uppercase tracking-wider">
            <span>Net Financial Balance</span>
            <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold ${
              netBalance >= 0 ? 'bg-emerald-500/15 border border-emerald-500/25 text-emerald-300' : 'bg-rose-500/15 border border-rose-500/25 text-rose-300'
            }`}>
              {netBalance >= 0 ? <ArrowDownLeft className="w-3 h-3 text-emerald-400" /> : <ArrowUpRight className="w-3 h-3 text-rose-400" />}
              {netBalance >= 0 ? 'Positive Net Flow' : 'Deficit'}
            </span>
          </div>

          <div className="mt-2 text-3xl sm:text-5xl font-black text-white tracking-tight tabular-nums">
            {netBalance < 0 ? '-' : ''}{currencySymbol}{Math.abs(netBalance).toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </div>

          <div className="grid grid-cols-2 gap-3 sm:gap-4 mt-5">
            <div className="p-3.5 sm:p-4 rounded-2xl bg-slate-900/80 border border-white/[0.06] flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-emerald-500/15 border border-emerald-500/25 flex items-center justify-center text-emerald-400 flex-shrink-0">
                <ArrowDownLeft className="w-5 h-5" />
              </div>
              <div className="min-w-0">
                <p className="text-[10px] uppercase tracking-wider font-semibold text-slate-400">Total Income</p>
                <p className="text-sm sm:text-lg font-bold text-emerald-400 truncate mt-0.5 tabular-nums">
                  +{currencySymbol}{totalIncome.toLocaleString()}
                </p>
              </div>
            </div>

            <div className="p-3.5 sm:p-4 rounded-2xl bg-slate-900/80 border border-white/[0.06] flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-rose-500/15 border border-rose-500/25 flex items-center justify-center text-rose-400 flex-shrink-0">
                <ArrowUpRight className="w-5 h-5" />
              </div>
              <div className="min-w-0">
                <p className="text-[10px] uppercase tracking-wider font-semibold text-slate-400">Total Spent</p>
                <p className="text-sm sm:text-lg font-bold text-rose-400 truncate mt-0.5 tabular-nums">
                  -{currencySymbol}{totalExpense.toLocaleString()}
                </p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 3. Refined Quick Actions Hub (4 Cards) */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 sm:gap-3">
        {/* Action 1: QR Scan */}
        <button
          onClick={openUpiScanModal}
          data-testid="btn_scan_upi_qr"
          className="flex items-center sm:flex-col justify-start sm:justify-center p-3 sm:py-3.5 sm:px-2 rounded-2xl bg-slate-900/70 hover:bg-slate-850 border border-white/[0.08] hover:border-cyan-500/30 text-slate-200 transition-all cursor-pointer group shadow-sm"
        >
          <div className="w-9 h-9 rounded-xl bg-cyan-500/10 border border-cyan-500/25 flex items-center justify-center text-cyan-400 mr-3 sm:mr-0 sm:mb-2 group-hover:scale-105 transition-transform flex-shrink-0">
            <QrCode className="w-4 h-4" />
          </div>
          <div className="text-left sm:text-center min-w-0">
            <p className="text-xs font-bold text-white group-hover:text-cyan-300 transition-colors truncate">Scan QR</p>
            <p className="text-[10px] text-slate-400 hidden sm:block truncate">Instant UPI payment</p>
          </div>
        </button>

        {/* Action 2: UPI Pay */}
        <button
          onClick={openUpiModal}
          data-testid="btn_pay_upi"
          className="flex items-center sm:flex-col justify-start sm:justify-center p-3 sm:py-3.5 sm:px-2 rounded-2xl bg-slate-900/70 hover:bg-slate-850 border border-white/[0.08] hover:border-emerald-500/30 text-slate-200 transition-all cursor-pointer group shadow-sm"
        >
          <div className="w-9 h-9 rounded-xl bg-emerald-500/10 border border-emerald-500/25 flex items-center justify-center text-emerald-400 mr-3 sm:mr-0 sm:mb-2 group-hover:scale-105 transition-transform flex-shrink-0">
            <CreditCard className="w-4 h-4" />
          </div>
          <div className="text-left sm:text-center min-w-0">
            <p className="text-xs font-bold text-white group-hover:text-emerald-300 transition-colors truncate">UPI Transfer</p>
            <p className="text-[10px] text-slate-400 hidden sm:block truncate">Send to VPA / ID</p>
          </div>
        </button>

        {/* Action 3: Receipt Scan */}
        <button
          onClick={openReceiptModal}
          data-testid="btn_scan_receipt"
          className="flex items-center sm:flex-col justify-start sm:justify-center p-3 sm:py-3.5 sm:px-2 rounded-2xl bg-slate-900/70 hover:bg-slate-850 border border-white/[0.08] hover:border-amber-500/30 text-slate-200 transition-all cursor-pointer group shadow-sm"
        >
          <div className="w-9 h-9 rounded-xl bg-amber-500/10 border border-amber-500/25 flex items-center justify-center text-amber-400 mr-3 sm:mr-0 sm:mb-2 group-hover:scale-105 transition-transform flex-shrink-0">
            <FileText className="w-4 h-4" />
          </div>
          <div className="text-left sm:text-center min-w-0">
            <p className="text-xs font-bold text-white group-hover:text-amber-300 transition-colors truncate">Smart Receipt</p>
            <p className="text-[10px] text-slate-400 hidden sm:block truncate">AI photo parse</p>
          </div>
        </button>

        {/* Action 4: Voice AI or Add */}
        <button
          onClick={isFamily ? () => openAddModal({ financeScope: 'FAMILY' }) : openVoiceModal}
          data-testid={isFamily ? "btn_quick_add" : "btn_voice_entry"}
          className="flex items-center sm:flex-col justify-start sm:justify-center p-3 sm:py-3.5 sm:px-2 rounded-2xl bg-slate-900/70 hover:bg-slate-850 border border-white/[0.08] hover:border-indigo-500/30 text-slate-200 transition-all cursor-pointer group shadow-sm"
        >
          <div className="w-9 h-9 rounded-xl bg-indigo-500/10 border border-indigo-500/25 flex items-center justify-center text-indigo-400 mr-3 sm:mr-0 sm:mb-2 group-hover:scale-105 transition-transform flex-shrink-0">
            {isFamily ? <Plus className="w-4 h-4" /> : <Mic className="w-4 h-4" />}
          </div>
          <div className="text-left sm:text-center min-w-0">
            <p className="text-xs font-bold text-white group-hover:text-indigo-300 transition-colors truncate">
              {isFamily ? 'Add Expense' : 'Voice AI'}
            </p>
            <p className="text-[10px] text-slate-400 hidden sm:block truncate">
              {isFamily ? 'Log to Vault' : 'Natural entry'}
            </p>
          </div>
        </button>
      </div>

      {/* 4. Family Members & Contributions Matrix (Only in Family Scope) */}
      {isFamily && (
        <div className="p-4 sm:p-5 rounded-2xl bg-slate-900/80 border border-white/[0.08] shadow-lg space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Users className="w-4 h-4 text-cyan-400" />
              <h4 className="text-xs font-bold text-slate-300 uppercase tracking-wider">Member Contributions</h4>
            </div>
            <button
              onClick={openFamilyModal}
              className="text-xs font-bold text-cyan-400 hover:text-cyan-300 hover:underline cursor-pointer"
            >
              + Manage Vault
            </button>
          </div>

          <div className="space-y-2">
            {familySettlementSummary.memberContributions.map(member => {
              const totalExp = Math.max(familySettlementSummary.familyTotalExpense, 1);
              const progress = Math.min(Math.max((member.totalPaid / totalExp) * 100, 0), 100);

              return (
                <div key={member.memberId} className="p-3 rounded-xl bg-slate-950/60 border border-white/5 space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <div className="flex items-center gap-2">
                      <div className="w-6 h-6 rounded-full bg-cyan-500/20 text-cyan-300 font-bold flex items-center justify-center text-[10px]">
                        {member.name.charAt(0).toUpperCase()}
                      </div>
                      <span className="font-bold text-white">{member.name}</span>
                    </div>

                    <div className="text-right tabular-nums">
                      <span className="font-bold text-white mr-2">Paid: {currencySymbol}{Math.round(member.totalPaid)}</span>
                      <span className={`text-[11px] font-semibold ${member.shareDiff >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                        {member.shareDiff >= 0 ? `+${currencySymbol}${Math.round(member.shareDiff)} (Owed)` : `-${currencySymbol}${Math.round(Math.abs(member.shareDiff))} (Owes)`}
                      </span>
                    </div>
                  </div>

                  {/* Progress Bar */}
                  <div className="w-full h-1.5 rounded-full bg-white/10 overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all duration-500 ${
                        member.shareDiff >= 0 ? 'bg-emerald-400' : 'bg-cyan-400'
                      }`}
                      style={{ width: `${progress}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* 5. Dynamic Spending Insight Banner */}
      <div className="p-3.5 sm:p-4 rounded-2xl bg-slate-900/60 border border-white/[0.07] flex items-center gap-3 shadow-sm">
        <div className="w-8 h-8 rounded-xl bg-indigo-500/10 border border-indigo-500/25 flex items-center justify-center text-indigo-400 flex-shrink-0">
          <Sparkles className="w-4 h-4" />
        </div>
        <p className="text-xs text-slate-300 leading-relaxed font-medium">
          {insightText}
        </p>
      </div>

      {/* 6. Recent Activity Section */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-base font-extrabold text-white tracking-tight">
              {isFamily ? 'Recent Family Activity' : 'Recent Activity'}
            </h3>
            <p className="text-xs text-slate-400">Click any transaction for details, breakdown, or editing</p>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => openAddModal({ financeScope: currentScope })}
              className="hidden sm:flex items-center gap-1 px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-semibold text-slate-200 hover:text-white transition-all cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add</span>
            </button>
            <button
              onClick={() => setSelectedTab(1)}
              className="text-xs font-bold text-indigo-400 hover:text-indigo-300 transition-colors cursor-pointer px-2 py-1"
            >
              See all →
            </button>
          </div>
        </div>

        {/* Member filter chips for Family mode */}
        {isFamily && familyMembers.length > 0 && (
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 no-scrollbar text-xs">
            <button
              onClick={() => setSelectedMemberFilter(null)}
              className={`px-3 py-1 rounded-xl text-xs font-bold transition-all whitespace-nowrap cursor-pointer ${
                selectedMemberFilter === null
                  ? 'bg-cyan-500 text-slate-950 font-black'
                  : 'bg-white/5 border border-white/10 text-slate-400 hover:text-white'
              }`}
            >
              All Members ({familyMembers.length})
            </button>
            {familyMembers.map(m => (
              <button
                key={m.userId}
                onClick={() => setSelectedMemberFilter(selectedMemberFilter === m.userId ? null : m.userId)}
                className={`px-3 py-1 rounded-xl text-xs font-semibold transition-all whitespace-nowrap cursor-pointer ${
                  selectedMemberFilter === m.userId
                    ? 'bg-cyan-500 text-slate-950 font-bold'
                    : 'bg-white/5 border border-white/10 text-slate-400 hover:text-white'
                }`}
              >
                {m.name}
              </button>
            ))}
          </div>
        )}

        {/* Transactions list */}
        {recentTransactions.length === 0 ? (
          <div className="p-8 sm:p-10 rounded-2xl bg-slate-900/60 border border-white/[0.07] text-center space-y-3">
            <div className="w-12 h-12 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 flex items-center justify-center mx-auto">
              <Receipt className="w-6 h-6" />
            </div>
            <p className="text-sm font-bold text-slate-200">No transactions recorded yet</p>
            <p className="text-xs text-slate-400 max-w-sm mx-auto leading-relaxed">
              {isFamily
                ? 'Log shared household bills, groceries, or dinners to start tracking team balances and fair shares.'
                : 'Click "+ Add" or speak into the Voice AI assistant to easily record your first expense or income.'}
            </p>
            <div className="pt-2 flex items-center justify-center gap-2">
              <button
                onClick={() => openAddModal({ financeScope: currentScope })}
                className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition-all cursor-pointer shadow-md"
              >
                + Add Transaction
              </button>
              <button
                onClick={openVoiceModal}
                className="px-4 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-slate-300 text-xs font-bold transition-all cursor-pointer"
              >
                Voice Entry
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-2">
            {recentTransactions.map(tx => (
              <TransactionItemCard key={tx.id} transaction={tx} />
            ))}
          </div>
        )}
      </div>

      {/* Floating Action Button (Mobile Only) */}
      <button
        onClick={() => openAddModal({ financeScope: currentScope })}
        data-testid="fab_add_transaction"
        className="md:hidden fixed right-5 bottom-20 z-30 w-13 h-13 rounded-2xl bg-indigo-600 hover:bg-indigo-500 text-white shadow-[0_10px_25px_-5px_rgba(99,102,241,0.5)] flex items-center justify-center transition-all hover:scale-105 active:scale-95 cursor-pointer"
        title="Add Transaction"
      >
        <Plus className="w-6 h-6 stroke-[2.5]" />
      </button>
    </div>
  );
};

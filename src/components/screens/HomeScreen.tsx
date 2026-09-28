import React, { useState } from 'react';
import {
  ArrowDownLeft,
  ArrowUpRight,
  QrCode,
  CreditCard,
  FileText,
  Mic,
  Plus,
  Lightbulb,
  Copy,
  Share2,
  Users,
  UserPlus,
  Check
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

  // Dynamic insight
  const insightText = React.useMemo(() => {
    if (displayedTransactions.length === 0) {
      return isFamily
        ? 'Add family entries to see group financial insights and fair split metrics.'
        : 'Add your transactions to unlock automated spending insights and budgeting tips.';
    }

    const expenses = displayedTransactions.filter(t => t.type === 'EXPENSE');
    if (expenses.length > 0 && totalExpense > 0) {
      const catTotals: Record<string, number> = {};
      expenses.forEach(e => { catTotals[e.category] = (catTotals[e.category] || 0) + e.amount; });
      const topCat = Object.entries(catTotals).sort((a, b) => b[1] - a[1])[0];
      if (topCat) {
        const pct = Math.round((topCat[1] / totalExpense) * 100);
        return isFamily
          ? `${pct}% of total family vault expenses are concentrated in ${topCat[0]}.`
          : `${pct}% of your current spending is in ${topCat[0]}. Review your budgets to stay on track.`;
      }
    }

    if (totalIncome > 0) {
      const savingsRate = Math.max(0, Math.round(((totalIncome - totalExpense) / totalIncome) * 100));
      return `Your net savings rate is currently ${savingsRate}% this period. Great progress!`;
    }

    return 'Track your cash flow daily to maintain complete financial clarity and confidence.';
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

  const familyTransactions = React.useMemo(() => {
    if (!selectedMemberFilter) return displayedTransactions;
    return displayedTransactions.filter(t => t.createdByUserId === selectedMemberFilter);
  }, [displayedTransactions, selectedMemberFilter]);

  const recentTransactions = isFamily ? familyTransactions.slice(0, 5) : displayedTransactions.slice(0, 5);

  return (
    <div className="space-y-4 pb-24">
      {/* 1. Mode Selector (Personal vs Family Ledger) */}
      <ScopeSwitcher />

      {/* 2. Mode-Specific Hero Card */}
      {isFamily ? (
        <div className="space-y-4">
          {/* Family Vault Invite Banner */}
          <div className="p-4 rounded-3xl bg-slate-900/80 border border-white/10 shadow-lg">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-2xl bg-cyan-500/15 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
                  <Users className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-white">{familyVault.name}</h3>
                  <p className="text-xs text-slate-400">{familyMembers.length} Connected Members</p>
                </div>
              </div>

              <button
                onClick={openFamilyModal}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-cyan-500/10 border border-cyan-500/20 text-cyan-300 hover:bg-cyan-500/20 text-xs font-bold transition-all cursor-pointer"
              >
                <UserPlus className="w-3.5 h-3.5" />
                <span>Manage</span>
              </button>
            </div>

            {/* Invite Code Pill */}
            <div className="flex items-center justify-between px-3.5 py-2 rounded-xl bg-slate-950/70 border border-cyan-500/25">
              <div className="flex items-center gap-2">
                <span className="text-[10px] font-extrabold uppercase tracking-wider text-slate-400">Invite Code:</span>
                <span className="font-mono text-sm font-black text-cyan-300 tracking-wider">
                  {familyVault.inviteCode}
                </span>
              </div>

              <div className="flex items-center gap-1">
                <button
                  onClick={handleCopyInvite}
                  className="p-1.5 rounded-lg text-slate-300 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
                  title="Copy Invite Code"
                >
                  {copiedInvite ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                </button>
                <button
                  onClick={handleShareInvite}
                  className="p-1.5 rounded-lg text-cyan-400 hover:text-cyan-300 hover:bg-white/10 transition-colors cursor-pointer"
                  title="Share Invite Code"
                >
                  <Share2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>

          {/* Main Hero Vault Balance Card */}
          <div className="relative p-5 rounded-3xl bg-gradient-to-br from-[#1e1b4b] via-[#0f172a] to-[#0e3a4a] border border-cyan-500/30 shadow-xl overflow-hidden">
            <div className="flex items-center justify-between text-xs font-bold text-cyan-300 tracking-wide uppercase">
              <span>Total Family Vault Spending</span>
              <span className="text-slate-400 normal-case">{familyMembers.length} Members</span>
            </div>

            <div className="mt-2 text-3xl sm:text-4xl font-black text-white tracking-tight">
              {currencySymbol}{familySettlementSummary.familyTotalExpense.toLocaleString('en-US', { minimumFractionDigits: 2 })}
            </div>

            {/* 3-Metric Interlock: Personal | You Paid | Fair Share */}
            <div className="grid grid-cols-3 gap-2 mt-4">
              <div className="p-2.5 rounded-2xl bg-slate-900/60 border border-white/5">
                <p className="text-[10px] text-slate-400">Personal</p>
                <p className="text-xs font-bold text-indigo-300 mt-0.5 truncate">
                  {currencySymbol}{Math.round(familySettlementSummary.personalTotalExpense).toLocaleString()}
                </p>
              </div>
              <div className="p-2.5 rounded-2xl bg-slate-900/60 border border-white/5">
                <p className="text-[10px] text-slate-400">You Paid</p>
                <p className="text-xs font-bold text-cyan-300 mt-0.5 truncate">
                  {currencySymbol}{Math.round(familySettlementSummary.userPaidForFamily).toLocaleString()}
                </p>
              </div>
              <div className="p-2.5 rounded-2xl bg-slate-900/60 border border-white/5">
                <p className="text-[10px] text-slate-400">Fair Share</p>
                <p className="text-xs font-bold text-indigo-200 mt-0.5 truncate">
                  {currencySymbol}{Math.round(familySettlementSummary.fairSharePerMember).toLocaleString()}
                </p>
              </div>
            </div>

            {/* Settlement Standing Banner */}
            <div className={`mt-4 p-3 rounded-2xl border flex items-center justify-between ${
              familySettlementSummary.netSettlementBalance > 0
                ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
                : familySettlementSummary.netSettlementBalance < 0
                ? 'bg-rose-500/10 border-rose-500/30 text-rose-300'
                : 'bg-cyan-500/10 border-cyan-500/30 text-cyan-300'
            }`}>
              <div>
                <p className="text-[11px] font-semibold">
                  {familySettlementSummary.netSettlementBalance > 0
                    ? 'You are owed by family members'
                    : familySettlementSummary.netSettlementBalance < 0
                    ? 'You owe the family vault'
                    : 'All family shares are balanced'}
                </p>
                <p className="text-base font-extrabold text-white">
                  {familySettlementSummary.netSettlementBalance > 0
                    ? `+${currencySymbol}${Math.abs(familySettlementSummary.netSettlementBalance).toFixed(2)}`
                    : familySettlementSummary.netSettlementBalance < 0
                    ? `-${currencySymbol}${Math.abs(familySettlementSummary.netSettlementBalance).toFixed(2)}`
                    : 'Settled Up'}
                </p>
              </div>

              {familySettlementSummary.netSettlementBalance < 0 && (
                <button
                  onClick={openUpiModal}
                  className="px-3 py-1.5 rounded-xl bg-rose-500 text-white text-xs font-bold shadow-md hover:bg-rose-600 transition-colors cursor-pointer"
                >
                  Settle UPI
                </button>
              )}
            </div>
          </div>
        </div>
      ) : (
        /* Personal Mode Hero Card */
        <div className="relative p-5 rounded-3xl bg-gradient-to-br from-[#1e1b4b] via-[#0f172a] to-[#090d16] border border-white/10 shadow-xl overflow-hidden">
          <div className="flex items-center justify-between text-xs font-bold text-slate-400 uppercase tracking-wide">
            <span>Net Financial Balance</span>
            <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold ${
              netBalance >= 0 ? 'bg-emerald-500/20 text-emerald-300' : 'bg-rose-500/20 text-rose-300'
            }`}>
              {netBalance >= 0 ? <ArrowDownLeft className="w-3 h-3 text-emerald-400" /> : <ArrowUpRight className="w-3 h-3 text-rose-400" />}
              {netBalance >= 0 ? 'Surplus' : 'Deficit'}
            </span>
          </div>

          <div className="mt-2 text-3xl sm:text-4xl font-black text-white tracking-tight">
            {netBalance < 0 ? '-' : ''}{currencySymbol}{Math.abs(netBalance).toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </div>

          <div className="grid grid-cols-2 gap-3 mt-4">
            <div className="p-3 rounded-2xl bg-slate-900/70 border border-white/5 flex items-center gap-3">
              <div className="w-8 h-8 rounded-xl bg-emerald-500/15 border border-emerald-500/25 flex items-center justify-center text-emerald-400">
                <ArrowDownLeft className="w-4 h-4" />
              </div>
              <div>
                <p className="text-[10px] text-slate-400">Income</p>
                <p className="text-sm font-bold text-emerald-400 truncate">
                  +{currencySymbol}{totalIncome.toLocaleString()}
                </p>
              </div>
            </div>

            <div className="p-3 rounded-2xl bg-slate-900/70 border border-white/5 flex items-center gap-3">
              <div className="w-8 h-8 rounded-xl bg-rose-500/15 border border-rose-500/25 flex items-center justify-center text-rose-400">
                <ArrowUpRight className="w-4 h-4" />
              </div>
              <div>
                <p className="text-[10px] text-slate-400">Spent</p>
                <p className="text-sm font-bold text-rose-400 truncate">
                  -{currencySymbol}{totalExpense.toLocaleString()}
                </p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 3. Quick Actions Hub (4 Cards) */}
      <div className="grid grid-cols-4 gap-2">
        <button
          onClick={openUpiScanModal}
          data-testid="btn_scan_upi_qr"
          className="flex flex-col items-center justify-center py-3 px-1 rounded-2xl bg-cyan-500/10 border border-cyan-500/20 text-cyan-300 hover:bg-cyan-500/20 transition-all cursor-pointer shadow-sm"
        >
          <QrCode className="w-5 h-5 text-cyan-400 mb-1" />
          <span className="text-[11px] font-bold truncate max-w-full">Scan QR</span>
        </button>

        <button
          onClick={openUpiModal}
          data-testid="btn_pay_upi"
          className="flex flex-col items-center justify-center py-3 px-1 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 hover:bg-emerald-500/20 transition-all cursor-pointer shadow-sm"
        >
          <CreditCard className="w-5 h-5 text-emerald-400 mb-1" />
          <span className="text-[11px] font-bold truncate max-w-full">UPI Pay</span>
        </button>

        <button
          onClick={openReceiptModal}
          data-testid="btn_scan_receipt"
          className="flex flex-col items-center justify-center py-3 px-1 rounded-2xl bg-amber-500/10 border border-amber-500/20 text-amber-300 hover:bg-amber-500/20 transition-all cursor-pointer shadow-sm"
        >
          <FileText className="w-5 h-5 text-amber-400 mb-1" />
          <span className="text-[11px] font-bold truncate max-w-full">Receipt</span>
        </button>

        <button
          onClick={isFamily ? () => openAddModal({ financeScope: 'FAMILY' }) : openVoiceModal}
          data-testid={isFamily ? "btn_quick_add" : "btn_voice_entry"}
          className={`flex flex-col items-center justify-center py-3 px-1 rounded-2xl transition-all cursor-pointer shadow-sm ${
            isFamily
              ? 'bg-indigo-600/30 border border-indigo-500/40 text-indigo-200 hover:bg-indigo-600/40'
              : 'bg-indigo-500/10 border border-indigo-500/20 text-indigo-300 hover:bg-indigo-500/20'
          }`}
        >
          {isFamily ? <Plus className="w-5 h-5 text-indigo-300 mb-1" /> : <Mic className="w-5 h-5 text-indigo-400 mb-1" />}
          <span className="text-[11px] font-bold truncate max-w-full">{isFamily ? '+ Add' : 'Voice'}</span>
        </button>
      </div>

      {/* 4. Family Members & Contributions Matrix (Only in Family Scope) */}
      {isFamily && (
        <div className="p-4 rounded-3xl bg-slate-900/80 border border-white/10 shadow-lg space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Users className="w-4 h-4 text-cyan-400" />
              <h4 className="text-xs font-bold text-slate-300 uppercase tracking-wider">Member Balances & Shares</h4>
            </div>
            <button
              onClick={openFamilyModal}
              className="text-xs font-bold text-cyan-400 hover:underline cursor-pointer"
            >
              + Manage
            </button>
          </div>

          <div className="space-y-2">
            {familySettlementSummary.memberContributions.map(member => {
              const totalExp = Math.max(familySettlementSummary.familyTotalExpense, 1);
              const progress = Math.min(Math.max((member.totalPaid / totalExp) * 100, 0), 100);

              return (
                <div key={member.memberId} className="p-2.5 rounded-2xl bg-slate-800/60 border border-white/5 space-y-1.5">
                  <div className="flex items-center justify-between text-xs">
                    <div className="flex items-center gap-2">
                      <div className="w-6 h-6 rounded-full bg-cyan-500/20 text-cyan-300 font-bold flex items-center justify-center text-[10px]">
                        {member.name.charAt(0).toUpperCase()}
                      </div>
                      <span className="font-bold text-white">{member.name}</span>
                    </div>

                    <div className="text-right">
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

      {/* 5. Dynamic Spending Insight Card */}
      <div className="p-4 rounded-3xl bg-slate-900/60 border border-white/5 flex items-center gap-3">
        <div className="w-9 h-9 rounded-xl bg-amber-500/15 border border-amber-500/25 flex items-center justify-center text-amber-400 flex-shrink-0">
          <Lightbulb className="w-4 h-4" />
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
            <p className="text-[11px] text-slate-400">Click any transaction for full breakdown & receipt</p>
          </div>

          <button
            onClick={() => setSelectedTab(1)}
            className="text-xs font-bold text-indigo-400 hover:text-indigo-300 transition-colors cursor-pointer"
          >
            See all →
          </button>
        </div>

        {/* Member filter chips for Family mode */}
        {isFamily && familyMembers.length > 0 && (
          <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar">
            <button
              onClick={() => setSelectedMemberFilter(null)}
              className={`px-3 py-1 rounded-full text-xs font-bold transition-all whitespace-nowrap cursor-pointer ${
                selectedMemberFilter === null
                  ? 'bg-cyan-500 text-slate-950 font-black'
                  : 'bg-white/5 border border-white/10 text-slate-400 hover:text-white'
              }`}
            >
              All ({familyMembers.length})
            </button>
            {familyMembers.map(m => (
              <button
                key={m.userId}
                onClick={() => setSelectedMemberFilter(selectedMemberFilter === m.userId ? null : m.userId)}
                className={`px-3 py-1 rounded-full text-xs font-bold transition-all whitespace-nowrap cursor-pointer ${
                  selectedMemberFilter === m.userId
                    ? 'bg-cyan-500 text-slate-950 font-black'
                    : 'bg-white/5 border border-white/10 text-slate-400 hover:text-white'
                }`}
              >
                👤 {m.name}
              </button>
            ))}
          </div>
        )}

        {/* Transactions list */}
        {recentTransactions.length === 0 ? (
          <div className="p-8 rounded-3xl bg-slate-900/60 border border-white/5 text-center space-y-2">
            <p className="text-sm font-bold text-slate-300">No transactions recorded yet</p>
            <p className="text-xs text-slate-500 max-w-xs mx-auto">
              {isFamily
                ? 'Add your first family expense to start tracking collaborative spending.'
                : 'Tap + or speak to Voice Assistant to record your first expense or income.'}
            </p>
            <button
              onClick={() => openAddModal({ financeScope: currentScope })}
              className="mt-3 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition-all cursor-pointer"
            >
              + Add Transaction
            </button>
          </div>
        ) : (
          <div className="space-y-2">
            {recentTransactions.map(tx => (
              <TransactionItemCard key={tx.id} transaction={tx} />
            ))}
          </div>
        )}
      </div>

      {/* Floating Action Button */}
      <button
        onClick={() => openAddModal({ financeScope: currentScope })}
        data-testid="fab_add_transaction"
        className="fixed right-6 bottom-22 z-30 w-13 h-13 rounded-2xl bg-indigo-600 hover:bg-indigo-500 text-white shadow-[0_10px_25px_-5px_rgba(99,102,241,0.5)] flex items-center justify-center transition-all hover:scale-105 active:scale-95 cursor-pointer"
        title="Add Transaction"
      >
        <Plus className="w-6 h-6 stroke-[2.5]" />
      </button>
    </div>
  );
};

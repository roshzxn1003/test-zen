import React, { useState, useMemo } from 'react';
import {
  Sparkles,
  ArrowDownLeft,
  ArrowUpRight,
  TrendingUp,
  RotateCw,
  PieChart,
  Users
} from 'lucide-react';
import { useFinance } from '../../context/FinanceContext';
import { ScopeSwitcher } from '../ScopeSwitcher';
import { AiService } from '../../services/aiService';

export const AnalyticsScreen: React.FC = () => {
  const {
    currentScope,
    currencySymbol,
    totalIncome,
    totalExpense,
    netBalance,
    transactions,
    familyMembers
  } = useFinance();

  const isFamily = currentScope === 'FAMILY';

  // AI Financial Coach State
  const [aiAdvice, setAiAdvice] = useState<string | null>(null);
  const [isAiLoading, setIsAiLoading] = useState(false);

  // Filter transactions for this scope
  const scopedTransactions = useMemo(() => {
    return transactions.filter(t => t.financeScope === currentScope);
  }, [transactions, currentScope]);

  // Category breakdown for expenses
  const categoryBreakdown = useMemo(() => {
    const expenses = scopedTransactions.filter(t => t.type === 'EXPENSE');
    const map: Record<string, number> = {};
    for (const e of expenses) {
      map[e.category] = (map[e.category] || 0) + e.amount;
    }

    const total = Object.values(map).reduce((sum, v) => sum + v, 0);

    return Object.entries(map)
      .map(([name, amount]) => ({
        name,
        amount,
        percentage: total > 0 ? Math.round((amount / total) * 100) : 0
      }))
      .sort((a, b) => b.amount - a.amount);
  }, [scopedTransactions]);

  const topCategory = categoryBreakdown[0]?.name || 'Food & Dining';

  // Member breakdown (Family scope only)
  const memberBreakdown = useMemo(() => {
    if (!isFamily) return [];
    const expenses = scopedTransactions.filter(t => t.type === 'EXPENSE');
    const total = expenses.reduce((sum, e) => sum + e.amount, 0);

    return familyMembers.map(member => {
      const paid = expenses
        .filter(t => t.createdByUserId === member.userId)
        .reduce((sum, t) => sum + t.amount, 0);

      return {
        id: member.id,
        name: member.name,
        amount: paid,
        percentage: total > 0 ? Math.round((paid / total) * 100) : 0
      };
    }).sort((a, b) => b.amount - a.amount);
  }, [isFamily, scopedTransactions, familyMembers]);

  // Savings rate
  const savingsRate = totalIncome > 0
    ? Math.max(0, Math.round(((totalIncome - totalExpense) / totalIncome) * 100))
    : 0;

  // Handle generating advice
  const handleGenerateAdvice = async () => {
    setIsAiLoading(true);
    try {
      const advice = await AiService.getCoachAdvice(totalIncome, totalExpense, topCategory);
      setAiAdvice(advice);
    } catch (e) {
      setAiAdvice(`Review your recurring expenses in ${topCategory} and prioritize an emergency fund of at least 3 months.`);
    } finally {
      setIsAiLoading(false);
    }
  };

  return (
    <div className="space-y-4 pb-24">
      {/* 1. Scope Switcher */}
      <ScopeSwitcher />

      {/* 2. Key Metrics Row */}
      <div className="grid grid-cols-2 gap-3">
        <div className="p-4 rounded-3xl bg-slate-900/80 border border-white/10 space-y-1">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Net Savings</span>
            <TrendingUp className="w-3.5 h-3.5 text-indigo-400" />
          </div>
          <p className="text-xl font-black text-white">
            {netBalance < 0 ? '-' : ''}{currencySymbol}{Math.abs(netBalance).toLocaleString()}
          </p>
          <p className={`text-[10px] font-bold ${netBalance >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
            {netBalance >= 0 ? 'Healthy Cash Flow' : 'Deficit this Period'}
          </p>
        </div>

        <div className="p-4 rounded-3xl bg-slate-900/80 border border-white/10 space-y-1">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Savings Rate</span>
            <span className="text-[10px] font-bold text-cyan-400">Target 20%</span>
          </div>
          <p className="text-xl font-black text-white">{savingsRate}%</p>
          <div className="w-full h-1.5 rounded-full bg-white/10 overflow-hidden">
            <div
              className={`h-full rounded-full transition-all duration-500 ${
                savingsRate >= 20 ? 'bg-emerald-400' : savingsRate >= 10 ? 'bg-amber-400' : 'bg-rose-400'
              }`}
              style={{ width: `${Math.min(savingsRate, 100)}%` }}
            />
          </div>
        </div>
      </div>

      {/* 3. AI Financial Coach Card */}
      <div className="relative p-5 rounded-3xl bg-gradient-to-br from-[#2e1065] via-[#1e1b4b] to-[#0e7490] border border-indigo-500/30 shadow-xl overflow-hidden space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-xl bg-purple-500/20 border border-purple-400/30 flex items-center justify-center text-purple-300">
              <Sparkles className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-xs font-black text-purple-200 uppercase tracking-wider">Zenith AI Financial Coach</h3>
              <p className="text-[10px] text-purple-300/70">Personalized spending & savings recommendations</p>
            </div>
          </div>

          <button
            onClick={handleGenerateAdvice}
            disabled={isAiLoading}
            className="px-3 py-1.5 rounded-xl bg-purple-500/30 hover:bg-purple-500/40 border border-purple-400/30 text-purple-100 text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer shadow-sm disabled:opacity-50"
          >
            <RotateCw className={`w-3.5 h-3.5 ${isAiLoading ? 'animate-spin' : ''}`} />
            <span>{isAiLoading ? 'Analyzing...' : 'Get Advice'}</span>
          </button>
        </div>

        <div className="p-3.5 rounded-2xl bg-slate-950/60 border border-white/10">
          <p className="text-xs text-slate-200 leading-relaxed font-medium">
            {aiAdvice || (
              totalExpense > 0
                ? `You have spent ${currencySymbol}${totalExpense.toLocaleString()} so far, with highest outlay in ${topCategory}. Tap "Get Advice" for AI coaching tips.`
                : "Record your transactions to receive personalized, AI-powered financial tips."
            )}
          </p>
        </div>
      </div>

      {/* 4. Income vs Expense Comparison Bar */}
      <div className="p-4 rounded-3xl bg-slate-900/80 border border-white/10 space-y-3">
        <h4 className="text-xs font-extrabold uppercase tracking-wider text-slate-400">Cash In vs Cash Out</h4>

        <div className="space-y-2">
          {/* Income Bar */}
          <div className="space-y-1">
            <div className="flex items-center justify-between text-xs">
              <span className="flex items-center gap-1.5 font-bold text-emerald-400">
                <ArrowDownLeft className="w-3.5 h-3.5" /> Income
              </span>
              <span className="font-extrabold text-white">+{currencySymbol}{totalIncome.toLocaleString()}</span>
            </div>
            <div className="w-full h-2.5 rounded-full bg-white/10 overflow-hidden">
              <div
                className="h-full bg-emerald-500 rounded-full transition-all duration-500"
                style={{ width: `${totalIncome + totalExpense > 0 ? (totalIncome / (totalIncome + totalExpense)) * 100 : 50}%` }}
              />
            </div>
          </div>

          {/* Expense Bar */}
          <div className="space-y-1">
            <div className="flex items-center justify-between text-xs">
              <span className="flex items-center gap-1.5 font-bold text-rose-400">
                <ArrowUpRight className="w-3.5 h-3.5" /> Expenses
              </span>
              <span className="font-extrabold text-white">-{currencySymbol}{totalExpense.toLocaleString()}</span>
            </div>
            <div className="w-full h-2.5 rounded-full bg-white/10 overflow-hidden">
              <div
                className="h-full bg-rose-500 rounded-full transition-all duration-500"
                style={{ width: `${totalIncome + totalExpense > 0 ? (totalExpense / (totalIncome + totalExpense)) * 100 : 50}%` }}
              />
            </div>
          </div>
        </div>
      </div>

      {/* 5. Spending by Category Breakdown */}
      <div className="p-4 rounded-3xl bg-slate-900/80 border border-white/10 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <PieChart className="w-4 h-4 text-indigo-400" />
            <h4 className="text-xs font-extrabold uppercase tracking-wider text-slate-400">Expense Breakdown by Category</h4>
          </div>
          <span className="text-[11px] font-bold text-slate-400">{categoryBreakdown.length} Categories</span>
        </div>

        {categoryBreakdown.length === 0 ? (
          <p className="text-xs text-slate-500 text-center py-4">No expense transactions recorded yet.</p>
        ) : (
          <div className="space-y-2.5">
            {categoryBreakdown.map((cat, idx) => (
              <div key={cat.name} className="space-y-1">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold text-slate-200">{cat.name}</span>
                  <div className="flex items-center gap-2">
                    <span className="font-extrabold text-white">{currencySymbol}{cat.amount.toLocaleString()}</span>
                    <span className="text-[11px] font-bold text-indigo-400 w-8 text-right">{cat.percentage}%</span>
                  </div>
                </div>

                <div className="w-full h-2 rounded-full bg-white/10 overflow-hidden">
                  <div
                    className="h-full rounded-full transition-all duration-500"
                    style={{
                      width: `${cat.percentage}%`,
                      backgroundColor: ['#6366f1', '#06b6d4', '#ec4899', '#f59e0b', '#10b981', '#8b5cf6', '#3b82f6'][idx % 7]
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 6. Member Breakdown (Family Scope Only) */}
      {isFamily && memberBreakdown.length > 0 && (
        <div className="p-4 rounded-3xl bg-slate-900/80 border border-white/10 space-y-3">
          <div className="flex items-center gap-2">
            <Users className="w-4 h-4 text-cyan-400" />
            <h4 className="text-xs font-extrabold uppercase tracking-wider text-slate-400">Family Member Contributions</h4>
          </div>

          <div className="space-y-2.5">
            {memberBreakdown.map(mem => (
              <div key={mem.id} className="space-y-1">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold text-white">{mem.name}</span>
                  <span className="font-extrabold text-cyan-300">{currencySymbol}{mem.amount.toLocaleString()} ({mem.percentage}%)</span>
                </div>
                <div className="w-full h-2 rounded-full bg-white/10 overflow-hidden">
                  <div
                    className="h-full bg-cyan-400 rounded-full transition-all duration-500"
                    style={{ width: `${mem.percentage}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

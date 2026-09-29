import React, { useState } from 'react';
import {
  PieChart,
  Target,
  Plus,
  Trash2,
  Edit2,
  Calendar,
  Sparkles,
  ArrowUpCircle,
  X
} from 'lucide-react';
import confetti from 'canvas-confetti';
import { useFinance } from '../../context/FinanceContext';
import { ScopeSwitcher } from '../ScopeSwitcher';
import { Budget, SavingsGoal } from '../../types';

export const BudgetsAndGoalsScreen: React.FC = () => {
  const {
    currentScope,
    currencySymbol,
    budgets,
    savingsGoals,
    transactions,
    categories,
    saveBudget,
    deleteBudget,
    saveSavingsGoal,
    deleteSavingsGoal,
    depositToGoal
  } = useFinance();

  const [activeSubTab, setActiveSubTab] = useState<'BUDGETS' | 'GOALS'>('BUDGETS');

  // Budget modal state
  const [isBudgetModalOpen, setIsBudgetModalOpen] = useState(false);
  const [editingBudget, setEditingBudget] = useState<Budget | null>(null);
  const [budgetCategory, setBudgetCategory] = useState(categories[0]?.name || 'Food & Dining');
  const [budgetLimit, setBudgetLimit] = useState('');
  const [budgetPeriod, setBudgetPeriod] = useState<'MONTHLY' | 'WEEKLY' | 'YEARLY'>('MONTHLY');

  // Goal modal state
  const [isGoalModalOpen, setIsGoalModalOpen] = useState(false);
  const [editingGoal, setEditingGoal] = useState<SavingsGoal | null>(null);
  const [goalTitle, setGoalTitle] = useState('');
  const [goalTarget, setGoalTarget] = useState('');
  const [goalCurrent, setGoalCurrent] = useState('0');
  const [goalDate, setGoalDate] = useState('');
  const [goalColor, setGoalColor] = useState('#10B981');

  // Deposit modal state
  const [depositGoalId, setDepositGoalId] = useState<string | null>(null);
  const [depositAmount, setDepositAmount] = useState('1000');

  // Filter budgets and goals by current scope
  const scopedBudgets = budgets.filter(b => b.financeScope === currentScope);
  const scopedGoals = savingsGoals.filter(g => g.financeScope === currentScope);

  // Compute category spending for the current month
  const categorySpending = React.useMemo(() => {
    const map: Record<string, number> = {};
    const now = new Date();
    const currentMonth = now.getMonth();
    const currentYear = now.getFullYear();

    transactions
      .filter(t => {
        if (t.financeScope !== currentScope || t.type !== 'EXPENSE') return false;
        const d = new Date(t.dateMillis);
        return d.getMonth() === currentMonth && d.getFullYear() === currentYear;
      })
      .forEach(t => {
        map[t.category] = (map[t.category] || 0) + t.amount;
      });

    return map;
  }, [transactions, currentScope]);

  // Open budget edit/create
  const handleOpenBudgetModal = (b?: Budget) => {
    if (b) {
      setEditingBudget(b);
      setBudgetCategory(b.categoryName);
      setBudgetLimit(b.monthlyLimit.toString());
      setBudgetPeriod(b.periodType as any);
    } else {
      setEditingBudget(null);
      setBudgetCategory(categories[0]?.name || 'Food & Dining');
      setBudgetLimit('5000');
      setBudgetPeriod('MONTHLY');
    }
    setIsBudgetModalOpen(true);
  };

  const handleSaveBudget = (e: React.FormEvent) => {
    e.preventDefault();
    const limit = parseFloat(budgetLimit);
    if (!limit || limit <= 0) return;

    saveBudget({
      id: editingBudget?.id,
      categoryName: budgetCategory,
      monthlyLimit: limit,
      monthYear: '2026-09',
      periodType: budgetPeriod,
      financeScope: currentScope
    });
    setIsBudgetModalOpen(false);
  };

  // Open goal edit/create
  const handleOpenGoalModal = (g?: SavingsGoal) => {
    if (g) {
      setEditingGoal(g);
      setGoalTitle(g.title);
      setGoalTarget(g.targetAmount.toString());
      setGoalCurrent(g.currentAmount.toString());
      setGoalDate(new Date(g.targetDateMillis).toISOString().split('T')[0]);
      setGoalColor(g.colorHex);
    } else {
      setEditingGoal(null);
      setGoalTitle('');
      setGoalTarget('25000');
      setGoalCurrent('0');
      const defaultTargetDate = new Date();
      defaultTargetDate.setDate(defaultTargetDate.getDate() + 60);
      setGoalDate(defaultTargetDate.toISOString().split('T')[0]);
      setGoalColor('#10B981');
    }
    setIsGoalModalOpen(true);
  };

  const handleSaveGoal = (e: React.FormEvent) => {
    e.preventDefault();
    const target = parseFloat(goalTarget);
    const current = parseFloat(goalCurrent) || 0;
    if (!goalTitle.trim() || !target || target <= 0) return;

    saveSavingsGoal({
      id: editingGoal?.id,
      title: goalTitle.trim(),
      targetAmount: target,
      currentAmount: current,
      targetDateMillis: new Date(goalDate || Date.now()).getTime(),
      iconName: 'Shield',
      colorHex: goalColor,
      financeScope: currentScope
    });
    setIsGoalModalOpen(false);
  };

  const handleDepositSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const amt = parseFloat(depositAmount);
    if (depositGoalId && !isNaN(amt) && amt > 0) {
      depositToGoal(depositGoalId, amt);
      const goal = savingsGoals.find(g => g.id === depositGoalId);
      if (goal && (goal.currentAmount + amt) >= goal.targetAmount) {
        confetti({
          particleCount: 80,
          spread: 70,
          origin: { y: 0.6 }
        });
      }
      setDepositGoalId(null);
      setDepositAmount('1000');
    }
  };

  return (
    <div className="space-y-4 pb-24">
      {/* 1. Scope Switcher (Mobile Only) */}
      <div className="md:hidden">
        <ScopeSwitcher />
      </div>

      {/* 2. Sub-Tabs (Budgets vs Savings Goals) */}
      <div className="flex rounded-2xl bg-slate-900/80 p-1 border border-white/10">
        <button
          onClick={() => setActiveSubTab('BUDGETS')}
          className={`flex-1 py-2 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 transition-all cursor-pointer ${
            activeSubTab === 'BUDGETS'
              ? 'bg-indigo-600 text-white shadow-md'
              : 'text-slate-400 hover:text-white'
          }`}
        >
          <PieChart className="w-4 h-4" />
          <span>Category Budgets ({scopedBudgets.length})</span>
        </button>

        <button
          onClick={() => setActiveSubTab('GOALS')}
          className={`flex-1 py-2 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 transition-all cursor-pointer ${
            activeSubTab === 'GOALS'
              ? 'bg-indigo-600 text-white shadow-md'
              : 'text-slate-400 hover:text-white'
          }`}
        >
          <Target className="w-4 h-4" />
          <span>Savings Goals ({scopedGoals.length})</span>
        </button>
      </div>

      {/* 3. Tab Content */}
      {activeSubTab === 'BUDGETS' ? (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-xs font-bold text-slate-400 uppercase tracking-wider">Active Monthly Limits</p>
            <button
              onClick={() => handleOpenBudgetModal()}
              className="flex items-center gap-1 text-xs font-bold text-indigo-400 hover:text-indigo-300 cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>New Budget</span>
            </button>
          </div>

          {scopedBudgets.length === 0 ? (
            <div className="p-8 rounded-3xl bg-slate-900/60 border border-white/5 text-center space-y-2">
              <PieChart className="w-8 h-8 text-indigo-400 mx-auto mb-2" />
              <p className="text-sm font-bold text-white">No budgets configured</p>
              <p className="text-xs text-slate-400 max-w-xs mx-auto">
                Set monthly spending limits for key categories to keep your expenses in check.
              </p>
              <button
                onClick={() => handleOpenBudgetModal()}
                className="mt-3 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition-all cursor-pointer"
              >
                + Create First Budget
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {scopedBudgets.map(budget => {
                const spent = categorySpending[budget.categoryName] || 0;
                const pct = Math.min(Math.round((spent / budget.monthlyLimit) * 100), 100);
                const isOver = spent > budget.monthlyLimit;
                const isWarning = pct >= 80 && !isOver;

                return (
                  <div
                    key={budget.id}
                    className="p-4 rounded-3xl bg-slate-900/80 border border-white/10 shadow-sm space-y-3"
                  >
                    <div className="flex items-center justify-between">
                      <div>
                        <h4 className="text-sm font-extrabold text-white">{budget.categoryName}</h4>
                        <p className="text-[11px] text-slate-400 capitalize">{budget.periodType.toLowerCase()} limit</p>
                      </div>

                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => handleOpenBudgetModal(budget)}
                          className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 cursor-pointer"
                          title="Edit Budget"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => deleteBudget(budget.id)}
                          className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 cursor-pointer"
                          title="Delete Budget"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>

                    {/* Progress Bar */}
                    <div className="space-y-1.5">
                      <div className="flex items-center justify-between text-xs">
                        <span className="font-bold text-slate-200">
                          {currencySymbol}{spent.toLocaleString()} <span className="text-slate-500 font-normal">of {currencySymbol}{budget.monthlyLimit.toLocaleString()}</span>
                        </span>
                        <span className={`font-bold ${isOver ? 'text-rose-400' : isWarning ? 'text-amber-400' : 'text-emerald-400'}`}>
                          {pct}% {isOver && '• Over limit!'}
                        </span>
                      </div>

                      <div className="w-full h-2 rounded-full bg-white/10 overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all duration-500 ${
                            isOver ? 'bg-rose-500' : isWarning ? 'bg-amber-400' : 'bg-emerald-500'
                          }`}
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      ) : (
        /* SAVINGS GOALS TAB */
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-xs font-bold text-slate-400 uppercase tracking-wider">Target Vaults & Goals</p>
            <button
              onClick={() => handleOpenGoalModal()}
              className="flex items-center gap-1 text-xs font-bold text-indigo-400 hover:text-indigo-300 cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>New Goal</span>
            </button>
          </div>

          {scopedGoals.length === 0 ? (
            <div className="p-8 rounded-3xl bg-slate-900/60 border border-white/5 text-center space-y-2">
              <Target className="w-8 h-8 text-indigo-400 mx-auto mb-2" />
              <p className="text-sm font-bold text-white">No savings goals yet</p>
              <p className="text-xs text-slate-400 max-w-xs mx-auto">
                Set target amounts and dates for gadgets, vacations, or rainy day emergency funds.
              </p>
              <button
                onClick={() => handleOpenGoalModal()}
                className="mt-3 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition-all cursor-pointer"
              >
                + Create Savings Goal
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {scopedGoals.map(goal => {
                const pct = Math.min(Math.round((goal.currentAmount / goal.targetAmount) * 100), 100);
                const isComplete = goal.currentAmount >= goal.targetAmount;
                const daysLeft = Math.max(0, Math.ceil((goal.targetDateMillis - Date.now()) / (1000 * 60 * 60 * 24)));

                return (
                  <div
                    key={goal.id}
                    className="p-4 rounded-3xl bg-slate-900/80 border border-white/10 shadow-sm space-y-3"
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex items-center gap-3">
                        <div
                          className="w-10 h-10 rounded-2xl flex items-center justify-center flex-shrink-0"
                          style={{ backgroundColor: `${goal.colorHex}25`, color: goal.colorHex }}
                        >
                          <Target className="w-5 h-5" />
                        </div>
                        <div>
                          <div className="flex items-center gap-1.5">
                            <h4 className="text-sm font-extrabold text-white">{goal.title}</h4>
                            {isComplete && (
                              <span className="inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 text-[10px] font-bold">
                                <Sparkles className="w-2.5 h-2.5" /> Reached!
                              </span>
                            )}
                          </div>
                          <div className="flex items-center gap-2 mt-0.5 text-[11px] text-slate-400">
                            <Calendar className="w-3 h-3 text-slate-500" />
                            <span>{daysLeft} days remaining</span>
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => handleOpenGoalModal(goal)}
                          className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 cursor-pointer"
                          title="Edit Goal"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => deleteSavingsGoal(goal.id)}
                          className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 cursor-pointer"
                          title="Delete Goal"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>

                    {/* Progress Bar & Amounts */}
                    <div className="space-y-1.5">
                      <div className="flex items-center justify-between text-xs">
                        <span className="font-bold text-white">
                          {currencySymbol}{goal.currentAmount.toLocaleString()} <span className="text-slate-500 font-normal">of {currencySymbol}{goal.targetAmount.toLocaleString()}</span>
                        </span>
                        <span className="font-bold text-indigo-400">{pct}%</span>
                      </div>

                      <div className="w-full h-2 rounded-full bg-white/10 overflow-hidden">
                        <div
                          className="h-full rounded-full transition-all duration-500"
                          style={{
                            width: `${pct}%`,
                            backgroundColor: goal.colorHex
                          }}
                        />
                      </div>
                    </div>

                    {/* Quick Deposit Actions */}
                    <div className="flex items-center gap-2 pt-1">
                      <button
                        onClick={() => {
                          setDepositGoalId(goal.id);
                          setDepositAmount('1000');
                        }}
                        className="flex-1 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-bold text-slate-200 flex items-center justify-center gap-1.5 transition-all cursor-pointer"
                      >
                        <ArrowUpCircle className="w-3.5 h-3.5 text-emerald-400" />
                        <span>+ Deposit Funds</span>
                      </button>

                      <button
                        onClick={() => {
                          depositToGoal(goal.id, 500);
                          confetti({ particleCount: 40, spread: 50, origin: { y: 0.7 } });
                        }}
                        className="px-3 py-1.5 rounded-xl bg-emerald-500/15 hover:bg-emerald-500/25 border border-emerald-500/30 text-xs font-bold text-emerald-300 transition-all cursor-pointer"
                      >
                        +{currencySymbol}500
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* BUDGET MODAL */}
      {isBudgetModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="w-full max-w-sm rounded-3xl bg-slate-900 border border-white/15 p-5 shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-extrabold text-white">
                {editingBudget ? 'Edit Category Budget' : 'Create Category Budget'}
              </h3>
              <button onClick={() => setIsBudgetModalOpen(false)} className="text-slate-400 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSaveBudget} className="space-y-3">
              <div>
                <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">Category</label>
                <select
                  value={budgetCategory}
                  onChange={e => setBudgetCategory(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-slate-800 border border-white/10 text-white text-xs font-semibold focus:outline-none focus:border-indigo-500"
                >
                  {categories.map(c => (
                    <option key={c.id} value={c.name}>{c.name}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">Monthly Limit ({currencySymbol})</label>
                <input
                  type="number"
                  step="any"
                  value={budgetLimit}
                  onChange={e => setBudgetLimit(e.target.value)}
                  placeholder="e.g. 8000"
                  required
                  className="w-full px-3.5 py-2.5 rounded-xl bg-slate-800 border border-white/10 text-white text-sm font-bold focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">Period Cycle</label>
                <select
                  value={budgetPeriod}
                  onChange={e => setBudgetPeriod(e.target.value as any)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-slate-800 border border-white/10 text-white text-xs font-semibold focus:outline-none focus:border-indigo-500"
                >
                  <option value="MONTHLY">Monthly</option>
                  <option value="WEEKLY">Weekly</option>
                  <option value="YEARLY">Yearly</option>
                </select>
              </div>

              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsBudgetModalOpen(false)}
                  className="flex-1 py-2.5 rounded-xl bg-white/10 text-slate-300 text-xs font-bold hover:bg-white/15 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 py-2.5 rounded-xl bg-indigo-600 text-white text-xs font-bold hover:bg-indigo-500 shadow-md cursor-pointer"
                >
                  Save Budget
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* GOAL MODAL */}
      {isGoalModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="w-full max-w-sm rounded-3xl bg-slate-900 border border-white/15 p-5 shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-extrabold text-white">
                {editingGoal ? 'Edit Savings Goal' : 'New Savings Goal'}
              </h3>
              <button onClick={() => setIsGoalModalOpen(false)} className="text-slate-400 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSaveGoal} className="space-y-3">
              <div>
                <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">Goal Title</label>
                <input
                  type="text"
                  value={goalTitle}
                  onChange={e => setGoalTitle(e.target.value)}
                  placeholder="e.g. MacBook Pro, Trip to Bali"
                  required
                  className="w-full px-3.5 py-2.5 rounded-xl bg-slate-800 border border-white/10 text-white text-xs font-bold focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">Target ({currencySymbol})</label>
                  <input
                    type="number"
                    value={goalTarget}
                    onChange={e => setGoalTarget(e.target.value)}
                    placeholder="e.g. 50000"
                    required
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-800 border border-white/10 text-white text-xs font-bold focus:outline-none focus:border-indigo-500"
                  />
                </div>
                <div>
                  <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">Already Saved ({currencySymbol})</label>
                  <input
                    type="number"
                    value={goalCurrent}
                    onChange={e => setGoalCurrent(e.target.value)}
                    placeholder="0"
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-800 border border-white/10 text-white text-xs font-bold focus:outline-none focus:border-indigo-500"
                  />
                </div>
              </div>

              <div>
                <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">Target Date</label>
                <input
                  type="date"
                  value={goalDate}
                  onChange={e => setGoalDate(e.target.value)}
                  required
                  className="w-full px-3.5 py-2.5 rounded-xl bg-slate-800 border border-white/10 text-white text-xs font-bold focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsGoalModalOpen(false)}
                  className="flex-1 py-2.5 rounded-xl bg-white/10 text-slate-300 text-xs font-bold hover:bg-white/15 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 py-2.5 rounded-xl bg-indigo-600 text-white text-xs font-bold hover:bg-indigo-500 shadow-md cursor-pointer"
                >
                  Save Goal
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* DEPOSIT MODAL */}
      {depositGoalId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="w-full max-w-xs rounded-3xl bg-slate-900 border border-white/15 p-5 shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-extrabold text-white">Deposit to Goal</h3>
              <button onClick={() => setDepositGoalId(null)} className="text-slate-400 hover:text-white">
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleDepositSubmit} className="space-y-3">
              <div>
                <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">Deposit Amount ({currencySymbol})</label>
                <input
                  type="number"
                  step="any"
                  value={depositAmount}
                  onChange={e => setDepositAmount(e.target.value)}
                  autoFocus
                  required
                  className="w-full px-3.5 py-2.5 rounded-xl bg-slate-800 border border-white/10 text-white text-base font-extrabold focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div className="flex gap-2">
                {[500, 1000, 2000].map(v => (
                  <button
                    key={v}
                    type="button"
                    onClick={() => setDepositAmount(v.toString())}
                    className="flex-1 py-1 rounded-lg bg-white/5 border border-white/10 text-xs font-bold text-slate-300 hover:bg-white/10"
                  >
                    +{v}
                  </button>
                ))}
              </div>

              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setDepositGoalId(null)}
                  className="flex-1 py-2 rounded-xl bg-white/10 text-slate-300 text-xs font-bold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 py-2 rounded-xl bg-emerald-600 text-white text-xs font-bold hover:bg-emerald-500 shadow-md"
                >
                  Deposit
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

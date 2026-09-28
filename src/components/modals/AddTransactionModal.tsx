import React, { useState, useEffect } from 'react';
import { X, Plus, Check } from 'lucide-react';
import { useFinance } from '../../context/FinanceContext';
import { TransactionType, PaymentMethod, FinanceScope } from '../../types';

const PAYMENT_METHODS: PaymentMethod[] = ['UPI', 'Cash', 'Credit Card', 'Debit Card', 'Bank Transfer'];

export const AddTransactionModal: React.FC = () => {
  const {
    isAddModalOpen,
    closeAddModal,
    addTransaction,
    updateTransaction,
    editingTransaction,
    categories,
    familyMembers,
    currentScope,
    currencySymbol,
    userProfile
  } = useFinance();

  const [title, setTitle] = useState('');
  const [amount, setAmount] = useState('');
  const [type, setType] = useState<TransactionType>('EXPENSE');
  const [category, setCategory] = useState(categories[0]?.name || 'Food & Dining');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('UPI');
  const [scope, setScope] = useState<FinanceScope>(currentScope);
  const [memberId, setMemberId] = useState(userProfile.id);
  const [note, setNote] = useState('');
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);

  useEffect(() => {
    if (editingTransaction) {
      setTitle(editingTransaction.title);
      setAmount(editingTransaction.amount.toString());
      setType(editingTransaction.type);
      setCategory(editingTransaction.category);
      setPaymentMethod(editingTransaction.paymentMethod);
      setScope(editingTransaction.financeScope);
      setMemberId(editingTransaction.createdByUserId || userProfile.id);
      setNote(editingTransaction.note || '');
      setDate(new Date(editingTransaction.dateMillis).toISOString().split('T')[0]);
    } else {
      setTitle('');
      setAmount('');
      setType('EXPENSE');
      setCategory(categories[0]?.name || 'Food & Dining');
      setPaymentMethod('UPI');
      setScope(currentScope);
      setMemberId(userProfile.id);
      setNote('');
      setDate(new Date().toISOString().split('T')[0]);
    }
  }, [editingTransaction, isAddModalOpen, currentScope, categories, userProfile.id]);

  if (!isAddModalOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const parsedAmount = parseFloat(amount);
    if (!title.trim() || isNaN(parsedAmount) || parsedAmount <= 0) return;

    const selectedMember = familyMembers.find(m => m.userId === memberId);
    const memberName = scope === 'FAMILY' ? (selectedMember?.name || userProfile.fullName) : userProfile.fullName;

    const txDate = new Date(date);
    const dateMillis = !isNaN(txDate.getTime()) ? txDate.getTime() : Date.now();

    if (editingTransaction) {
      updateTransaction({
        ...editingTransaction,
        title: title.trim(),
        amount: parsedAmount,
        type,
        category,
        paymentMethod,
        financeScope: scope,
        createdByUserId: memberId,
        createdByName: memberName,
        note: note.trim(),
        dateMillis
      });
    } else {
      addTransaction({
        title: title.trim(),
        amount: parsedAmount,
        type,
        category,
        paymentMethod,
        financeScope: scope,
        createdByUserId: memberId,
        createdByName: memberName,
        note: note.trim(),
        dateMillis
      });
    }

    closeAddModal();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="w-full max-w-md rounded-3xl bg-slate-900 border border-white/15 p-5 shadow-2xl max-h-[90vh] overflow-y-auto space-y-4 no-scrollbar">
        {/* Header */}
        <div className="flex items-center justify-between">
          <h2 className="text-base font-extrabold text-white">
            {editingTransaction ? 'Edit Transaction' : 'Record Transaction'}
          </h2>
          <button
            onClick={closeAddModal}
            className="p-1 rounded-xl text-slate-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Type Toggle: Expense vs Income */}
          <div className="flex rounded-2xl bg-slate-950 p-1 border border-white/10">
            <button
              type="button"
              onClick={() => setType('EXPENSE')}
              className={`flex-1 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                type === 'EXPENSE'
                  ? 'bg-rose-500 text-white shadow-md'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              Expense
            </button>
            <button
              type="button"
              onClick={() => setType('INCOME')}
              className={`flex-1 py-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                type === 'INCOME'
                  ? 'bg-emerald-500 text-white shadow-md'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              Income
            </button>
          </div>

          {/* Amount Input */}
          <div>
            <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">
              Amount ({currencySymbol})
            </label>
            <div className="relative">
              <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-lg font-black text-slate-400">
                {currencySymbol}
              </span>
              <input
                type="number"
                step="any"
                value={amount}
                onChange={e => setAmount(e.target.value)}
                placeholder="0.00"
                autoFocus
                required
                className="w-full pl-8 pr-4 py-2.5 rounded-2xl bg-slate-800 border border-white/10 text-white text-xl font-extrabold focus:outline-none focus:border-indigo-500 transition-colors"
              />
            </div>
          </div>

          {/* Title Input */}
          <div>
            <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">
              Title / Description
            </label>
            <input
              type="text"
              value={title}
              onChange={e => setTitle(e.target.value)}
              placeholder="e.g. Swiggy Lunch, Movie Tickets, Salary"
              required
              className="w-full px-3.5 py-2.5 rounded-2xl bg-slate-800 border border-white/10 text-white text-xs font-semibold focus:outline-none focus:border-indigo-500 transition-colors"
            />
          </div>

          {/* Category Dropdown */}
          <div>
            <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">
              Category
            </label>
            <select
              value={category}
              onChange={e => setCategory(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-2xl bg-slate-800 border border-white/10 text-white text-xs font-semibold focus:outline-none focus:border-indigo-500 cursor-pointer"
            >
              {categories
                .filter(c => type === 'INCOME' ? c.type === 'INCOME' : c.type === 'EXPENSE')
                .map(c => (
                  <option key={c.id} value={c.name} className="bg-slate-900 text-white">
                    {c.name}
                  </option>
                ))}
            </select>
          </div>

          {/* Payment Method */}
          <div>
            <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">
              Payment Method
            </label>
            <div className="grid grid-cols-3 gap-1.5">
              {PAYMENT_METHODS.map(pm => (
                <button
                  key={pm}
                  type="button"
                  onClick={() => setPaymentMethod(pm)}
                  className={`py-2 px-1 rounded-xl text-[11px] font-bold border transition-all truncate cursor-pointer ${
                    paymentMethod === pm
                      ? 'bg-indigo-600 border-indigo-400 text-white shadow-sm'
                      : 'bg-slate-800/80 border-white/5 text-slate-400 hover:text-white'
                  }`}
                >
                  {pm}
                </button>
              ))}
            </div>
          </div>

          {/* Scope Selector: Personal vs Family */}
          <div className="grid grid-cols-2 gap-2 pt-1">
            <button
              type="button"
              onClick={() => setScope('PERSONAL')}
              className={`py-2 rounded-xl text-xs font-bold border transition-all cursor-pointer ${
                scope === 'PERSONAL'
                  ? 'bg-indigo-600 border-indigo-400 text-white'
                  : 'bg-slate-800 border-white/5 text-slate-400'
              }`}
            >
              Personal Scope
            </button>
            <button
              type="button"
              onClick={() => setScope('FAMILY')}
              className={`py-2 rounded-xl text-xs font-bold border transition-all cursor-pointer ${
                scope === 'FAMILY'
                  ? 'bg-cyan-500 border-cyan-300 text-slate-950 font-black'
                  : 'bg-slate-800 border-white/5 text-slate-400'
              }`}
            >
              Family Vault
            </button>
          </div>

          {/* Paid By Member (Only if Family scope) */}
          {scope === 'FAMILY' && familyMembers.length > 0 && (
            <div>
              <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">
                Paid By Family Member
              </label>
              <select
                value={memberId}
                onChange={e => setMemberId(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-2xl bg-slate-800 border border-white/10 text-white text-xs font-semibold focus:outline-none focus:border-cyan-500 cursor-pointer"
              >
                {familyMembers.map(m => (
                  <option key={m.userId} value={m.userId} className="bg-slate-900 text-white">
                    {m.name} {m.userId === userProfile.id ? '(You)' : ''}
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Date Picker */}
          <div>
            <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">
              Date
            </label>
            <input
              type="date"
              value={date}
              onChange={e => setDate(e.target.value)}
              className="w-full px-3.5 py-2 rounded-2xl bg-slate-800 border border-white/10 text-white text-xs font-semibold focus:outline-none focus:border-indigo-500"
            />
          </div>

          {/* Note Input */}
          <div>
            <label className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-1 block">
              Additional Notes (Optional)
            </label>
            <textarea
              rows={2}
              value={note}
              onChange={e => setNote(e.target.value)}
              placeholder="e.g. Split with Priya, Receipt attached"
              className="w-full px-3.5 py-2 rounded-2xl bg-slate-800 border border-white/10 text-white text-xs focus:outline-none focus:border-indigo-500 resize-none"
            />
          </div>

          {/* Submit Actions */}
          <div className="flex gap-2 pt-2">
            <button
              type="button"
              onClick={closeAddModal}
              className="flex-1 py-3 rounded-2xl bg-white/10 hover:bg-white/15 text-slate-300 text-xs font-bold transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="flex-1 py-3 rounded-2xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold shadow-lg shadow-indigo-600/30 transition-all cursor-pointer"
            >
              {editingTransaction ? 'Save Changes' : 'Record Transaction'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

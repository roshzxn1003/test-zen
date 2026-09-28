import React from 'react';
import { User, Users } from 'lucide-react';
import { useFinance } from '../context/FinanceContext';

export const ScopeSwitcher: React.FC = () => {
  const { currentScope, setCurrentScope } = useFinance();
  const isPersonal = currentScope === 'PERSONAL';

  return (
    <div className="w-full h-11 p-1 rounded-2xl bg-slate-900/80 border border-white/10 flex gap-1 shadow-inner">
      <button
        onClick={() => setCurrentScope('PERSONAL')}
        className={`flex-1 flex items-center justify-center gap-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
          isPersonal
            ? 'bg-indigo-600 text-white shadow-md'
            : 'text-slate-400 hover:text-slate-200'
        }`}
      >
        <User className="w-4 h-4" />
        <span>Personal</span>
      </button>

      <button
        onClick={() => setCurrentScope('FAMILY')}
        className={`flex-1 flex items-center justify-center gap-2 rounded-xl text-xs font-bold transition-all cursor-pointer ${
          !isPersonal
            ? 'bg-cyan-500 text-slate-950 shadow-md font-extrabold'
            : 'text-slate-400 hover:text-slate-200'
        }`}
      >
        <Users className="w-4 h-4" />
        <span>Family Ledger</span>
      </button>
    </div>
  );
};

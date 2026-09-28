import React, { useState } from 'react';
import {
  Users,
  UserPlus,
  X,
  Copy,
  Check,
  Share2,
  Shield,
  ArrowRight
} from 'lucide-react';
import { useFinance } from '../../context/FinanceContext';
import { FamilyRole } from '../../types';

export const FamilyMembersModal: React.FC = () => {
  const {
    isFamilyModalOpen,
    closeFamilyModal,
    familyVault,
    familyMembers,
    addFamilyMember,
    joinFamilyVault,
    currencySymbol,
    familySettlementSummary
  } = useFinance();

  const [copiedCode, setCopiedCode] = useState(false);
  const [newMemberName, setNewMemberName] = useState('');
  const [newMemberRole, setNewMemberRole] = useState<FamilyRole>('MEMBER');
  const [joinCodeInput, setJoinCodeInput] = useState('');
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  if (!isFamilyModalOpen) return null;

  const handleCopyCode = () => {
    navigator.clipboard.writeText(familyVault.inviteCode);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  const handleAddMember = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newMemberName.trim()) return;
    addFamilyMember(newMemberName.trim(), newMemberRole);
    setNewMemberName('');
    setStatusMessage(`Added ${newMemberName.trim()} to the family vault!`);
    setTimeout(() => setStatusMessage(null), 3000);
  };

  const handleJoinVault = (e: React.FormEvent) => {
    e.preventDefault();
    if (!joinCodeInput.trim()) return;
    const res = joinFamilyVault(joinCodeInput);
    setStatusMessage(res.message);
    setJoinCodeInput('');
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="w-full max-w-md rounded-3xl bg-slate-900 border border-white/15 p-5 shadow-2xl max-h-[90vh] overflow-y-auto space-y-4 no-scrollbar">
        {/* Header */}
        <div className="flex items-center justify-between pb-1 border-b border-white/10">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-xl bg-cyan-500/20 text-cyan-400 flex items-center justify-center">
              <Users className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-extrabold text-white">Family Vault Members</h2>
              <p className="text-[10px] text-slate-400">{familyVault.name}</p>
            </div>
          </div>

          <button onClick={closeFamilyModal} className="text-slate-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>

        {statusMessage && (
          <div className="p-3 rounded-2xl bg-cyan-500/15 border border-cyan-500/30 text-xs font-bold text-cyan-300">
            {statusMessage}
          </div>
        )}

        {/* Invite Code Bar */}
        <div className="p-4 rounded-2xl bg-slate-950/60 border border-cyan-500/25 space-y-2">
          <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Family Invite Code</span>
          <div className="flex items-center justify-between">
            <span className="font-mono text-base font-black text-cyan-300 tracking-wider">
              {familyVault.inviteCode}
            </span>
            <div className="flex items-center gap-2">
              <button
                onClick={handleCopyCode}
                className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-cyan-500/20 text-cyan-300 text-xs font-bold hover:bg-cyan-500/30 cursor-pointer"
              >
                {copiedCode ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedCode ? 'Copied' : 'Copy'}</span>
              </button>
            </div>
          </div>
          <p className="text-[11px] text-slate-400">
            Share this invite code with family members to let them sync and contribute to shared expenses.
          </p>
        </div>

        {/* Active Members List */}
        <div className="space-y-2">
          <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">Connected Members ({familyMembers.length})</span>
          <div className="space-y-2">
            {familyMembers.map(member => (
              <div
                key={member.id}
                className="p-3 rounded-2xl bg-slate-800/60 border border-white/5 flex items-center justify-between"
              >
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-full bg-cyan-500/20 text-cyan-300 font-bold flex items-center justify-center text-xs">
                    {member.name.charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <p className="text-xs font-bold text-white">{member.name}</p>
                    <p className="text-[10px] text-slate-400">Joined {new Date(member.joinedAt).toLocaleDateString()}</p>
                  </div>
                </div>

                <span className={`px-2 py-0.5 rounded-md text-[10px] font-extrabold ${
                  member.role === 'ADMIN'
                    ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30'
                    : 'bg-white/10 text-slate-300'
                }`}>
                  {member.role}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Add New Member Form */}
        <form onSubmit={handleAddMember} className="p-3.5 rounded-2xl bg-slate-950/40 border border-white/5 space-y-3">
          <span className="text-xs font-extrabold text-white flex items-center gap-1.5">
            <UserPlus className="w-3.5 h-3.5 text-cyan-400" />
            Add Family Member
          </span>

          <div className="flex gap-2">
            <input
              type="text"
              value={newMemberName}
              onChange={e => setNewMemberName(e.target.value)}
              placeholder="e.g. Maya, Father, Roommate"
              required
              className="flex-1 px-3 py-2 rounded-xl bg-slate-800 border border-white/10 text-white text-xs focus:outline-none focus:border-cyan-500"
            />
            <select
              value={newMemberRole}
              onChange={e => setNewMemberRole(e.target.value as any)}
              className="px-3 py-2 rounded-xl bg-slate-800 border border-white/10 text-white text-xs font-semibold focus:outline-none focus:border-cyan-500 cursor-pointer"
            >
              <option value="MEMBER">Member</option>
              <option value="ADMIN">Admin</option>
              <option value="VIEWER">Viewer</option>
            </select>
          </div>

          <button
            type="submit"
            className="w-full py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-slate-950 text-xs font-extrabold transition-all cursor-pointer"
          >
            + Add to Vault
          </button>
        </form>

        {/* Join Another Vault Form */}
        <form onSubmit={handleJoinVault} className="p-3.5 rounded-2xl bg-slate-950/40 border border-white/5 space-y-2.5">
          <span className="text-xs font-extrabold text-white">Join Another Vault</span>
          <div className="flex gap-2">
            <input
              type="text"
              value={joinCodeInput}
              onChange={e => setJoinCodeInput(e.target.value)}
              placeholder="Enter invite code (e.g. FAM-XYZ123)"
              required
              className="flex-1 px-3 py-2 rounded-xl bg-slate-800 border border-white/10 text-white text-xs font-mono focus:outline-none focus:border-indigo-500"
            />
            <button
              type="submit"
              className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition-all cursor-pointer"
            >
              Join
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

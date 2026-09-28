# Security Specification: Zenith CashFlow

## 1. Data Invariants

1. **User Ownership Invariant**: A user document at `/users/{userId}` can only be read or written by the authenticated user whose `request.auth.uid == userId`.
2. **Subcollection Isolation Invariant**: All personal subcollections under `/users/{userId}/*` (transactions, categories, budgets, savings goals) are strictly partitioned to the parent user. No other user can read, list, create, update, or delete records in another user's subcollections.
3. **Family Membership Gate Invariant**: Any operation inside `/familyVaults/{vaultId}` or its subcollections requires active membership verified through `/familyVaults/{vaultId}/members/{memberId}` where `member.userId == request.auth.uid`.
4. **Identity Tampering Invariant**: For all transactions, `createdByUserId` must strictly match `request.auth.uid`. Users cannot forge records attributing spending or income to another person.
5. **Role-Based Authorization Invariant**: In a family vault, only members with `ADMIN` role can update the vault configuration, invite codes, or remove members. `MEMBER` can create and edit transactions. `VIEWER` has read-only access.
6. **Input Size & Type Invariant**: All string fields are constrained with bounded maximum lengths (e.g., titles <= 140 chars, IDs <= 128 chars, notes <= 500 chars). Numeric amounts must be non-negative real numbers.
7. **Default Deny Invariant**: Any path not explicitly matched is denied (`match /{document=**} { allow read, write: if false; }`).

## 2. The Dirty Dozen Payloads

1. **Payload 1 (Ghost Field Injection)**: Attempting to insert `isAdmin: true` into `/users/{userId}`. *Expected: PERMISSION_DENIED*.
2. **Payload 2 (ID Spoofing)**: Submitting a transaction with `createdByUserId: "attacker_uid"` under a different user's path. *Expected: PERMISSION_DENIED*.
3. **Payload 3 (Cross-User Read)**: Unauthenticated or non-owner user attempting `get` or `list` on `/users/victim_user/transactions`. *Expected: PERMISSION_DENIED*.
4. **Payload 4 (Massive String DOS)**: Submitting a transaction title with a 100KB junk string. *Expected: PERMISSION_DENIED* (max 140 chars).
5. **Payload 5 (Unauthenticated Write)**: Calling `create` on any collection when `request.auth == null`. *Expected: PERMISSION_DENIED*.
6. **Payload 6 (Unauthorized Family Access)**: Non-member reading `/familyVaults/{vaultId}/transactions`. *Expected: PERMISSION_DENIED*.
7. **Payload 7 (Role Escalation)**: Regular `MEMBER` attempting to update their role to `ADMIN` in `/familyVaults/{vaultId}/members/{memberId}`. *Expected: PERMISSION_DENIED*.
8. **Payload 8 (Negative Amount Exploit)**: Submitting a transaction with `amount: -999999`. *Expected: PERMISSION_DENIED*.
9. **Payload 9 (Orphaned Family Write)**: Attempting to write a family transaction without being an active member in the vault's `members` subcollection. *Expected: PERMISSION_DENIED*.
10. **Payload 10 (Viewer Write Exploit)**: A member with role `VIEWER` attempting to create a transaction. *Expected: PERMISSION_DENIED*.
11. **Payload 11 (Vault Deletion by Non-Admin)**: Standard member attempting to delete the parent `familyVaults/{vaultId}`. *Expected: PERMISSION_DENIED*.
12. **Payload 12 (Path Injection / Long Document ID)**: Attempting to create a document with an ID containing malicious symbols or exceeding 128 characters. *Expected: PERMISSION_DENIED*.

# Zenith CashFlow (React)

A personal and family shared finance application with AI voice transaction logging, receipt OCR scanning, category budgets, savings goals, instant UPI payments, and financial analytics.

## Features

- **Personal & Family Shared Ledger**:
  - Seamless toggle between Personal finances and collaborative Family Vault.
  - Fair share calculation, member settlement tracking ("You owe" / "You are owed"), and 1-tap invite code sharing (`FAM-XXXXXX`).
- **Zenith AI Voice Assistant**:
  - Natural speech-to-text and command parser supporting both English and Tanglish (Romanized Tamil) phrases (e.g., *"Spent 350 for lunch via UPI"*, *"Innaiku movie ki 250 selavu"*, *"Veetu vaadagai 14000 family vault bank transfer"*).
  - Clean English display and confirmation before logging.
- **Smart Receipt Scanner & OCR**:
  - Upload receipt photos or paste receipt text.
  - Automatically extracts merchant name, date, tax, subtotal, total, and itemized lines with quantities.
- **Instant UPI Payments & QR Scanning**:
  - NPCI / BHIM UPI intent generation with direct payment recording and UTR reference tracking.
- **Category Budgets & Savings Goals**:
  - Monthly spending limits with warning indicators (>80%, over-limit).
  - Target vaults with deposit trackers and celebratory confetti on reaching goals.
- **Financial Analytics & AI Coach**:
  - Spending distribution by category and family member.
  - Empirical 2-sentence actionable advice from Zenith AI coach based on real cash flow.
- **Offline First & Data Management**:
  - Full local persistence via localStorage.
  - JSON and CSV backup exports and restore.

## Tech Stack

- React 19 + TypeScript
- Vite 6 + Tailwind CSS v4
- Lucide React Icons
- Canvas Confetti

# Mulya Legacy UX Audit & Screen Inventory (v2.26)
**Task Reference:** MLY-004 (Capture and Audit Current UX)  
**Evaluator:** Pair Programming Analysis (Shailesh & Antigravity)  
**Total Screenshots Analyzed:** 30  
**Repository Directory:** `./docs/legacy-ux/`

---

## 1. Executive Summary & Observations

Review of the 30 legacy Android screenshots (frozen at `native-v2.26`) reveals the core design personality, major strengths, and clear redesign opportunities for the Mulya rebuild:

### Core Strengths (What to preserve in Mulya v2)
1. **Ultra-Fast Transaction Entry:** The add-transaction sheet with an embedded on-screen numeric keypad is exceptionally fast. Users can log an expense in 3 quick taps without waiting for the system soft keyboard to pop up or reflow the UI.
2. **Clear Top Balance Breakdown:** The hero balance card displaying total balance alongside active sub-balances (Bank vs. Cash) gives immediate situational awareness.
3. **Number Formatting Preferences:** Built-in support for Indian Lakhs/Crores (`1,00,000`) alongside International (`100,000`) formatting is a distinct, user-centric feature.
4. **Dark Theme Foundation:** The dark slate theme (`#0f172a`) with blue accents already provides a handsome visual baseline.

### Key Pain Points & Redesign Needs
1. **Navigation Duplication:** Navigation is split awkwardly between a 5-tab bottom navigation bar, top-bar buttons, and an 8-item navigation drawer (e.g., Transaction History exists in both the drawer and the home view).
2. **Personal Data Friction in Profile:** The profile screen features invasive personal inputs (Gender, DOB, Phone, City, State) which feel completely unnecessary for an offline personal finance tool.
3. **Category & Account Pickers:** Categories and accounts use small Android dropdown menus rather than a tactile icon/emoji grid.
4. **Unreachable / Orphaned Features:** "Plans" and "Recurring" forms were unreachable via standard drawer/bottom navigation; only "Upcoming Actions" was exposed on the home dashboard.

---

## 2. Complete Screen Inventory & Keep / Redesign / Remove Verdicts

| # | Screen / State | Screenshot File | What it does (1 line) | Verdict | Technical & UX Notes for Rebuild |
|---|---|---|---|---|---|
| **01** | Onboarding Slide 1 | `./Screenshot_20261015_001006.png` | Introduces multi-budget tracking (Personal, Savings, Biz). | **Redesign** | Modernize illustration; showcase multi-currency & offline-first sync. |
| **02** | Onboarding Slide 2 | `./Screenshot_20261015_001506.png` | Introduces analytics & category breakdown charts. | **Redesign** | Replace placeholder pencil icon with interactive micro-chart preview. |
| **03** | Onboarding Slide 3 | `./Screenshot_20261015_001526.png` | Introduces financial goals & savings targets. | **Redesign** | Streamline "Get Started" to drop user directly into workspace setup. |
| **04** | Initial Balance Dialog | `./Screenshot_20261015_001532.png` | Prompts for initial Bank and Cash amounts on first run. | **Redesign** | Replace hardcoded Bank/Cash with dynamic account creation & ledger opening balance. |
| **05** | Home Dashboard (Top) | `./Screenshot_20261015_001552.png` | Displays active budget balance, sub-accounts, profile pill, and action buttons. | **Keep & Polish** | Retain hero balance card; balance adjustments must log `ADJUSTMENT` transactions. |
| **06** | Home Dashboard (Bottom) | `./Screenshot_20261015_001615.png` | Shows Financial Overview donut chart and Cash Flow Trend line chart. | **Keep & Polish** | Replace flat zero-line with engaging empty-state visual prompt. |
| **07** | Transaction History (Empty) | `./Screenshot_20261015_001626.png` | Shows period filter chips, calendar picker, and PDF/CSV export button. | **Redesign** | Add illustration & quick "+ Add Expense" prompt; move export button off bottom navigation. |
| **08** | Upcoming Actions Sheet | `./Screenshot_20261015_001636.png` | Bottom sheet listing scheduled upcoming payments and reminders. | **Redesign** | Integrate actionable "Mark Paid" buttons; show recurrence frequency & due dates. |
| **09** | Analytics — Spending Tab | `./Screenshot_20261015_001655.png` | Displays spending insights, period toggles, donut chart, and trend line. | **Keep & Redesign** | Add category percentage breakdown table below donut chart. |
| **10** | Analytics — Income Tab | `./Screenshot_20261015_001701.png` | Displays income insights, period toggles, and income trend chart. | **Keep & Redesign** | Symmetrical with spending tab; add period-over-period comparison. |
| **11** | Add Transaction Sheet | `./Screenshot_20261015_001707.png` | Quick entry sheet with Expense/Income toggle, custom numeric keypad, and save button. | **Keep (Core UX)** | **Crown jewel of speed.** Keep keypad, add today date chip, multi-currency symbol support. |
| **12** | Account Dropdown | `./Screenshot_20261015_001712.png` | Popup menu showing hardcoded "BANK" and "CASH". | **Redesign** | Replace dropdown with dynamic account chip selector supporting all user accounts. |
| **13** | Category Dropdown | `./Screenshot_20261015_001716.png` | Vertical list of categories + "Edit Categories..." link. | **Redesign** | Replace text dropdown with an emoji/icon grid picker with search bar. |
| **14** | New Category Dialog | `./Screenshot_20261015_001719.png` | Modal to create custom category with emoji and title. | **Keep & Polish** | Add built-in emoji palette instead of requiring system emoji keyboard. |
| **15** | Goals Screen (Empty) | `./Screenshot_20261015_001731.png` | Shows goals list header, "+ Add Goal" button, and empty state graphic. | **Keep & Polish** | Retain clear visual call-to-action; wire to savings envelopes in v2 schema. |
| **16** | New Savings Goal Dialog | `./Screenshot_20261015_001736.png` | Modal input for Goal Name, Target Amount, and optional Deadline. | **Keep & Polish** | Add target account selector (which account holds the savings). |
| **17** | Navigation Drawer | `./Screenshot_20261015_001744.png` | Side drawer linking to History, Backup, Recycle Bin, Settings, Docs, About. | **Redesign** | Consolidate drawer into modern Profile/Settings screen to remove navigation duplication. |
| **18** | Backup & Restore | `./Screenshot_20261015_001800.png` | Allows manual JSON export and overwrite-based JSON restore. | **Redesign** | Replaced by automatic cloud/local sync in v2; keep JSON/CSV export for data portability. |
| **19** | Recycle Bin (Empty) | `./Screenshot_20261015_001808.png` | Lists soft-deleted transactions with restore options. | **Keep & Polish** | Implement 30-day purge retention countdown aligned with CONVENTIONS.md Q5. |
| **20** | Review & Feedback | `./Screenshot_20261015_001814.png` | Screen encouraging bug reports and feature suggestions. | **Remove** | Remove standalone screen; replace with simple GitHub/Email link in About. |
| **21** | Help & Docs | `./Screenshot_20261015_001819.png` | In-app user manual detailing dashboard, revenue, and charts. | **Keep & Polish** | Relocate under Settings -> Help & Docs; update content for v2 features. |
| **22** | Settings (Appearance & Prefs) | `./Screenshot_20261015_001824.png` | Theme picker, Currency Symbol, Indian/International formatting, Negative balance toggle. | **Keep & Redesign** | Maintain Indian number format toggle; note that negative balance is now strictly guarded in DB. |
| **23** | Settings (Alerts & Data) | `./Screenshot_20261015_001830.png` | Low balance alerts, Budget warning threshold, Auto backup reminder, Clear Data. | **Keep & Redesign** | Add double-confirmation protection to "Clear All Data"; enhance export options. |
| **24** | Settings (About links) | `./Screenshot_20261015_001832.png` | Navigation links to Help & Docs and About Us. | **Keep & Consolidate** | Standardize settings menu hierarchy. |
| **25** | About Us | `./Screenshot_20261015_001836.png` | App credits, v2.26 build number, developer handle (Messiah), copyright. | **Keep & Polish** | Update build version and credits for Mulya rebuild. |
| **26** | What's New in v2.26 | `./Screenshot_20261015_001841.png` | Release notes card list highlighting new features in update. | **Keep & Polish** | Great user engagement pattern; retain "What's New" modal on app version upgrades. |
| **27** | Dark Theme Home | `./Screenshot_20261015_001849.png` | Complete dashboard rendered in dark navy/slate theme. | **Keep & Polish** | Use CSS design tokens for slate background, elevated cards, and high-contrast text. |
| **28** | Profile & Budget Switcher | `./Screenshot_20261015_001902.png` | Personal profile form behind; "Switch Budget Profile" sheet in front. | **Redesign** | **Crucial finding:** Remove personal info inputs (Gender, DOB, City); streamline profile switcher. |
| **29** | New Budget Profile Dialog | `./Screenshot_20261015_001907.png` | Modal to create a new budget profile with name and emoji. | **Keep & Polish** | Maps directly to Multi-Workspace / Multi-Profile support. |
| **30** | Edit Budget Profile Dialog | `./Screenshot_20261015_001916.png` | Modal to edit name and icon of existing budget profile. | **Keep & Polish** | Add archive / delete profile action with safety checks for active transactions. |

---

## 3. Findings on Special Questions (Block B3 & B4)

### Q18 & Q19: Where are "Recurring" and "Plans"?
- **Finding:** Neither a dedicated "Plans" screen nor an independent "Recurring" management screen was reachable via standard top-level navigation (drawer or bottom bar).
- **Explanation:** In v2.26, recurring items and planned payments were partially consolidated under "Upcoming Actions" on the Home dashboard (`./Screenshot_20261015_001636.png`).
- **Rebuild Action:** Mulya v2 will provide a clear, first-class section for **Recurring & Scheduled Transactions** linked directly to the ledger scheduler.

### Five Direct Product Insights:
1. **Most Used Flow:** Adding an expense via bottom bar `[+]` -> entering amount on the embedded numeric keypad -> `[Save]`. Takes only **3 taps** and zero keyboard latency.
2. **Biggest Hesitation / Fumble:** Selecting categories from an unstyled dropdown list without visible emojis, and feeling disoriented by the KYC-style Profile screen.
3. **Screens Seldom / Never Opened:** Standalone "Review & Feedback", "About Us", and the personal profile inputs (DOB, Gender, City, State).
4. **Candidates to Delete Without Regret:**
   - The personal demographic fields in Profile.
   - The separate "Review & Feedback" top-level screen.
   - The redundant side navigation drawer (consolidating items into modern Settings / Profile tabs).
5. **The One Thing Mulya Does Better Than Other Apps:** **Frictionless speed.** Instant opening to the dashboard, instant custom numeric keypad with no soft keyboard delays, and clear, simple visibility into net worth without ad spam or bank login friction.

---

*Document compiled and audited for MLY-004.*

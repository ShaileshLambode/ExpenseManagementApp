# Technical Documentation - Mulya

## 🏗 Architecture
The application follows the **MVVM (Model-View-ViewModel)** architectural pattern to ensure separation of concerns and testability.

### Layers

1.  **Model (Data Layer)**
    - **Room Database**: Acts as the single source of truth (SQLite).
    - **Entities**: Represents database tables (Transactions, Profiles, Goals, etc.).
    - **DAO (Data Access Object)**: Defines methods to access and manipulate data.
    - **Repository**: Mediates between ViewModel and Data Sources (Room, Preferences).

2.  **ViewModel**
    - Holds and manages UI-related data in a lifecycle-conscious way.
    - Uses **LiveData** and **MediatorLiveData** to reactively update the UI.
    - Example: `MainViewModel`, `BudgetProfileViewModel`, `ExpensesViewModel`, `RecycleBinViewModel`.

3.  **View (UI Layer)**
    - Fragments and Activities responsible for rendering the UI.
    - **ViewBinding**: Used for safe interaction with XML layouts.
    - **Navigation Component**: Manages app flow via `nav_graph.xml`.

## 💾 Core Components

### Database Schema (Room)
The app uses `AppDatabase` with the following core entities:
- **BudgetProfileEntity**: Manages multiple independent budget contexts (e.g., Home, Work).
- **TransactionEntity**: Stores income/expense records linked to a specific profile.
- **BalanceEntity**: Tracks current Bank/Cash balances per profile.
- **CategoryEntity**: Custom categories for transactions.
- **GoalEntity**: Savings goals with target amounts and progress tracking.
- **RecurringTransactionEntity**: Template for automated transactions (Daily/Weekly/Monthly).

### Interactive Charts (`SparklineView.kt`, `DonutChartView.kt`)
- **Custom Canvas Drawing**: Uses low-level `Canvas` APIs for high-performance rendering.
- **Touch-Swipe Interaction**: Implements `OnTouchListener` to detect swipes and calculate the nearest data point on a path, providing real-time value tooltips.
- **Dynamic Legends**: The Donut chart features an interactive legend that highlights specific slices when tapped.

### Smart Backup & Restore (`BackupRestoreManager.kt`)
- **Format**: Structured JSON.
- **Data Integrity**: Uses `database.withTransaction` for atomic restores.
- **Self-Healing Recovery**: During restoration, if a transaction references a missing budget profile, the manager automatically reconstructs a placeholder profile to ensure data consistency.
- **Foreign Key Management**: Reorders insertion (Profiles -> Categories -> Transactions) to satisfy SQLite constraints.

### Recycle Bin Logic
- **Soft Delete**: Items are marked `is_deleted = true` with a `deleted_at` timestamp.
- **Auto-Purge**: A 30-day TTL (Time-To-Live) is enforced; older items are permanently removed.
- **Balance Reconciliation**: Restoring a transaction from the bin automatically triggers a balance update to reflect the "undeleted" funds.

## 🔧 Setup & Configuration

### Prerequisites
- Android Studio Koala or newer.
- JDK 17.
- Android SDK API 36 (VanillaIceCream).

### Build Variants
- **Debug**: Standard development build.
- **Release**: Optimized build with code shrinking (R8).

## 🔮 Future Improvements
- **Cloud Sync**: Optional Google Drive or Dropbox integration for cross-device sync.
- **Advanced Reports**: Exporting interactive HTML reports.
- **Security**: Biometric (Fingerprint/Face) lock for the application.

---
*Generated for Internal Development & Review - v2.26*

# Technical Documentation - Mulya

## 🏗 Architecture
The application follows the **MVVM (Model-View-ViewModel)** architectural pattern to ensure separation of concerns and testability.

### Layers

1.  **Model (Data Layer)**
    - **Room Database**: Acts as the single source of truth.
    - **Entities**: Represents database tables (`TransactionEntity`, `PendingPlanEntity`).
    - **DAO (Data Access Object)**: Defines methods to access the database.
    - **Repository**: Mediates between the ViewModel and the Data Source.

2.  **ViewModel**
    - Holds and manages UI-related data in a lifecycle-conscious way.
    - Uses **LiveData** or **Flow** to communicate changes to the UI.
    - Example: `MainViewModel`, `ProfileViewModel`, `PlansViewModel`.

3.  **View (UI Layer)**
    - Fragments and Activities responsible for rendering the UI.
    - Observes the ViewModel and updates the UI accordingly.
    - **ViewBinding**: Used for safe interaction with views.

## 💾 Core Components

### Database Schema
The app uses `AppDatabase` with the following key entities:
-   **TransactionEntity**: Stores income and expense records.
    -   Fields: `id`, `title`, `amount`, `type`, `category`, `date`, `mode` (Bank/Cash).
-   **PendingPlanEntity**: Stores future planned payments.
    -   Fields: `id`, `title`, `amount`, `dueDate`, `isCompleted`.

### Backup & Restore System (`BackupRestoreManager.kt`)
-   **Format**: JSON.
-   **Mechanism**:
    -   **Backup**: Queries all tables, serializes data to JSON using GSON, and saves to the device's internal/external storage.
    -   **Restore**: Reads the JSON file, parses it, validates structure, wipes existing tables, and inserts the restored data atomically.

### PDF Export (`ExportUtils.kt`)
-   Uses Android's native `PdfDocument` API.
-   Draws text and lines programmatically via `Canvas`.
-   Supports pagination for long transaction lists.

## 🎨 UI Guidelines
-   **Theme**: Material Design 3.
-   **Dark Mode**: Fully supported via `values-night` resources.
-   **Custom Views**: Custom drawing logic for charts (if applicable) or specialized RecyclerView adapters.

## 🔧 Setup & Configuration

### Prerequisites
-   Android Studio Koala or newer.
-   JDK 17.
-   Android SDK API 34 (UpsideDownCake).

### Build Variants
-   **Debug**: Includes logging and debugging symbols.
-   **Release**: Minified and obfuscated (configured in `build.gradle.kts`).

## 🔮 Future Improvements
-   **Cloud Sync**: Optional Google Drive integration.
-   **Budgeting**: Set monthly limits per category.
-   **Recurring Transactions**: Auto-add expenses each month.

---
*Generated for Internal Development & Review*

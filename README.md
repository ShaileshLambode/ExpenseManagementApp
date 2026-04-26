# Mulya - Personal Expense Manager 💰

Mulya is a privacy-focused, offline-first personal finance application for Android. It helps you track expenses, manage income, and plan future payments without requiring an internet connection or account login.

![App Banner](docs/banner_placeholder.png)

## ✨ Features

- **Interactive Trend Charts**: Experience your data with touch-enabled charts. Swipe to see exact amounts and dates at any point in time.
- **Multiple Budget Profiles**: Create and switch between distinct financial profiles (e.g., Personal, Business) seamlessly.
- **Smart Recycle Bin**: Accidentally deleted something? Restore it within 30 days. Balances are automatically re-adjusted on restoration.
- **Savings Goals**: Set up and track custom savings goals with visual progress bars.
- **Recurring Transactions**: Automate your subscriptions, bills, or regular income with customizable frequencies (Daily, Weekly, Monthly).
- **Dashboard**: Visual overview of your financial health with sparklines, donut charts, and an integrated "Upcoming Actions" summary.
- **Transaction Tracking**: Record Expenses and Revenue using a custom-built numeric keypad.
- **Guided Feedback**: A new portal to report bugs or suggest features directly to the developers.
- **Backup & Restore**: Securely backup your entire database to a local JSON file and restore with built-in data integrity checks.
- **Export**: Export financial records to CSV or PDF formats.
- **Customization**: Dark Mode support, Currency formatting, and a detailed User Profile.
- **Zero Ads & Privacy**: 100% offline. Your data never leaves your device. No ads, no tracking.

## 🛠 Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room Database (SQLite)
- **Concurrency**: Coroutines & WorkManager (for recurring tasks)
- **UI**: XML Layouts, Material Design 3, Custom Canvas Drawing (for interactive charts)
- **Navigation**: Android Jetpack Navigation
- **Other Libs**: Gson (JSON parsing), DotsIndicator (Onboarding)

## 📂 Project Structure

```text
ExpenseManagementApp/
├── app/
│   ├── src/main/java/com/example/expensemanagementapp/
│   │   ├── data/             # Room Database, Entities, DAO
│   │   ├── repository/       # Data Repositories
│   │   ├── ui/               # Fragments, Activities, Adapters
│   │   │   ├── add/          # Custom Keypad & Transaction entry
│   │   │   ├── backup/       # JSON Backup & Restore (Self-healing)
│   │   │   ├── custom/       # Interactive Canvas Views (Charts)
│   │   │   ├── dashboard/    # Dashboard & Analytics
│   │   │   ├── help/         # Help, Docs & Guided Feedback
│   │   │   ├── history/      # Transaction history & filtering
│   │   │   ├── plans/        # Recurring & Upcoming Action logic
│   │   │   ├── profile/      # User Profile management
│   │   │   ├── recyclebin/   # Deleted item recovery (30-day purge)
│   │   │   └── settings/     # App Settings & "What's New"
│   │   ├── utils/            # Helper classes (Export, BackupManager)
│   │   └── viewmodel/        # ViewModels for UI state management
│   └── src/main/res/         # XML Layouts, Drawables, Themes
```

## 🚀 Getting Started

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/yourusername/ExpenseManagementApp.git
    ```
2.  **Open in Android Studio**:
    - File > Open > Select the project directory.
3.  **Build and Run**:
    - Connect a device or start an emulator.
    - Click the **Run** button (Shift+F10).

## 🤝 Contributing

Contributions are welcome!
1.  Fork the project
2.  Create your feature branch (`git checkout -b feature/AmazingFeature`)
3.  Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4.  Push to the branch (`git push origin feature/AmazingFeature`)
5.  Open a Pull Request

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

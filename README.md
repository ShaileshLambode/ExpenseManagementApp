# Mulya - Personal Expense Manager 💰

Mulya is a privacy-focused, offline-first personal finance application for Android. It helps you track expenses, manage income, and plan future payments without requiring an internet connection or account login.

![App Banner](docs/banner_placeholder.png)

## ✨ Features

- **Multiple Budget Profiles**: Create and switch between distinct financial profiles (e.g., Personal, Business) seamlessly.
- **Dashboard**: Visual overview of your financial health with sparklines, donut charts, and an integrated "Upcoming Actions" summary.
- **Transaction Tracking**: Easily record Expenses (Bank/Cash) and Revenue using a custom-built numeric keypad.
- **Savings Goals**: Set up and track custom savings goals. Adding money to goals automatically updates your balances.
- **Recurring Transactions**: Automate your subscriptions, bills, or regular income with customizable frequencies.
- **Upcoming Actions**: Schedule future payments and track receivables directly from the dashboard bottom sheets.
- **Category Management**: Edit, delete, and add new transaction categories dynamically.
- **History**: Detailed transaction history with date grouping and filters (Today, Week, Month).
- **Data Privacy**: 100% offline. Your data never leaves your device.
- **Backup & Restore**: Securely backup your entire database to a local JSON file and restore when needed.
- **Export**: Export financial records to CSV or PDF formats.
- **Customization**: Dark Mode support, Currency formatting (Indian/International), and a detailed User Profile.
- **Zero Ads**: Clean, interruption-free user experience.

## 📱 Screenshots

| Dashboard | Upcoming Actions | Add Transaction |
|:---:|:---:|:---:|
| ![Dashboard] | ![Actions] | ![Add] |

| History | Settings | Profile |
|:---:|:---:|:---:|
| ![History] | ![Settings] | ![Profile] |

*(Note: Add screenshots to a `docs/` folder in your repo)*

## 🛠 Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room Database (SQLite)
- **Concurrency**: Coroutines & Flow
- **UI**: XML Layouts, Material Design Components, BottomSheetDialogFragments
- **Navigation**: Android Jetpack Navigation
- **Other Libs**: Gson (JSON parsing), Custom canvas drawing for Charts

## 📂 Project Structure

```text
ExpenseManagementApp/
├── app/
│   ├── src/main/java/com/example/expensemanagementapp/
│   │   ├── data/             # Room Database, Entities, DAO
│   │   ├── repository/       # Data Repositories
│   │   ├── ui/               # Fragments, Activities, Adapters, BottomSheets
│   │   │   ├── add/          # Custom Keypad & BottomSheets for entries
│   │   │   ├── backup/       # JSON Backup & Restore logic
│   │   │   ├── custom/       # Custom Views (DonutChart)
│   │   │   ├── dashboard/    # Dashboard UI & Analytics
│   │   │   ├── history/      # Transaction history & filtering
│   │   │   ├── plans/        # Recurring and Upcoming Action logic
│   │   │   ├── profile/      # User Profile
│   │   │   └── settings/     # App Settings & Preferences
│   │   ├── utils/            # Helper classes (Export, Formatting)
│   │   └── viewmodel/        # ViewModels for UI state management
│   └── src/main/res/         # XML Layouts, Drawables, Strings, Themes
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

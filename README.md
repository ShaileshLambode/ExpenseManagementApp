# Mulya - Personal Expense Manager 💰

Mulya is a privacy-focused, offline-first personal finance application for Android. It helps you track expenses, manage income, and plan future payments without requiring an internet connection or account login.

![App Banner](docs/banner_placeholder.png)

## ✨ Features

- **Dashboard**: Visual overview of your financial health with Sparkline and Donut charts.
- **Transaction Tracking**: Easily record Expenses (Bank/Cash) and Revenue.
- **Plans**: Schedule future payments and track receivables.
- **History**: Detailed transaction history with date grouping and filters (Today, Week, Month).
- **Data Privacy**: 100% offline. Your data never leaves your device.
- **Backup & Restore**: Securely backup your data to a local JSON file and restore when needed.
- **Export**: Export financial records to CSV or PDF formats.
- **Customization**: Dark Mode support, Currency formatting (Indian/International), and detailed User Profile.
- **Zero Ads**: Clean, interruption-free user experience.

## 📱 Screenshots

| Dashboard | Expenses | History |
|:---:|:---:|:---:|
| ![Dashboard] | ![Expenses] | ![History] |

| Plans | Settings | Profile |
|:---:|:---:|:---:|
| ![Plans] | ![Settings] | ![Profile] |

*(Note: Add screenshots to a `docs/` folder in your repo)*

## 🛠 Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room Database (SQLite)
- **Concurrency**: Coroutines & Flow
- **UI**: XML Layouts, Material Design Components
- **Navigation**: Android Jetpack Navigation
- **Other Libs**: Gson (JSON parsing), MPAndroidChart (if used, or custom views)

## 📂 Project Structure

```text
ExpenseManagementApp/
├── app/
│   ├── src/main/java/com/example/expensemanagementapp/
│   │   ├── data/             # Room Database, Entities, DAO
│   │   ├── repository/       # Data Repositories
│   │   ├── ui/               # Fragments, Activities, Adapters
│   │   │   ├── dashboard/    # Dashboard UI & Logic
│   │   │   ├── expenses/     # Add/Edit Transaction UI
│   │   │   ├── history/      # Components to view history
│   │   │   ├── plans/        # Future Plans management
│   │   │   ├── profile/      # User Profile
│   │   │   └── settings/     # App Settings & Preferences
│   │   ├── utils/            # Helper classes (Export, Backup, Formatting)
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

<p align="center">
  <img src="jeib-logo-rounded.png" alt="Jeib logo" width="120">
</p>

<h1 align="center">Jeib</h1>

<p align="center">A local-first desktop app to plan, track, and review your spending.</p>

Set monthly limits, log payments, and see where you stand. At month end, Jeib generates a report
and an Excel file. Your data stays on your computer by default; optionally, sync it across devices
through your own backend.

**Status:** planning / early development. See [the roadmap](developer-roadmap.docx).

## Features

### v1.0: everything that works offline

**Track**
- Add, edit, and delete income and expenses (amount, date, category, note)
- Editable categories, multiple accounts (cash, card, savings), multi-currency
- Recurring payments with reminders for upcoming bills
- Search, filters, and tags
- **CSV import** from bank statements with automatic categorization

**Plan**
- Monthly limit per category and an overall monthly limit
- Limit status with in-app notifications: **OK**, **Warning** (≥ 80%), **Exceeded** (≥ 100%)
- Savings goals with progress tracking

**Understand**
- Dashboard: spent so far, remaining, chart
- **Spending forecast**: "At this pace you'll exceed Food around day 22"
- Month-over-month comparison
- Subscription finder: spots repeated monthly payments
- Monthly report, with Excel and PDF export

**Work fast**
- **Undo/redo** for every add, edit, and delete (Ctrl+Z / Ctrl+Y)
- **Quick-add bar** and keyboard shortcuts: type `250 food lunch`, press Enter

**Stay safe**
- **Password lock** with an encrypted database
- **Automatic backups**: a daily local snapshot, last 7 kept, one-click restore
- Crash log with "Report a problem"

**Feel at home**
- Dark mode, first-run setup, remembers your window layout
- Auto-update check and a "Send feedback" button

### v2.0: optional cloud sync

- Account: register, log in, log out
- Sync across devices — the app still works fully offline
- Sync status indicator
- Reports generated on the server
- Shared household budget (stretch goal)

## Architecture

```
┌────────────────────┐   sync (HTTPS, JWT)   ┌──────────────────┐      ┌────────────┐
│ JavaFX desktop app │ ◄───────────────────► │ Spring Boot API  │ ◄──► │ PostgreSQL │
│  SQLite (local)    │                       └────────┬─────────┘      └────────────┘
│  works offline     │                                │
└────────────────────┘                  ┌─────────────┴─────────────┐
                                        │                           │
                              Redis (cache, rate limit)   RabbitMQ (reports, Excel jobs)
```

The app writes to SQLite first, so the UI never waits for the network. Sync runs in the
background when you're logged in and online. Conflicts resolve with last-write-wins; pushes are
idempotent (client-generated UUIDs).

## Tech stack

| Area | Tools |
|---|---|
| Desktop | Java 21, JavaFX 21 (FXML + CSS), SQLite, Flyway, Apache POI |
| Backend | Spring Boot 3, Spring Security (JWT), Spring Data JPA, PostgreSQL |
| Infrastructure | Redis, RabbitMQ, Docker, docker-compose, GitHub Actions |
| Quality | JUnit 5, Mockito, Testcontainers, Actuator, Prometheus, Grafana |
| Packaging | jpackage installers via GitHub Releases |

## Getting started

Installers will be published on the GitHub Releases page starting with v1.0 (planned for
December 20, 2026). Users won't need Java installed.

### Build from source

You need **JDK 21** with `JAVA_HOME` pointing to it. Maven comes with the repo (`mvnw`).

```bash
./mvnw javafx:run          # run the app
./mvnw test                # run the tests
./mvnw package             # build target/jeib-*.jar and target/libs/
```

On Windows, use `mvnw.cmd` instead of `./mvnw`. To build `dist\Jeib\Jeib.exe` (bundles its own Java
runtime):

```bash
powershell -ExecutionPolicy Bypass -File scripts/package-windows.ps1
```

Jeib keeps its database in your app-data folder (`%APPDATA%\Jeib\jeib.db` on Windows,
`~/Library/Application Support/Jeib` on macOS, `~/.local/share/jeib` on Linux).

### Project layout

```
src/main/java/com/jeib/
  model/        records and rules (Category, Money, LimitRules)
  db/           SQLite connection and Flyway migrations
  repository/   SQL for each table
  ui/           JavaFX views (FXML), view-models, theme switching
src/main/resources/
  db/migration/          V1__initial_schema.sql, V2__default_categories.sql
  com/jeib/ui/styles/    theme-light.css, theme-dark.css (color tokens), app.css (components)
```

## Documentation

Architecture and analysis docs (SRS, UML, ERD, BPMN, C4 diagrams, ADRs, performance report) will
live in [`/docs`](docs/).

## Roadmap

| Milestone | Target |
|---|---|
| JavaFX + SQLite skeleton, schema migrated | Oct 25, 2026 |
| Core features, limits, dashboard, undo/redo, quick-add | Nov 22, 2026 |
| Private build in daily use (self-test starts) | Nov 29, 2026 |
| **v1.0** released on GitHub with every offline feature | Dec 20, 2026 |
| **v2.0**: sync on two devices, Redis, RabbitMQ, CI/CD | Jan 17, 2027 |
| Documentation package complete | Feb 7, 2027 |
| Performance report | Feb 28, 2027 |

## Feedback

Found a bug or have an idea? Open a GitHub Issue (bug, idea, and question templates).

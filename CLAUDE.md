# CLAUDE.md

Guidance for Claude when working in this folder.

## What this is

This folder holds **Khalil's career roadmap** (`developer-roadmap.docx`) and will become the home of
**Jeib** (formerly "MoneyMap"), the single project the roadmap is built around. The goal is to go
from student to job-ready **Junior Backend Developer (Java) / System Analyst** by **end of March /
early April 2027**.

- Start: Mon, Oct 12, 2026. 24 weeks + 1 buffer week.
- Time budget: 4-5 h/day, 6 days/week (~25-30 h/week). Sunday is rest + 20-min weekly review.
- Daily split: Build 2.5-3 h, Learn 1 h, Practice (DSA/SQL/system design) 30-60 min.

The `.docx` is the source of truth for the plan. See "Editing the roadmap docx" below before
touching it.

## The product: Jeib

A **local-first desktop app** to plan, track, and review personal spending. Monthly limits per
category and overall, limit status notifications, monthly report, Excel export. Optional sync
across devices via the user's own Spring Boot backend (Phase 3).

### Brand

- Logo: `jeib-logo-rounded.png` (512x512, white "J" on an orange rounded square).
- Brand color: **#FF751F** (orange). It's an accent only (~10% of the screen).
- **`jeib-color-system.md` is the source of truth for all UI colors, typography, and shape**
  (light + dark tokens, status colors, chart palette, JavaFX CSS structure). Never hard-code hex
  values in component styles; use its tokens. Text on orange buttons is dark (`on-primary`), not
  white — white on the brand orange is only 2.7:1.

### Stack

| Area | Tool |
|---|---|
| Language | Java 21 |
| Desktop UI | JavaFX 21 (FXML + CSS), MVVM-style layering |
| Local DB | SQLite + Flyway |
| Excel / charts | Apache POI / JavaFX charts |
| Packaging | jpackage, distributed via GitHub Releases |
| Backend | Spring Boot 3, Spring Security (JWT), Spring Data JPA |
| Server DB | PostgreSQL |
| Cache / rate limit | Redis |
| Background jobs | RabbitMQ (report + Excel generation, DLQ) |
| Infra | Docker + docker-compose, GitHub Actions, VPS with Caddy/Nginx (HTTPS) |
| Tests | JUnit 5, Mockito, Testcontainers |
| Monitoring | Actuator + Prometheus + Grafana |
| Docs | OpenAPI, Mermaid/PlantUML, C4, ADRs |

Python is secondary: fake-data generation (100k-500k transactions), analysis, load-test helpers.

**Explicitly out of scope:** microservices, Kubernetes, GraphQL, web frontend, mobile app.
Recommend against them if they come up — a well-structured monolith is the decision.

**Design (Nov 11, 2026):** JavaFX stays for now. Khalil wants a much more professional design that
doesn't look like a basic AI-made UI, but that overhaul comes **after Dec 1**. Until then keep every
color, size, and radius in the CSS tokens and keep screens simple, so the redesign is mostly CSS
and layout, not rewritten logic.

### Features (final scope, agreed Oct 24, 2026)

Rule: **v1.0 (Dec 20, 2026) has every feature that works without a server. v2.0 (Jan 17, 2027)
adds sync and everything that depends on it.** Numbers are the ones used when the scope was agreed.

**Ready to use by Dec 1, 2026 (decided Nov 11; replaces the week-by-week order below).** Plan
by scope, not by roadmap week. Dec 1 build, for Khalil plus 2-3 survey respondents, as a zipped
app image (no installer):

1. Categories: create, rename/recolor, delete (transactions move to "Other")
2. Transactions: add, edit, delete, monthly list, working month switcher, validation
3. Quick-add bar ("250 food lunch")
4. Undo/redo through the service layer (Command pattern)
5. Per-category + overall limits, OK/WARNING/EXCEEDED, in-app alert
6. Dashboard: spent, remaining, **left per day until month end**, category chart. Decided Nov 12:
   "left per day" = (overall limit − spent this month) ÷ days left including today, **limit only**
   (no income-based option, no switch); without an overall limit the card asks the user to set one.
7. Automatic daily backup (keep 7) + crash log
8. `Jeib.exe` app image, zipped
9. Currencies (added Nov 11): **USD, ILS, RUB**. Each transaction has one of them; the user picks
   a **main currency** when setting up their plan (a small first-launch step), and totals, limits,
   and "left per day" are shown in it using **manual exchange rates** ("1 USD = 92.50 RUB") they
   can update any time. Changing the main currency re-expresses the rates automatically.

Currency rules: all three have 2 decimals (what `Money` assumes; a 3-decimal currency like JOD
needs `Money` changes first). Rates are `BigDecimal` (max 6 decimals), stored in `settings` as
`main_currency` and `rate.<CODE>` (main currency's own rate is never stored). Convert sums per
currency, then convert once (`ExchangeRates.toMain`, half-even rounding). A transaction in a
non-main currency needs its rate set first. **Budgets (Week 5):** limits are in the main currency;
when the main currency changes, existing limits must be converted too.

Milestones: Nov 11-14 categories → `TransactionService` → Transactions screen · Nov 16-21
add/edit dialog, quick-add, delete, undo/redo · Nov 23-28 limits, statuses, dashboard · Nov 30 -
Dec 1 backup, crash log, packaging, bug bash. After Dec 1: monthly report + Excel/PDF (needed by
the first month end, Dec 31), then the rest of the list below in the order real use shows.
Student survey findings that shaped this: `docs/user-research.md`.

**v1.0 — 34 features, by roadmap week (original order, now flexible):**

| Week | Features |
|---|---|
| 3-4 | (1-5) add/edit/delete transactions, monthly list, editable categories, validation · (13) undo/redo (Command pattern) |
| 5 | (6-9) per-category + overall limits, OK/WARNING/EXCEEDED, notifications · (10) dashboard · (14-15) shortcuts + quick-add bar ("250 food lunch") |
| 6 | (11) monthly report · (12) Excel export |
| 7 | (18) installers · (17) "Send feedback" · (16) automatic daily backups, keep 7 · (37) crash log + "Report a problem" — **private build into daily use Nov 29** (self-test starts) |
| 8 | (20) multiple accounts · (21) multi-currency (manual exchange rates) · (19) recurring payments + bill reminders · (33) subscription finder · (32) savings goals |
| 9 | (24) CSV import + keyword auto-categorization · (22) search/filters/tags · (25) spending forecast · (31) month-over-month comparison · (34) PDF report |
| 10 | (35) password lock + SQLCipher-encrypted DB · (23) dark mode · (36) first-run setup · (38) remembered window size · (26) auto-update check · README → **public v1.0 release** |

SQLCipher note: the standard `sqlite-jdbc` driver can't encrypt; v1.0 needs an SQLCipher-capable
JDBC driver.

Categories (decided Oct 25, 2026): users create their own (feature 4: a name plus one of the 14
chart colors from `jeib-color-system.md`; every category gets a unique color, see its assignment
rules), and two built-in categories always exist and can't be
deleted: **Other** (expense) and **Other income**. They're the fallback for quick-add and CSV
import rows that match no category.

UI interaction rule: hover highlights (orange border, arrow) only go on things that are clickable.
Dashboard cards, limit rows, chart legend rows, and insights are all clickable drill-downs into
filtered Transactions.

**v2.0 — sync only:** (27) account: register/log in/log out · (28) sync across devices · (29) sync
status indicator · (30) server-generated reports (RabbitMQ) · (39) shared household budget
(stretch: only if sync is solid).

**Not planned:** bank API integrations, AI chat, investment tracking, receipt OCR, web/mobile.

### Languages (decided Nov 12, 2026): English + Arabic

- **Every user-facing string goes through `Messages.t("English text", args…)`** (gettext-style: the
  English sentence is the key; placeholders `{0}`, `{1}` are replaced literally, no MessageFormat).
  In FXML write `text="%English text"` (loaders use `Messages.bundle()`). Add the Arabic in
  `i18n/Arabic.java`; `ArabicCoverageTest` fails if a sentence or a placeholder is missing.
  Keys reached through a variable (page names, color names…) are listed in that test's `INDIRECT`.
- Arabic is right-to-left: the window root gets `NodeOrientation.RIGHT_TO_LEFT`, which mirrors
  every layout. Amounts and other left-to-right snippets go through `Messages.ltr` (built into
  `SupportedCurrency.format`; use `formatPlain` + `ltr` when adding a sign: "+$300.00").
  Arabic counts are phrased "عدد المعاملات: 12" to avoid plural agreement.
- Digits: Arabic-Indic input (٢٥٠٫٥) is accepted everywhere amounts are parsed (`model/Digits`);
  displayed numbers stay Western (250.50). Dates and month names follow the language.
- The 11 default categories show in the app's language while their names are unchanged
  (`i18n/DefaultCategories`, applied in `CategoryRepository.map`). Quick add knows Arabic
  currency words (دولار، شيكل، روبل).
- The language is saved in settings (`PreferencesService`, key `language`), read in
  `JeibApp.init()` before the window is built (default: Arabic on an Arabic Windows, else
  English). Switching (setup or Settings) saves it and rebuilds the window (`LanguageSwitcher`,
  `JeibApp.rebuildIn`). Sidebar items use `userData` as their fixed page id, never their text.

## Non-negotiable engineering rules

These come straight from the roadmap; follow them in any code you write.

- **Money is never `double`.** Store integer minor units (cents) in the DB; use `long` or
  `BigDecimal` in Java.
- **IDs are UUIDs (TEXT), generated by the client.** No auto-increment on synced tables.
- **Every synced table has** `created_at`, `updated_at` (ISO-8601 UTC), `deleted` (soft delete,
  0/1), and `version`. Never hard-delete synced rows.
- **Never block the JavaFX UI thread.** DB, file, and network work goes in `Task`; UI updates go
  through `Platform.runLater`.
- **Local-first:** writes go to SQLite first; the UI never waits on the network. Sync runs in the
  background only when logged in and online.
- **Schema changes go through Flyway migrations**, never ad-hoc DDL.
- **Sync:** push + pull, last-write-wins by `updated_at`/`version`, idempotent upserts keyed on
  client UUIDs, server pulls by `server_seq > last_pull_token`, unique `(user_id, id)` on server.
- **Limit status:** `percent = spent / limit * 100`; `>= 100` → `EXCEEDED`, `>= 80` → `WARNING`,
  else `OK`. `spent` = sum of non-deleted EXPENSE transactions in that category and month. Budget
  with `category_id NULL` = overall limit; `month NULL` = default for every month. Test the edges:
  exactly 80%, exactly 100%, month boundaries, no limit set.
- **Undo/redo** must go through the service layer as commands, so undoing a delete restores the
  soft-deleted row (and bumps `version`/`updated_at` so sync sees it).

Starting schema (SQLite): `accounts`, `categories`, `transactions` (with `sync_state`
PENDING/SYNCED), `budgets`, `recurring_payments` (v2), `sync_state` (key/value), `settings`
(key/value). Indexes on `transactions(occurred_on)` and `transactions(category_id, occurred_on)`.
Server adds `users`, `devices`, `user_id` + `server_seq BIGSERIAL` on each synced table.

## Timeline

Rescheduled Oct 24, 2026 so all local features fit in v1.0; the April deadline is unchanged.
Nov 11, 2026: Phase 2's weekly order no longer binds; the hard date is the Dec 1 usable build
(see Features). Dec 20 v1.0 stays the target, to be re-checked on Dec 1.

| Phase | Weeks | Dates | Output |
|---|---|---|---|
| 1. Foundations & design | 1-2 | Oct 12 - Oct 25 | Brief, mockups, JavaFX + SQLite skeleton, first migration, jpackage hello-world |
| 2. Desktop v1.0 | 3-10 | Oct 26 - Dec 20 | Private build in daily use Nov 29; v1.0 with all local features on GitHub Dec 20 |
| 3. Sync backend | 11-14 | Dec 21 - Jan 17 | Spring Boot API, auth, sync, Redis, RabbitMQ, CI/CD, monitoring, v2.0 (Weeks 11-12 are holidays) |
| 4. Analysis & design | 15-17 | Jan 18 - Feb 7 | SRS, UML, ERD, BPMN, C4, 6+ ADRs, "scale to 100k users" write-up in `/docs` |
| 5. Scale & sharpen | 18-20 | Feb 8 - Feb 28 | Performance report (before/after), DSA, system design drills |
| 6. Job hunt | 21-24 | Mar 1 - Mar 28 | CV, mock interviews, 40+ applications |
| Buffer | 25 | Mar 29 - Apr 4 | Catch-up |

Parallel tracks: DSA from Week 6 (2-3/week → 4-5/week in Phase 5), weekly communication practice,
Sunday review in `journal.md`, applications from Week 16 (daily from Week 21).

**If behind schedule, protect:** the Dec 1 usable build (self-test), then the released MVP (Phase 2), sync basics (auth + push/pull), and
the documentation package (Phase 4). Cut extras, fancy Excel formatting, and Phase 5 volume first.

## Commands

Use JDK 21 (`JAVA_HOME`); the Maven wrapper is in the repo. On Windows use `mvnw.cmd`.

```bash
./mvnw javafx:run                       # run the app
./mvnw test                             # all tests (JUnit 5)
./mvnw test -Dtest=LimitRulesTest       # one test class
./mvnw package                          # jar + target/libs
powershell -ExecutionPolicy Bypass -File scripts/package-windows.ps1   # dist\Jeib\Jeib.exe (app image)
```

## Code layout

- `com.jeib.Launcher` is the entry point (JavaFX needs a main class that doesn't extend
  `Application` when running from the classpath); `JeibApp` wires everything up.
- `model/` records and pure rules (`LimitRules`, `Money`), `db/` (`Database`: SQLite + Flyway),
  `repository/` (plain JDBC), `ui/` (FXML + controller + view-model per screen).
- MVVM: controllers only bind FXML to a view-model; view-models hold state as JavaFX properties and
  run database work in a `javafx.concurrent.Task` on a virtual thread.
- Styling: `styles/theme-light.css` and `theme-dark.css` define only the color tokens from
  `jeib-color-system.md`; `styles/app.css` styles components with those tokens. Category colors are
  `.swatch-0` … `.swatch-13`. Keyboard focus uses `:focus-visible`.
- The database lives in the user's app-data folder (`%APPDATA%\Jeib\jeib.db`); tests use `@TempDir`.
- New schema changes: add `V3__….sql` etc. under `src/main/resources/db/migration`; never edit an
  applied migration.

## Current status

- In the roadmap, Week 1 items marked done: tool setup, GitHub repo + README/.gitignore/branching,
  one-page project brief.
- Oct 25, 2026: this folder is the git checkout of https://github.com/Khalil-Pal/money_map- (branch
  `main`). Phase 1 skeleton done: Maven project, V1 schema + V2 default categories, themed app shell
  (light/dark) matching the mockups, jpackage app image builds. Week 3 step 1 done: `Transaction`
  record, `TransactionRepository` (insert, findById, findByMonth, softDelete), `Timestamps`
  (fixed-width UTC text); 40 tests passing. Both merged into `main` via PR #1 (Oct 25).
  For session history, decisions, and pending items, see `HANDOFF.md`.
  `developer-roadmap.docx` is git-ignored (personal).
- Nov 1 - Nov 10: no work. Nov 11: plan changed to "usable by Dec 1" (see Features); branch
  `feature/categories` (from `docs/handoff`, PR #2): `CategoryService` + repository methods for
  create/update/delete. Branch `feature/transactions` (stacked on it): `TransactionService`
  (add/edit/delete/listMonth with validation; `ValidationException.field()` names the form field),
  `TransactionRepository.update` with an optimistic version check. Then currencies:
  `SupportedCurrency`, `ExchangeRates`, `SettingsRepository`, `CurrencyService`; transactions
  carry USD/ILS/RUB. Nov 12: Transactions screen (`transactions.fxml` + `TransactionsController` +
  `TransactionsViewModel`, rows built by `TransactionRow.from`), working ◀ ▶ month switcher (month
  lives in `MainViewModel`, shared by all pages), `SupportedCurrency.format` ("$12.50", "₪45.00",
  "950.00 ₽"). `JeibApp.createMainWindow(database, clock)` is the one place that wires the app (also
  used by `tools/Snap.java`, which now also captures the Transactions page).
  Then: dialogs open inside the window over a dimmed backdrop (`DialogHost`, opened via `Dialogs`;
  the content behind is disabled). First launch shows `setup.fxml` (choose main currency, can't be
  skipped). New/Edit transaction dialog (`transaction-dialog.fxml`, `TransactionDialogViewModel`):
  type, amount + currency, an exchange-rate row when the currency isn't the main one (prefilled,
  saved first if new or changed), date, category chips, note; each `ValidationException` shows
  under its field. Opened by the New transaction button, Ctrl+N, or clicking / Enter on a list row.
  View-models keep DB work in plain methods (`loadData`/`apply`, `request`/`perform`) so tests run
  without JavaFX. `tools/Snap.java` clicks through setup → new → errors → rate → save → dark edit.
  Then undo/redo (Command pattern, `service/`): `Command`, `UndoManager` (undo + redo stacks, per
  session, last 100, failed commands not recorded, new change clears redo), `TransactionCommands`
  (Add/Edit/Delete; the UI saves through these, never `TransactionService` directly). Undo of a
  delete = `TransactionService.restore` (repository `restore`: deleted 0, version + 1, PENDING);
  undo of an add = soft delete, its redo = restore of the same row; undo of an edit re-applies the
  old values through normal validation. Delete: red Delete button in the edit dialog (no
  confirmation) or Delete/Backspace on a focused row → `Toasts` "Transaction deleted · Undo".
  Ctrl+Z / Ctrl+Y (also Ctrl+Shift+Z) via `HistoryViewModel`, as a root key handler so text fields
  keep their own undo; ignored while a dialog is open.
  Then the quick-add bar: `service/QuickAddParser` (pure; rules in its Javadoc: amount first, `+`
  = income, currency glued or next word incl. usd/nis/rub/$/₪/₽, category by full name or a unique
  start of ≥2 letters, expense first, an income-only word makes it income, no match → built-in
  Other/Other income, rest = note, date = today) + `QuickAddViewModel` (live preview bar under the
  top bar, red on errors; Enter saves through `TransactionCommands` → toast "Added … · Undo"; Esc
  clears). Week 4's milestone is complete (Nov 12).
  Nov 12 (later), branch `feature/limits` (from `feature/transactions`): limits. `V3` migration (one live
  limit per category+month), `Budget`, `BudgetRepository` (defaults only: `month IS NULL`, same
  limit every month), `TransactionRepository.sumExpensesByCategory` (SQL GROUP BY category,
  currency), `BudgetService` (`forMonth` → `MonthBudget` with an overall `LimitLine` and one per
  expense category, each currency converted once, unconverted currencies reported;
  `setLimit`/`removeLimit` with validation). `LimitRules.percentUsed` now rounds **down** so the
  shown % never contradicts the status (79.99% → 79%, not 80%). Budgets page (`budgets.fxml`,
  `BudgetsController`/`ViewModel`, `StatusTile` = color system §4 icon tiles, status-colored
  bars + % text), `limit-dialog.fxml` (set/change/remove). `LimitWatcher` toasts when a limit
  becomes near/over after a change (first check after start is silent). `DataChanged` is the one
  "data changed" signal: list, Budgets, and the watcher listen; dialogs, undo, quick add, and
  deletes fire it. 224 tests passing.
  Then the Dashboard (`dashboard.fxml`, `DashboardController`/`ViewModel`, `DashboardData.from` is
  pure): cards Spent / Remaining / Left per day (`model/LeftPerDay`: remaining ÷ days left incl.
  today, rounded down; future month = all days; past month = none), a donut drawn with `Arc`s in
  the category colors + a legend (share %, "<1%" for tiny ones), and the 5 most urgent category
  limits. Everything is clickable: legend/limit rows → Transactions filtered to that category
  (`Navigation`, `TransactionsViewModel.filterBy`, "Showing only X · Show all" bar; a sidebar click
  shows all again), cards → Transactions/Budgets or the limit dialog. The old placeholder dashboard
  (DB status + category chips) is gone. 238 tests passing.
  Then categories UI, branch `feature/categories-ui` (from `feature/limits`): a Categories page
  in the sidebar (expense and income lists, "+ New", rows open the dialog) and
  `category-dialog.fxml` (`CategoryDialogViewModel`): name, a 14-color picker (colors used by
  other categories are greyed out until all 14 are taken), delete with a confirmation step
  (it moves transactions to Other and isn't undoable; built-ins can't be deleted). The
  transaction dialog has an inline "+ New category" (name only, first free color, selected
  right away). 245 tests passing.
  Then Settings, branch `feature/settings`: theme (Light/Dark, remembered via
  `PreferencesService` → settings key `theme`), main currency (switching asks to confirm and
  calls `BudgetService.changeMainCurrency`, which needs a rate for the new currency and converts
  every limit by dividing by the typed rate, so 92,500 RUB at 92.5 is exactly $1,000.00), the
  exchange rates for the other two currencies (each saved separately; one bad rate doesn't block
  the others), and the data file path. `CurrencyService.setMainCurrency` now refuses a switch to
  a currency without a rate.
  Then English + Arabic, branch `feature/arabic` (see "Languages" above for the rules). Language
  picker in first-launch setup and in Settings.
  Then the top-right controls, branch `feature/top-bar-controls`: ⚙️ Settings shortcut, a theme
  button cycling Light → Dark → Automatic (`ThemeMode`; Automatic = dark from 19:00 to 07:00,
  fixed hours, re-checked every 30 s while open; saved as LIGHT/DARK/AUTO), and an orange
  language pill (earth icon + "English"/"العربية", menu with both). The sidebar theme button
  is gone. Motion (`ThemeTransition`): the new theme is revealed as a circle growing from the
  theme button over a snapshot of the old one (520 ms, emphasized ease-out), the icon turns into
  the next one, and a language switch fades out/in. Arabic names: الوضع النهاري / الوضع الليلي /
  تلقائي. No accent-color picker (decided: Jeib stays orange). Merged to `main` via PR #3 (Nov 12).
  Then backups + crash log, branch `feature/backup-crash-log`: `BackupService` makes
  `backups/jeib-YYYY-MM-DD.db` next to the database on the first launch of each day (SQLite
  `VACUUM INTO` to a `.part` file, then an atomic rename) and keeps the newest 7; restore saves
  the current file as `jeib-before-restore-<time>.db` first, then rebuilds the window.
  `CrashLog` (installed in `JeibApp.init`) writes uncaught errors to `logs/jeib.log` (rotated at
  512 KB) and shows a toast with "Report". `ProblemReport` builds prefilled GitHub-issue links
  (Report a problem with the log tail, Send feedback); nothing is sent by the app itself.
  Settings has Backups (list, Back up now, Restore with confirmation, open folder) and Help cards.
  Uses `java.awt.Desktop` to open the browser/Explorer, so a jlink-trimmed runtime must keep
  `java.desktop`. 283 tests passing.
  Then packaging, branch `feature/packaging`: `scripts/package-windows.ps1` reads the version from
  pom.xml, builds a trimmed runtime with jlink (java.base, desktop, sql, logging, naming,
  management, xml, scripting, jdk.unsupported, jdk.localedata with en/ar/ru/he, charsets, zipfs)
  and jpackage `--runtime-image`, then zips `dist\Jeib-<version>-windows.zip`. 76 MB app image
  (was 178 MB), 59 MB zip. Checked by running the real Jeib.exe with APPDATA pointed at a temp
  folder: opens in Arabic with Arabic month names, makes its daily backup, no crash log.
  README has tester steps (including the unsigned-app SmartScreen warning).
  **All Dec 1 items are built (Nov 12).** Next: daily use and fixing what it finds; then the
  monthly report + Excel/PDF before Dec 31; the design overhaul; re-plan the rest of v1.0.
- Not done yet: Inter font isn't bundled (falls back to Segoe UI); real installers need the WiX
  Toolset; the exe isn't code-signed (Windows shows a SmartScreen warning the first time).
- Round 1 mockups (app shell, Dashboard, Transactions, New transaction dialog; light + dark) are a
  Design canvas artifact: https://claude.ai/artifact/7Ju8TzxGXtYCpKLRMvBWFu. Round 2 covers the
  remaining screens.
- JDK 21.0.12 is installed and `JAVA_HOME` points to it, but an old Oracle Java 8 shim
  (`C:\Program Files (x86)\Common Files\Oracle\Java\javapath`) comes first on PATH, so a bare
  `java -version` shows 1.8. Maven uses `JAVA_HOME`, so builds are fine.

## Editing the roadmap docx

The docx was converted from a PDF, which makes it awkward to edit:

- Almost every word is its own `<w:r>` run. Run the docx skill's `merge_runs.py` first, then match
  paragraphs by their concatenated `<w:t>` text, not by raw XML substrings.
- Task items in section 6 are paragraphs with an anchored dot shape plus an inline checkbox image.
  To add one, clone an existing task paragraph and give it fresh `wp:docPr`/`pic:cNvPr` ids,
  `w14:paraId`, and `wp14:anchorId`/`editId`.
- Each original page ended with a next-page section break (`<w:sectPr>` with no `w:type`); these
  are now continuous so text flows. `w:type` controls how a section *starts*, so it lives on the
  sectPr that ends the following section. Exception: sectPr #13-#15 (the Phase 6 / database
  schema pages) must stay next-page — their code-block shapes are positioned relative to the page.
- The milestones table (section 13) is one paragraph with tab stops; rows are separated by
  explicit `<w:br/>` line breaks. Keep milestone texts to one line.
- No `pandoc`, `zip`, `lxml`, LibreOffice, or poppler here. Use Python `zipfile` to repack, and
  Word (COM automation) to verify: open the file, export a PDF, or render pages through
  `Pages(i).EnhMetaFileBits`.

## Working agreement (Oct 25, 2026)

Update Oct 25: for now all work (building and management) happens in the Claude desktop app
session; IntelliJ is paused. The coaching rule below still applies to whichever session builds.

Khalil's planned split, when IntelliJ is used again:

- **IntelliJ (Claude Code plugin): building.** Khalil writes the features. Claude coaches: explains
  concepts and code, reviews what Khalil wrote, suggests the next small step, helps write tests, and
  unblocks when stuck (after ~20 minutes, give a hint first, then the fix). Claude may write
  boilerplate, CSS, test data, and migrations' repetitive parts. For core logic (repositories,
  services, view-models, sync), don't write the whole thing unasked: Khalil must be able to explain
  every line in an interview.
- **Claude desktop app: management.** Planning, weekly reviews, roadmap changes, mockups, ADRs and
  diagrams, CV and interview prep, end-of-week code reviews.
- Only one session edits code at a time. Docs (CLAUDE.md, roadmap) may change from either.
- Commit and push only when Khalil asks; work on a branch per feature, merge to `main` via PR.

## How to help

- **Build first, learn on demand.** Explain theory when it solves the problem at hand; don't send
  the user back to tutorials.
- Keep scope tight: one project, MVP before sync, always something working and released.
- Phase 4 artifacts (ADRs, diagrams) should live as text in `/docs` (Mermaid/PlantUML) so they're
  versioned in Git.
- When suggesting CV lines or metrics, use only real numbers the user has measured.
- Watch for burnout: if goals slip two weeks in a row, suggest reducing scope, not adding hours.

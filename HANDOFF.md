# Jeib — session handoff

Written **Sunday, Nov 1, 2026** (end of roadmap Week 3) so a new Claude session can pick up exactly
where the last one stopped.

**To start a new session, paste this:**

> Read `HANDOFF.md` and `CLAUDE.md` in this folder, then continue from "Next steps". Before writing
> code, tell me in a few lines where we are and what you'll do first.

`CLAUDE.md` holds the permanent project rules (stack, scope, engineering rules, timeline, commands,
code layout). This file holds what a new session can't know otherwise: what happened, what was
decided and why, what's pending, and how Khalil likes to work.

---

## 1. Where everything is

| What | Where |
|---|---|
| Code (Git) | This folder = checkout of https://github.com/Khalil-Pal/money_map- , branch `main` |
| Merged work | PR #1 "Add the Jeib app skeleton and transaction data layer", merged Oct 25 |
| Career roadmap (source of truth for the plan) | `developer-roadmap.docx` (git-ignored, personal) |
| Project rules | `CLAUDE.md` |
| Colors, typography, status labels, insight tiles | `jeib-color-system.md` |
| Logo | `jeib-logo-rounded.png` (app copy: `src/main/resources/com/jeib/ui/logo.png`, icon: `packaging/jeib.ico`) |
| Mockups (Round 1, clickable, light + dark) | Design canvas artifact: https://claude.ai/artifact/7Ju8TzxGXtYCpKLRMvBWFu |
| Weekly journal | `journal.md` (untracked, see §5) |
| UI screenshot tool | `tools/Snap.java` (see §7) |
| Windows app build | `scripts/package-windows.ps1` → `dist\Jeib\Jeib.exe` |
| App's real database | `%APPDATA%\Jeib\jeib.db` |

## 2. Where the schedule stands

**Update Nov 11, 2026 — the plan changed.** Khalil wants Jeib **ready to use by Dec 1** and dropped
the week-by-week order; the Dec 1 scope and milestones are in `CLAUDE.md` → Features. A design
overhaul ("more professional, not a basic AI look") comes after Dec 1. A 7-student survey shaped the
priorities: `docs/user-research.md`. The roadmap .docx has an "Update Nov 11" note under "Safety
net". Done Nov 11: categories (step 2 below), on branch `feature/categories`. The table below is
the old Nov 1 view, kept for history.

Timeline (from `CLAUDE.md`): Phase 2 = Weeks 3-10 (Oct 26 - Dec 20). Key dates: **Nov 22** core
features + limits + dashboard, **Nov 29** private build into daily use (30-day self-test),
**Dec 20** public v1.0.

| Item | Status |
|---|---|
| Phase 1 (Weeks 1-2): brief, repo, mockups, skeleton, first migration, jpackage test | **Done** (Oct 25) |
| Week 3 step 1: `Transaction` record + `TransactionRepository` + tests | **Done** (Oct 25) |
| Week 3 step 2: categories create / rename / delete | **Done** Nov 11 (`CategoryService`, 21 tests; 61 total) |
| Week 3 step 3: `TransactionService` with validation | Not started |
| Week 3 step 4: Transactions screen (monthly list) | Not started |
| Week 4: add/edit dialog with category chips; undo/redo (Command pattern) | Not started |

**No work happened Oct 26 - Nov 1**, so Week 3 slipped by about four steps. Weeks 3-4 together
(Oct 26 - Nov 8) were planned for transactions + categories + undo/redo, so there's one week left
for most of it. Roadmap guidance when behind: protect the Nov 29 private build; reduce scope rather
than adding hours. Raise this gently at the start of the next session and agree a catch-up plan
(e.g. do steps 2-4 this week, undo/redo early Week 5).

## 3. Decisions made (and why)

All agreed with Khalil; most are also recorded in `CLAUDE.md` or `jeib-color-system.md`.

| Date | Decision | Why |
|---|---|---|
| Oct 24 | App renamed **MoneyMap → Jeib**; logo = white "J" on orange | Khalil's choice |
| Oct 24 | **Scope:** v1.0 (Dec 20) = all 34 offline features; v2.0 (Jan 17) = sync only (account, sync, sync status, server reports, shared budget as stretch) | Khalil wanted everything except sync in v1 |
| Oct 24 | **Rescheduled roadmap:** Phase 2 → 8 weeks; Phases 4 and 5 shortened by a week; April deadline unchanged; private build Nov 29 keeps the self-test early | Fit the bigger v1.0 |
| Oct 24 | Roadmap .docx updated (features, weeks 8-10, milestones) | Keep the plan the source of truth |
| Oct 24-25 | Color system from Khalil's file kept; brand set to logo-exact **#FF751F**; light `success` darkened to **#137A3E** (badge contrast) | Contrast checks |
| Oct 25 | Chart palette **8 → 14 colors**; every category gets a unique color; "Other" = Slate, "Other income" = Taupe | Khalil: no two categories with the same color |
| Oct 25 | Users create their own categories; built-ins **Other** + **Other income** can't be deleted | Fallback for quick-add and CSV import |
| Oct 25 | **Hover highlights only on clickable things**; dashboard cards, limit rows, legend rows, insights are drill-downs | Hover on non-clickable items feels broken |
| Oct 25 | **Status labels = icon tile** (20 px tile, 6 px radius, icon + label), no pills; insights use solid 36 px tiles with FORECAST / OVER LIMIT / TREND kickers | Khalil wanted a modern, non-oval style; picked option A of three |
| Oct 25 | Stay with **JavaFX** (built the skeleton in it) | Java backend career goal; roadmap rules out web frontends. `CLAUDE.md` still lists this as an "open question": confirm with Khalil and update it |
| Oct 25 | **Maven** (with wrapper), package `com.jeib`, MVVM, plain JDBC repositories | Simplest for JavaFX; roadmap allowed Maven or Gradle |
| Oct 25 | Round 2 mockups (Budgets, Reports, Accounts, Recurring, Goals, CSV import, Settings, first-run, lock screen) designed **just in time**, about a week before each is built | Don't let design delay coding |
| Oct 25 | IntelliJ + Claude Code paused; **all work in the Claude desktop app** for now | Khalil's choice |

## 4. How Khalil wants to work

- **Build mode (chosen Oct 25): Claude writes the code for each step, then walks Khalil through it
  and quizzes him** so he can explain it in an interview. Keep steps small; after each: run tests,
  explain file by file (what and why), then 4-6 interview-style questions, then grade his answers.
- **Khalil tends to skip the quiz** and ask "what's next". Gently insist on at least 2-3 questions
  before the next step; that's the point of this mode (the job hunt needs him to defend the code).
- **Commit and push only when he asks.** One branch per feature (`feature/<name>`), PR into `main`.
  Commit messages end with the co-author line from the session's attribution instructions.
- One Claude session edits code at a time.
- Sunday = rest + 20-minute weekly review in `journal.md`.
- He writes casually and briefly ("what's next?"). Give a clear recommendation, not a menu.

## 5. Open items waiting on Khalil

1. **Step 1 quiz, unanswered.** Ask these before or alongside the next step:
   1. Why store money as `long` cents instead of `double`? Example of what goes wrong.
   2. Why does the app create the UUID instead of the database auto-numbering rows?
   3. `softDelete` has `AND deleted = 0` in its `WHERE`. What breaks without it?
   4. Why filter a month with `occurred_on >= … AND occurred_on < …` instead of
      `strftime('%Y-%m', occurred_on) = '2026-10'`? (Index use; half-open range.)
   5. Why does the repository take a `Clock` instead of calling `Instant.now()`?
   6. Two offline edits of the same transaction, then sync: which fields decide the winner, and one
      weakness? (`updated_at`/`version`, last-write-wins; clock skew, lost update.)
2. **`journal.md`:** the Week 2 entry is pre-filled, but his ✏️ lines (learned, hours, one thing
   to change) are empty. The Week 3 entry is due today. He hasn't decided whether the journal goes in
   the public repo or stays private (add to `.gitignore` like the roadmap).
3. **`CLAUDE.md` is in the public repo** and describes his career plan. Flagged; no decision yet
   (keep, or move the personal parts out).
4. **Confirm JavaFX** formally and update the "Open question" line in `CLAUDE.md`.
5. **Fix PATH:** an old Java 8 (`C:\Program Files (x86)\Common Files\Oracle\Java\javapath`) comes
   before JDK 21, so `java -version` shows 1.8. He has to change it himself in Windows Environment
   Variables.
6. **Optional:** install GitHub CLI (`winget install GitHub.cli`, then `gh auth login`) so the app
   can track PRs.
7. `HANDOFF.md` and `tools/Snap.java` are new and uncommitted; ask whether to commit them.

## 6. Next steps (in order)

Steps 1-2 are done (Nov 11). `feature/categories` was branched from `docs/handoff`, so it also
carries this file and `tools/Snap.java`. Category decisions made while building: a color may repeat
only when all 14 are used (then the least-used, lowest index); names are whitespace-collapsed, max
40 characters; built-ins can be renamed and recolored but not deleted; only live transactions move
on delete, and `softDelete` returns their ids for the coming undo. Step 2 quiz is pending.

Step 3 is done too (Nov 11, branch `feature/transactions`, stacked on `feature/categories`).
Decisions: amount max 999,999,999.99; date at most one year ahead (past dates allowed); note
optional, max 200 characters; edits keep id/account/currency/created_at and use an optimistic
version check (`WHERE version = expected`) so a stale edit never overwrites silently; currency is a
constructor argument ("USD" until settings exist). Khalil asked to skip the quizzes for now (Nov
11); offer them again later, don't push.

Currencies (Nov 11, same branch): Khalil chose USD, ILS, RUB, **mixed and converted** into a
main currency **the user picks when setting up** (not a fixed default), with manual rates. Rules
are in `CLAUDE.md` → Features, item 9. The Dec 1 build therefore needs a small first-launch step
(choose main currency) and a place to enter the rates.

Step 4 (Transactions screen) done Nov 12 on `feature/transactions`. Notes: the month list reloads
when the month or DB readiness changes, and ignores results for a month no longer on screen (fast
◀ ▶ clicks). Rows aren't clickable yet, so no hover (CLAUDE.md rule). Amounts show in each
transaction's own currency; converted totals come with the dashboard. To screenshot with sample
data: seed a temp DB through the services, then run `tools/Snap.java <db> <prefix> 2026-11-12`.
Khalil asked to keep the conversation in English (even when a request arrives in Russian).

Nov 12, later: first-launch setup + New/Edit transaction dialog done (see CLAUDE.md status).
Notes: the Round 1 mockup artifact couldn't be read from this account (404), so the dialog follows
the color system and HANDOFF notes instead; dialogs are in-window overlays, not OS windows. The
service stops at the first error, so the dialog shows one error at a time (amount, then category…);
showing all at once is a possible later polish. The DatePicker uses the OS date format (e.g.
12.11.2026 on a Russian locale) while the list uses "Thu, Nov 12". Exchange rates are global (not
per transaction): changing a rate changes how all past amounts in that currency are counted.

Nov 12, evening: delete + undo/redo done (see CLAUDE.md status). Known edge: undoing a transaction
delete after its category was deleted brings it back into the deleted category (shown as "Unknown
category"); category deletion has no UI yet, so this is rare. Undo history is per app session.
Week 4 (add/edit dialog, delete, undo/redo) is done on Nov 12, ahead of the Nov 16-21 milestone;
only quick-add is left from it.

Nov 12, night: quick-add done, so the whole Nov 16-21 milestone is finished early. Next is limits +
the dashboard (budgets table exists since V1; remember: limits are in the main currency, converted
sums per currency via `ExchangeRates.toMain`, "left per day until month end" is the headline from
the survey). Round 2 mockups for Budgets/Dashboard were planned but aren't made; build from the
color system (status icon tiles, §4) and keep it simple until the post-Dec 1 redesign.

Nov 12 (later): limits done on `feature/limits` (see CLAUDE.md status). Khalil chose option A for the
dashboard's "left per day": based on the overall limit only. Limit changes are not undoable
(only transactions are); add if real use asks for it. When a Settings page lets the user change
the main currency, existing limits must be converted with the rebased rates (CLAUDE.md rule).

Nov 12 (later still): Dashboard done on `feature/limits` (same branch as limits). All Dec 1
features except backup + crash log + packaging are built. Charts are hand-drawn `Arc`s, not
JavaFX `PieChart`, so slice colors come straight from the palette tokens. Motion is still none
(instant hovers, no transitions): planned for the post-Dec 1 redesign.

Nov 12 (end of day): categories UI done on `feature/categories-ui`. Known edges: a deleted
category's limit row stays in the table but is ignored (forMonth only shows live categories);
undoing an edit whose old category was deleted fails with "Can't undo: …" (by design). Still
missing for Dec 1: Settings (rates editing, remembered theme), backup + crash log, packaging.

Nov 12 (night): Settings done on `feature/settings`. The currency switch writes the currency
first, then each limit (separate repositories, not one DB transaction); fine on a local DB, but a
shared `Database.inTransaction` would make it atomic if it ever matters. Branch stack now:
`feature/settings` → `feature/categories-ui` → `feature/limits` → `feature/transactions` → PR #2.
Left for Dec 1: daily backup (keep 7) + crash log, then the zipped `Jeib.exe`.

Nov 12 (late): English + Arabic on `feature/arabic` (stacked on `feature/settings`). Checked with
screenshots in Arabic (fresh setup, dashboard, list, dialog, budgets, settings) and English
(unchanged). Not checked live: switching language while the app runs (JeibApp.rebuildIn); Snap
builds one language per run (`tools/Snap.java <db> <prefix> <date> ar`). Known edges: editing a
default category while in Arabic saves its Arabic name (it then stays Arabic in English too);
a user category named "طعام" next to the default Food shows two "طعام" in Arabic. The Arabic
text was written by Claude; ask an Arabic-speaking tester to read through it.

Nov 12 (late): top-right controls + theme/language motion on `feature/top-bar-controls`. Khalil
decided: Automatic theme with fixed hours 19:00-07:00; no accent picker (Jeib stays orange, the
globe too). Checked with screenshots, including a frame 230 ms into the circle reveal. Not
checked: the language menu popup (scene snapshots don't include popups) and the live language
fade. Small gap: in Arabic, quick add matches Arabic category names only ("250 food" goes to
Other); could also match the default categories' English names later.

1. ~~Start of session: short status, step 1 quiz, catch-up plan, branch.~~
2. ~~**Week 3 step 2: categories.**~~ Original brief, for reference — in `CategoryRepository` (+ a small service if it helps):
   - create: name required and trimmed, unique per kind ignoring case (DB index
     `ux_categories_kind_name` already enforces it), gets the **first free color index** 0-13
     (if all 14 are used, colors may repeat), `built_in = 0`, UUID, timestamps via `Timestamps`;
   - rename: same uniqueness rule; bumps `version` / `updated_at`;
   - delete: soft delete; **built-ins can't be deleted**; the category's transactions move to
     "Other" (expense) or "Other income" (income) in the same DB transaction, each bumping
     `version` / `updated_at` and `sync_state = 'PENDING'`;
   - tests for each rule (follow `TransactionRepositoryTest`: temp DB, fixed `Clock`).
   Then walkthrough + quiz.
3. ~~**Step 3: `TransactionService`:**~~ (done) validates input (amount > 0 via `Money.parseMinorUnits`, date
   required, category exists and is live, transaction type matches the category's kind) and calls
   the repository. Tests.
4. ~~**Step 4: Transactions screen:**~~ (done) month list (newest first) wired to the top-bar month switcher;
   view-model loads on a background `Task`. Match the Round 1 mockup (date, description, category
   with color swatch, account, tags, amount; income in green with "+").
5. **Week 4:** add/edit dialog (category chips, "+ New category" with color picker, limit preview),
   delete with undo toast, **undo/redo via the Command pattern in the service layer** (undoing a
   delete restores the soft-deleted row and bumps `version` / `updated_at`).
6. Before Week 5 (Nov 9): Round 2 mockups for **Budgets** and **Reports** on the same canvas.

## 7. Environment and tools

- **JDK 21.0.12** at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` (`JAVA_HOME` is
  set). Builds: `mvnw.cmd test`, `mvnw.cmd javafx:run` (commands in `CLAUDE.md`). In Git Bash, set
  `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"` and use `./mvnw`.
- **No system Maven**; the repo's wrapper uses Maven 3.9.14 (already cached in `~/.m2`).
- **Library versions:** JavaFX 21.0.5, sqlite-jdbc 3.46.1.3, Flyway 10.22.0 (SQLite works with
  `flyway-core` alone), JUnit 5.11.3.
- **Checking the UI without clicking:** `tools/Snap.java` (usage in its header) starts the real
  window on a temp database and saves PNG screenshots; read them to review layout. Used to catch the
  startup focus-ring bug and the theme icon.
- **Opening PRs without `gh`:** `git push`, then POST to `https://api.github.com/repos/Khalil-Pal/money_map-/pulls`
  with the token from `git credential fill` (Git Credential Manager). Never print the token.
  Installing `gh` (§5.6) makes this unnecessary.
- **Line endings:** `.gitattributes` keeps `mvnw` LF (needed for Linux CI in Week 7). Git prints
  CRLF warnings on Windows; they're harmless.
- **Editing the roadmap .docx:** see "Editing the roadmap docx" in `CLAUDE.md` (it's a PDF
  conversion; Word COM is used to render and check pages).
- **Editing the mockup canvas:** it's a Design-type artifact. Lessons learned:
  - all screens and states come from one `Main.dc.html`; the five other boards import it with props
    (`theme`, `start-screen`, `start-dialog`, `start-toast`);
  - don't put `<sc-for>` inside `<select>`, `<table>` or `<svg>` (the browser's HTML parser moves or
    drops it): options are literal, the table is a CSS grid, and each donut slice is its own `<svg>`;
  - imported boards may only have a font link and `body{margin:0}` in `<helmet>`, so hover and focus
    styles are done with component state (`onMouseEnter` / `onMouseLeave`, `onFocus` / `onBlur`);
  - viewing the canvas in the app's browser pane needs a claude.ai sign-in; logic was tested instead
    by running the component's script in Node and checking every `{{hole}}` resolves (65 checks).

## 8. What the code does today

- `com.jeib.Launcher` → `JeibApp`: opens the DB, builds `MainViewModel` + `MainController` from
  `ui/main.fxml`, shows the window, loads in the background.
- `db/Database` (SQLite path, Flyway `migrate()`, `connect()` with foreign keys on),
  `db/Timestamps` (fixed-width UTC text: `2026-10-25T09:30:00.123Z`).
- Migrations: `V1__initial_schema.sql` (all roadmap tables), `V2__default_categories.sql` (11
  categories with fixed UUIDs and unique colors).
- `model/`: `Category`, `Transaction` (validated record, `create(...)` with `Clock`),
  `TransactionType`, `LimitStatus`, `LimitRules` (integer math; 80% / 100% edges), `Money` (parse
  "12.50" → 1250, format with `Currency` + `Locale`).
- `repository/`: `CategoryRepository.findActive()`, `TransactionRepository` (`insert`, `findById`
  incl. deleted, `findByMonth`, `softDelete`).
- `ui/`: app shell (sidebar with icons, top bar, light/dark toggle), dashboard placeholder showing
  DB status and the default categories; other pages say which roadmap week builds them.
- **40 tests pass:** `LimitRulesTest` 11, `MoneyTest` 17, `MigrationTest` 5,
  `TransactionRepositoryTest` 7.
- Known gaps: Inter font not bundled (Segoe UI fallback); app image ~177 MB (jlink in Week 7);
  installers need WiX (Week 7); top-bar buttons and quick-add aren't wired yet.

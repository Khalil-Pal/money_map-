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

**Open question (Oct 24, 2026):** the user wants a very modern-looking UI and asked whether to keep
JavaFX. Not decided yet — update this section once they choose.

### Features (final scope, agreed Oct 24, 2026)

Rule: **v1.0 (Dec 20, 2026) has every feature that works without a server. v2.0 (Jan 17, 2027)
adds sync and everything that depends on it.** Numbers are the ones used when the scope was agreed.

**v1.0 — 34 features, by roadmap week:**

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

**If behind schedule, protect:** the Nov 29 private build (self-test), then the released MVP (Phase 2), sync basics (auth + push/pull), and
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
  (fixed-width UTC text); 40 tests passing. Both are on branch `feature/skeleton` (PR to `main`).
  `developer-roadmap.docx` is git-ignored (personal).
- Week 3 next: categories create/rename/delete (built-ins protected, first free color, case-insensitive
  unique names, deleting moves its transactions to "Other"), then `TransactionService` validation,
  then the Transactions screen.
- Not done yet: Inter font isn't bundled (falls back to Segoe UI); the app image is ~177 MB (trim
  with jlink / `--add-modules` in Week 7); real installers need the WiX Toolset (Week 7).
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

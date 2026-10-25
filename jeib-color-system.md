# Jeib Color System

Design tokens for the Jeib desktop app (JavaFX), covering the light theme, the dark theme, status colors, chart colors, and usage rules.

**Brand color:** orange `#FF751F` (sampled from `jeib-logo-rounded.png`).
**Logo:** white "J" on orange.

> Revised Oct 24, 2026: brand value set to the exact logo color (was `#FF7420`, visually identical);
> light-theme `success` darkened from `#178043` to `#137A3E` so "Within limit" badge text reaches
> 4.5:1 on `success-tint` (it was 4.40:1).

---

## 1. Principles

1. **Orange is rare.** Use the brand orange for roughly 10% of the screen: primary buttons, the selected navigation item, and one or two key numbers.
2. **Neutrals do the work.** Backgrounds, cards, borders, and text are warm neutrals, so the orange stands out.
3. **Status colors are only for status.** Green = OK, amber = near limit, red = exceeded. Never use them for decoration.
4. **Never rely on color alone.** Pair every status color with an icon and a label (for example "Near limit"), so the app stays clear for color-blind users and the amber warning isn't confused with the orange brand.
5. **Contrast matters.** White text on `#FF751F` is only about 2.7:1, which is too low. Use dark text on orange buttons, and use darker orange (`#C2410C` in light mode) when orange is the text color on a light background.

---

## 2. Light Theme

| Token | Hex | Used for |
|---|---|---|
| `bg-app` | `#FAF7F3` | Window background |
| `bg-surface` | `#FFFFFF` | Cards, dialogs, tables |
| `bg-surface-alt` | `#F3EEE8` | Sidebar, table header, input fields |
| `border` | `#E6DED5` | Card borders, separators |
| `text-primary` | `#1F1A17` | Titles, amounts, main text |
| `text-secondary` | `#6B625A` | Labels, hints, dates |
| `text-disabled` | `#A39A91` | Disabled controls |
| `primary` | `#FF751F` | Main buttons, selected nav item, key highlights |
| `primary-hover` | `#F0620D` | Button hover |
| `primary-pressed` | `#D9540A` | Button pressed |
| `on-primary` | `#1F1A17` | Text and icons inside orange buttons |
| `primary-text` | `#C2410C` | Links, orange text on light backgrounds |
| `primary-tint` | `#FFEFE4` | Selected row, active tab background |
| `success` | `#137A3E` | Income amounts, "within limit" |
| `success-tint` | `#E6F4EA` | OK badge background |
| `warning-text` | `#8A5A00` | "Near limit" text |
| `warning-bar` | `#F2B01E` | Progress bar when near limit |
| `warning-tint` | `#FFF4D6` | Warning badge background |
| `danger-text` | `#C62828` | "Limit exceeded" text, delete actions |
| `danger-bar` | `#D32F2F` | Progress bar when exceeded |
| `danger-tint` | `#FDE8E8` | Exceeded badge background |
| `info` / `focus-ring` | `#2563EB` | Keyboard focus outline, info messages |
| `shadow` | `rgba(31, 26, 23, 0.08)` | Card shadow |

---

## 3. Dark Theme

| Token | Hex | Used for |
|---|---|---|
| `bg-app` | `#14110F` | Window background |
| `bg-surface` | `#1E1A17` | Cards, dialogs, tables |
| `bg-surface-elevated` | `#27221E` | Popups, dropdowns, hovered rows |
| `bg-sidebar` | `#181513` | Navigation panel |
| `border` | `#3A332D` | Card borders, separators |
| `text-primary` | `#F5F0EB` | Titles, amounts, main text |
| `text-secondary` | `#B5ABA1` | Labels, hints, dates |
| `text-disabled` | `#6F665E` | Disabled controls |
| `primary` | `#FF751F` | Main buttons, selected nav item, highlights |
| `primary-hover` | `#FF8A42` | Button hover |
| `primary-pressed` | `#E8620F` | Button pressed |
| `on-primary` | `#1A120B` | Text and icons inside orange buttons |
| `primary-text` | `#FF8A42` | Links, orange text on dark backgrounds |
| `primary-tint` | `#3A2416` | Selected row, active tab background |
| `success` | `#4CC38A` | Income amounts, "within limit" |
| `success-tint` | `#12281C` | OK badge background |
| `warning` | `#F5C542` | "Near limit" text and bar |
| `warning-tint` | `#33290E` | Warning badge background |
| `danger` | `#FF6B6B` | "Limit exceeded" text and bar, delete actions |
| `danger-tint` | `#3A1717` | Exceeded badge background |
| `info` / `focus-ring` | `#6EA8FE` | Keyboard focus outline, info messages |
| `shadow` | `rgba(0, 0, 0, 0.5)` | Card shadow |

In dark mode, layering goes background, then surface, then elevated. Each layer is slightly lighter, and you rely on borders rather than heavy shadows.

---

## 4. Status Colors (Limits)

| Status | Condition | Light | Dark | Icon / label |
|---|---|---|---|---|
| **OK** | below 80% of limit | `#137A3E` on `#E6F4EA` | `#4CC38A` on `#12281C` | check, "Within limit" |
| **Warning** | 80% to 99% | `#8A5A00` on `#FFF4D6` (bar `#F2B01E`) | `#F5C542` on `#33290E` | warning triangle, "Near limit" |
| **Exceeded** | 100% or more | `#C62828` on `#FDE8E8` (bar `#D32F2F`) | `#FF6B6B` on `#3A1717` | octagon alert, "Limit exceeded" |
| **No limit** | no budget set | `#6B625A` on `#F3EEE8` | `#B5ABA1` on `#27221E` | dash, "No limit" |

Logic reference:

```
percent = spent / limit * 100

status = percent >= 100 ? EXCEEDED
       : percent >= 80  ? WARNING
       : OK
```

Always show the percentage as text next to the bar (for example "86%"). This is required, not
optional: the light-theme `warning-bar` (`#F2B01E`) is only 1.9:1 against white, so the bar alone
can't carry the status.

### Status label: icon tile (chosen Oct 25, 2026)

Status is shown as an **icon tile followed by the label**, never as a rounded pill or oval.

```
[✓] Within limit      [⚠] Near limit      [⯃] Limit exceeded      [–] No limit
```

| Part | Spec |
|---|---|
| Tile | 20 × 20 px, corner radius 6 px, background = the status tint (`*-tint`) |
| Icon | 12 px stroke icon, centered, color = the status text color; stroke 3 px for check and dash, 2.5 px for triangle and octagon |
| Label | 12 px, semibold, color = the status text color, no background, never wraps |
| Gap | 7 px between tile and label |

Also considered and rejected: a square dot + label (no icon, so it relies on the word alone) and an
uppercase tag with 4 px corners.

In JavaFX this is an `HBox` (spacing 7) holding a `StackPane` tile and a `Label`:

```css
.status-tile { -fx-min-width: 20; -fx-min-height: 20; -fx-max-width: 20; -fx-max-height: 20; -fx-background-radius: 6; }
.status-ok   .status-tile { -fx-background-color: -success-tint; }
.status-ok   .status-label { -fx-text-fill: -success; -fx-font-size: 12px; -fx-font-weight: 600; }
/* same pattern for .status-warning, .status-exceeded, .status-none */
```

### Insight tiles

Dashboard insights use a bolder, **solid** tile, so they read as a different thing from status labels:

| Insight | Tile (light / dark) | Glyph (light / dark) | Icon | Kicker |
|---|---|---|---|---|
| Forecast | `#F2B01E` / `#F5C542` | `#1F1A17` / `#1F1A17` | calendar-clock | FORECAST in `warning-text` |
| Over limit | `#D32F2F` / `#FF6B6B` | `#FFFFFF` / `#1A120B` | octagon alert | OVER LIMIT in `danger-text` |
| Trend | `#2563EB` / `#6EA8FE` (`info`) | `#FFFFFF` / `#14110F` | arrow up-right | TREND in `info` |

Tile: 36 × 36 px, corner radius 10 px, 18 px icon. Kicker: 11 px, bold, uppercase, letter-spacing
0.06em, above a semibold title and a `text-secondary` body line. Trends use blue (information),
not a status color, so a trend never reads as a warning.

---

## 5. Chart Colors (Categories)

Give each category its own color and keep it the same in every chart and in the Excel export.

| # | Light | Dark |
|---|---|---|
| 1 | `#FF751F` | `#FF751F` |
| 2 | `#3B82C4` | `#5BA4E6` |
| 3 | `#8B5CF6` | `#A78BFA` |
| 4 | `#14A38B` | `#2DD4BF` |
| 5 | `#D6336C` | `#F472B6` |
| 6 | `#A16207` | `#E5B84B` |
| 7 | `#64748B` | `#94A3B8` |
| 8 | `#84A816` | `#A3C93A` |
| 9 | `#0891B2` | `#22D3EE` |
| 10 | `#C026D3` | `#E879F9` |
| 11 | `#881337` | `#FDA4AF` |
| 12 | `#8B5E3C` | `#C49A6C` |
| 13 | `#7C2D6B` | `#D58BC7` |
| 14 | `#7C6F64` | `#BFB3A8` |

Names, in order: Orange, Blue, Purple, Teal, Pink, Ochre, Slate, Lime, Cyan, Fuchsia, Maroon,
Brown, Plum, Taupe.

Colors 9-14 were added Oct 25, 2026 so that no two categories share a color. They were picked by
search: every one is at least 3:1 against `bg-surface` in its theme, and differs from the other 13
and from the status colors in lightness as well as hue (minimum CIELAB distance about 21).

**Assignment rules**

1. Every category gets its own color. The built-ins are fixed: Food 1, Rent 2, Shopping 3,
   Transport 4, Entertainment 5, Subscriptions 6, Other 7 (Slate), Health 8, Salary 9,
   Freelance 10, Other income 14 (Taupe). The two "Other" categories get the neutral colors on
   purpose, so "uncategorized" looks neutral.
2. A new category gets the first unused color; the picker greys out colors already in use.
3. Only when all 14 are taken may a color repeat, and the picker says so.
4. Beyond about 10 colors, people can't reliably tell similar colors apart, so a color is never the
   only cue: charts always show the category name next to its color.

---

## 6. Usage Rules

1. **Amounts:** income in `success` with a `+` sign. Expenses in normal `text-primary` (not red), so the screen doesn't look alarming. Reserve red for exceeded limits.
2. **Buttons:**
   - Primary: `primary` background, `on-primary` text.
   - Secondary: transparent or `bg-surface-alt` background, `border` outline, `text-primary` text.
   - Destructive: `danger` color, and always ask for confirmation.
3. **Selected navigation item:** `primary-tint` background with `primary-text` label and a `primary` indicator bar.
4. **Inputs:** `bg-surface-alt` background, `border` outline, and on focus a 2 px `focus-ring` outline.
5. **Tables:** header in `bg-surface-alt`, rows in `bg-surface`, hovered row in `primary-tint` (light) or `bg-surface-elevated` (dark).
6. **Links:** `primary-text`, underlined on hover.
7. **Disabled controls:** `text-disabled` on `bg-surface-alt`, no hover effect.
8. **Excel export:** reuse the same status colors (green, amber, red) for the status column, and use the chart palette for the chart.

---

## 7. Typography and Shape (companion rules)

| Item | Value |
|---|---|
| Font family | Inter (fallback: Segoe UI, system sans-serif) |
| Numbers | Use tabular (fixed-width) digits so amounts line up |
| Title | 24 px, semibold |
| Section heading | 18 px, semibold |
| Body | 14 px, regular |
| Caption / label | 12 px, medium |
| Card radius | 12 px |
| Button / input radius | 8 px |
| Card padding | 16 to 24 px |
| Spacing scale | 4, 8, 12, 16, 24, 32, 48 px |

---

## 8. Implementation Notes

### Figma
- Create all tokens as **Variables** with two modes: **Light** and **Dark**.
- Name them exactly as in the tables above (for example `bg/app`, `text/primary`, `status/warning`), so components switch theme automatically.

### JavaFX CSS
- Define colors as looked-up colors on `.root` in two files, `light.css` and `dark.css`, then swap the stylesheet at runtime.
- Example structure:

```css
/* light.css */
.root {
    -bg-app: #FAF7F3;
    -bg-surface: #FFFFFF;
    -bg-surface-alt: #F3EEE8;
    -border: #E6DED5;
    -text-primary: #1F1A17;
    -text-secondary: #6B625A;
    -primary: #FF751F;
    -on-primary: #1F1A17;
    -success: #137A3E;
    -warning-text: #8A5A00;
    -danger-text: #C62828;
    -focus-ring: #2563EB;
}
```

```css
/* shared component styles use only the variables */
.card {
    -fx-background-color: -bg-surface;
    -fx-background-radius: 12;
    -fx-border-color: -border;
    -fx-border-radius: 12;
}
.button-primary {
    -fx-background-color: -primary;
    -fx-text-fill: -on-primary;
    -fx-background-radius: 8;
}
```

- Keep one shared stylesheet for components and one theme file for colors. Never hard-code a hex value inside a component rule.

### Accessibility checklist
- [ ] Body text contrast is at least 4.5:1 against its background.
- [ ] Large text and icons are at least 3:1.
- [ ] Status is never shown by color alone.
- [ ] Keyboard focus is always visible.
- [ ] Both themes were checked with real screens, not only in Figma.

---

## 9. Quick Reference

| Need | Light | Dark |
|---|---|---|
| Brand | `#FF751F` | `#FF751F` |
| Background | `#FAF7F3` | `#14110F` |
| Card | `#FFFFFF` | `#1E1A17` |
| Text | `#1F1A17` | `#F5F0EB` |
| Secondary text | `#6B625A` | `#B5ABA1` |
| Border | `#E6DED5` | `#3A332D` |
| OK | `#137A3E` | `#4CC38A` |
| Warning | `#8A5A00` / `#F2B01E` | `#F5C542` |
| Exceeded | `#C62828` / `#D32F2F` | `#FF6B6B` |

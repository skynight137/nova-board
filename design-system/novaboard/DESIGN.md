# NovaBoard Design System

**Status:** Approved source of truth for the UI/UX Pro Max redesign  
**Platform:** Native Android Views, Android 8+  
**Product identity:** A fast, private keyboard for people who move between
  writing, editing, and utility actions without leaving the text field.

## Design direction

NovaBoard should feel like a precision instrument: quiet when typing, obvious
when a mode changes, and fast to operate with one hand. The visual language is
restrained and technical rather than decorative. Dense controls are acceptable
because the keyboard is a high-frequency tool, but every control needs a clear
hierarchy, adequate touch area, and a visible state.

The generated UI/UX direction is adapted from a minimal Swiss/technical system:

- use a calm blue action color instead of generic red/green semantics
- make the keyboard surface and editor panels feel like one continuous tool
- reserve strong color for focus, active state, incognito, and destructive actions
- prefer native Android typography and vector icons over web assets
- use motion only to explain state changes; never delay typing

## Token architecture

Tokens are organized as:

```text
primitive values → semantic roles → component states
```

The machine-readable source is `design-tokens.json`. Web previews should use
the CSS variables in `design-preview.html`; Android resources should map the
same semantic roles into `colors.xml`, `dimens.xml`, and styles.

## Primitive tokens

### Color primitives

| Token | Light | Dark | Purpose |
|---|---:|---:|---|
| `blue-500` | `#4C7CF3` | `#8AB4FF` | Primary action and focus |
| `blue-700` | `#315FCE` | `#B7D0FF` | Pressed/strong action |
| `ink-950` | `#15171A` | `#F4F5F7` | Primary text |
| `ink-700` | `#4C4F55` | `#C4C7CD` | Secondary text |
| `surface-0` | `#F4F5F8` | `#111317` | Keyboard background |
| `surface-1` | `#FFFFFF` | `#262A31` | Standard key/panel |
| `surface-2` | `#DDE1E8` | `#1D2026` | Utility key/panel |
| `surface-pressed` | `#C9CED8` | `#414650` | Pressed key |
| `line` | `#C7CBD3` | `#343840` | Dividers and borders |
| `success` | `#177245` | `#66D39A` | Confirmed/available state |
| `warning` | `#8A5A00` | `#F6C866` | Caution state |
| `danger` | `#B3261E` | `#FFB4AB` | Destructive/error state |

### Layout primitives

- `space-1`: 2dp — optical correction
- `space-2`: 4dp — icon/key gap
- `space-3`: 6dp — key and chip inset
- `space-4`: 8dp — standard control gap
- `space-5`: 12dp — panel inset
- `space-6`: 16dp — section inset
- `space-7`: 24dp — settings section gap
- `space-8`: 32dp — large screen breathing room

### Shape and elevation primitives

- key radius: 6dp
- panel radius: 10dp
- dialog radius: 16dp
- pill radius: 999dp
- key elevation: 1dp at rest, 2dp pressed
- panel elevation: 3dp
- dialog elevation: 8dp

## Semantic color roles

| Role | Light mapping | Dark mapping |
|---|---|---|
| `background` | `surface-0` | `surface-0` |
| `key.default` | `surface-1` | `surface-1` |
| `key.utility` | `surface-2` | `surface-2` |
| `key.pressed` | `surface-pressed` | `surface-pressed` |
| `text.primary` | `ink-950` | `ink-950` |
| `text.secondary` | `ink-700` | `ink-700` |
| `text.onAccent` | `#FFFFFF` | `#15171A` |
| `action.primary` | `blue-500` | `blue-500` |
| `action.pressed` | `blue-700` | `blue-700` |
| `focus.ring` | `blue-500` | `blue-500` |
| `divider` | `line` | `line` |
| `incognito.surface` | `#DCE8FF` | `#263B61` |
| `incognito.text` | `#183A75` | `#D9E7FF` |
| `danger` | `danger` | `danger` |

Text and icon combinations must meet 4.5:1 contrast for normal text and
3:1 for large text or essential graphical controls. Do not use opacity as the
only disabled-state signal.

## Typography

Use Android system fonts for offline reliability and platform consistency:

| Role | Android family | Size | Weight | Use |
|---|---|---:|---|---|
| Settings title | `sans-serif` | 24sp | 600 | Screen title |
| Section title | `sans-serif` | 16sp | 600 | Group headings |
| Body | `sans-serif` | 16sp | 400 | Explanatory copy |
| Supporting | `sans-serif` | 13sp | 400 | Summaries and metadata |
| Key label | `sans-serif` | 20sp | 500 | Letter keys |
| Utility label | `sans-serif` | 12sp | 600 | Toolbar and modifiers |
| Shortcut label | `monospace` | 12sp | 500 | `Ctrl`, `F5`, and key hints |
| Emoji | system emoji fallback | device | — | Emoji only |

Keep body text at or above 14sp in settings and panels. Key labels may be
smaller only when needed to fit a real key; never reduce touch target size to
make a label fit.

## Component specifications

### Letter key

- default: `key.default`, primary text, 6dp radius, 1dp elevation
- pressed: `key.pressed`, no layout shift, 2dp elevation
- long press: pressed state remains visible and opens the symbol preview
- active multi-touch: each pressed key keeps its own preview
- minimum interactive bounds: 44dp × 44dp

### Utility key

Use `key.utility` for shift, delete, enter, arrows, and tool controls. Use a
label or vector icon, never an emoji. Destructive actions such as delete may
use `danger` only for confirmation/error feedback; do not color the normal
delete key red.

### Toolbar action

- 44dp minimum touch target
- 8dp spacing between adjacent actions
- icon plus content description
- selected state uses a blue 2dp indicator or blue-tinted surface
- unavailable state uses reduced contrast plus an explanatory message

### Panel

- continuous with the keyboard background; avoid unrelated floating cards
- 12dp inset on compact panels, 16dp on settings content
- header has a clear title and a labeled dismiss/back action
- empty states explain what to do next
- search fields keep the keyboard visible and show an explicit clear action

### Settings row

- title at 16sp, summary at 13sp
- minimum height 56dp
- full-row touch target when the row is actionable
- state is described in text, not color alone
- destructive/reset actions are separated from normal preferences
- enabled switches use a blue track with a light thumb; disabled switches use
  a soft neutral track with a darker thumb while retaining a 44dp touch target

### Feedback

- pressed state: immediate visual response, ≤80ms
- panel transition: 140–180ms, fade/translation only
- success/error message: visible for at least 2 seconds or until dismissed
- loading: show an inline progress state beside the action
- reduced motion: render the final state immediately

## Interaction rules

1. Typing always wins over decoration; no animation may block text insertion.
2. Every async callback is scoped to the active input session.
3. Every settings page is dismissed by the explicit toolbar Back action or the
   device Back action; device Back exits only from the root settings page.
   Outside-tap dismissal is reserved for transient keyboard panels.
4. Focus and selected states must remain visible in light and dark themes.
5. Avoid web-only hover behavior; Android touch feedback is the primary signal.
6. Use vector drawables with consistent 24dp optical bounds.
7. Keep keyboard geometry responsive; never tune to one screenshot size.

## Accessibility and privacy gates

- label every icon-only action with a content description
- keep touch targets at least 44dp and use 8dp separation where possible
- preserve readable contrast in both themes
- announce panel titles and state changes to TalkBack
- do not expose typed content in diagnostic UI
- show an explicit incognito state when learning/privacy behavior changes
- do not use decorative gradients, glass effects, or color alone to communicate
  meaning

## Implementation order

1. Normalize colors and dimensions into semantic Android resources.
2. Apply key and utility-key states without changing input behavior.
3. Apply toolbar/panel hierarchy and empty/loading/error feedback.
4. Apply settings typography and row rhythm.
5. Run light/dark contrast and narrow/tall device checks.
6. Only then add subtle transitions and icon polish.

---
name: Wearable Heart-Rate Data Viewer
description: A quiet field journal for recorded heart-rate data.
colors:
  forest-deep: "#173e32"
  accent: "light-dark(#245744, #78bca0)"
  paper: "#fafbf8"
  surface: "light-dark(#ffffff, #262626)"
  ink: "light-dark(#000000, #ffffff)"
  secondary-ink: "light-dark(#474747, #9e9e9e)"
  rule: "#d9e1da"
  on-forest: "#f6f5ec"
  sage: "#c8d9cd"
  nav-active: "#305547"
  tint: "#edf2ed"
  on-accent: "#ffffff"
  error: "#9b2525"
  error-surface: "#fff0ee"
typography:
  display:
    fontFamily: '"Manrope Variable", sans-serif'
    fontSize: "clamp(3rem, 5.5vw, 5.25rem)"
    fontWeight: 550
    lineHeight: 1.06
    letterSpacing: "-.04em"
  headline:
    fontFamily: '"Manrope Variable", sans-serif'
    fontSize: "clamp(2rem, 3vw, 3rem)"
    lineHeight: 1.15
    letterSpacing: "-.035em"
  title:
    fontSize: "1.5rem"
    lineHeight: 1.25
    letterSpacing: "-.035em"
  body:
    fontFamily: '"Manrope Variable", sans-serif'
    fontSize: ".875rem"
    fontWeight: 400
    lineHeight: 1.4286
  label:
    fontSize: ".875rem"
    fontWeight: 500
    lineHeight: 1.4286
  table:
    fontSize: ".85rem"
  supporting:
    fontSize: ".75rem"
    lineHeight: 1.6
rounded:
  control: ".625rem"
  container: ".75rem"
spacing:
  control-inline: "1.2rem"
  control-block: ".85rem"
  mobile-gutter: "1.5rem"
  section: "3rem"
components:
  action-link:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.on-accent}"
    rounded: "{rounded.control}"
    padding: ".85rem 1.2rem"
  action-link-on-forest:
    backgroundColor: "{colors.on-forest}"
    textColor: "{colors.forest-deep}"
    rounded: "{rounded.control}"
  date-input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: ".5rem .65rem"
  error-message:
    backgroundColor: "{colors.error-surface}"
    textColor: "{colors.error}"
    rounded: "{rounded.control}"
---

# Design System: Wearable Heart-Rate Data Viewer

## Overview

**Creative North Star: "The Field Journal"**

The Field Journal gives recorded readings a calm, personal setting: forest-green anchors, pale paper, warm watch photography and clear Manrope type. Spacious introductions lead into compact tables, quiet charts and thin dividing rules.

The implementation uses a fixed light theme. Photography is illustrative; data views retain exact rows and use readable, tabular numbers.

**Key Characteristics:**

- Forest-green anchors on pale paper.
- Editorial headings with compact data typography.
- Thin rules and tonal surfaces before decorative elevation.
- Control feedback with reduced-motion support.

## Colors

Forest greens anchor a pale, restrained canvas. Frontmatter preserves the source theme's color expressions; the application explicitly selects light mode.

### Primary

- **Forest Deep:** large editorial fields and the workspace rail.
- **Accent:** primary actions, links, chart marks and focus outlines.
- **Sage / On Forest / Nav Active:** readable text and selected navigation within the forest rail.

### Neutral

- **Paper / Surface:** page canvas and field surfaces.
- **Ink / Secondary Ink:** primary content and supporting explanations.
- **Rule:** table dividers, section boundaries and field outlines.
- **Tint:** success feedback and import guidance.
- **Error / Error Surface:** recoverable failure feedback, separate from the green data palette.

## Typography

Manrope Variable is self-hosted and used for both headings and body, with a sans-serif fallback. The frontmatter records the implemented roles; the home display is deliberately larger than workspace headings. Authentication titles use a dedicated size (2.5rem desktop; 2.1rem at the mobile breakpoint). Paragraphs use generous leading (1.65), while the underlying body role remains compact. Data tables and summary values use tabular numerals. Supporting copy stays sentence case.

**The Stable Numbers Rule.** Use tabular numerals for measured values and exact-reading tables.

## Layout

Use generous section separation and compact relationships within controls. Workspace content is bounded (88rem) with fluid desktop gutters; its rail is 15rem, reduced to 12.5rem at 70rem. At 48rem, the rail becomes a horizontal header, import columns stack, summary metrics become two columns and tables retain horizontal scrolling with a visible hint.

Authentication is a specific full-viewport composition: equal photograph-left and form-right columns, no outer card or navbar, at least 100dvh. The form is bounded (33rem) and centered. At 48rem and below the photograph is hidden and the form occupies at least 100dvh, with side padding (1.5rem). The home composition remains available separately; its photograph and forest text field stack on mobile. These compositions are surface rules, not a universal two-column requirement.

## Elevation & Depth

Custom page layouts are flat: boundaries come from rules, background tones and photographic material. Do not add decorative shadows to data sections. Astryx owns dialog elevation, overlay and transitions; retain those component behaviors rather than replacing them with an application-wide shadow treatment.

**The Quiet Canvas Rule.** Separate data sections with thin rules and spacing before introducing containers.

## Shapes

Controls use gently curved corners; larger import and home containers use the container radius. Authentication photography is edge-to-edge with square outer corners. One-pixel rules and the import area's dashed boundary communicate structure. These existing curved containers remain valid outside authentication.

## Components

### Buttons and links

Astryx supplies primary, secondary, ghost and destructive buttons, including disabled and loading states. Authentication uses large, full-width primary submission controls and a ghost password-visibility control. Custom action links use the frontmatter colors and padding, a minimum touch height (2.75rem), and semibold text (650). Pointer hover reduces opacity (.9) and raises custom action links slightly (-.125rem); active presses scale them (.98). Inline links underline on hover.

### Inputs / Fields

Use labelled Astryx TextInput controls for account fields, with large height (3rem), descriptions and associated status messages. Date filters use the date-input token, a thin rule-colored border and minimum height (2.75rem). Preserve the component focus treatment; custom keyboard targets use an accent outline (.15rem) offset (.2rem). Keep one visible contained focus treatment per control.

### Navigation

The private workspace rail uses pale text on forest, sage inactive links and a lighter green active surface. Links have generous touch height (2.75rem) and rounded corners. Mobile links form a row. Authentication has no navigation shell.

### Containers and feedback

Import uses a dashed, rounded drop area and a tinted guidance sample. Dragging adds an accent outline and tint. Error feedback uses the error pair with recovery actions where available; success uses tint and forest. Empty data states use thin horizontal rules, centered copy and a clear next action. There is no general-purpose decorative card grid.

### Charts and tables

Charts use thin green lines (2px), quiet rule-colored gridlines and secondary labels. Exact tables use compact type, restrained separators and horizontal overflow rather than hiding columns. Keep aggregation explanations near the chart.

### Motion

Custom control feedback lasts 160ms with cubic-bezier(.23,1,.32,1). Newly opened authentication forms enter through opacity and a small vertical shift (.5rem) over 240ms with the same easing. Reduced motion removes this movement and effectively disables transitions; smooth scrolling becomes automatic. Astryx dialogs retain their own transitions.

Motion explains control feedback and state changes; measurements and chart geometry do not move for decoration.

## Do's and Don'ts

### Do:

- Do use the light field-journal theme and self-hosted Manrope.
- Do keep exact readings available beside chart summaries.
- Do preserve visible keyboard focus and associated field errors.
- Do keep watch imagery illustrative and retain its provenance sidecar.

### Don't:

- Don't animate readings or chart geometry for decoration.
- Don't add a surrounding card or navbar to authentication.
- Don't imply live-device acquisition or clinical interpretation through the visuals.

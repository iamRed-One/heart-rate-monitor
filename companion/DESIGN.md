---
name: HeaRt Android
description: Independent native heart-rate collection with the HeaRt white and forest-green identity.
colors:
  background: "#FFFFFF"
  surface: "#FFFFFF"
  surface2: "#EDF2ED"
  line: "#D9E1DA"
  line2: "#BCCBBF"
  ink: "#0C0B10"
  muted: "#4A4652"
  dim: "#6B6672"
  faint: "#8C8794"
  accent: "#245744"
  on-accent: "#FFFFFF"
  accent-ink: "#173E32"
  error: "#C2361A"
typography:
  display:
    fontFamily: Manrope
    fontSize: 72sp
    fontWeight: 300
    lineHeight: 1
    fontFeature: tnum
  headline:
    fontFamily: Manrope
    fontSize: 32sp
    fontWeight: 600
    letterSpacing: -0.02em
    lineHeight: 1
  title:
    fontFamily: Manrope
    fontSize: 16sp
    fontWeight: 600
    lineHeight: 24sp
  body:
    fontFamily: Manrope
    fontSize: 14sp
    fontWeight: 400
    lineHeight: 20sp
  label:
    fontFamily: Manrope
    fontSize: 12sp
    fontWeight: 500
    letterSpacing: 0.06em
rounded:
  button: 10dp
spacing:
  inline: 8dp
  related: 10dp
  account-stack: 18dp
  page-stack: 20dp
  page-edge: 24dp
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.on-accent}"
    typography: "{typography.label}"
    rounded: "{rounded.button}"
    height: 48dp
    padding: 0dp 16dp
  button-outline:
    backgroundColor: transparent
    textColor: "{colors.ink}"
    rounded: "{rounded.button}"
    height: 48dp
    padding: 0dp 16dp
  navigation-selected:
    backgroundColor: "{colors.surface2}"
    textColor: "{colors.accent-ink}"
---

# Design System: HeaRt Android

## Overview

**Creative North Star: "A clear view of your rhythm"**

HeaRt is an independent Kotlin Android application with the same white, forest-green and Manrope identity as the React web viewer. Its task interface makes device connection, collection state, saved readings and file recovery easy to scan. This document records the implemented native extension; the repository root DESIGN.md remains the web design authority.

Native controls, a four-destination bottom navigation and generous white space carry the interaction. Brand expression comes from restrained green actions, confident headings and an oversized live BPM reading. The descriptive North Star summarizes the existing implementation rather than establishing a new visual identity.

**Key Characteristics:**
- White surfaces with forest-green actions and pale-green selection.
- Manrope throughout, with tabular figures for measurement values.
- Flat lists, fine dividers and Android-native forms and dialogs.
- Visible collection, connection, loading and recovery states.

## Colors

The palette combines forest green, pale foliage and near-black text on white. Frontmatter values are normative for the active light theme.

### Primary

- **Forest green** (`accent`): filled primary actions such as Find devices, Sign in and Start/Stop recording.
- **Deep forest ink** (`accent-ink`): live status, heart icon and selected native navigation content.
- **White on green** (`on-accent`): primary-action labels and loading indicator.

### Neutral

- **White canvas** (`background`, `surface`): page, native navigation and main content surfaces.
- **Pale foliage** (`surface2`): selected navigation indicator and tonal containers. The light scheme maps secondaryContainer to this tone and onSecondaryContainer to deep forest ink.
- **Fine foliage border** (`line`): one-dp separators between records and device groups.
- **Defined foliage border** (`line2`): custom outlined-button boundaries and native outline role.
- **Near-black** (`ink`): headings and primary reading content.
- **Muted graphite** (`muted`): descriptions and device metadata.
- **Quiet graphite** (`dim`): supporting constraints and small recovery guidance.
- **Faint graphite** (`faint`): disabled custom outlined-action text.

### Functional status

- **Brick error** (`error`): account validation and light-theme error role. Collection and transport messages must retain clear text, rather than relying on color alone.

**The Light Identity Rule.** The application entry point explicitly selects the light theme. The retained NightDark palette is shared-code compatibility, not the shipped HeaRt appearance; do not derive new HeaRt screens from its lime, purple or dark canvas.

## Typography

Bundled Manrope serves every text role. The legacy helpers named `serif` and `mono` both produce Manrope: neither name authorizes a serif or monospace substitution. Headline emphasis is semibold, not an italic font face. Numeric styles enable tabular figures.

Page titles use the headline role; the shared theme additionally defines semibold headlines at 34sp and 26sp and a 24sp title. Body roles are 16/24sp, 14/20sp and 12/16sp. The live measurement uses the display role, with the unit stated separately in readable body text. The label helper enforces a 12sp minimum and caps tracking at 0.06em. Native sp units retain Android text scaling.

**The One Family Rule.** Keep account, device and data interfaces in Manrope; distinguish roles through size, weight, spacing and tabular figures.

## Layout

Authenticated pages use one scrolling column inside Scaffold, with 24dp content padding and 20dp vertical separation. Scaffold owns system insets; do not add a second status-bar inset to this content. The account column uses 24dp edges, 18dp separation, status-bar padding and keyboard padding. Keep controls reachable when the keyboard is open.

The header combines a 32dp HeaRt mark, title and Sign out. Account pages use a 38dp mark and a full-width, 180dp-high illustrative watch image. The bottom bar exposes Devices, Live, Readings and Files with persistent icons and labels. Flowing protocol and source chips wrap naturally; long lists scroll vertically. No custom tablet breakpoint or multi-column layout is currently defined.

Use Android's document picker for imports and exports, system Bluetooth/notification permission prompts and an ongoing foreground-service notification during collection. The status bar and navigation bar show dark system icons on the white light surface. Keep system bars visible.

## Elevation & Depth

The page is predominantly flat: white surfaces, spacing and fine rules separate groups. There is no custom shadow scale. Native Material dialogs provide the temporary foreground layer for import review and deletion confirmation. Preserve their platform behavior rather than inventing a branded modal system.

## Shapes

Custom NButton actions have gently rounded 10dp corners, a 48dp default height and 16dp horizontal padding. Outlined variants use a one-dp border. Native Material fields, chips, navigation indicators, buttons and dialogs retain their component shapes; do not infer a global 10dp radius from the custom button. The heart mark and outlined icons remain clear and recognizable.

## Components

- **Primary custom action:** forest fill, white tracked label; disabled fills fade to 40% opacity. Loading retains its color, shows a small spinner, disables repeat clicks and announces “In progress”. Press feedback scales to 0.96.
- **Outlined custom action:** transparent fill, near-black label, fine defined border. Native OutlinedButton and TextButton also occur; preserve each control's existing Material behavior.
- **Account form:** labeled full-width outlined fields, email/URI keyboards, password visibility toggle, adjacent validation, sign-in/register switch and a busy action. Registration explains the password requirement.
- **Navigation:** four labeled destinations. Selection uses a pale-green indicator with deep-green content; keep the selected destination obvious without color alone.
- **Protocol/source chips:** native FilterChip controls. Live offers ET488 watch, Q520 ring and Bluetooth HR; disable protocol changes during active collection. File import distinguishes Recorded and Synthetic.
- **Live measurement:** outlined heart, large tabular BPM or an em dash, explicit unit, collector message and captured/sent counts. Start changes to Stop while collection is active. Preserve this explicit state relationship.
- **Record and device rows:** concise titles, secondary metadata, contextual actions and a hairline. Readings pair UTC timestamps with BPM values; pagination uses Load more.
- **Import and delete dialogs:** show validated import count, time range and source before Save recording; destructive deletion states its consequence and requires confirmation.

**The Compatibility Rule.** Device copy names supported protocols: ET488/Veepoo, Q520/Yucheng and sensors advertising standard Bluetooth Heart Rate service 180D with measurement 2A37. Never imply universal watch support. Preserve explicit connection and transport failure messages, and distinguish captured samples from sent samples.

The sidecar contains browser-renderable visual references for the native controls. These are documentation previews, not replacement HTML components or a specification for Android touch, ripple, focus or accessibility behavior.

## Do's and Don'ts

### Do:

- Do use the light white and forest-green identity on every HeaRt screen.
- Do keep native Android navigation, forms, dialogs, permission prompts and file pickers familiar.
- Do show device compatibility, collection state and recovery feedback explicitly.
- Do keep BPM values tabular and pair them with units.
- Do preserve dark system-bar icons on the white light surface.

### Don't:

- Don't introduce the retained dark/lime Vigil appearance into HeaRt.
- Don't claim that every watch supports collection or that heart rate is ECG or diagnosis.
- Don't add a duplicate status-bar inset inside the authenticated Scaffold.
- Don't use decorative shadows or cards to replace the existing flat reading lists.

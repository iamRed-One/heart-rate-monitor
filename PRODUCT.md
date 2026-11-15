# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

User selected React with Astryx for the frontend, Python/FastAPI for the backend and PostgreSQL in the existing local Docker container. Vite and TypeScript are frontend implementation tools.

## Users

Wearable owners reviewing recorded heart-rate data. The student and course assessor also evaluate the system with explicitly synthetic data.

## Product Purpose

Import a supported CSV, review it before saving, then explore exact readings, charts and descriptive summaries. Users must be able to find, rename and delete their own recordings.

## Operating Context

Local development with a tested API, PostgreSQL and TablePlus. Data is uploaded as timestamp/heart_rate CSV, not collected live. The public home page introduces the viewer; authentication opens a private workspace.

## Capabilities and Constraints

Accounts use private cookie sessions, an Origin check and CSRF tokens. CSV validation is all-or-nothing, with a preview before confirmation. Date filters use UTC days. Chart aggregation is disclosed and exact readings remain available. No clinical diagnosis, emergency alert, social login, password-reset flow, Bluetooth or companion-app acquisition is implemented.

## Brand Commitments

The coursework title is Wearable Heart-Rate Data Viewer. The user requests a full-height authentication split: watch photography on the left, form on the right, blending into the background without a surrounding card or navbar. The index must open login; preserve the home page for later. Use Astryx and concise copy. The user delegated other aesthetic choices and requested Impeccable, UI/UX Pro Max, Animate and Emil Design Engineering guidance.

## Evidence on Hand

The existing local backend and PostgreSQL integration tests cover authentication, CSV import, dashboards and recording management. `backend/samples/synthetic-heart-rate.csv` is synthetic. There are no testimonials, customer numbers, clinical validations or performance claims to fabricate. Watch imagery will be illustrative, not proof of a supported device integration.

## Product Principles

- Show the origin and age of data honestly.
- Keep the path from upload to a useful view short and recoverable.
- Protect ownership and require confirmation for deletion.
- Let a readable chart and exact table complement each other.
- Motion explains feedback and state; it does not move measurements for decoration.

## Accessibility & Inclusion

The SRS requires keyboard-operable flows, associated field errors, visible focus, a table alternative to charts and layouts usable from 360px to desktop. Respect reduced motion and maintain readable contrast.

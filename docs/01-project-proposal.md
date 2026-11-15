# Project proposal: Wearable Heart-Rate Data Viewer

**Project ID:** SP-332 | **Domain:** IoT & Smart Systems | **Level:** Intermediate  
**Student:** Ambali Ridwan Oladele | **Matric number:** U23CS1019  
**Course:** CSC403 Software Engineering | **Lecturer:** To be supplied  
**Date:** 15 November 2026 | **Status:** Draft, intended as a one-page proposal when formatted

## Problem statement

Recorded heart-rate measurements can be difficult to explore when presented as individual rows in a file. This project addresses the need for a simple web interface that organises timestamped wearable readings into recording sessions and presents their trends and summaries. It focuses on viewing recorded data rather than connecting directly to a wearable.

## Target users

The primary users are wearable owners who can obtain recorded readings in the supported CSV format. Students and the course assessor can use a clearly labelled synthetic dataset to demonstrate the application without requiring a physical device. No clinician workflow is proposed.

## Aim and objectives

Develop a web application that imports, stores and visualises recorded heart-rate data. The objectives are to: (1) provide user registration and sign-in; (2) validate CSV readings and explain errors before saving; (3) store recordings under the correct user; (4) display timestamped charts and minimum, maximum and average BPM; (5) support date filtering and recording management; and (6) verify correctness, access control and usability through documented tests.

## Scope

Version 1 includes private user accounts, CSV validation and confirmation, recording-session history, a dashboard, chart and table views, date filtering, session labels and deletion, and labelled sample data. CSV columns are `timestamp` and `heart_rate`; timestamps must include a UTC offset. Supported files are defined by our documented contract; compatibility with arbitrary vendor exports is not assumed. Live readings, direct Bluetooth, USB acquisition, Vigil integration, ECG, other vital signs, diagnosis and emergency alerts are excluded from version 1 and may be assessed later.

## Technology and process

The selected backend stack is **Python / FastAPI + PostgreSQL**, as listed in the workbook. The frontend will use **React and the Astryx design system**, as selected by the student; FastAPI will provide the data API and PostgreSQL will run locally in Docker. Development will follow the **Incremental model**: establish accounts and validated import, add viewing and history, then improve quality and deploy. This supports the mid-semester prototype and focused testing. The process justification is in [02](02-process-model.md).

## Deliverables and evaluation

The 13-week coursework requires a proposal and repository; SRS and stories; backlog, schedule, risk register and estimate; UML models; architecture, ERD and schema script; patterns and wireframes; working modules and Git history; prototype and progress report; code-review and static-analysis evidence; testing evidence with at least 60% coverage targeted; threat model, CI and a test deployment; change request, manuals and metrics; and the final demo, report, reflection and peer review. The **exact wording and marks** are retained in the [deliverables schedule](13-deliverables-tracker.md) and [source extraction](00-workbook-requirements.md), which form the proposal's supporting schedule.

Success means a user can import a valid file, see correct summaries and time-ordered readings, filter them, and manage recordings while another user cannot access them. Invalid imports must leave no partial recordings. Test results, deployment and assessment evidence will be recorded only after execution. **Repository link:** Pending repository creation and hosting selection.

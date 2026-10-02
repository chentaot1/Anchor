# Anchor: turning intentions into a next action

## The problem and intended users

A student can know an assignment matters and still struggle to begin it. Deadlines, course materials, distractions, and planning tools often live in separate places. Anchor explores a single workflow: capture work, find an achievable first action, protect a focus session, and resume after an interruption.

The project is grounded in personal use and iterative debugging. Its intended benefit is practical support for students with executive-function difficulties. There are no established adoption figures, measured improvements in grades or focus, clinical results, or nonprofit deployments to report. Broader accessibility and community impact remain goals to validate with users.

## What the repository implements

Anchor has Android and Windows desktop clients. The shared module contains common planning and UI logic; platform modules handle persistence, notifications, blocking, and local inference. Desktop uses SQLite and Windows foreground-process information, while Android uses Room and accessibility-based blocking.

The workflow includes tasks and milestones, a timeline, syllabus records, focus sessions, and a progress garden. The AI studio supports task breakdown, brain-dump capture, triage, replanning, and conversation. Desktop connects pending syllabus records to the AI context and uses deterministic answers for supported deadline questions.

The current source includes desktop fixes for executable/website identity, focus-only scope checks, stricter breakdown validation, syllabus context, and additional blocking/settings behavior. These changes require rebuilding an existing desktop installation. The repository also retains the earlier development history rather than presenting the project as a single polished final submission.

## Decisions and tradeoffs

**Local inference.** GGUF models run through llama.cpp on the user's machine. This avoids needing a hosted-model request for the core inference path and permits offline use. It also makes model size, packaging, hardware, and latency part of the product experience. Git excludes the weights; Android's offline installation package bundles a verified model, making that package large.

**Useful behavior before model availability.** Deterministic intent routing and task-specific scaffolds provide an immediate response. When a model is available, it refines the scaffold. An invalid decomposition returns to the scaffold rather than showing an arbitrary list.

**Context from saved records.** The desktop AI reads the same syllabus records shown in the app. It selects incomplete items by course, date, and relevance, bounds their size, and treats imported text as data. A deadline answer refers to saved dates; it does not claim to know an unsaved assignment.

**Platform identity matters.** A desktop executable named ChatGPT is different from a ChatGPT browser tab. Website matching is limited to recognized browser executables, and usage counters retain distinct app and website identities. Focus scope checks apply during active focus sessions.

## A concrete failure and the response

A breakdown of a written language assignment produced unrelated actions: open a document, put dishes in a sink, and write a failing test. Those actions had appeared as examples in the system prompt. The failure pointed to example leakage and weak enforcement of relevance, rather than demonstrating that valid JSON was sufficient.

The revised desktop prompt preserves the full task, asks each step to advance that task, and uses a task-specific draft. The grammar enforces the selected step-count range. Parsing rejects empty or duplicate steps, incorrect counts, a changed first action, and inconsistent optional action/time fields. Routing preserves the user's full task instead of reducing it through a generic heuristic.

These checks improve the boundary around the model but do not prove semantic correctness. A plausible step can still invent a requirement or be impractical. The user remains responsible for checking assignment instructions, deadlines, and whether a proposed plan is useful.

## Verification and handoff

On October 2, 2026, the desktop Gradle test suite passed **92 tests with zero failures, errors, or skips**. Regression tests cover the app-versus-website distinction, scope checks outside focus, task routing, breakdown parsing, and syllabus relevance/deadline behavior. Other desktop tests cover existing policy, database, and platform helpers.

For a new developer, begin with the README, run `:desktopApp:test`, then trace `DesktopSmartRouter` into `DesktopAiEngine`. The blocker entry point is `DesktopProcessMonitor`; syllabus context is selected by `DesktopAcademicContext`. Run `:app:testDebugUnitTest` when changing Android behavior. Native window blocking, accessibility permissions, installation/update flows, and real-device behavior also need manual verification on their supported platforms.

AI coding assistance has supported implementation and debugging. Model-generated code and model-generated plans both require human review. This case study does not attribute the development to a particular hosted model or claim that a fine-tuned model's filename establishes use of the Claude API.

## Remaining work

- Check proposed plans against the actual task instructions, including preservation of tasks, dates, course identifiers, and constraints.
- Validate website detection and experimental scope classification against false positives on real Windows sessions.
- Test accessibility and usefulness with people beyond personal use, with consent and measures defined before claiming impact.
- Improve packaging and model management so offline AI does not create unnecessary duplicate storage.

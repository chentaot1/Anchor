# Anchor — Full App Specification

Personal ADHD planner for Samsung Galaxy S24 Ultra. **Single deliverable** (no phased MVP product); implementation may still follow dependency order internally.

## Owner
- **Built by:** agent (Cursor)
- **Tested by:** you on real school days

## AI
- **Model:** unsloth/Qwen3.5-4B-GGUF **Q8_0** (UD-Q6_K_L fallback in Settings)
- **Runtime:** llama.cpp via JNI (stub ships until native + GGUF linked)
- **JSON:** GBNF grammar, simple schema, editable preview, line fallback
- **Unload** during focus/blocking

## Modules (all in v1 scope)

### Today
- Focus session count (primary success metric)
- Manual energy (Low / OK / High)
- Replan prompt when queue non-empty
- Quick start focus

### Plan
- Timeline + inbox (today / someday / waiting)
- Assignments with due dates
- Routines (morning launch, study start)
- Replan queue
- Calendar **read-only** Google import
- Drag scheduling (UI completion in progress)

### Focus
- 20/5 and 10/3 timers
- Deep Focus AccessibilityService blocking
- Default + custom blocklists
- Scheduled shields
- Focus garden (tree per completed session)
- Locked sessions

### Grow
- Finch-style companion (partial credit, pause mode)
- 5 static CBT cards
- Mood tap + optional note

### AI
- Break down task
- Brain dump → tasks
- Inbox triage
- If-then on routines
- Replan assistant
- Sunday weekly plan
- Model download manager (~5GB)
- Debug prompt regression screen

### System
- Room local DB + export backup
- Home screen widget
- Share-to-inbox intent
- Samsung setup guide (battery, accessibility)
- Notifications capped (transitions + Replan)

## Agreed revisions
- Success = focus sessions + Replan on derail (not rigid 3 wins)
- Calendar read-only until stable
- GBNF + simple JSON + preview + fallback

## Build status
Full feature implementation: timeline, focus timer + blocking, replan, grow, AI modes, settings, calendar import, model download, widget, backup export. Native llama.cpp JNI still optional — stub AI works until GGUF is downloaded on device.

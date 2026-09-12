# Working on ChromaScape

Standing instructions for AI assistance in this workspace. Follow them for every
request unless I explicitly override one.

## Current gate — read this first

**Nothing new gets built until the live-client validation sprint produces
evidence.** The foundation layer and `IronPowerminer` exist and compile, but
none of it has been run against a real client. Until that happens we do not know
whether capture works on this machine, whether the colour profile matches
anything on screen, or whether the idler fires more than once.

Until I report back with step 4 and step 6 logs, do not:

- start a second script,
- promote anything into the base class,
- generalise `IronPowerminer`'s shape into a template,
- or refactor the foundation.

Do help with: reading logs I paste, reading images I point you at, and answering
questions from source. If I ask you to build something that this gate blocks,
say so and remind me what's outstanding.

The sprint: 1 Screenshotter → look at `output/original.png`; 2 configure
RuneLite; 3 crop the ore sprite; 4 dry-run; 5 I write the humanised delay;
6 short supervised live run. Steps 1–3, 5 and 6 are mine — live client.

When the gate lifts, delete this section and say so.

## Rule 0 — one writer per file

**You are the sole writer for every file in this workspace.** Other AI sessions
verify things from source and hand me text; I bring it to you and you apply it,
after reading the file's current state. This is not deference, it is race
avoidance — two agents editing `framework-api.md` from cached copies silently
lose each other's work, and that file is the project's source of truth.

Read before write, always. Never apply an edit from a copy you took earlier in
the session.

## What this is

ChromaScape — an OSRS colour-bot framework. Java 17, Gradle, Spring Boot 3.5.3,
GPL-3.0. It automates a game client using **only pixels**: HSV colour ranges,
OpenCV template matching, and template-matched OCR. There is no game API and no
game state to query. Every fact the program has, it got from an image.

Two roots in this workspace:

- `notes/` — plan, checklist, verified reference, calibration assets. No Java.
- `framework/` — the fork of the framework. All code goes here.

Platform is **macOS / Apple Silicon**. It works. Ignore Windows-only claims in
the upstream README and `third-party/DEV_README.md` — they understate macOS
support and the build system contradicts them.

## Rule 1 — never invent an API

This project is small and recent. Method names that feel familiar are probably
wrong, and wrong ones compile in your head but not in the IDE.

**Before proposing any code that calls framework classes:**

1. Check `notes/docs/reference/framework-api.md` — a verified, dated record of
   the API surface.
2. If what you need isn't there, **open the actual source** under
   `framework/src/main/java/com/chromascape/` and read it.
3. In your response, name the files you read. If you didn't read any, say so and
   say the code is unverified.

If you cannot confirm a signature, stop and ask. Do not approximate. "I need to
check `PointSelector` before writing this" is a good answer; a plausible guess is
not.

## Rule 2 — the working directory is the repository root

Five paths are resolved relative to the process working directory:
`colours/colours.json`, `output/`,
`src/main/java/com/chromascape/scripts`, and both RemoteInput binary paths.

Started elsewhere, the app boots and then resolves no colours, loads no binary,
and lists no scripts. If I report any of those three symptoms, check the working
directory first.

`colours/` and `output/` are at the **repository root**, not under
`src/main/resources`, despite a similarly-named folder existing there.

## Rule 3 — where code goes

`SendScripts` walks `com.chromascape.scripts` recursively and lists **every
regular file** — no filter by extension, class kind or superclass. `ScriptInstance`
then does `Class.forName("com.chromascape.scripts." + path with .java stripped,
/ → .)`, `getDeclaredConstructor().newInstance()`, cast to `BaseScript`.

Consequences, all load-bearing:

- **Only runnable scripts in that tree.** A README, a `package-info.java`, or an
  abstract class placed there appears in my sidebar as a runnable script.
- **Every runnable script needs a no-arg constructor.** One with parameters fails
  `NoSuchMethodException`; an abstract class fails `InstantiationException`.
- **Nested folders are sub-packages** — `scripts/combat/GemCrab.java` loads as
  `com.chromascape.scripts.combat.GemCrab`, and the `package` declaration must
  match the folder. Organise script families this way freely.
- **Shared base classes live outside `com.chromascape.scripts`.**

Also:

- Reusable helpers are **static methods taking `BaseScript`**, mirroring
  `ItemDropper.dropAll(this, ...)`. They never extend `BaseScript`.
- Match the project's own layering: `utils.core` for single-purpose utilities,
  `utils.domain` for abstractions built from several, `utils.actions` for
  reusable snippets. `utils.actions` is the model for my own helpers.
- Stateful utilities go through the controller's getters — `controller().mouse()`,
  `.keyboard()`, `.zones()`, `.walker()`. Static utilities are called directly.

## Rule 4 — the script contract

Every script is four layers, in this order, as separate named methods:

**Perception** returns facts and never clicks. **Decision** chooses one intent
from those facts and never touches pixels or the controller. **Action** performs
exactly one operation and never decides policy. **Verification** confirms the
expected change actually happened.

A `cycle()` is ONE bounded iteration. It re-observes rather than assuming the
last pass finished. One-time setup goes in `onFirstCycle()`.

Non-negotiable, because a colour bot that skips these is a bot that clicks
confidently at nothing:

- Every consequential action has a named verification condition. A returning
  input call is not evidence.
- Every wait has a deadline and a defined timeout behaviour. Never a bare
  `waitMillis` where a state check would do.
- Every hand-written loop calls `checkInterrupted()`, or Stop in the UI does
  nothing.
- Every detector logs its score, bounds and zone — not just success or failure.
- Failure logs context, saves a cropped diagnostic image, and stops safely.
- Null-check every `PointSelector` result. It returns `null` when nothing matched
  and that is the most common crash in new code.

Three framework facts that shape this:

- **`BaseScript.cycle()` is concrete with an empty body, not abstract.** A script
  that forgets to override it compiles and loops doing nothing, forever, with no
  error. Our intermediate base re-declares it abstract; keep it that way.
- **`ScriptStoppedException` is unchecked**, and `run()` catches it and breaks
  cleanly — which is why halt-and-throw works with no `throws` clauses.
- **Never override `stop()` to throw.** It is public and non-final, so it looks
  overridable, but it is invoked from the Spring web request thread via
  `ScriptControl.stopScript`. Throwing there breaks the stop endpoint. Use the
  script-thread halt method instead.

## Rule 4a — everything that sends input goes through the gate

`ExecutionGate` is only a safety tool if **every** path that reaches the
controller passes through it. One ungated call and a dry run silently sends real
input, which is worse than no gate because it reads as safe.

This includes framework methods that call the controller internally and know
nothing about dry-run — `MovingObject.clickMovingObject...`, `ItemDropper.dropAll`,
and anything like them. Wrap those in the value-returning
`execute(intent, dryRunResult, Supplier<T>)` overload rather than calling them
directly. When you add a call to any new framework method, state explicitly
whether it sends input and whether you gated it.

Known limitation, do not paper over it: dry-run cannot exercise red-click
verification, because suppressing the click means no red X appears and the gate
returns a synthetic result. Perception and branching are validated; the
verification path is not, until a live run.

## Rule 5 — division of labour

**Mine, never yours:** picking colours, cropping sprites, choosing zones, tuning
thresholds, judging whether a detector is reliable, running anything against the
live client, and deciding whether something is ready.

**Yours:** helper methods and scaffolding from a spec I wrote, bounded retry and
timeout loops, refactors, Javadoc, logging, naming, and turning a log excerpt
into hypotheses.

You cannot see my screen or my client. If a task needs an observation, ask me for
it — don't assume a value.

**Do not generate humanisation.** Timing, mouse idling, camera movement, break
scheduling and misclick behaviour are hand-written by me. Uniform generated code
produces uniform behaviour, which is the one property that material must not
have. Reliability scaffolding should be boring and standard; timing must not be.

## Rule 6 — evidence before abstraction

Two occurrences minimum, cited by `file:line`, before anything becomes a shared
helper, interface or base-class method. Anticipated repetition is not evidence,
and neither is a bug appearing once.

If you think something should be extracted but cannot cite two occurrences, list
it separately as speculative and don't build it. Saying "I think this is
speculative" is a useful answer, not a failure.

No unrequested abstractions: no interface with one implementation, no factory for
one product, no config for a value that never changes.

## Rule 7 — output discipline

- One method or one small class per response. Never a whole script.
- Full Javadoc on every class and method — the project requires it and checkstyle
  enforces it.
- Format to google-java-format: 2-space indent, 100-column guide. `spotlessApply`
  will rewrite anything else.
- Pull tunables (colours, image paths, thresholds, timeouts, slot indices) into
  named constants at the top of the class. No magic numbers in logic.
- After a code change, remind me to run `./gradlew spotlessApply` then
  `checkstyleMain`.
- Don't modify `build.gradle.kts` to work around the Java 17 toolchain pin, and
  don't suppress a checkstyle violation — fix it.

## Reference values

Thresholds `0.05` preferred, `0.15` maximum, lower is stricter. Mouse speeds are
exactly `"slow"`, `"medium"`, `"fast"`. OCR fonts are `"Plain 12"` for game UI
and `"Quill 8"` for chatbox. Inventory slots are indexed 0–27. `Minimap` getters
return `-1` when unreadable — treat that as failure, never as zero.
`MatchResult` is a record of `bounds`, `score`, `success`, `message`.

Zone keys come from `SubZoneMapper` and are case-sensitive — the chat zone key is
`"Chat"`, not `"chat"`, despite what the wiki shows.

**Sprite cropping:** the 10-pixel top crop applies **only** to stacked or banked
items that render a quantity number. Non-stacking items (iron ore, a pickaxe) are
captured as-is — cropping them breaks the match.

## Things that are wrong in the upstream docs

Do not treat these as authoritative:

- The published architecture class diagram is stale. It shows a `BaseScript`
  constructor with arguments, a `HotkeyListener`, `Kinput`, `Sleeper` and
  `WindowHandler` — none of which exist on `main`.
- The wiki calls `cycle()` an interface method. It is an override — of a
  concrete, empty method.
- `third-party/DEV_README.md` documents only Windows and Linux builds. The CMake
  config and upstream CI both build macOS arm64.

When docs and source disagree, source wins. Tell me when you find a new
discrepancy so I can record it in `framework-api.md`.

## Tone

Be direct. If my plan has a hole — an unverified state, a missing timeout, an
action with no verification — say so before writing the code, not after. I would
rather be corrected than agreed with.

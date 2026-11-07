# AGENTS.md

Guidance for coding agents working in this repo.

## README rules

The README is for players browsing the Plugin Hub, not for developers. Most readers skim the first screen and leave, so keep it short.

### Structure

Keep these sections, in this order, and add no new top-level sections:

1. **Title + icon + one-sentence pitch.** Say what the plugin does for the player in plain English.
2. **Features.** One line per bullet, 8 bullets at most. Lead each bullet with the benefit, not the mechanism.
3. **Getting started.** Numbered steps from installing to first use. Keep it to 5 steps or fewer.
4. **Settings** (optional). Cover only the settings a new user needs to understand, one line each.
5. **Good to know.** 3 to 5 bullets: limitations, common surprises, and the reset or troubleshooting step.
6. **More.** Links to the `docs/` pages and the issue tracker.
7. **License / Credits.**

### Style

- Write in second person ("you") with short sentences and no jargon. A player who has never read the source should understand every line.
- Use **bold** for the names of in-game or config-panel things the player will look for. Use `code` only for commands they type.
- Don't use internal names: varbits, class names, tick buckets, Dijkstra, cache keys, or Java versions.
- Aim for under 60 lines in total. If a change pushes the README past that, cut something or move it to `docs/`.

### What does NOT go in the README

Put these in `docs/` (create a file if none fits), or leave them out completely:

- Edge cases, precision caveats, and "X does not happen when Y" clarifications → `docs/` reference pages
- How the plugin works internally: timers, routing, caching, data sources → `docs/` reference pages
- Build, run, IDE, or JDK instructions → `docs/development.md`
- Manual verification and test checklists → `docs/development.md`
- Changelogs and "in version 1.x.y" notes → git history and the Plugin Hub update, never the README
- A paragraph for every new setting

### When you change the plugin

- **New user-visible feature?** Add at most one bullet to Features, or one line to Settings, and only if a new user would care. Put the full behaviour in the relevant `docs/` page.
- **Bug fix or internal change?** Don't touch the README. Update `docs/` only if the documented behaviour changed.
- **New manual test steps?** Add them to `docs/development.md`.
- **Data source or attribution added?** Add a short line to Credits, and keep the full notice in `THIRD_PARTY_NOTICES` if the project has one.
- Before finishing, reread the whole README as a first-time player would. If any part reads like a spec, rewrite or move it.

## Plugin Hub icon

`icon.png` in the repo root is the Plugin Hub icon. It must be at most 48×72 px. Don't remove or rename it.

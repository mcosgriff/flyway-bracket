# Contributing

Flyway Bracket is maintained by one person; issues and pull requests are welcome. This page is for people
working on the code. The rules a change is held to live in [`CLAUDE.md`](CLAUDE.md) (Decisions,
Conventions); [`CODING_STANDARDS.md`](CODING_STANDARDS.md) says which apply to a diff.

Flyway Bracket is under the GPL-3.0 ([`LICENSE`](LICENSE)); a contribution is accepted under the same
licence.

## Setup

Open the folder in Android Studio and let Gradle sync. JDK 17. The project tracks the latest Android
Gradle Plugin; upgrade it with Tools → AGP Upgrade Assistant.

Turn on the repo's git hooks once per clone; the pre-commit hook refuses commits on `main`:

```
git config core.hooksPath .githooks
```

## Tests

| Command | What it runs |
|---|---|
| `./gradlew :domain:test` | The game rules: pure Kotlin, fast. Run it often. |
| `./gradlew build` | Everything: unit tests, screenshot and accessibility tests, lint. Lint fails on a warning. |
| `./gradlew :app:recordRoborazziDebug` | Re-records the reference screenshots in `app/src/test/screenshots/`. Look at every image it changes before committing. |
| `./gradlew :server:test` | The league server's tests. Some start a real Postgres in Docker, so Docker must be running. `docs/server/running.md` says how to run the server. |
| `./gradlew koverHtmlReportCoverage` | Test coverage of `:domain`, `:api`, `:data` and `:app`, in `build/reports/kover/htmlCoverage/`. |

Every screen's `…Content` composable is drawn under Robolectric in light and dark (`shoot` in
`app/src/test/java/io/cosgriff/flywaybracket/ui/screens/Screenshots.kt`), checked with Google's
Accessibility Test Framework (a warning fails), and compared pixel for pixel with its reference. On a
mismatch, CI uploads the new image and the difference as the `screenshot-differences` artifact. Record
references on Linux, as CI runs: text is drawn slightly differently on other systems.

## The user guide

The website for players is built from `site/` and published to GitHub Pages by
`.github/workflows/pages.yml` on each push to `main` that changes it.

- A page is a Markdown file in `site/pages/`. Its front matter has a `title` and a `description`. A new
  page also needs a link in the menu in `site/template.html`.
- `site/build.sh` builds the site into `site/build/` with [pandoc](https://pandoc.org) (3.13, the version
  the workflow installs). It copies the screenshots from `app/src/test/screenshots/` and the font from the
  app, so neither is kept twice.
- `python3 site/check.py` checks the built pages: links, nothing loaded from another host, the markup an
  accessible page needs, color contrast, and that the colors in `site/style.css` match `Color.kt`. The
  workflow also runs pa11y (`cd site && npm ci && npm run a11y`).
- Links between pages are relative (`install.html`), so the site works at any address. The pages load
  nothing from another host and use no JavaScript.
- A page about something the app cannot do yet has `beta: true` in its front matter, which puts the
  "Coming in the beta" note under its title. Remove the line when the feature ships.
  `grep -l '^beta: true' site/pages/*.md` lists the pages still marked.
- Write for a reader who has never done a bracket or used eBird: short sentences, no jargon, one idea per
  paragraph. A rule stated on the site must match `CLAUDE.md` and the tests in `:domain`.

## Bird data

Never commit eBird data, or files built from it: eBird's terms of use don't allow it. Tests use made-up
figures (`domain/src/test/.../Charts.kt`). Put local downloads in `packs-local/`, which git ignores.
`docs/packs.md` says how to build a state pack from them with `./gradlew :tools:packbuilder:run`.

## Changing a dependency

A library must carry no trackers; CI scans the APK with Exodus Privacy and fails on one, or on a
permission that isn't in `.github/privacy/permissions.txt`. Check the dependency tree of any Google or
Firebase library before adding it. A pull request also fails if it adds a dependency with a known
vulnerability, or with a licence outside the GPL-compatible list in `.github/dependency-review-config.yml`.

## Wording and translations

All text the app shows is in `app/src/main/res/values/strings.xml`. A new language is a
`res/values-xx/strings.xml`.

## Pull requests

- Work on a branch named `<type>/<topic>` (`feat/outing-flow`, `fix/seed-order`), never on `main`.
- Pull requests are squash-merged, so the title becomes the commit on `main`. It must be a
  [Conventional Commit](https://www.conventionalcommits.org/) (`fix:`, `feat:`, `docs:`, …);
  `release-please` builds the version and changelog from them, and `pr-title.yml` checks it.
- `./gradlew build` must be green.
- A change people would notice updates `README.md` in the same pull request.
- Test data is made up: no real personal data and no real bird figures in the repo.
- Security problems go through a private report, not an issue: see [`SECURITY.md`](SECURITY.md).

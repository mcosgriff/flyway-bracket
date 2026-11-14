# Design

A local copy of the Flyway Bracket design canvas, so screens are built from the exact mockups.

- Canvas: https://claude.ai/artifact/6YaSetusq6mnkkiE5fds6R

The canvas is the source of truth. A change made there reaches the repo only when these files are copied
again; change a mockup on the canvas, not here.

## Boards

| Screen | Source |
| --- | --- |
| Home | `source/Main.dc.html` |
| Packs | `source/Packs.dc.html` |
| Outing in progress | `source/Outing.dc.html` |
| Add sighting | `source/AddSighting.dc.html` |
| Outing summary | `source/Summary.dc.html` |
| Bracket, list by round | `source/Bracket.dc.html` |
| Bracket, a finished round | `source/RoundDone.dc.html` |
| Bracket, tree of one quarter | `source/BracketTree.dc.html` |
| Bracket, whole field | `source/FullBracket.dc.html` |
| Matchup | `source/Matchup.dc.html` |
| League | `source/League.dc.html` |
| Join a league | `source/LeagueJoin.dc.html` |
| Create a league | `source/LeagueCreate.dc.html` |

## Reading a board

Each board is an HTML page drawn at 390×844. The markup above the `<script>` is the layout; the script's
`theme(dark)` function holds the colors for light and dark, the same values as
`app/src/main/java/io/cosgriff/flywaybracket/ui/theme/Color.kt`. Every screen is built in both.

The boards load `support.js`, which belongs to the canvas and is not copied here, so a board does not
render when opened from this folder. Read it as source: exact text, colors, spacing and order.

## Where the app differs on purpose

- **Round names.** Some boards say "Sweet 16", "Elite 8" and "Final Four". Those are NCAA trademarks and
  the app does not use them (`CLAUDE.md`, Decisions). Use "Round of 16", "Quarterfinals", "Semifinals"
  and "Final".
- **Title font.** The boards set titles in Barlow Condensed. The app has only Inter bundled (`Display` in
  `ui/theme/Type.kt`).
- **Sample data.** Names, places, counts and percentages on the boards are made up.

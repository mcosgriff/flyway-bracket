#!/usr/bin/env python3
"""Checks the built user guide (site/build/). Standard library only; run after build.sh.

Links: every relative link, image and stylesheet points at a file that was built, and every `#anchor`
at an id on its page. Links to other websites are listed, not fetched.

No third-party requests: nothing a browser loads by itself (stylesheet, image, font, script, frame)
comes from another host, and no page has a script.

Accessibility, the parts that can be read from the markup: a language, a title, a description, one
`h1`, heading levels that do not skip, text for every image and link, and a header cell in every table.
The contrast of each text color on its background is worked out for light and for dark (WCAG AA, 4.5:1).

Colors: every token in style.css has the value it has in the app's Color.kt.

pa11y runs in the workflow as well, for what needs a browser.
"""

from __future__ import annotations

import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

SITE = Path(__file__).resolve().parent
BUILD = SITE / "build"
COLOR_KT = SITE.parent / "app/src/main/java/io/cosgriff/flywaybracket/ui/theme/Color.kt"

# (text token, background token): each pair style.css puts together.
TEXT_ON_BACKGROUND = [
    ("ink", "ground"),
    ("ink", "surface"),
    ("ink", "surfaceMuted"),
    ("ink", "pickContainer"),
    ("inkMuted", "ground"),
    ("pick", "ground"),
    ("pick", "surface"),
    ("pick", "pickContainer"),
    ("onWarningContainer", "warningContainer"),
]
MIN_CONTRAST = 4.5

# Elements whose address a browser fetches without being asked.
LOADED = {"img": "src", "source": "srcset", "script": "src", "iframe": "src", "audio": "src", "video": "src", "embed": "src", "object": "data"}
VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr"}


class Page(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.lang: str | None = None
        self.title = ""
        self.description: str | None = None
        self.ids: list[str] = []
        self.headings: list[int] = []
        self.links: list[tuple[str, str]] = []  # (href, text)
        self.loads: list[str] = []
        self.problems: list[str] = []
        self._stack: list[str] = []
        self._link: list[str] | None = None
        self._link_href = ""
        self._tables: list[bool] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        a = {k: (v or "") for k, v in attrs}
        if tag not in VOID:
            self._stack.append(tag)
        if "id" in a:
            self.ids.append(a["id"])
        if tag == "html":
            self.lang = a.get("lang")
        elif tag == "meta" and a.get("name") == "description":
            self.description = a.get("content", "")
        elif re.fullmatch(r"h[1-6]", tag):
            self.headings.append(int(tag[1]))
        elif tag == "a" and "href" in a:
            self._link, self._link_href = [a.get("aria-label", "")], a["href"]
        elif tag == "link":
            if a.get("href"):
                self.loads.append(a["href"])
        elif tag == "script":
            self.problems.append("has a <script>; the site uses no JavaScript")
        elif tag == "table":
            self._tables.append(False)
        elif tag == "th" and self._tables:
            self._tables[-1] = True
        if tag == "img":
            if not a.get("alt", "").strip():
                self.problems.append(f"image {a.get('src')} has no alt text")
            elif self._link is not None:
                self._link.append(a["alt"])
        if tag in LOADED and a.get(LOADED[tag]):
            # A srcset is a list of "address size" entries.
            for entry in a[LOADED[tag]].split(","):
                self.loads.append(entry.split()[0])
        if "style" in a and "url(" in a["style"]:
            self.problems.append(f"<{tag}> has an inline style that loads a file")

    def handle_endtag(self, tag: str) -> None:
        if tag in self._stack:
            while self._stack.pop() != tag:
                pass
        if tag == "a" and self._link is not None:
            self.links.append((self._link_href, " ".join(self._link).strip()))
            self._link = None
        elif tag == "table" and self._tables and not self._tables.pop():
            self.problems.append("a table has no header cells")

    def handle_data(self, data: str) -> None:
        if self._stack and self._stack[-1] == "title":
            self.title += data
        if self._link is not None:
            self._link.append(data.strip())


def is_external(address: str) -> bool:
    parts = urlsplit(address)
    return bool(parts.scheme or parts.netloc)


def check_target(page: Path, address: str, ids: dict[Path, list[str]]) -> str | None:
    """Why a relative address is broken, or None."""
    parts = urlsplit(address)
    target = page if not parts.path else (page.parent / unquote(parts.path)).resolve()
    if parts.path.startswith("/"):
        return f"{address} starts with /; links must be relative so the site works under any address"
    if not target.is_file() or BUILD not in target.parents:
        return f"{address} points at no built file"
    if parts.fragment and target in ids and parts.fragment not in ids[target]:
        return f"{address} points at an id that is not on {target.name}"
    return None


def check_pages() -> tuple[list[str], set[str]]:
    problems: list[str] = []
    external: set[str] = set()
    pages: dict[Path, Page] = {}
    for path in sorted(BUILD.glob("*.html")):
        parsed = Page()
        parsed.feed(path.read_text(encoding="utf-8"))
        pages[path.resolve()] = parsed
    if not pages:
        return ([f"no pages in {BUILD}; run build.sh first"], external)
    ids = {path: page.ids for path, page in pages.items()}

    for path, page in pages.items():
        found = list(page.problems)
        if not page.lang:
            found.append("<html> has no lang")
        if not page.title.strip():
            found.append("no <title>")
        if not (page.description or "").strip():
            found.append("no description; give the page a `description` in its front matter")
        if page.headings.count(1) != 1:
            found.append(f"{page.headings.count(1)} h1 headings; a page has one")
        for before, after in zip(page.headings, page.headings[1:]):
            if after > before + 1:
                found.append(f"heading level jumps from h{before} to h{after}")
        for duplicate in sorted({i for i in page.ids if page.ids.count(i) > 1}):
            found.append(f"id {duplicate} is used more than once")
        for href, text in page.links:
            if not text:
                found.append(f"link to {href} has no text")
            if is_external(href):
                if urlsplit(href).scheme != "https":
                    found.append(f"link {href} is not https")
                external.add(href)
            elif broken := check_target(path, href, ids):
                found.append(broken)
        for address in page.loads:
            if is_external(address):
                found.append(f"loads {address} from another host")
            elif broken := check_target(path, address, ids):
                found.append(broken)
        problems += [f"{path.name}: {p}" for p in found]

    for sheet in sorted(BUILD.glob("*.css")):
        text = sheet.read_text(encoding="utf-8")
        if "@import" in text:
            problems.append(f"{sheet.name}: uses @import")
        for address in re.findall(r"url\(\s*[\"']?([^\"')]+)", text):
            if is_external(address):
                problems.append(f"{sheet.name}: loads {address} from another host")
            elif broken := check_target(sheet.resolve(), address, ids):
                problems.append(f"{sheet.name}: {broken}")
    return problems, external


def app_tokens() -> dict[str, dict[str, str]]:
    """Color.kt's `name = Color(0xFFRRGGBB)` lines, by theme."""
    text = COLOR_KT.read_text(encoding="utf-8")
    themes = {}
    for theme, name in (("light", "LightFlywayColors"), ("dark", "DarkFlywayColors")):
        block = re.search(rf"val {name} = FlywayColors\((.*?)\n\)", text, re.S)
        if not block:
            raise SystemExit(f"{name} not found in {COLOR_KT}")
        themes[theme] = {k: v.upper() for k, v in re.findall(r"(\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)", block.group(1))}
    return themes


def site_tokens() -> dict[str, dict[str, str]]:
    text = (SITE / "style.css").read_text(encoding="utf-8")
    dark_at = text.index("prefers-color-scheme: dark")
    declared = [(m.start(), m.group(1), m.group(2).upper()) for m in re.finditer(r"--(\w+):\s*#([0-9A-Fa-f]{6})\b", text)]
    return {
        "light": {name: value for at, name, value in declared if at < dark_at},
        "dark": {name: value for at, name, value in declared if at > dark_at},
    }


def luminance(rgb: str) -> float:
    def channel(c: int) -> float:
        s = c / 255
        return s / 12.92 if s <= 0.04045 else ((s + 0.055) / 1.055) ** 2.4

    r, g, b = (int(rgb[i : i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def contrast(a: str, b: str) -> float:
    high, low = sorted((luminance(a), luminance(b)), reverse=True)
    return (high + 0.05) / (low + 0.05)


def check_colors() -> list[str]:
    problems: list[str] = []
    app, site = app_tokens(), site_tokens()
    for theme in ("light", "dark"):
        if set(site[theme]) != set(site["light"]):
            problems.append(f"style.css: the {theme} theme does not set the same tokens as the light theme")
        for name, value in site[theme].items():
            if name not in app[theme]:
                problems.append(f"style.css: --{name} is not a token in Color.kt")
            elif app[theme][name] != value:
                problems.append(f"style.css: {theme} --{name} is #{value}; Color.kt has #{app[theme][name]}")
        for text, background in TEXT_ON_BACKGROUND:
            if text in site[theme] and background in site[theme]:
                ratio = contrast(site[theme][text], site[theme][background])
                if ratio < MIN_CONTRAST:
                    problems.append(f"style.css: {theme} {text} on {background} has contrast {ratio:.2f}; needs {MIN_CONTRAST}")
            else:
                problems.append(f"style.css: {theme} theme lacks --{text} or --{background}")
    return problems


def main() -> int:
    problems, external = check_pages()
    problems += check_colors()
    for address in sorted(external):
        print(f"other website, not fetched: {address}")
    for problem in problems:
        print(f"PROBLEM {problem}")
    if problems:
        print(f"{len(problems)} problems")
        return 1
    print(f"{len(list(BUILD.glob('*.html')))} pages: links, third-party requests, markup and colors are fine")
    return 0


if __name__ == "__main__":
    sys.exit(main())

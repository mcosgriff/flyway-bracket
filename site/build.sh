#!/usr/bin/env bash
# Builds the user guide into site/build/ with pandoc: one HTML page per Markdown file in site/pages/.
# The screenshots and the font are copied from the app on every build, so the site shows the screens
# as the tests last drew them (made-up data) and the app's own typeface.
set -euo pipefail

site="$(cd "$(dirname "$0")" && pwd)"
repo="$(dirname "$site")"
out="$site/build"

rm -rf "$out"
mkdir -p "$out/screenshots" "$out/fonts" "$out/licences"

for page in "$site"/pages/*.md; do
  name="$(basename "$page" .md)"
  args=(--from markdown --to html5 --template "$site/template.html" --variable "page_$name=1")
  if [ "$name" = index ]; then args+=(--variable home=1); fi
  pandoc "${args[@]}" --output "$out/$name.html" "$page"
done

cp "$site/style.css" "$out/"
cp "$repo"/app/src/test/screenshots/*.png "$out/screenshots/"
cp "$repo/app/src/main/res/font/inter.ttf" "$out/fonts/"
cp "$repo/app/src/main/assets/licences/inter-OFL.txt" "$out/licences/"

echo "Built $(find "$out" -name '*.html' | wc -l) pages in $out"

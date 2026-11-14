# Building a state pack

A state pack (`US-CO.fbpack`) holds one state's counties, each with its eBird bar chart and its outline.
The pack builder, a command-line tool in `tools/packbuilder`, makes one from files you download by hand.
The file format is `StatePackCodec` in `:domain`.

**Nothing on this page goes into git.** The downloads and the pack that comes out all live in
`packs-local/`, which `.gitignore` leaves out. eBird's terms of use don't allow its data, or files built
from it, in this repository. Never move a file out of `packs-local/` into a tracked folder, and never
`git add -f` one. The tool's tests run on made-up inputs
(`tools/packbuilder/src/test/resources/made-up-inputs/`); keep them made up.

The steps below build Colorado, `US-CO`. For another state, change the two letters and the state's FIPS
code (Colorado's is `08`).

## 1. Make the folders

From the repository root:

```
mkdir -p packs-local/US-CO/barcharts
```

## 2. Download a bar chart for each county (eBird)

This is done by hand, signed in to eBird, one county at a time.

1. Sign in at <https://ebird.org>.
2. Open **Explore**, then **Bar charts**.
3. Choose the state, then **Counties in Colorado**, select one county, and continue.
4. At the foot of the bar chart page, choose **Download Histogram Data**.
5. Move the file into `packs-local/US-CO/barcharts/`. Keep the name eBird gave it, such as
   `ebird_US-CO-059__1900_2026_1_12_barchart.txt`: the tool reads the county code from it.
6. Repeat for every county of the state.

Keep one file per county; two for the same county stop the tool. The pack's `source` gives the day of the
newest bar chart file, taken from the files' modified times, so don't edit or touch the files afterwards.

## 3. Download the county outlines (US Census Bureau)

The outlines come from the Census Bureau's cartographic boundary files, which are in the public domain.

1. Open <https://www.census.gov/geographies/mapping-files/time-series/geo/cartographic-boundary.html>
   and pick the latest year.
2. Under **Counties**, download the national file at **1 : 20,000,000**, as a shapefile:
   `cb_<year>_us_county_20m.zip`.
3. Unzip it, and convert it to GeoJSON with GDAL's `ogr2ogr`, keeping only the state's counties:

   ```
   ogr2ogr -f GeoJSON -t_srs EPSG:4326 -where "STATEFP = '08'" \
     packs-local/US-CO/counties.geojson cb_2023_us_county_20m.shp
   ```

The tool needs each county's `STATEFP`, `COUNTYFP` and `NAME` properties, which `ogr2ogr` keeps. It also
reads the Census file's year from the name `ogr2ogr` writes at the top of the GeoJSON
(`"name": "cb_2023_us_county_20m"`). If you convert with something else and that name is lost, give the
year when you run the tool (step 4).

A file that still holds every state works too: the tool passes over the other states' counties.

## 4. Run the tool

```
./gradlew :tools:packbuilder:run --args="US-CO"
```

or, when the GeoJSON does not name its Census file:

```
./gradlew :tools:packbuilder:run --args="US-CO --census-vintage=2023"
```

It writes `packs-local/out/US-CO.fbpack` and prints a summary:

```
Wrote packs-local/out/US-CO.fbpack
File size: 607 bytes
Source: eBird bar chart data, downloaded 2026-11-05; US Census Bureau cartographic boundary file, 2023, 1:20,000,000
Counties found: 2
  US-CO-001 Alder: 2 species
  US-CO-003 Birch: 3 species
Counties missing a bar chart: none
Counties missing an outline: none
```

(That summary is from the made-up test inputs.)

## What the tool does with what it finds

- A county is in the pack only when it has both a bar chart and an outline. A county with one and not the
  other is listed in the summary and left out; the tool still succeeds. Download what is missing and run
  it again.
- A bar chart is matched to an outline by the county's FIPS code: the last three digits of the eBird
  county code (`US-CO-059`) are the Census `COUNTYFP` (`059`), in the state whose `STATEFP` goes with the
  postal code (`CO` is `08`). Where eBird's counties are not the Census Bureau's for that year (the
  Census files for Connecticut changed from counties to planning regions in 2022), the counties show up as
  missing on both lists; use an earlier year's Census file for that state.
- A bar chart file that cannot be read stops the tool, with the file's name and the reason
  (`NO_SAMPLE_SIZE_ROW`, `TOO_FEW_BINS`, `BAD_NUMBER`, `NO_SPECIES`) and nothing is written. Download that
  county again.
- Only species are kept from a bar chart (no hybrids, "sp." or either-or entries), and a species on fewer
  than 0.1% of checklists in every week of the year is left out of that county. "Species" in the summary
  counts what is in the file.
- A county's outline is the outer ring of each of its pieces. Holes are dropped.

## What you may do with a pack

Keep it on your own machine. Shipping a pack, or putting one on a server, needs the Cornell Lab of
Ornithology's permission, which has not been obtained yet (see `README.md`, "Bird data").

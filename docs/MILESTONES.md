# Milestones

Each section is one GitHub issue. Paste the title and body as-is; the body
starts with `@claude` so the workflow picks it up. Do them in order, one PR
each.

All catalog fixtures except the tribunal form and the Accion=2 response are
in place, so every milestone can start.

---

## 1. Scaffold, theme and CI

```
@claude Milestone 1. Read CLAUDE.md first.

Scaffold the Gradle project:
- Gradle wrapper (run `gradle wrapper`, commit gradlew, gradlew.bat and gradle/wrapper/*), Kotlin DSL, version catalog in gradle/libs.versions.toml, current stable AGP/Kotlin/Compose BOM, JDK 17.
- Module :sise-core (Kotlin/JVM, JUnit 5, Jsoup), package mx.sisetracker.core.
- Module :app (Android, Compose, Material 3, minSdk 26), applicationId mx.sisetracker.
- Theme exactly per "Visual design" in CLAUDE.md: Color.kt with every token for light and dark, dynamicColor off, primary top app bar, edge-to-edge.
- A placeholder home screen titled "SISE Tracker" with a primary button "Buscar expediente" (no action yet), so the theme is visible.
- A smoke test in :sise-core that loads fixtures/vercaptura_1183-2025_amparo-indirecto.html from test resources and asserts it contains "40612904".
- .gitignore for Android/Gradle/IDE files.

Acceptance: `./gradlew :sise-core:test testDebugUnitTest assembleDebug` passes. Report the command output in the PR.
```

---

## 2. Case and síntesis parsers

```
@claude Milestone 2. Read CLAUDE.md and sise-core/src/test/resources/fixtures/README.md.

In :sise-core implement:
- `CaseUrl`: parse a vercaptura.aspx URL (raw or %2f slash) into (tipoAsunto, organismo, expediente, tipoProcedimiento) and build it back. Also extract it from the ExpedienteyTipo.asp result HTML (iframe#ifr).
- `CasePageParser.parse(html: String): CasePage`, covering the header, acuerdos (keyed by orden), resoluciones (archivo URL from the onclick), asuntos relacionados, and captura de información (ordered section/label/value list + partyCount).
- `VerAcuerdoUrl.build(acuerdo)` per the protocol section.
- `SintesisPageParser.parse(html: String): Sintesis` for VerAcuerdo.aspx (expediente, dates, síntesis text with line breaks kept).
- `Acuerdo.isResumenTruncated` (résumé ends with " ...").
- Dates as java.time.LocalDate.

Tests: assert every expected value in the fixtures README for these files, including the résumé/síntesis prefix check.
```

---

## 3. Search form parser and request builders

```
@claude Milestone 3. Read CLAUDE.md, especially hard rule 1 and "Search pages".

In :sise-core implement:
- `SearchFormParser.parse(html: String): SearchForm` for the ExpedienteyTipo.asp form: TipoAsunto options (value, label, order, selected), TipoProcedimiento options, whether the procedimiento row is shown (TipoAsunto in {6, 9, 125, 126}), Expediente value and maxlength, and all hidden fields.
- `SearchRequests`: build the windows-1252 form bodies for step C (load form for an órgano) and step D (Accion=2, procedimiento options). There must be no builder for Accion=1 or anything touching the captcha.
- `CaseUrl.forLookup(organismo, tipoAsunto, tipoProcedimientoShown, tipoProcedimiento, expediente)`, with the tipoprocedimiento rule from "Search flow in the app" kept in one function.
- `CasePageParser` returns a NotFound result when #lblNEUN is empty, tested against vercaptura_not-found_99999-2025.html per the fixtures README.
- Tests against expedienteytipo_result_1183-2025.html per the fixtures README, plus encoding tests showing that "PRIMER CIRCUITO" and an órgano name with "México" encode as windows-1252.
```

---

## 4. Circuit and órgano catalogs (needs fixtures)

```
@claude Milestone 4. Read CLAUDE.md "Search pages" A and B.

- A bundled circuit list (32 entries) as a static resource, with a test asserting it equals the circuits in oaj_circuitos_excerpt.html. Use a parser for that test only; the app never fetches the OAJ page.
- `OrganoListParser` for circuitos.asp: the Organismo options, the CircuitoName from the "Circuito:" row, and kinds Juzgados/Tribunales/Otros derived from names as CLAUDE.md describes.
- Expected-value tests per the fixtures README.
- In :app: a `CatalogRepository` backed by Room. It fetches on demand through the polite request queue, caches for 30 days, and has a manual refresh.
```

---

## 5. Native search screen and direct lookup

```
@claude Milestone 5. Read CLAUDE.md "Search flow in the app", hard rules 1 and 3, and "Distribution".

- Search screen, in order: Circuito dropdown, Tipo de órgano chips, Órgano dropdown with type-ahead, Tipo de asunto dropdown, Tipo de procedimiento dropdown (only when the portal would show it), Número de expediente field, "Buscar". Show every portal option with exact labels in portal order. Include "Órganos recientes" chips, and remember the last circuit.
- "Buscar" builds the case URL with CaseUrl.forLookup and fetches it through the request queue. Show a preview with "Guardar", or the not-found message. One lookup per tap: no batch, range or guessed lookups.
- "Abrir en el portal" fallback: a WebView on circuitos.asp for the chosen circuit, where the user searches and solves the captcha by hand. Observe shouldInterceptRequest for vercaptura.aspx and show the "¿Guardar este expediente?" sheet.
- Accept ACTION_SEND text containing a vercaptura.aspx link, with the same save sheet.
- Until milestone 4 lands, the Circuito and Órgano dropdowns can temporarily be a numeric organismo field plus the portal fallback, so this milestone isn't blocked.
```

---

## 6. Storage, refresh, case list and detail

```
@claude Milestone 6. Read CLAUDE.md.

- Room database per the data model in CLAUDE.md, with an FTS4 table over acuerdo resumen + sintesis.
- `SiseClient` (OkHttp) with a single-flight request queue: one request at a time, at least 2 s apart, with the User-Agent from CLAUDE.md. Catalog requests share this queue.
- Saving a case fetches its page once, parses it with CasePageParser and stores everything.
- "Mis expedientes" list: expediente, órgano, latest acuerdo date and unseen count, with a filter box matching expediente number or órgano name.
- Case detail: acuerdos newest first, resoluciones, relacionados (with "Buscar este expediente", which pre-fills the search screen), and captura de información in collapsible sections. Pull to refresh.
- Opening an acuerdo shows its síntesis. If the résumé is truncated and the síntesis isn't stored yet, fetch it through the queue, store it and index it in FTS. Otherwise use the résumé as the síntesis, with no request.
- The síntesis view handles 20k+ character texts (smooth scrolling, selectable text). Style short ALL-CAPS lines as headings.
- Exclude the database from backup and device transfer (hard rule 6).
- Resolución archivo links open in a Custom Tab.
- Repository tests use the fixtures through a fake HTTP layer. No live requests in tests.
```

---

## 7. Search across acuerdos, daily check, signed release

```
@claude Milestone 7. Read CLAUDE.md.

- Full-text search screen over all saved acuerdos (FTS4), with results linking to the case and acuerdo.
- WorkManager periodic work, at most once a day, network required. It refreshes saved cases through the queue, detects new acuerdos by orden set difference, fetches síntesis only for new acuerdos whose résumé is truncated, and posts one notification per case (expediente, órgano and count only, VISIBILITY_PRIVATE; never síntesis text).
- Settings: toggle the daily check, a minimum interval (1 to 7 days), and "Actualizar catálogos".
- Gradle release signing config that reads the keystore path, passwords and alias from environment variables, and falls back to unsigned when they're absent. Put a proposed .github/workflows/release.yml in the PR description (don't create it under .github/workflows). It should run on tags v*, decode a base64 keystore from secrets, build assembleRelease, and attach the APK to a GitHub Release. Document the keystore and secrets setup in README.md.
- Unit tests for the new-acuerdo diffing.
```

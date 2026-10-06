# SISE Tracker

Android app that makes the federal judiciary's public SISE portal ("Acuerdos
por expediente", www.dgej.cjf.gob.mx, now administered by the Órgano de
Administración Judicial, OAJ) easier to use. It only uses the public portal.

Milestones and acceptance criteria live in `docs/MILESTONES.md`. Work one
milestone per PR.

## What the app does

1. **Search like the portal, natively.** Every dropdown the portal offers is
   available in the app, with the same options in the same order:
   - Circuito
   - Tipo de órgano: Juzgados / Tribunales / Otros filter (or the portal's
     own type list, if it has one)
   - Órgano: the individual juzgado or tribunal, searchable
   - Tipo de asunto
   - Tipo de procedimiento, when the portal shows it
   - Número de expediente

   "Buscar" looks the case up directly, with no captcha (see "Search flow in
   the app").
2. **Track cases.** Save a case, read its acuerdos and full síntesis,
   resoluciones, related cases and case data.
3. **Search what you've saved.** Filter saved cases by expediente number or
   órgano, and full-text search across all saved acuerdos.
4. **Get notified** when a saved case has new acuerdos.

## Hard rules

1. **Never solve or automate the reCAPTCHA.** No solving services, no models,
   and never call grecaptcha or submit the portal's form with `Accion=1` from
   code. The app doesn't need to: the case page is a public GET, so lookups
   build the `vercaptura.aspx` URL from the user's dropdown choices and fetch
   it directly. This direct lookup was decided for small-team use only (see
   "Distribution").
2. **Tests and CI never touch the live site.** Parsers are tested against the
   saved responses in `sise-core/src/test/resources/fixtures/`. If a fixture you
   need is missing, say so in the issue or PR instead of guessing at markup.
3. **Be polite to the server.** One request at a time, at least 2 s apart.
   - Refreshes are user-triggered, plus at most one background check per day
     (acuerdos publish at most once per business day).
   - Catalogs load on demand: one circuit or órgano at a time, when the user
     opens it. They're cached for 30 days, with a manual "Actualizar catálogos"
     action.
   - Lookups happen only for an expediente a user typed, picked from a related
     case, or shared, one per tap. Never generate, guess or iterate expediente
     numbers: no ranges, no "nearby" searches, no batch lookups.
   - The site's robots.txt disallows automated crawling, so never add
     crawling, or prefetching of catalogs or cases the user didn't open.
4. **Don't edit `.github/workflows/`.** The GitHub App can't push workflow
   changes. If one is needed, put the proposed YAML in the PR description.
5. **Keep `:sise-core` free of Android dependencies** so its tests run on the
   plain JVM.
6. **Case text stays on the device.** Redaction on the portal is inconsistent:
   some síntesis publish parties' full names, in sensitive matters (e.g.
   amparos against incommunicado detention). So:
   - No analytics or crash reporting that could include case text.
   - Exclude the database from Android backup and device transfer
     (`android:allowBackup="false"`, or data extraction rules excluding it).
   - Notifications show only expediente, órgano and a count, with
     `VISIBILITY_PRIVATE`, never síntesis text.
   - Mask personal names (same-length `*`) in any new fixture before
     committing it.

## Distribution

Private: CI builds the APK, and a small team installs it from the private
repo. The direct lookup in hard rule 1 depends on this. Don't add Play Store
publishing or any public distribution. If that's ever considered, revisit hard
rule 1 first, and restore the portal-form flow (user solves the captcha) as the
default. The portal itself points users who want to skip the captcha to the
official Portal de Servicios en Línea (www.serviciosenlinea.pjf.gob.mx,
"Consulta de Expediente", with a login or firma electrónica). That's the
sanctioned no-captcha route for anything beyond a small team.

## Visual design

Institutional blues, as the user asked, combined with the OAJ's real
neutrals.
- The OAJ's "Consulta de Datos de Expedientes" stylesheet doesn't use blue.
  Its palette is charcoal `#161A1D` (primary buttons, active tabs), slate
  `#575F71` (hover), text `#333333`, white backgrounds and red `#B31217` for
  alerts.
- The blues (primary, tertiary) are therefore our own approximation.
  Secondary, error and text colors come from the OAJ stylesheet.
- Keep every color in `ui/theme/Color.kt` so values can be swapped in later.

- Material 3 with `dynamicColor = false`, so Material You never replaces the
  brand blues.
- Light and dark schemes, both checked for WCAG AA (4.5:1 for body text).
- Top app bar in `primary` with white text, edge-to-edge.
- "Nuevo" badges on unseen acuerdos use `tertiary`.

| Token | Light | Dark |
|---|---|---|
| primary | `#0B3B6E` | `#A8C7F0` |
| onPrimary | `#FFFFFF` | `#00315F` |
| primaryContainer | `#D3E3F8` | `#0F4A85` |
| onPrimaryContainer | `#001D3D` | `#D3E3F8` |
| secondary (OAJ slate) | `#575F71` | `#BEC5D4` |
| onSecondary | `#FFFFFF` | `#262B36` |
| secondaryContainer | `#E1E4EB` | `#3E4554` |
| onSecondaryContainer | `#161A1D` | `#E1E4EB` |
| tertiary | `#1A6FCC` | `#8CC2FF` |
| onTertiary | `#FFFFFF` | `#002E5C` |
| background / surface | `#F6F8FB` | `#0F151C` |
| onBackground / onSurface (OAJ text) | `#333333` | `#E2E6EB` |
| surfaceVariant | `#E2E8F0` | `#2A3440` |
| onSurfaceVariant | `#44505F` | `#BFC8D3` |
| outline | `#74808F` | `#8A95A3` |
| error (OAJ red) | `#B31217` | `#F2B8B5` |

## Architecture

- `:sise-core`: Kotlin/JVM library. Models, URL builders, HTML parsers (Jsoup),
  date parsing. Pure functions, JUnit 5 tests against fixtures.
- `:app`: Android, Kotlin, Jetpack Compose, Material 3, minSdk 26.
  - OkHttp for fetching. Send the WebView default User-Agent with
    ` SiseTracker/<versionName>` appended.
  - Room for storage, with an FTS4 table over acuerdo résumé and síntesis text.
  - WorkManager for the daily check, and notifications for new acuerdos.
  - Native search screen with direct lookup, plus a WebView "Abrir en el
    portal" fallback (see "Search flow in the app").
  - Catalog cache in Room (circuits, órganos per circuit, tipos de asunto per
    órgano, tipos de procedimiento per órgano and tipo) with a fetchedAt
    timestamp.
- applicationId `mx.sisetracker` (placeholder, may change).
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`). Use
  current stable versions of AGP, Kotlin, Compose BOM and libraries. JDK 17.

### Data model (suggested)

- `Case`: neun (PK), organismoId, tipoAsuntoId, expediente, tipoProcedimiento,
  organoName, tipoAsuntoName, noControlOcc, caseUrl, addedAt, lastCheckedAt.
- `Acuerdo`: PK (neun, orden). numero (displayed), fechaAuto, fechaPublicacion,
  tipoCuaderno, resumen, sintesis (nullable until fetched), verAcuerdoUrl,
  firstSeenAt.
- `Resolucion`: neun, fechaIngreso, tema, archivoUrl.
- `AsuntoRelacionado`: neun, relatedNeun, expediente, organo, fechaRelacion.
- `CapturaInfo`: ordered list of (section, label, value) for the case, plus
  partyCount.
- Catalog tables: `Circuito`(num, label from the OAJ list, portalName =
  CircuitoName, nullable until circuitos.asp has loaded), `Organo`(id,
  circuito, name, kind),
  `TipoAsunto`(organoId, id, name, position) and
  `TipoProcedimiento`(organoId, tipoAsuntoId, id, name, position). Each table
  also stores fetchedAt.

As built (`data/db` and `data/catalog` in `:app`):
- `sise.db` holds the saved cases (user data: schema changes need migrations;
  schemas are exported to `app/schemas/`). `Acuerdo` adds `seen` (false for
  acuerdos a refresh found, until the case is opened: the "Nuevo" badge).
  `Case` adds partyCount. Resoluciones, relacionados and captura rows are
  keyed by (neun, position) and replaced on every read; captura rows keep the
  record index as `group_index`. Children cascade-delete with their case.
- `acuerdos_fts` is an FTS4 external-content table over resumen and sintesis
  (unicode61, `remove_diacritics=1`, so searches ignore accents and case).
- `catalog.db` is only a cache (destructive migrations are fine). It holds
  each loaded circuit's `portalName` (its CircuitoName) and órgano list
  (kind stored, derived from the name), tipos de asunto/procedimiento per
  órgano, and each órgano's search form hidden fields (echoed back in step D).
  The circuit list itself is bundled in `:sise-core` (`circuitos.tsv`).
- The search screen loads a circuit's órganos when the user picks the
  circuit or opens the Órgano field; the remembered last circuit shows only
  what's cached until then. Step C needs the CircuitoName, so loading an
  órgano's tipos first loads its circuit's órgano list if it isn't cached
  (e.g. when the órgano came from "Órganos recientes").

## SISE protocol reference

### Search pages (classic ASP, windows-1252)

The portal's search is a chain of plain form pages. The app reads steps A to D
for its dropdowns. Those pages have no captcha. It never performs step E; it
fetches the case page directly instead.

**A. Circuits.** Fixture: `oaj_circuitos_excerpt.html`.
- The list lives on the OAJ's "Consulta de Datos de Expedientes" page
  (`https://www.oaj.gob.mx/micrositios/dggj/paginas/serviciosTramites.htm?pageName=servicios%2Fexpedientes.htm`).
  It's `select#circuito` with 32 options, values `1` to `32`, plus a `-1`
  "Seleccione un circuito" placeholder.
- Labels combine ordinal and state, e.g. `Primer Circuito Ciudad de México`,
  `Vigésimo Primer Circuito Guerrero`. Show them exactly as given, typos
  included (`Decimosexto Circuito Guanuajuato`).
- The option value is the `Cir` parameter of `circuitos.asp`. The page's own
  script (not in the capture) does that navigation. Value 1 → `Cir=1` is
  confirmed by the user's own navigation; the rest is assumed.
- **Bundle this list in the app** as a static resource, with a test that
  checks it against the fixture. Don't fetch the OAJ page, which is ~700 KB,
  mostly map SVG. Circuits change very rarely; when they do, update the
  fixture and the bundled list together.
- `CircuitoName`, the value sent in form bodies, is **not** the OAJ label. It
  is the uppercase name shown on `circuitos.asp` (e.g. `PRIMER CIRCUITO`).
  Read it from the `td` after the `th` containing "Circuito:", trimmed and
  without `&nbsp;`, when loading the órgano list, and store it with the
  circuit.

**B. Órganos.** `GET /internet/expedientes/circuitos.asp?Cir={n}&Exp=1`.
Fixture: `circuitos_cir1.html`.
- One flat `select[name=Organismo]` inside `form[name=Editar]`: no optgroups,
  no type selector and no placeholder option. The Primer Circuito has 184
  options. Option values are the organismo IDs used everywhere else (767,
  18, 500…).
- Options don't have closing `</option>` tags; let Jsoup handle it. Some
  labels end with a period (`…del Primer Circuito.`); keep labels exactly.
- The page shows the circuit name as text ("Circuito: PRIMER CIRCUITO").
- Its form POSTs `Organismo`, `Buscar=Buscar`, `Circuito` and `CircuitoName`
  to step C. The hidden `Circuito`/`CircuitoName` inputs aren't in the
  capture (Chrome's page save seems to drop hidden inputs). The app supplies
  them from the circuit list.
- The list mixes jurisdictional and administrative bodies (Comisión de
  Disciplina, Secretaría General de Acuerdos…) and Plenos Regionales.
- Tipo de órgano, derived from the name because the page has no type
  selector: starts with "Juzgado" → Juzgados; contains "Tribunal" →
  Tribunales; anything else → Otros. For the Primer Circuito that gives 79,
  93 and 12.

**C. Form and tipos de asunto.** `POST /internet/expedientes/ExpedienteyTipo.asp`
with the four fields from B. No captcha is needed to load it. The response
form is `name="Editar"`:
- `select[name=TipoAsunto]`: options depend on the órgano. Its `onchange`
  calls `CargaTipoProcedimiento()`.
- `tr#regTipoProc select[name=TipoProcedimiento]`: the select is always in
  the markup, but the row is shown only when TipoAsunto is 6, 9, 125 or 126
  (see `EjecutaAntes()`). Show it in the app under the same rule.
- `input[name=Expediente]`: maxlength 15. It has been `n/yyyy` so far, but
  don't reject other shapes; just warn.
- Hidden fields: `Circuito`, `CircuitoName`, `Organismo`, `OrgName`,
  `TipoOrganismo`, `Accion`. In the saved page `OrgName` is empty and the
  "Órgano Jurisdiccional" cell is blank, so take the órgano's name from the
  órgano list (B) or the case page header, not from this form.
- `div#recaptchaArea`: reCAPTCHA v2 with explicit render.
- The existing fixture `expedienteytipo_result_1183-2025.html` contains this
  same form, so the parser can be built now.
- The app sends the CircuitoName read from the circuit's órgano list (B).
  If that page doesn't show one, it sends it empty; unverified whether the
  portal then still lists the tipos.

**D. Tipos de procedimiento.**
- Changing TipoAsunto submits the form with `Accion=2`. The server re-renders
  it with that tipo's procedimiento options.
- Natively, make the same POST (`Circuito`, `CircuitoName`, `Organismo`,
  `OrgName`, `TipoOrganismo`, `TipoAsunto`, `Expediente`, `Accion=2`), and
  only when the tipo shows the row.
- Fixture pending for a tipo that shows it.

**E. Portal lookup (not used by the app, except in the WebView fallback).**
On the portal, Buscar posts `Accion=1` plus `g-recaptcha-response`, and the
page then shows
`<iframe id="ifr" src="https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx?tipoasunto=…&organismo=…&expediente=…&tipoprocedimiento=…">`.

**Encoding.** These pages declare `charset=windows-1252` (a superset of
ISO-8859-1). Decode responses as windows-1252. Form bodies must be URL-encoded
as windows-1252, because
`CircuitoName` and `OrgName` contain accents. OkHttp must send those bytes as-is.

**Dropdown parsing.**
- Keep every option's value and label exactly, in page order.
- Skip only placeholder options (value `0` or empty).
- Decode labels as windows-1252 and normalize whitespace.

### Search flow in the app

1. **Native search screen**, in this order:
   - Circuito dropdown (from A).
   - Tipo de órgano filter chips.
   - Órgano dropdown with type-ahead filtering (from B).
   - Tipo de asunto dropdown (from C).
   - Tipo de procedimiento dropdown, only when the portal would show it
     (from D).
   - Número de expediente field, with a numeric keyboard that allows `/`.
   - "Órganos recientes" chips for quick re-selection.
   - Remember the last circuit used.
2. **"Buscar"** builds
   `vercaptura.aspx?tipoasunto={TipoAsunto}&organismo={Organismo}&expediente={n/yyyy}&tipoprocedimiento={p}`
   and fetches it through the request queue.
   - `p` is `0` unless the tipo shows the procedimiento row; then it's the
     chosen procedimiento's value. That second part is unverified, since
     every capture so far had `0`. Confirm it with the pending fixture and
     keep the choice in one function.
3. **Result:**
   - If `#lblNEUN` has a value, show a preview (órgano, tipo, number of
     acuerdos, latest acuerdo date) with "Guardar".
   - Otherwise show "No se encontró el expediente. Revisa el órgano, el tipo
     de asunto y el número."
   - **Not found, confirmed:** the portal returns the normal page shell with
     every header span empty (`#lblNEUN`, `#lblNombreOrgano`, …), no
     acuerdos, resoluciones or related-cases tables, and no error message of
     its own. Detect it by an empty `#lblNEUN` only, never by missing tables,
     because a real case can have zero acuerdos. A wrong órgano or tipo for a
     real number most likely looks the same, which the message covers.
4. **Fallback "Abrir en el portal":**
   - Opens `circuitos.asp?Cir={n}&Exp=1` in a WebView, where the user searches
     on the portal and solves its captcha by hand.
   - The app watches `shouldInterceptRequest` (observe only, return null) for
     path `/siseinternet/reportes/vercaptura.aspx` and offers "¿Guardar este
     expediente?". Keep the query values exactly as given.
   - This is for when direct lookup fails, e.g. after the portal changes.

**Other ways in:**
- Accept a shared or pasted `vercaptura.aspx` link (Android share intent).
- From a related case ("Asuntos Relacionados"), "Buscar este expediente"
  pre-fills the search screen with its expediente. If the cached catalogs
  match the órgano name and the tipo de asunto (the part after the last
  `" - "`) exactly, it pre-fills those too, and the user only taps Buscar.

### Case page (ASP.NET WebForms, UTF-8): public GET, no cookies needed

`GET https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx?tipoasunto={t}&organismo={o}&expediente={n/yyyy}&tipoprocedimiento={p}`

The slash in `expediente` may be raw or `%2f`; both work.

**Header spans:**
- `#lblNombreOrgano`: `"{órgano} - {tipo asunto}"`. Split on the last `" - "`.
- `#lblNEUN`: NEUN, the stable unique case ID. Use it as the primary key.
- `#lblNoExpedienteAsignado`: the expediente.
- `#lblNoControlOCC`: control number of the Oficina de Correspondencia Común.
- `#lblNumResultado`: contains "Listado de Resoluciones (N)".

**Captura de Información** (`div#pnlVista`):
- One accordion per party. The header is `div.accordionHeader > span`, with
  names redacted as `*****`. It is followed by `div#pBody{digits}`.
- The panels are identical copies of the case data. Parse the first one and
  store partyCount = number of panels.
- Inside a panel, each section is a `table.div_principal`. Its title is in
  `tr.colapsable_vc > td`, and the nested table rows are label/value `td`
  pairs.
- Labels repeat (several "Fecha señalada para audiencia
  constitucional(diferimiento)"), so keep an ordered list, not a map.
- Labels have irregular whitespace ("Fecha  presentación"); normalize it.
- Section titles seen so far (exact text): Datos Generales, Actos Reclamados,
  Resolucion Inicial, Audiencia, Sentencia, Sentencia Recurso Contra Sentencia,
  Suspension Audiencia Incidental, Suspension Suspension Definitiva,
  Suspension Suspension De Plano, Suspension Suspension Provisional.
- `div#panelVacio` exists, and the section may be empty for some cases.
- Within a section, each nested table is one record (e.g. each audiencia
  diferimiento); the parser keeps its index as `CapturaEntry.group`. Records
  after the first start with an empty `tr.fila_agregada` separator row.
- The page ends with a script that hides every cell containing
  "Observaciones" in the panels (jQuery `:contains`, case-sensitive), plus the
  value cell next to such a label. The app drops the same entries, even
  though no fixture has one yet.

**Asuntos Relacionados** (`table#grvAsuntosRelacionados`):
- Per-row span IDs end in `_lblContenido` (NEUN), `_lblNúmeroExpediente`
  (non-ASCII `ú` in the ID!), `_lblOrgano` and `_lblFechaPresentacion`
  (header "Fecha relación").
- This links instances: 1183/2025 (juzgado) ↔ 293/2026 (tribunal colegiado,
  revisión). The app should offer to open or save the related case. A related
  case's tipoasunto/organismo IDs aren't in this table; resolve them from the
  cached catalogs by name (see "Other ways in").

**Resoluciones** (`table#grvReporteSentencias`):
- Spans `_cmdAsuntoNeunid`, `_lblFechaIngreso` (dd/MM/yyyy) and `_lblTema`.
- The document link `a[id$=_SentenciasLinkButton]` has
  `onclick="AbrirVentana('http://sise.cjf.gob.mx/SVP/word1.aspx?arch=…&sec=…&svp=1')"`.
  Take the URL from the onclick and HTML-decode `&amp;`. Ignore the `__doPostBack`
  href.
- The URL is cleartext `http://`. Open it in a Custom Tab or the browser; don't
  fetch it from the app.

**Acuerdos** (`table#grvAcuerdos`, inside a scroll div; no paging seen at 28
rows). Columns: No., Fecha del Auto, Tipo Cuaderno, Fecha de publicación,
Resumen, Ver síntesis completa.
- `td[0] span[id$=_lblContenido]`: the displayed number.
- `td[1]`: fecha auto, `dd-MM-yyyy`.
- `td[2]`: tipo cuaderno (Principal / Incidental; at tribunals it shows the
  asunto type).
- `td[3]`: fecha publicación, `dd-MM-yyyy`.
- `td[4]`: resumen. HTML entities and newlines; truncated with `" ..."` when
  long. The cell text uses `\n` (the markup around it uses CRLF), some cells
  start with a line break and some end with `" \n"`; the parser drops leading
  blank lines and trailing whitespace, which keeps the prefix relation with
  the síntesis.
- `td[5] a[id$=_lnkTicketLink2]`: href
  `javascript:DoVerAcuerdo(org, orden, neun, asuntoId, "dd/MM/yyyy 12:00:00 a.m.", "dd/MM/yyyy 12:00:00 a.m.", "n/yyyy")`
  with `&quot;` entities in the raw HTML.
- `asuntoId` is not the case URL's `tipoasunto`. For the tribunal case
  293/2026 the case page is `tipoasunto=11&organismo=18` (seen in the
  postback deltas' `formAction`), but its DoVerAcuerdo links pass
  `asuntoId` 1. Always build the síntesis URL from the link's own arguments.

Gotchas for this grid:
- **Key acuerdos by `orden` (2nd argument), never by the displayed No.** Orden
  has gaps (1, 2, 4, 5, 6, 8…). Detect new acuerdos by set difference on orden,
  not by max.
- The page also contains the `function DoVerAcuerdo(...)` definition. Only
  parse arguments from `a[href^="javascript:DoVerAcuerdo"]`.

### Acuerdo síntesis page: public GET

`GET https://www.dgej.cjf.gob.mx/siseinternet/Actuaria/VerAcuerdo.aspx?listaAcOrd={orden}&listaCatOrg={org}&listaNeun={neun}&listaAsuId={asuntoId}&listaExped={n/yyyy}&listaFAuto={dd/MM/yyyy}&listaFPublicacion={dd/MM/yyyy}`

- Built from the DoVerAcuerdo arguments, with dates trimmed to the date part.
  Confirmed by the server's own postback responses (see fixtures).
- Small UTF-8 page titled "Ver síntesis completa." It shows the full
  síntesis, not the acuerdo document itself. Spans:
  - `#lblNoExp`: expediente.
  - `#lblFAuto`: fecha del auto, `dd/MM/yyyy`.
  - `#lblFPublica`: fecha de publicación, `dd/MM/yyyy`.
  - `#lblAcuerdo`: the síntesis text. Plain text with `\n` line breaks that
    separate headings and paragraphs, sometimes with blank lines between
    paragraphs. Keep internal blank lines; trim trailing ones. It can start
    mid-sentence in lowercase (e.g. `téngase por recibida…`); don't
    capitalize it.
- Observed in 3 of 3 samples (juzgado and tribunal): the grid's `Resumen` is
  exactly the first 255 characters of this síntesis, cut mid-word if needed,
  followed by `" ..."` (259 characters in total).
- Assumed, not yet verified: when the résumé doesn't end in `" ..."`, it is
  already the whole síntesis, so the app skips fetching this page. Keep that
  check in one function so it's easy to change if a counterexample shows up.
- Length varies enormously: from one line to 21,500+ characters (effectively
  the whole auto, with quoted tesis, numbered lists inside quotes, and ALL-CAPS
  section headings such as `SUSPENSIÓN DE PLANO` or `HABILITACIÓN.`). The
  detail view must scroll long text smoothly and keep it selectable. Styling
  short ALL-CAPS lines as headings is a nice touch.
- Redaction is inconsistent. Some síntesis mask names with `*****`, others
  publish them in full.
- The portal's anonymizer can mask more than names, e.g.
  `293********************` where a toca number appeared. Show the text
  as-is; never try to "fix" masked text.

### Postback (reference only: don't use)

Clicking "Ver síntesis" does an UpdatePanel async postback:
- Request fields: `ScriptManager1=updPanel|AcuerdoLinkButton`, the hidden
  fields, `__ASYNCPOST=true`, header `X-MicrosoftAjax: Delta=true`.
- The response is MS AJAX delta format: repeated `length|type|id|content|`.
  `length` counts characters (UTF-16 units), not bytes, and content may
  contain `|`, so parse by length.
- The only useful block is `scriptStartupBlock`, which calls
  `AbrirVentanaTotal('../Actuaria/VerAcuerdo.aspx?…')`. We build that URL
  directly instead.

### Formats

- `.asp` pages are windows-1252; `.aspx` pages are UTF-8. Fixtures are saved as
  UTF-8; see the fixtures README for how each one was captured.
- Dates: grid `dd-MM-yyyy`; spans `dd/MM/yyyy`; DoVerAcuerdo args
  `dd/MM/yyyy 12:00:00 a.m.`.
- Line breaks carry meaning in résumés and síntesis. With Jsoup, read them
  with `wholeText()` (then decode/trim), not `text()`, which collapses
  newlines.

## Fixtures

See `sise-core/src/test/resources/fixtures/README.md` for the file list,
expected values to assert, and what's still missing.

## Commands

- `./gradlew :sise-core:test`: fast parser tests.
- `./gradlew testDebugUnitTest assembleDebug`: what CI runs.
- `SISE_SCREENSHOTS_DIR=/some/dir ./gradlew testDebugUnitTest`: the
  Robolectric UI tests also save PNGs of the screens there.
- Robolectric is pinned to SDK 34 (`app/src/test/resources/robolectric.properties`):
  its SDK 35+ runtimes need Java 21, and the build uses JDK 17.

## Conventions

- Kotlin official style, coroutines, no `!!`.
- Every parser change ships with fixture tests.
- Keep test names ASCII (no accents in backticked names): they become class
  file names, which fail to compile on JVMs with a non-UTF-8 file encoding.
- `:sise-core` also runs on Android (API 26+), and lint's NewApi check doesn't
  cover plain JVM modules. Use the charset-name overloads of
  `URLEncoder`/`URLDecoder` (the `Charset` ones need API 33), and avoid other
  JDK APIs newer than API 26. The app enables core library desugaring (with
  NIO), which Jsoup needs.
- Keep user-facing strings in Spanish (`strings.xml`), with code and comments in
  English.
- When you learn something new about the portal, update the protocol section
  of this file in the same PR.

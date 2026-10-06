# Fixtures

Real responses from the public SISE portal (October 2026), saved as UTF-8.
The HAR-derived files keep the original CRLF line endings. Do not edit these
files; add new ones instead.

**Personal names:** the portal usually masks party names as `*****`, but not
always. Before committing a new fixture, replace any personal name with
same-length asterisks (so character counts don't change), as was done for
`veracuerdo_1068-2025_orden1.html`.

Read them as UTF-8 without newline translation (`readText()` is fine; don't use
anything that normalizes line endings).

## Files

| File | What it is |
|---|---|
| `vercaptura_1183-2025_amparo-indirecto.html` | Case page, GET. Juzgado de distrito, amparo indirecto. Every section populated. |
| `veracuerdo_1183-2025_orden38.html` | Síntesis page (`VerAcuerdo.aspx`) for 1183/2025, orden 38. Saved with Chrome's "Download page" (MHTML), so it's Chrome's re-serialized DOM rather than byte-exact server output: entity encoding may differ, and the hidden form fields are gone. Parse through Jsoup, never by regex on raw markup. LF line endings. |
| `veracuerdo_293-2026_orden3.html` | Síntesis page for the tribunal colegiado case 293/2026, orden 3. Same capture method and caveats as the one above. |
| `veracuerdo_1068-2025_orden1.html` | Síntesis page for 1068/2025 (another juzgado de distrito, organismo 721), orden 1: a 21.5k-character síntesis. The portal published three parties' names unredacted; we masked them with same-length asterisks. Same capture caveats as the other VerAcuerdo files. |
| `vercaptura_not-found_99999-2025.html` | Case page for an expediente that doesn't exist (organismo 767, tipo 1, 99999/2025). Chrome "Download page" capture, so it's the re-serialized DOM. |
| `expedienteytipo_result_1183-2025.html` | Search form result page (`ExpedienteyTipo.asp`) containing the `iframe#ifr` with the case URL. Originally windows-1252. |
| `oaj_circuitos_excerpt.html` | **Excerpt** of the OAJ's "Consulta de Datos de Expedientes" page: only the circuit selector (`form#form2`) is kept, unchanged; the full ~700 KB page is mostly a map SVG. Chrome "Download page" capture. |
| `circuitos_cir1.html` | Órgano list for the Primer Circuito (`circuitos.asp?Cir=1&Exp=1`). Chrome "Download page" capture, so hidden inputs are missing. Originally windows-1252, converted to UTF-8. |
| `delta_ver-acuerdo_1183-2025_orden38.txt` | MS AJAX delta response to the "Ver síntesis" postback, orden 38. Reference only. |
| `delta_ver-acuerdo_293-2026_orden{1,2,3}.txt` | Same, for the tribunal colegiado case 293/2026. Reference only. |

## Expected values: `vercaptura_1183-2025_amparo-indirecto.html`

Source URL: `https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx?tipoasunto=1&organismo=767&expediente=1183/2025&tipoprocedimiento=0`

**Header**
- NEUN `40612904`, expediente `1183/2025`, OCC `20255739005400076/2025`.
- Órgano `Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México`, tipo asunto `Amparo Indirecto`.
- Resoluciones count `1`.

**Acuerdos: 28 rows**
- Ordenes in page order: `1, 2, 4, 5, 6, 8, 9, 10, 12, 14, 15, 16, 17, 19, 21, 23, 24, 26, 27, 30, 31, 32, 33, 34, 35, 36, 37, 38`.
- Row 1: No. 1, orden 1, auto 2025-11-27, publicación 2025-11-28, cuaderno `Principal`, resumen starts `Con fundamento en los artículos 15, 126 y 160 de la Ley de Amparo` and ends with ` ...` (truncated).
- Row 3: No. 3, **orden 4**, auto 2025-12-05, publicación 2025-12-08, cuaderno `Incidental`, resumen exactly `Único. Se declara sin materia la suspensión definitiva solicitada.`
- Row 28: No. 28, orden 38, auto 2026-08-31, publicación 2026-09-01, cuaderno `Principal`.
- Every row: organismo 767, neun 40612904, asuntoId 1, expediente `1183/2025`.
- VerAcuerdo URL for orden 38 must equal the one in `delta_ver-acuerdo_1183-2025_orden38.txt` (resolved against `/siseinternet/reportes/`):
  `https://www.dgej.cjf.gob.mx/siseinternet/Actuaria/VerAcuerdo.aspx?listaAcOrd=38&listaCatOrg=767&listaNeun=40612904&listaAsuId=1&listaExped=1183/2025&listaFAuto=31/08/2026&listaFPublicacion=01/09/2026`

**Resoluciones: 1 row**
- NEUN 40612904, fecha ingreso 2026-06-12.
- Tema `Se concede el amparo respecto del acuerdo que concede la extradición internacional del quejoso a los Estados Unidos de América.`
- Archivo URL starts `http://sise.cjf.gob.mx/SVP/word1.aspx?arch=767/0767000040612904025.pdf_1&` (with `&amp;` decoded).

**Asuntos relacionados: 1 row**
- NEUN `42423129`, expediente `293/2026`, órgano `Segundo Tribunal Colegiado en Materia Penal del Primer Circuito - Amparo en revisión`, fecha `2026-08-24`.

**Captura de Información**
- partyCount 5 (all panels identical).
- Section titles, in order (exact text): `Datos Generales`, `Actos Reclamados`, `Resolucion Inicial`, `Audiencia`, `Sentencia`, `Sentencia Recurso Contra Sentencia`, `Suspension Audiencia Incidental`, `Suspension Suspension Definitiva`, `Suspension Suspension De Plano`, `Suspension Suspension Provisional`.
- Spot checks: `Mesa` = `III`; `Sentido sentencia o resolución que puso fin al juicio` = `Ampara para efectos`; `Número de toca` = `293/2026`.
- 4 entries labelled `Fecha señalada para audiencia constitucional(diferimiento)` per panel (labels repeat): 03/02/2026, 19/02/2026, 05/03/2026, 23/03/2026.

## Expected values: `expedienteytipo_result_1183-2025.html`

**Captured case URL**
- `iframe#ifr` src = `https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx?tipoasunto=1&organismo=767&expediente=1183/2025&tipoprocedimiento=0`.
- Parsed: tipoasunto 1, organismo 767, expediente `1183/2025`, tipoprocedimiento 0.

**Search form `Editar`** (this page contains the same form as step C)
- `TipoAsunto`: 10 options in this order: 1 Amparo Indirecto, 2 Causa Penal, 46 Concursos Mercantiles, 71 Denuncia por Incumplimiento de la Declaratoria General de Inconstitucionalidad, 67 Ejecución de Penas, 58 Extinción de Dominio, 68 Juicio Oral Mercantil, 18 Medidas Precautorias, 19 Procedimientos de Extradición, 4 Procesos Civiles o Administrativos. Selected: 1.
- `TipoProcedimiento`: 9 options in this order: 276 Apelación, 979 Conflicto competencial entre jueces, 1214 Denegada apelación, 1715 Impedimento, 1719 Impedimento (excusa), 1720 Impedimento (recusación), 2670 Otro, 3042 Queja, 4258 Sumario. The row should be reported as not shown, because TipoAsunto 1 isn't in {6, 9, 125, 126}.
- `Expediente`: value `1183/2025`, maxlength 15.
- Hidden fields: Circuito `1`, CircuitoName `PRIMER CIRCUITO`, Organismo `767`, OrgName empty, TipoOrganismo empty, Accion `0`.
- reCAPTCHA site key `6LeLhuwUAAAAAKjHVkZzba4i2pPoJckOARPebyIs`, in `div#recaptchaArea`.

## Expected values: `veracuerdo_1183-2025_orden38.html`

Source URL: `https://www.dgej.cjf.gob.mx/siseinternet/Actuaria/VerAcuerdo.aspx?listaAcOrd=38&listaCatOrg=767&listaNeun=40612904&listaAsuId=1&listaExped=1183/2025&listaFAuto=31/08/2026&listaFPublicacion=01/09/2026`

- Expediente `1183/2025`, fecha auto 2026-08-31, publicación 2026-09-01.
- Síntesis, after trimming trailing blank lines, has 8 lines:
  1. `Ciudad de México, treinta y uno de agosto de dos mil veintiséis.`
  2. `Tribunal colegiado acusa recibo`
  3. starts `Intégrese el oficio proveniente del Segundo Tribunal Colegiado`
  4. `Admite revisión`
  5. contains the masked text `293********************.`
  6. starts `Asimismo comunica que desecho`
  7. `De lo que se toma conocimiento para los afectos legales conducentes.`
  8. `Notifíquese.`
- Cross-fixture check: the résumé of orden 38 in `vercaptura_1183-2025_amparo-indirecto.html` ends with `" ..."`, and without that suffix it is a prefix of this síntesis.

## Expected values: `veracuerdo_293-2026_orden3.html`

Source URL: `https://www.dgej.cjf.gob.mx/siseinternet/Actuaria/VerAcuerdo.aspx?listaAcOrd=3&listaCatOrg=18&listaNeun=42423129&listaAsuId=1&listaExped=293/2026&listaFAuto=17/09/2026&listaFPublicacion=18/09/2026`

- Expediente `293/2026`, fecha auto 2026-09-17, publicación 2026-09-18.
- Síntesis, after trimming trailing blank lines, has 3 lines: a paragraph, an empty line, a paragraph.
  1. starts `téngase por recibida la opinión ministerial 340/2026` (lowercase first letter, keep it).
  2. empty.
  3. starts `En atención a su contenido, téngase por hechas las manifestaciones` and ends `sentencia correspondiente.`
- This is the VerAcuerdo URL built from the grid row `DoVerAcuerdo(18,3,42423129,1,"17/09/2026 12:00:00 a.m.","18/09/2026 12:00:00 a.m.","293/2026")`, and it matches `delta_ver-acuerdo_293-2026_orden3.txt`.

## Expected values: `veracuerdo_1068-2025_orden1.html`

Source URL: `https://www.dgej.cjf.gob.mx/siseinternet/Actuaria/VerAcuerdo.aspx?listaAcOrd=1&listaCatOrg=721&listaNeun=39712162&listaAsuId=1&listaExped=1068/2025&listaFAuto=02/09/2025&listaFPublicacion=03/09/2025`

- Expediente `1068/2025`, fecha auto 2025-09-02, publicación 2025-09-03.
- Síntesis after trimming trailing blank lines: 21,507 characters, 57 lines (internal blank lines kept).
- First line: `Chilpancingo de Los Bravo, Guerrero, dos de septiembre de dos mil veinticinco.`
- Last line ends `en días hábiles.`
- Contains these lines exactly (candidate headings): `CUMPLIMIENTO AL ACUERDO GENERAL 12/2020.`, `SUSPENSIÓN DE PLANO`, `EXHORTOS.`, `HABILITACIÓN.`, `DOMICILIO Y AUTORIZADOS.`, `AUTORIZACIÓN DE COPIAS`.

## Expected values: `vercaptura_not-found_99999-2025.html`

Source URL: `https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx?tipoasunto=1&organismo=767&expediente=99999/2025&tipoprocedimiento=0`

- `#lblNEUN`, `#lblNombreOrgano`, `#lblNoExpedienteAsignado`, `#lblNoControlOCC` and `#lblNumResultado` are all present and empty.
- No `table#grvAcuerdos`, `table#grvReporteSentencias` or `table#grvAsuntosRelacionados`.
- No error message anywhere on the page.
- The parser must return "not found", with no exception, and must not report an empty-but-valid case.

## Expected values: `oaj_circuitos_excerpt.html`

- `select#circuito`: 33 options. First is the placeholder `-1` → `Seleccione un circuito` (skip it). Then values `1`…`32` in order.
- `1` → `Primer Circuito Ciudad de México`; `16` → `Decimosexto Circuito Guanuajuato` (typo kept); `21` → `Vigésimo Primer Circuito Guerrero`; `32` → `Trigésimo Segundo Circuito Colima`.
- The app's bundled circuit list must equal these 32 (value, label) pairs exactly.

## Expected values: `circuitos_cir1.html`

Source URL: `https://www.dgej.cjf.gob.mx/internet/expedientes/circuitos.asp?Cir=1&Exp=1`

- `select[name=Organismo]`: 184 options, no optgroups, none selected, no placeholder.
- First: `10` → `Juzgado Primero de Distrito en Materia Administrativa en la Ciudad de México`.
- Last: `4393` → `Pleno Regional Especializado en Competencia Económica, Radiodifusión y Telecomunicaciones`.
- Spot checks:
  - `767` → `Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México` (case 1183/2025).
  - `18` → `Segundo Tribunal Colegiado en Materia Penal del Primer Circuito` (case 293/2026).
  - `500` → `Octavo Tribunal Colegiado en Materia Penal del Primer Circuito`.
  - `1671` → `Décimo Tribunal Colegiado en Materia Penal del Primer Circuito.` (trailing period kept).
  - `6316` → `Comisión de Disciplina` (kind Otros).
- Kinds derived from names: 79 Juzgados, 93 Tribunales, 12 Otros.
- No duplicate values.
- Page text includes `Circuito: PRIMER CIRCUITO`. The parsed CircuitoName (`td` after the `th` containing "Circuito:", trimmed, no `&nbsp;`) is exactly `PRIMER CIRCUITO`.

## Still missing (ask the user before writing code that depends on these)

To capture each one, open the page in Chrome on the phone, then use ⋮ → download (saves `.mhtml`). Mask any personal names before committing.

- **Form for a tribunal colegiado:** the `ExpedienteyTipo.asp` page right after choosing a tribunal. Its tipos de asunto differ from a juzgado's.
- **Accion=2 response, plus its search result:** the form after choosing a tipo de asunto that shows "Tipo de procedimiento" (6, 9, 125 or 126, wherever one exists). Then, if possible, the result page of a real search with that tipo, whose iframe shows which `tipoprocedimiento` value the case URL uses.
- **Tribunal case page:** `vercaptura.aspx` for a tribunal colegiado, e.g. 293/2026. Checks grid variations and an empty Captura de Información.

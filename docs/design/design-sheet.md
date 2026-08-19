# Design Sheet — Guest Experience Platform

Das gestalterische Regelwerk der App: Haltung, Farbsystem, Typografie,
Komponenten, Bewegung und Sprache.

> **Rolle dieses Dokuments.** Es beschreibt das **System als Ganzes** — auch
> Teile, die noch nicht gezeichnet sind. `tokens.md` und `screens/*.md`
> beschreiben dagegen den **Bestand**: exakt das, was in Figma existiert und
> ausgelesen wurde.
>
> **Bei Widerspruch gewinnt der Bestand.** Dieses Dokument wurde am 2026-08-17
> auf den tatsächlich gebauten Stand korrigiert; die ursprüngliche Fassung
> (v1, Juli) enthielt Werte aus der Planungsphase, die so nie umgesetzt wurden.
> Alle Abweichungen sind unten protokolliert.

**Legende**

| | Bedeutung |
|---|---|
| ✅ | Gebaut und aus Figma ausgelesen — verbindlich |
| 📋 | **Nicht aus Figma** — entweder noch nicht gezeichnet (Absicht, keine Spezifikation) oder selbst entworfen und gebaut, ohne Figma-Vorlage |
| ⚠️ | Bewusste Abweichung von v1, siehe Protokoll am Ende |

> **Ergänzt 2026-08-18.** 📋 hieß bis dahin ausschließlich *"Vorschlag, noch
> nicht gezeichnet"*. Seit dem dunklen Thema und der Statusleiste gibt es einen
> zweiten Fall: **entworfen, gemessen und im Code gebaut — aber nicht in
> Figma**. Beide teilen die eine Eigenschaft, auf die es ankommt: **ein
> erneuter Figma-Import bringt diese Werte nicht zurück**, ihr Fehlen in einer
> frischen Extraktion ist kein Beleg dafür, dass sie falsch sind. Wo der
> Unterschied zählt, sagt der jeweilige Abschnitt es ausdrücklich.

**Verwandte Dokumente:** [`tokens.md`](tokens.md) · [`dark-theme.md`](dark-theme.md) · [`screens/home.md`](screens/home.md) · [`extraction-log.md`](extraction-log.md)

---

## Design Direction

### Gefühl

Die App soll sich anfühlen wie ein digitaler Concierge in einem modernen
Boutique-Apartment:

- Warm · Ruhig · Premium · Freundlich · Großzügig
- Leicht futuristisch
- **Nicht** technisch, **nicht** nach Admin-Dashboard

### Visueller Kern

- Heller Mesh-Gradient als atmosphärischer Hintergrund
- Glassmorphism für Cards und Panels
- Große, klare Typografie
- Runde, weiche Formen
- Touch-optimierte Bedienelemente
- Icons immer mit klarer Funktion
- Wenig Text, schnelle Orientierung

---

## Canvas und Maßstab ✅

⚠️ **Geändert gegenüber v1** (dort: 1440 × 1024).

| | Wert |
|---|---|
| Figma-Canvas | 2560 × 1664 (2x) |
| **Logisches Viewport** | **1280 × 832** |
| Zielgerät | Tablet 11–13", Betrachtungsabstand ~40–70 cm |
| Breakpoints | keine — ein fester Screen, Skalierung über die Wurzelschriftgröße |

**Alle Werte in diesem Dokument sind CSS-Pixel** (Figma-Wert ÷ 2). Implementiert
wird in `rem` gegen einen 16px-Wurzelwert, siehe `tokens.md` → "Units".

📋 Ein 12-Spalten-Grid (Margin 64, Gutter 24) war in v1 vorgesehen, wird im
gebauten Screen aber nicht verwendet — dort liegt ein Flex-Layout mit fester
Sidebar. Das Grid bleibt als Option für spätere, inhaltsreichere Screens.

---

## Spacing ✅

4px-System. Diese Werte sind aus dem gebauten Screen belegt:

```text
4 · 6 · 8 · 12 · 16 · 24 · 32 · 40 · 64
```

📋 Ergänzend verfügbar, noch nicht verwendet: `48 · 80 · 96`

**Standard-Abstände**

| Kontext | Wert |
|---|---|
| Zwischen kleinen Elementen | 4–6 |
| Innerhalb von Cards | 16 horizontal / 24 vertikal |
| Zwischen Cards | 12 |
| Sidebar zu Inhalt | 40 |
| Seitenrand | 32 |

---

## Farben

### Marke ✅ — als Figma-Variablen definiert

| Name | Wert | Verwendung |
|---|---|---|
| `Brand_Primary-Pink` | `#F45AA8` | Icon-Verlauf, Logo, Akzente, aktive Zustände |
| `Brand_Secondary-Purple` | `#8A5CF6` | Sekundäre Aktionen, Smart-Home- und Wetterakzente |
| `Text_Primary` | `#2C1F4A` | Überschriften, Kartentitel, Menü-Labels |
| `Text_Secondary` | `#6E6486` | Beschreibungen, unterstützender Text |
| `Text_On-Dark` | `#FFFFFF` | Text auf dunklem oder gesättigtem Grund |

`Brand_Secondary-Purple` ist definiert, im gebauten Screen aber noch nirgends
sichtbar eingesetzt.

📋 **Noch keine Variablen, aus v1 übernommen:**
`Text Muted #9D92B2` · `Text Disabled #B8AEC8` ·
`Accent Peach #FFD6C7` · `Accent Lavender #C98BFF`

### Hintergrund ✅ — 4 × 4 Mesh-Gradient

⚠️ **Ersetzt die v1-Angaben** (Base Cream / Warm Cream / Soft Rose mit
prozentualer Gewichtung). Der tatsächliche Hintergrund ist ein Mesh mit sechzehn
Farbpunkten, randbündig auf `0 % / 33,33 % / 66,67 % / 100 %` beider Achsen:

| | Spalte 1 | Spalte 2 | Spalte 3 | Spalte 4 |
|---|---|---|---|---|
| **Zeile 1** | `#F5668C` | `#F4779C` | `#FE9B82` | `#FDCFAD` |
| **Zeile 2** | `#EFA2B5` | `#E8B2BC` | `#F3B5AA` | `#FDD6BE` |
| **Zeile 3** | `#D5B3D0` | `#C693A7` | `#A27684` | `#F4B9BF` |
| **Zeile 4** | `#B69ED1` | `#AF99D2` | `#BEA3D6` | `#E4BFDD` |

Umsetzung als sechzehn überlagerte `radial-gradient`-Ebenen — Details und
Begründung in `tokens.md` → "Background implementation".

### Oberflächen ✅

⚠️ **Deutlich abweichend von v1**, die weißes Glas mit 14 % Deckkraft vorsah.
Gebaut wurde **cremefarbenes Glas mit 60 %** — wärmer und wesentlich dichter.

| Name | Wert | Verwendung |
|---|---|---|
| Glass Surface | `rgba(255, 246, 240, 0.6)` | Glas-Panels (die vier Karten) |
| Glass Border | `rgba(255, 255, 255, 0.4)` | 1px-Kante der Panels |
| Menu Surface | `rgba(255, 246, 240, 0.4)` | Sidebar |

Bemerkenswert: `rgb(255, 246, 240)` ist exakt das **Base Cream `#FFF6F0`** aus
v1. Die Farbe hat also überlebt, nur ihre Rolle hat gewechselt — sie ist nicht
mehr der Seitenhintergrund, sondern die Glasfüllung.

### Verläufe ✅

| Name | Wert |
|---|---|
| Icon-Verlauf | `linear-gradient(223.139deg, #F45AA8 55.7%, #CC49AF 88.1%)` |
| Menü aktiv | `linear-gradient(-74.707deg, rgba(255,243,250,0.9) 7.7%, rgba(255,243,250,0.5) 92.1%)` |

`#CC49AF` als Endpunkt des Icon-Verlaufs ist keine Variable.

### Status 📋 — noch nicht verwendet

Aus v1 übernommen, bislang nirgends im Design eingesetzt. Wird spätestens
gebraucht für Smart-Home-Zustände, WLAN-Status und Fehlermeldungen.

```text
Success  #46D38A
Warning  #F8B84E
Danger   #F26666
Info     #67B8FF
Offline  #A79DBA
```

### Dark Mode 📋 — vollständig entworfen, **nicht in Figma**

Alle Farben oben sind das **helle** Thema. Seit 2026-08-18 existiert ein
komplettes dunkles Gegenstück: **[`dark-theme.md`](dark-theme.md)** — sechzehn
Mesh-Primärwerte, zwei neue Ink-Werte, drei neue Oberflächen und die
vollständige Rollentabelle für `[data-theme='dark']`, jede Textrolle mit
gemessenem WCAG-Kontrast.

📋 steht hier **nicht** für "noch nicht entworfen" — das Thema ist
entworfen, gemessen und gebaut. Es steht für *nicht aus Figma ausgelesen*:
Es gibt keinen dunklen Frame, keinen dunklen Variablensatz, keine dunkle
Komponentenvariante. Ein erneuter Import bringt keinen dieser Werte zurück.
Wird eine dunkle Variante je in Figma gezeichnet, gewinnt diese — dann wird
`dark-theme.md` zum Abweichungsprotokoll.

**Werte stehen ausschließlich in `dark-theme.md`**, bewusst weder hier noch in
`tokens.md`: `tokens.md` ist der Figma-Bestand, dieses Dokument ist Prosa über
das System — eine 30-zeilige Token-Tabelle mit Kontrastmessungen würde beides
verfälschen. Kurzfassung der Haltung: **gleiche Struktur, nicht gleiche
Helligkeit.** Dieselben Rollen, dieselbe Glasoptik (die Oberflächen bleiben
transluzent und weiterhin `blur(8px)`), dieselbe Geometrie — nur die Farbe
wandert in ein Nachtband. Bemerkenswert: **im dunklen Thema liegt keine
Textrolle unter 4,5 : 1, im hellen zwei** (`Text_Secondary` auf Glas 3,99 und
auf der Sidebar 3,48).

---

## Typografie

**Schrift:** Plus Jakarta Sans — freundlicher als Inter, modern ohne technisch
zu wirken, gut lesbar auf Touchscreens, trägt große Headlines ebenso wie
UI-Labels.

📋 Alternativen, falls je nötig: Geist · Inter · SF Pro Display

### Type Scale

⚠️ **Vollständig neu skaliert.** v1 ging von einer 1440er-Canvas aus und nannte
u.a. Display 64 / Body 18. Der gebaute Screen liegt niedriger, und er verwendet
**ExtraBold 800** statt der in v1 vorgesehenen Bold 700 / SemiBold 600.

| Stufe | Größe | rem | Gewicht | Zeilenhöhe | Verwendung | |
|---|---|---|---|---|---|---|
| Display | 48 | `3rem` | ExtraBold 800 | 1.1 | Begrüßung auf dem Homescreen | ✅ |
| H1 | 32 | `2rem` | ExtraBold 800 | 1.1 | Seitentitel | 📋 |
| H2 | 24 | `1.5rem` | Bold 700 | 1.3 | Sektionstitel | 📋 |
| H3 | 20 | `1.25rem` | Bold 700 | 1.3 | Panel-Titel | 📋 |
| Card Title | 18 | `1.125rem` | ExtraBold 800 | 1.3 | Kartentitel | ✅ |
| Body Large | 18 | `1.125rem` | Regular 400 | 1.5 | Einleitungen, wichtige Texte | ✅ |
| Body | 14 | `0.875rem` | Regular 400 | 1.5 | Kartenbeschreibungen, Standardtext | ✅ |
| Caption | 13 | `0.8125rem` | Medium 500 | 1.35 | Zeiten, Distanzen, Meta-Infos | 📋 |
| Label | 12 | `0.75rem` | Bold 700 / ExtraBold 800 | 1.3 | Menü-Labels (ExtraBold = aktiv) | ✅ |

**Zeilenhöhen leben hier, nicht in Figma.** Sämtliche Textebenen in Figma stehen
auf „Auto"; diese Werte sind die verbindlichen. Siehe `tokens.md`.

### Typografie-Regeln

- Headlines kurz halten.
- Maximal zwei Schriftgewichte pro Screen sichtbar.
- Kein All Caps, außer sehr kleine Statuslabels.
- ⚠️ Die v1-Regel *„keine Schrift kleiner als 18 px für wichtige Inhalte"* wird
  vom gebauten Screen nicht eingehalten — siehe Abweichungsprotokoll.

---

## Icons ✅

**Set:** Phosphor Icons, bezogen über `@ng-icons/core` + `@ng-icons/phosphor-icons`.

| | |
|---|---|
| Gewicht | `regular` — in **allen** Zuständen, auch aktiv |
| Glyph-Box | ⚠️ keine feste Größe — siehe Korrektur unten |
| Gefüllte Icon-Kachel | 56 px, Radius 30, Icon-Verlauf, 90 % Deckkraft |

> ⚠️ **Korrektur 2026-08-18: „Glyph-Box 12 px" war ein Lesefehler.** Der Wert
> stammte aus Figmas `aspect-[24/24]`, halbiert auf CSS-px. `aspect-[a/b]`
> beschreibt ein **Seitenverhältnis** (24:24 = 1:1) und enthält keine Größe.
> Die tatsächlichen Glyph-Größen werden aus den umgebenden Figma-Boxen
> abgeleitet und stehen in `tokens.md` → Sizing (`--size-icon-lg` 36 px,
> `--size-icon-md` 26 px, `--size-icon-sm` 24 px) — bei Widerspruch gilt
> `tokens.md` (siehe `docs/constitution.md`). Deckt sich mit der Icon-Skala
> weiter unten in diesem Dokument (Large 36 / Default 28 / Small 22).

> ⚠️ **Korrektur 2026-08-18: `regular` gilt nicht mehr „in allen Fällen".**
> Zwei Glyphen weichen ab, beide auf Nutzerentscheidung und gegen den Render
> geprüft: das Icon des **aktiven** Sidebar-Eintrags und das **Sparkle** im
> Heading-Block tragen den Marken-Verlauf (Pink → Violett), das Sparkle
> zusätzlich im Gewicht `fill`. Details in `screens/home.md` und
> `docs/features/home-screen-prototype/01-prd.md` → REQ-023.

⚠️ v1 sah aktive Icons in Primary Pink vor. Gebaut wurde stattdessen: das Icon
bleibt unverändert, der aktive Zustand entsteht aus Hintergrundverlauf und
schwererem Label. `fill` für aktive Icons wurde erwogen und aus gestalterischen
Gründen verworfen.

### Icon-Regeln

- Icons immer mit Text kombinieren, wenn die Funktion nicht eindeutig ist.
- Keine zu technischen Smart-Home-Symbole.
- Warme, einfache Metaphern: Sonne, Haus, Schlüssel, Licht, Karte, Herz, Uhr.

### Mapping

✅ **Im Einsatz:** `House` (Home) · `Info` · `MapPin` (Umgebung) ·
`LightbulbFilament` (Smart Home) · `ShoppingBag` (Services) · `GearSix`
(Einstellungen) · `Key` (Check-out) · `FileText` (Wichtige Infos) · `Sparkle` ·
`ArrowRight`

📋 **Für kommende Screens vorgesehen:** `WifiHigh` (WLAN) · `ClockCountdown` /
`SignOut` (Check-out) · `ThermometerSimple` (Temperatur) · `Compass`
(Aktivitäten) · `ChatCircle` / `Phone` (Kontakt) · `ListChecks` (Hausregeln) ·
`Bell` (Services) · `WarningCircle` (Problem melden)

---

## Form

### Border Radius

| Element | Wert | |
|---|---|---|
| Panels, Sidebar, Screen-Rahmen | 32 | ✅ |
| Icon-Kachel | 30 | ✅ |
| Logo-Kachel | 24 | ✅ |
| Sidebar-Eintrag (aktiv) | 16 | ✅ |
| Buttons | 22 | 📋 |
| Inputs | 18 | 📋 |
| Chips | 999 | 📋 |

### Border

| | |
|---|---|
| Glas-Panels ✅ | `1px rgba(255, 255, 255, 0.4)` |
| Dezente Container 📋 | `1px rgba(44, 31, 74, 0.08)` |

---

## Effekte

### Glass Panel ✅

⚠️ Werte gegenüber v1 deutlich verändert — dichteres, wärmeres Glas und
schwächerer Blur.

```text
Fill:   rgba(255, 246, 240, 0.6)
Blur:   backdrop-filter: blur(8px)
Border: 1px rgba(255, 255, 255, 0.4)
Shadow: 0 10px 40px rgba(44, 31, 74, 0.10)
```

⚠️ **Zum Schatten:** in Figma steht `0 20 80`, was in der 2x-Canvas zu
`0 10 40` in CSS wird. v1 nannte `0 20 80` als CSS-Wert. Der Wert wurde also
offenbar unverdoppelt in die 2x-Canvas übernommen. **Der gebaute Stand gilt:
`0 10px 40px`.** Falls der Schatten in der Umsetzung zu knapp wirkt, ist das die
erste Stelle zum Nachjustieren.

### Weitere Schatten 📋

```text
Soft Shadow:     0 6px 30px rgba(44, 31, 74, 0.12)
Floating Shadow: 0 12px 45px rgba(44, 31, 74, 0.16)
```

Gegenüber v1 halbiert, um zum tatsächlichen Maßstab zu passen.

---

## Komponenten

### Gebaut ✅

Vollständige Spezifikationen in [`screens/home.md`](screens/home.md).

| Komponente | Kurzfassung |
|---|---|
| **Glass Panel** | 196 × 196, Radius 32, Glasfüllung, Icon-Kachel + Titel + Beschreibung + Pfeil |
| **Menu Icons** | 72 × 72, zwei Zustände (aktiv/inaktiv) über Hintergrund und Label-Gewicht |
| **Intense Icon** | 56 × 56 Kachel, Radius 30, Icon-Verlauf, 90 % Deckkraft |
| **Sidebar** | 100 breit, Logo oben, Menüpunkte mittig, Einstellungen unten |

**Fehlende Zustände** über alle gebauten Komponenten hinweg: es gibt **nur**
Default und (bei der Sidebar) Aktiv. Kein Hover, kein Focus, kein Pressed, kein
Disabled — obwohl jede Karte anklickbar ist. Muss vor der Implementierung
ergänzt werden.

### Vorgesehen 📋

Noch nicht gezeichnet. Werte sind Absichten, keine Spezifikationen — und alle
auf den 1280er-Maßstab umgerechnet.

| Komponente | Eckdaten |
|---|---|
| **Primary Button** | Höhe 44, Padding X 20, Radius 22, Fill `#F45AA8`, Text weiß |
| **Secondary Button** | Höhe 44, Glasfüllung, Radius 22, Text `#2C1F4A` |
| **Ghost Button** | Höhe 36, transparent, Text `#6E6486` |
| **Info Card** | Padding 16–20, Radius 32, Glasfüllung |
| **Weather Card** | Temperatur groß, Zustand, Ort, Tageshinweis |
| **Smart Home Tile** | Min-Höhe 90, Aktiv-Fill `rgba(244, 90, 168, 0.16)` |
| **Chip** | Höhe 28, Radius 999, aktiv `#2C1F4A` auf weiß |

### Selbst entworfen, ohne Figma-Vorlage 📋 — Statusleiste und Einstellungen

Gebaut am 2026-08-18 durch `docs/features/appearance-and-language/`, aber
**nicht in Figma gezeichnet** — deshalb 📋 und nicht ✅, obwohl beide auf
jedem Screen laufen. Die Statusleiste wurde aus dem KI-Konzeptbild
[`assets/home-concept-ai.png`](assets/home-concept-ai.png) abgelesen, das
Komposition und grobe Proportion liefert, aber **keine token-genauen Werte**;
für den Einstellungen-Screen existiert überhaupt keine Vorlage.

| Komponente | Eckdaten |
|---|---|
| **Statusleiste** | Overlay oben rechts (kein Fließelement, damit die vermessene Homescreen-Geometrie sich nicht verschiebt): Sprachwähler (Globus + Kürzel + Caret), Darstellungswähler (Sonne/Mond, ohne Textlabel), Haarlinie, Uhr |
| **Dropdown** | Glas-Panel, Radius 24, Zeilen mind. 44 hoch, gewählte Zeile mit `Menü aktiv`-Verlauf und `Check`-Glyphe |
| **Einstellungen-Screen** | Seitentitel H1 32/ExtraBold, zwei Glas-Panels (*Sprache*, *Darstellung*), pro Einstellung alle Optionen sichtbar statt eingeklappt |

Zwei bewusste Abweichungen von diesem Dokument, beide protokolliert: der
Darstellungswähler trägt **kein Textlabel** (sonst springt die Breite der
Leiste beim Sprachwechsel), und die Uhr nutzt **Bold 700 statt Caption
Medium 500** — nur 400, 700 und 800 sind selbst gehostet, und 500 hieße eine
vierte `woff2`-Datei für eine einzige Zeile. Neue Tokens dieser beiden Screens
stehen in `tokens.md` → "In-house tokens", nicht in den Figma-Tabellen.

Vollständige Spezifikation:
`docs/features/appearance-and-language/02-design.md` §8 (Statusleiste) und
§9 (Einstellungen).

---

## Homescreen

Vollständig in [`screens/home.md`](screens/home.md). Kurzfassung:

- **Links:** Sidebar (100 breit), daneben Überschriftenblock und vier Karten
- **Rechts:** Widget-Spalte, 220 breit — **bewusst leer**, Inhalt wird in der
  Planungsphase entschieden
- **Hintergrund:** Mesh-Gradient, darüber das 3D-Rendering der Unterkunft

### Rendering

⚠️ v1 sah Deckkraft 65–85 %, leichten Blur und Soft-Light-Blend vor. Gebaut
wurde es **ohne jede Abschwächung**, voll deckend und ohne Blend-Modus.

Das Rendering ist **kein statisches Asset**, sondern Inhalt pro Unterkunft:
ein eigens angefertigtes 3D-Rendering als kostenpflichtiges Zusatzprodukt.
Unterkünfte ohne Rendering bekommen eine eigene Variante — nur Mesh-Verlauf,
kein Ersatzbild.

Die Regel aus v1 gilt unverändert: **Text hat immer Vorrang.** Sie wird
allerdings durch die Produktionspraxis abgesichert und nicht durch einen
technischen Mechanismus — die Renderings werden so angefertigt, dass die
Überschrift lesbar bleibt.

---

## Touchscreen-Regeln

### Mindestgrößen

| | v1 | Tatsächlich | |
|---|---|---|---|
| Sidebar-Eintrag | 60 | **72** | ✅ komfortabel über der 44–48px-Empfehlung |
| Karte | 260 × 180 | **196 × 196** | ⚠️ kleiner, als Touch-Ziel aber unkritisch |
| Wichtiger Text | ≥ 18 | **14** (Body), **12** (Label) | ⚠️ siehe Protokoll |

📋 Für noch nicht gebaute Elemente gilt weiterhin: Buttons und Icon-Buttons
mindestens 44 × 44.

### Interaktion

- Keine kleinen Links als Hauptaktionen.
- **Keine Hover-only-Informationen** — auf einem Touchscreen existiert kein Hover.
- Alles muss per Touch sofort verständlich sein.
- Maximal zwei Navigationsebenen.

---

## Motion 📋

Bislang nirgends umgesetzt — dieses Dokument ist die einzige Quelle. Relevant,
sobald Untermenüs und Popups dazukommen.

| Anlass | Dauer |
|---|---|
| Press / Zustandswechsel | 180 ms |
| Card Transition | 250 ms |
| Page Transition | 350 ms |

**Easing:** `cubic-bezier(0.16, 1, 0.3, 1)` (easeOutExpo)

- Animationen ruhig und hochwertig, keine Bounce-Effekte.
- Cards dürfen leicht einblenden oder hochfahren.
- Buttons dürfen beim Press minimal skalieren.
- Bewegung muss `prefers-reduced-motion` respektieren.

---

## Sprache und Texte

**Ton:** freundlich · kurz · hilfsbereit · warm · nicht werblich · nicht technisch

Durchgehend informelle Anrede (*ihr/euch*).

### Gute Labels

```text
Dein WLAN · Check-out · Licht steuern · Temperatur
Umgebung entdecken · Gastgeber kontaktieren
Heute in der Nähe · Vor der Abreise · Problem melden
```

### Vermeiden

```text
Feature entdecken · Smart Home Control Center
Property Information · User Dashboard · Management · Konfiguration
```

**Schreibweise:** korrekt ist **Airbnb**, nicht „AirBnb". Im Figma-Design steht
derzeit die falsche Variante in der Überschrift.

---

## Design-Prinzipien

1. Jede wichtige Funktion soll innerhalb von 3 Sekunden auffindbar sein.
2. Die Oberfläche wirkt wie eine digitale Rezeption, nicht wie ein Admin-Tool.
3. Der Hintergrund schafft Atmosphäre, der Inhalt bleibt immer klar lesbar.
4. Pink und Lila sind Akzente, nicht die komplette UI.
5. Touch-Ziele sind großzügig und bequem.
6. Karten strukturieren Inhalte, ohne dass die Seite wie Kachelchaos wirkt.
7. Icons unterstützen Text, ersetzen ihn nur bei sehr eindeutigen Aktionen.
8. Jede Seite hat eine klare primäre Aktion.
9. Glassmorphism dient Fokus und Tiefe, nicht sich selbst.
10. Weniger Text ist besser, solange die Handlung klar bleibt.

---

## Figma-Benennung

### Variablen ✅ — tatsächlicher Stand

```text
Brand_Primary-Pink
Brand_Secondary-Purple
Text_Primary
Text_Secondary
Text_On-Dark
```

⚠️ Weicht von der in v1 vorgeschlagenen Pfadschreibweise ab
(`Color/Brand/Primary Pink`). Die tatsächliche Konvention ist
`Gruppe_Name-Variante` mit Unterstrich. **Die gebaute Konvention gilt** —
umbenennen wäre Aufwand ohne Nutzen.

### Text Styles

Existieren **nicht**. Textebenen tragen ihre Eigenschaften direkt. Anlegen wurde
erwogen und als unverhältnismäßig verworfen, solange nur ein Screen existiert.

### Komponenten ✅

```text
Glass Panel · Menu Icons · Menu Bar · Intense_Icon
```

---

## Abweichungsprotokoll

Stellen, an denen v1 (Juli, Planungsphase) und der gebaute Stand auseinandergehen.
Überall gilt der gebaute Stand; hier steht, was das kostet.

| # | Thema | v1 | Gebaut | Bewertung |
|---|---|---|---|---|
| 1 | Canvas | 1440 × 1024 | 1280 × 832 | Unkritisch. Die Maße stammten aus der Panel-Auflösung eines MacBook Air, nicht aus einer Gerätekonvention |
| 2 | Schriftgewicht | Bold 700 / SemiBold 600 | **ExtraBold 800** | Unkritisch, gestalterische Entscheidung — kräftiger und markanter |
| 3 | Glas | weiß, 14 %, Blur 32–40 | cremefarben, 60 %, Blur 8 | Unkritisch. Wärmer und dichter, passt besser zum bunten Mesh |
| 4 | Hintergrund | Cream + prozentuale Gewichtung | 4 × 4 Mesh, 16 Farben | Verbesserung — konkret statt ungefähr |
| 5 | Schatten | `0 20 80` | `0 10 40` | **Vermutlich unbeabsichtigt.** Der Wert wurde nicht mitverdoppelt. Erste Stelle zum Nachjustieren, falls die Panels zu flach wirken |
| 6 | Aktives Icon | Primary Pink | unverändert | Bewusst; der aktive Zustand trägt über Hintergrund und Label-Gewicht |
| 7 | Rendering | 65–85 % Deckkraft, Blur, Soft Light | voll deckend | Bewusst, da eigens angefertigt und damit kontrollierbar |
| 8 | **Textgröße** | min. 18 px für wichtige Inhalte | **14 px** Body, **12 px** Labels | **Offen — siehe unten** |

### Zur Textgröße

Die einzige Abweichung, die eine echte Frage aufwirft statt nur eine
Geschmacksentscheidung zu dokumentieren.

v1 setzte 18 px als Untergrenze für wichtige Touchscreen-Inhalte und erlaubte
13–15 px ausdrücklich nur für Zusatzinfos. Der gebaute Screen zeigt
**Kartenbeschreibungen mit 14 px** — also Inhalt, den der Gast lesen soll — und
**Menü-Labels mit 12 px**.

Auf einem 11–13"-Tablet aus 40–70 cm ist das lesbar, aber es liegt am unteren
Rand. Die Touch-*Ziele* sind unproblematisch: 72 px Sidebar-Einträge und
196 px-Karten liegen deutlich über jeder Empfehlung. Es geht ausschließlich um
die Schriftgröße.

Weil in `rem` implementiert wird, ist die Korrektur billig: eine größere
Wurzelschriftgröße skaliert alles proportional mit, ohne dass ein einziger Token
angefasst wird.

`[ZU KLÄREN: an echter Hardware prüfen. Entweder v1s 18px-Regel war für dieses Layout zu streng, oder der Screen braucht eine größere Wurzelschriftgröße. Vor dem ersten Feature-Build entscheiden.]`

# Design-Canvas-Harness

Erzeugt aus der **laufenden App** die Artboards für die Screen-Canvas auf
claude.ai — keine Nachzeichnung, sondern das echte gerenderte DOM samt seiner
Stylesheets. Damit bleibt die Canvas nach jeder UI-Änderung reproduzierbar.

Playwright ist dafür Review-Tooling wie `tools/measure.mjs`
(`docs/constitution.md` v1.3.0); nichts davon hängt am Build oder an `npm test`.

## Die Canvas

    https://claude.ai/code/artifact/c8fd8406-ac00-4d93-9853-f1629a4425a1

**Diese URL ist die Adresse der Canvas — sie gehört hierher, nicht in einen
Chatverlauf.** Wer sie aktualisieren will, muss sie kennen: eine Session, die
das Artifact nicht selbst veröffentlicht hat, legt ohne diese URL ein **zweites**
an, statt das bestehende zu ändern. Konkret heißt das: Artifact-Werkzeug mit
`action: "read"` und dieser `url` lesen, bearbeiten, mit derselben `url`
veröffentlichen.

Wurde die Canvas zwischendurch im Browser bearbeitet, ist die gespeicherte
Fassung die Wahrheit — nicht die Dateien hier. Dann erst über `--extract` des
`/design`-Skills zurücklesen und von dort weiterarbeiten, sonst gehen fremde
Änderungen verloren.

## Ablauf

```bash
cd frontend && npm run build && cd ..
node tools/design-canvas/snapshot.mjs   # dist -> boards/*.dc.html  (20 Screens)
node tools/design-canvas/capture.mjs    # dist -> shots/*.png   (Referenz)
node tools/design-canvas/verify.mjs     # rendert die Boards
node tools/design-canvas/masked.mjs     # Pixel-Abgleich, Uhr ausmaskiert
```

Danach die Boards mit dem `/design`-Skill neu zusammenstellen und auf dieselbe
Artifact-URL veröffentlichen.

## Wie ohne Backend gerendert wird

Kein Backend nötig: `fixture.mjs` hält einen `GuestContent`-Payload in der Form
aus `guest-content.mapper.spec.ts` (die laut deren Kommentar den `DevDataSeeder`
spiegelt), verbreitert auf 8 Menüpunkte und 4 Action-Cards, damit die Sidebar
überläuft (`SIDEBAR_SLOTS` = 5) und Home seine volle Kartenreihe zeigt.
Playwright fängt `/api/guest/content` ab, legt das Device-Token in
`localStorage` und liefert das Logo aus. Die Zustände hinter Guards entstehen durch
Weglassen: kein Token → Kopplung, `/api` blockiert → Verbindung,
`occupancy: VACANT` → Standby. Staleness-Pille und -Banner kommen über den
Cache (`gxp.cache.content` vorbelegen, dann `/api` blockieren) — das `storedAt`
entscheidet, welcher von beiden erscheint: frisch → Pille, älter als eine
Stunde → Banner.

## Drei Eingriffe am Snapshot — und warum

1. **Schriften und Logo werden eingebettet.** Ein Artboard hat kein Netz. Das
   Logo ist besonders tückisch: `GuestAssetService` gibt dem `<img>` eine
   `blob:`-Objekt-URL, die den Snapshot nicht überlebt.
2. **`html { font-size: min(1.25vw, 1.9231vh) }` wird auf `16px` festgesetzt** —
   exakt der Wert, auf den die Regel bei 1280×832 auflöst.
3. **Das Theme wandert von `[data-theme='dark']` nach `:root`.** Ein Artboard
   besitzt kein `<html>`, und ein Wrapper-Element zwischen Wurzel und
   Glasflächen verändert, was `backdrop-filter` als Hintergrund abtastet — als
   sichtbare Abweichung auf jedem dunklen Screen mit Glas-Panel gemessen.

## Erwartetes Ergebnis

18 der 20 App-Screens sind pixelgleich (0,000 % abweichende Pixel; Uhr und der
„Stand HH:MM"-Text von Pille und Banner ausmaskiert, die laufen weiter).

Die beiden Ausreißer sind kein Fehler: **Verbindung und Kopplung können in der
App heute gar nicht dunkel sein.** Beide erscheinen genau dann, wenn keine
Inhalte geladen sind — und das Theme kommt aus eben diesen Inhalten bzw. den
serverseitig gespeicherten Einstellungen (`http-preference-gateway.ts`; lokal
wird nichts gespeichert). Ohne sie fällt die App auf `light` zurück. Die beiden
Dark-Artboards zeigen daher einen Vorschlag, keinen Ist-Zustand.

## Die Modul-Entwürfe

Die Seite „Module" der Canvas kommt NICHT aus diesem Harness. Sie stammt aus
einem früheren Artefakt (`Infos`, `Aufenthalt`, Fülltest, Infos dunkel) und
wurde übernommen; `AufenthaltDunkel` ist daraus abgeleitet, indem die
Hell→Dunkel-Umschaltung des vorhandenen Infos-Paares Zeile für Zeile
übertragen wurde. Diese Boards sind handgeschriebene Design-Components mit
eigener Logik — nicht mit `snapshot.mjs` regenerierbar. Wer sie ändern will,
liest sie über `--extract` des `/design`-Skills aus der veröffentlichten
Fassung zurück.

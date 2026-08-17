# Guest Experience Platform

Eine digitale Guest Experience Platform fuer Airbnbs, Ferienhaeuser und Serviced Apartments. Der Fokus liegt nicht auf Property Management fuer Gastgeber, sondern auf einer besonders angenehmen, schnellen und intuitiven Erfahrung fuer Gaeste vor Ort.

Die App ersetzt die klassische Hausmappe, Flyer und verstreute WhatsApp-Nachrichten durch eine touch-optimierte Oberflaeche auf Tablet, Touchscreen oder als Web App.

## Produktvision

Die langfristige Vision ist ein digitaler Concierge fuer den gesamten Aufenthalt:

> Gaeste bekommen vor, waehrend und nach dem Aufenthalt genau die richtige Hilfe zur richtigen Zeit.

Die Plattform soll spaeter erweiterbar sein fuer mehrere Unterkuenfte, unterschiedliche Gastgeber, mehrsprachige Inhalte, Empfehlungen, Automationen, Zusatzangebote und optional KI-gestuetzte Assistenz.

## MVP-Ziel

Die erste Version soll eine klare Aufgabe besonders gut loesen:

> Ein Gast kann waehrend seines Aufenthalts die haeufigsten Fragen zur Unterkunft und Umgebung selbst beantworten, ohne den Gastgeber kontaktieren zu muessen.

Praktischer formuliert:

> Alles, was Gaeste sonst per WhatsApp fragen wuerden, ist in wenigen Sekunden auffindbar.

## Zielgruppe

Primaere Zielgruppe:

- Gaeste in Airbnbs, Ferienhaeusern, Ferienwohnungen und Serviced Apartments
- Menschen, die gerade angekommen sind, sich orientieren wollen oder schnell eine konkrete Information brauchen

Sekundaere Zielgruppe fuer spaetere Versionen:

- Gastgeber
- Ferienhausbetreiber
- Property Manager
- Boutique-Hotels und Serviced-Apartment-Anbieter

## MVP-Module

Die erste Version wird in wenige klare Bereiche gegliedert. Sie sollen sich eher wie ein ruhiger, kontextbewusster Concierge anfuehlen als wie ein Admin-Dashboard.

### 1. Ankommen

Hilft dem Gast in den ersten Minuten und Stunden.

- Check-in Informationen
- Adresse und Eingang
- Schluesselbox oder Smart-Lock
- Zugangscode
- Fotos oder Hinweise zum Eingang
- WLAN-Zugang mit Netzwerk, Passwort und QR-Code
- Parken, Garage, Stellplatz, Ladezone oder Parkregeln

### 2. Wohnen

Erklaert, wie die Unterkunft funktioniert.

- Smart-Home Features
- Heizung, Klima, Licht und Rolllaeden
- TV, Streaming, Speaker und Entertainment
- Kaffeemaschine, Waschmaschine, Spuelmaschine, Herd und weitere Geraete
- Hausregeln
- Ruhezeiten, Rauchen, Haustiere, Partys und Besucher
- Muell und Recycling
- Wo wichtige Dinge zu finden sind, z. B. Handtuecher, Decken, Sicherungskasten oder Erste-Hilfe-Set

### 3. Hilfe

Gibt Sicherheit, wenn etwas unklar ist oder dringend wird.

- Gastgeberkontakt
- Bevorzugter Kontaktkanal
- Erreichbarkeitszeiten
- Notfallkontakte
- Polizei, Feuerwehr, Rettungsdienst
- Naechstes Krankenhaus, Apotheke oder Arzt
- Troubleshooting fuer haeufige Probleme

### 4. Umgebung

Hilft dem Gast, den Aufenthalt schoener und einfacher zu machen.

- Restaurants und Cafes
- Supermarkt, Baecker, Apotheke und lokale Basics
- Aktivitaeten und Sehenswuerdigkeiten
- Strand, Wandern, Kultur, Wellness oder Familienaktivitaeten
- Nahverkehr
- Naechste Haltestellen, Tickets, Taxi, Bahnhof, Flughafen, Fahrrad oder Scooter

### 5. Abreise

Macht den Check-out klar und stressfrei.

- Check-out Uhrzeit
- Schluesselrueckgabe
- Muell
- Geschirr
- Bettwaesche und Handtuecher
- Fenster, Heizung, Klima und Licht
- Letzte Schritte vor dem Verlassen der Unterkunft

### 6. Einstellungen

Haelt die App zuganglich und flexibel.

- Sprache
- Darstellung
- Impressum, Datenschutz und rechtliche Hinweise

## Homescreen

Der Homescreen soll keine reine Sammlung von Widgets sein. Er soll die wichtigsten Gast-Momente priorisieren:

- Was brauche ich direkt nach der Ankunft?
- Was brauche ich heute wahrscheinlich?
- Wie erreiche ich schnell Hilfe?
- Wie finde ich die wichtigsten Informationen ohne Suche?

Moegliche Homescreen-Elemente:

- Begruessung mit Name der Unterkunft oder Gastgebername
- Schnellzugriffe zu WLAN, Check-out, Hausregeln und Umgebung
- Heute relevante Hinweise
- Gastgeberkontakt
- Wetter als optionales Kontext-Widget
- Empfehlungen in der Naehe

Die rechten Widgets im aktuellen Design sind daher als Hypothesen zu verstehen. Sie bleiben, wenn sie dem Gast wirklich helfen, und werden reduziert, wenn sie nur dekorativ wirken.

## Nicht Im MVP

Diese Themen sind fuer die Vision interessant, aber nicht Teil der ersten Version:

- Buchungssystem
- Payments
- komplexes Property Management
- vollstaendiges Gastgeber-Dashboard
- In-App Chat
- KI-Concierge
- Push Notifications
- Automationen nach Aufenthaltsphase
- personalisierte Empfehlungen
- Upsells wie Late Check-out, Fahrradverleih oder Fruehstueck
- Bewertungsflow

## Produktprinzipien

- Gast zuerst, Gastgeberverwaltung spaeter
- Informationen muessen in Sekunden auffindbar sein
- Weniger Menues, mehr klare Situationen
- Touch-optimiert statt desktoplastig
- Ruhig, warm, hochwertig und nicht technisch
- Jede Karte und jedes Widget braucht eine klare Aufgabe
- Inhalte sollen spaeter pro Unterkunft konfigurierbar sein

## Erste Technische Richtung

Die erste Umsetzung startet als Angular Web App. Angular passt gut, weil die Plattform langfristig aus mehreren Bereichen, Routen, konfigurierbaren Inhalten, Mehrsprachigkeit und spaeter eventuell einem Gastgeber- oder Adminbereich bestehen wird.

Technischer Startpunkt:

- Angular mit TypeScript
- Standalone Components
- Angular Router fuer die Hauptbereiche
- Signals fuer lokalen UI-State
- Web App fuer Tablet und Touchscreen
- zunaechst statische Inhalte aus JSON oder TypeScript-Dateien
- modulare Komponenten fuer Bereiche, Cards und Widgets
- noch kein Backend, keine Auth und kein Adminbereich im ersten Schritt

Wichtig ist, die Datenstruktur von Anfang an so zu denken, dass spaeter mehrere Unterkuenfte und unterschiedliche Inhalte moeglich sind.

### Betriebsmodell: selbst gehostet

Die Plattform soll **pro Unterkunft auf einem eigenen Server im Gebaeude**
laufen, nicht als zentraler Cloud-Dienst. Ziel dahinter sind hohe
IT-Sicherheitsstandards: die Daten einer Unterkunft verlassen das Haus nicht,
und die Mandantentrennung ist physisch statt logisch.

Das hat drei Konsequenzen, die jede Entwicklung betreffen:

- Die App muss **ohne Internet** funktionieren. Keine externen Schriften,
  Skripte, Bilder oder Telemetrie zur Laufzeit — alles liegt im Build.
- Das **lokale Netz ist nicht vertrauenswuerdig**. Der Server haengt am selben
  WLAN wie die Gaestegeraete.
- Es gibt **kein zentrales Patchen**. Jede Abhaengigkeit muss spaeter auf
  Geraeten aktualisiert werden, auf die wir womoeglich keinen Zugriff haben —
  also so wenige wie moeglich.

Details und die offenen Sicherheitsfragen stehen in `docs/system-context.md`.

### Backend: spaeter Java und Quarkus

Die statischen JSON-Inhalte sind ausdruecklich nur der MVP-Stand. Danach ist ein
eigenes **Backend mit Java und Quarkus** vorgesehen. Quarkus passt gut zum
selbst gehosteten Betrieb: als Native Image startet es schnell und braucht wenig
Speicher — was auf einem kleinen Server im Gebaeude mehr zaehlt als in der Cloud.

Damit dieser Wechsel guenstig bleibt, duerfen Komponenten Inhalte **niemals
direkt importieren**, sondern beziehen sie immer ueber einen Service — auch
solange die Daten noch statisch sind. Diese eine Abkuerzung waere der
Unterschied zwischen einem Austausch der Datenquelle und einem Umbau jeder
Komponente.

Moegliche Kerndomains:

- Properties
- Stays
- Guests
- Content
- Recommendations
- Contacts
- Widgets
- Automations

## Naechster Schritt

Als naechstes sollte aus diesem Brief ein sehr kleiner erster Build entstehen:

1. Statische Tablet-Web-App mit Homescreen
2. Inhaltsstruktur fuer eine Beispiel-Unterkunft
3. Detailseiten fuer WLAN, Check-out, Hausregeln, Geraete und Umgebung
4. Erste modulare Widget-Struktur fuer den Homescreen

Das Ziel ist nicht, sofort die ganze Plattform zu bauen, sondern das Produktgefuehl zu beweisen: ein Gast oeffnet die App und findet sofort, was er braucht.

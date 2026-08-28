# ADR-027: Deterministischer adaptiver Trainingsassistent

- Status: angenommen
- Datum: 2026-08-26

## Kontext

Satzschritte speichern bisher Wiederholungen, aber weder den tatsächlich verwendeten Widerstand
noch die Nähe zum Muskelversagen. Für eine sinnvolle, automatische Progression fehlen damit die
entscheidenden Belastungssignale. Gleichzeitig darf der Assistent keine medizinischen Aussagen
treffen, keine bereits geplanten Vorkommen nachträglich verändern und aus automatisch erzeugten
oder unvollständigen Ergebnissen lernen.

Die Regeln orientieren sich am aktuellen Forschungsstand für gesunde Erwachsene: Kraft- und
Hypertrophieanpassungen funktionieren über ein breites Wiederholungsspektrum, ausreichend hohe
Anstrengung und progressiv erhöhte Belastung; Training bis zum momentanen Versagen ist dafür
nicht generell nötig. Subjektive Repetitions-in-Reserve-Angaben sind als alltagstaugliches Signal
brauchbar, bleiben aber fehlerbehaftet. Deshalb reagiert die App erst auf wiederholte konsistente
Signale und verändert pro Entscheidung nur eine Dimension. Grundlage sind insbesondere der
[ACSM Position Stand 2026](https://pubmed.ncbi.nlm.nih.gov/41843416/), die Metaanalyse zur
[Dosis-Wirkungs-Beziehung des Satzvolumens](https://pubmed.ncbi.nlm.nih.gov/41343037/), die
[Untersuchung zur RIR-Schätzgenauigkeit](https://pubmed.ncbi.nlm.nih.gov/38563729/) sowie
systematische Arbeiten zu [Trainingsfrequenz](https://pubmed.ncbi.nlm.nih.gov/38286426/) und
[Nähe zum Muskelversagen](https://pubmed.ncbi.nlm.nih.gov/38393985/).

## Entscheidung

Der Assistent ist pro Satzschritt optional und lokal. Beim Aktivieren werden die aktuellen
globalen Standardleitplanken in die Schrittvorlage kopiert: zwei bis drei Sätze, acht bis zwölf
Wiederholungen, Ziel-RIR 2, 2,5 kg beziehungsweise 5 lb Lastschritt und maximal zehn automatisch
verordnete effektive Sätze pro primärer Muskelgruppe in rollierenden sieben Tagen.

Jeder echte Satz speichert atomar Wiederholungen, exakten Widerstandsmodus und -wert, RIR von null
bis fünf sowie einen Sicherheitsmarker. Unterstützt werden externes Gewicht, Körpergewicht,
Körpergewicht plus Zusatzlast und unterstütztes Körpergewicht. Körpermasse selbst wird nicht
gespeichert. Schmerz oder problematische Technik pausieren die Automatik. Synthetische,
historische und unvollständige Ergebnisse sind nicht lernberechtigt.

Die Zustände lauten `DISABLED`, `CALIBRATING`, `ACTIVE` und `PAUSED`. Drei vollständige
Beobachtungen kalibrieren einen Schritt. Danach erfordern Progression und Regression jeweils zwei
gleichartige Signale:

- bereit: alle Sollwiederholungen erreicht, Median-RIR mindestens zwei und kein Satz bei RIR null;
- zu schwer: mehr als die Hälfte der Sätze verfehlt das Ziel oder Median-RIR null;
- andernfalls stabil.

Progression erhöht zuerst Wiederholungen innerhalb des Korridors, dann den Widerstand und zuletzt
die Satzanzahl. Regression arbeitet in umgekehrter Reihenfolge. Ein Lastsprung ist auf zehn
Prozent begrenzt. Pro Entscheidung wird genau eine Dimension verändert. Für die Volumengrenze
zählt ein primärer Satz 1,0 und ein sekundärer Satz 0,5. Ohne primäre Muskelzuordnung verändert die
Automatik die Satzanzahl nicht.

Eine Anpassung aktualisiert ausschließlich die Vorlage. Bereits materialisierte Vorkommen bleiben
als unveränderliche Planungssnapshots bestehen. Jede Anpassung erhält einen lokalen Auditdatensatz
mit Vorher-/Nachherwerten und Grund. Nur die neueste, noch aktuelle Anpassung kann rückgängig
gemacht werden. Eine nachträgliche Satzkorrektur startet die Kalibrierung konservativ neu.

## Konsequenzen

Die Regel-Engine liegt im reinen Domänenmodul und ist ohne Android oder Room testbar. Room-Schema
20 normalisiert die zusätzlichen Satzdaten und speichert Konfiguration, Lernzustand,
Planungssnapshots und Auditspur. Der Today-Bildschirm protokolliert Gewicht, RIR und den
Sicherheitsmarker direkt beim Satz; der Editor konfiguriert Widerstand und Muskelzuordnung.

Der Assistent ist kein medizinisches System und keine offene KI. Er diagnostiziert nichts,
ersetzt keine fachliche Betreuung und passt bei Sicherheitsmarkern nichts automatisch an.
Erklärtexte und eine sichtbare Anpassungshistorie können auf der Auditspur aufgebaut werden,
ohne die deterministische Entscheidung selbst zu verändern.

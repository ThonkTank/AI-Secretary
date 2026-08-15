# Auto Secretary

Eine kleine, private ADHS-Task-App für Android: nur **jetzt**, **danach** und ein ruhiger Tagesblick.

## Was bereits funktioniert

- Einmalige, wiederkehrende und fortlaufende Aufgaben mit Schritten
- Tageszeiten Morgen, Mittag, Abend und Später
- Wiederholungen: täglich, alle N Tage und ausgewählte Wochentage
- Sanftes Verschieben mit „Später“, XP und Routine-Level ohne Levelverlust
- Uhrzeitabhängiger Heute-Screen im Design „Tiefer Wald, goldener Sonnenaufgang“
- Vier responsive Homescreen-Widgetgrößen mit direkten Schritt- und Abschlussaktionen
- Rein lesende Einbindung aller sichtbaren Google-Kalender über die Android-Kalenderfreigabe
- Optionen für automatische, helle oder dunkle Darstellung und den GitHub-Updatekanal
- Keine Benachrichtigungen im Stabilitäts-Release: Das Widget bleibt die einzige proaktive Erinnerung
- Button zu den GitHub Releases für Updates

## Lokal testen

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Die Release-Einrichtung ist in [docs/releasing.md](docs/releasing.md) beschrieben. Die Produktentscheidung ist in [docs/produktziele.md](docs/produktziele.md) festgehalten.

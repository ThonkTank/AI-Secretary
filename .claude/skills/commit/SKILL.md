---
name: commit
description: Alle offenen Änderungen committen und zum Remote pushen
disable-model-invocation: true
allowed-tools: Bash
---

Committe alle offenen Änderungen und pushe sie zum Remote-Repository.

## Ablauf

1. Führe `git status` aus, um alle geänderten und untrackten Dateien zu sehen.
2. Führe `git diff` aus, um die konkreten Änderungen zu verstehen.
3. Füge ALLE geänderten und neuen Dateien zum Staging hinzu (`git add -A`).
4. Erstelle eine prägnante, aussagekräftige Commit-Message auf Deutsch, die den Inhalt der Änderungen zusammenfasst. Format: `<typ>: <beschreibung>` (z.B. `fix:`, `feat:`, `refactor:`, `build:`, `docs:`, `test:`).
5. Committe mit der Message (Co-Authored-By Header anhängen).
6. Pushe zum Remote (`git push`).
7. Zeige das Ergebnis an (Commit-Hash, gepushte Änderungen).

## Regeln

- Keine interaktiven Git-Befehle (`-i` Flag).
- Commit-Message IMMER über HEREDOC übergeben.
- Niemals `--force` pushen.
- Falls der Push fehlschlägt, den Fehler melden und NICHT automatisch force-pushen.
- Falls es keine Änderungen gibt, das melden und nichts tun.

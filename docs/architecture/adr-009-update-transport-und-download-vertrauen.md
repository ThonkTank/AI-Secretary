# ADR-009: Vertrauensgrenze für Update-Transport und Download

- Status: angenommen
- Datum: 2026-08-16

## Kontext

Ein Update ist ausführbarer Code. HTTPS allein reicht deshalb nicht als Freigabekriterium:
automatische Redirects könnten einen Download auf einen nicht vorgesehenen Host umlenken,
unbegrenzte Antworten Speicher oder Datenträger füllen und vorübergehende Serverfehler einen
Release unnötig unbenutzbar machen. Unvollständige Dateien dürfen außerdem nie den
Android-Installer erreichen.

## Entscheidung

Der produktive Updatekanal verwendet eine explizite, exakt vergleichende HTTPS-Allowlist für
`api.github.com`, `github.com` sowie die derzeit von GitHub verwendeten Asset-Hosts
`release-assets.githubusercontent.com`, `objects.githubusercontent.com` und
`github-releases.githubusercontent.com`. Subdomain-Suffixe, Host-Lookalikes, Userinfo und andere
Ports als der implizite oder explizite HTTPS-Port 443 werden abgewiesen. Sowohl das Repository
als auch der HTTP-Transport prüfen die URLs. Der Transport folgt höchstens fünf Redirects
manuell und prüft jeden einzelnen Ziel-Hop erneut.

Verbindungen besitzen 10 Sekunden Connect- und 30 Sekunden Read-Timeout. Netzwerkfehler,
Timeouts, HTTP 429, ein durch `X-RateLimit-Remaining: 0` bestätigtes GitHub-403 und HTTP 5xx
werden höchstens dreimal mit exponentiellem Backoff von 250 und 500 Millisekunden versucht.
Andere HTTP-Fehler werden sofort typisiert zurückgegeben. Ein gesetztes Interrupt-Flag wird vor
jedem Request und während des Kopierens als `CANCELLED` behandelt; ein unterbrochener Backoff
stellt das Flag wieder her.

Releasefeed, Metadaten und APK haben getrennte Obergrenzen. Deklarierte und tatsächlich
gelesene Größen werden geprüft; die APK muss zusätzlich exakt der in den signierten
Release-Metadaten angegebenen Größe entsprechen. Der Download entsteht als `.partial` im
privaten Cache. Erst nach SHA-256-, Paket-ID-, Versions-, Commit- und APK-Signaturprüfung wird er
im selben Verzeichnis mit `ATOMIC_MOVE` in eine installierbare `.apk` umbenannt. Vorherige
temporäre Dateien werden kontrolliert entfernt; fehlschlagende Bereinigung oder atomische
Finalisierung bricht den Ablauf ab. Nur ein `VerifiedUpdate` kann den Installer-Port erreichen.
Die installierte und heruntergeladene APK müssen vom gleichen Zertifikat signiert sein.

## Konsequenzen

Ein unzulässiger Host oder eine nicht vollständig verifizierte APK kann den Android-Installer
nicht erreichen. Temporäre, übergroße oder unvollständige Downloads werden nicht als fertige
Datei sichtbar. Vorübergehende GitHub- und Netzwerkfehler sind tolerierbar, ohne unbeschränkte
Last oder Wartezeit zu erzeugen.

Die Hostliste ist bewusst operativ streng: Ändert GitHub seine Asset-Infrastruktur, ist eine
geprüfte Codeänderung mit Tests und neuem Release erforderlich. Die Lösung verwendet den
Android-System-Truststore und die unabhängige APK-/Metadatenprüfung, aber kein Certificate
Pinning und kein separates TUF-ähnliches Update-Root-of-Trust. Diese verbleibende Abhängigkeit
ist akzeptiert, solange Releases weiterhin durch den dauerhaften Android-Signaturschlüssel und
den im Repository fixierten Zertifikat-Fingerprint abgesichert werden.

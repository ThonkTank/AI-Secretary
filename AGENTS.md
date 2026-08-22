# Repository-Regel

Jede Implementierungsphase wird auf einem eigenen Themenbranch entwickelt und committed. Der
Branch wird als Pull Request gegen `main` geprüft. Erst wenn alle erforderlichen Checks grün sind,
wird er per Squash-Merge nach `main` übernommen; der gemergte Stand auf dem Remote-Branch `main`
ist der Abschluss der Phase.

Direkte Pushes auf `main` sind nur für dokumentierte Notfälle zulässig. Der Grund, die vorherigen
Prüfungen und die nachgelagerte Verifikation müssen dann im Commit beziehungsweise in der
zugehörigen Betriebsdokumentation nachvollziehbar sein. Ein fehlgeschlagener regulärer Check ist
kein Notfallgrund.

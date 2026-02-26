# Review Backlog — task/ui/edit/internal/editor

## Open Issues

[nit] GoalSectionController.java:18-21 — `GOAL_COLORS` array embeds 10 Material 600-series ARGB hex strings in Java source. Not in `colors.xml`, invisible to theming, and unfindable via resource search. Scale factors `1.25f / 1.0f / 0.75f` at lines 91-93 are undocumented magic numbers.

[nit] PrefSlotUIBuilder.java:65-136 — `rebuild()` is 71 lines mixing header creation, row layout, and two near-identical button setup blocks. The two `LayoutParams` blocks (lines 101-107 vs 123-128) differ only in margin end.

[warning] TaskEditSectionBinder.java:68-146 — `bindScheduling()` is 78 lines mixing view inflation, value population, date picker wiring, and spinner listener setup. Will grow further when TERMIN scheduling type is exposed. Extract behavioral setup into separate methods.

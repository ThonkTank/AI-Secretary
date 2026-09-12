# Today placement actions

## Behavior

The title of a timeline task in Today moves it before the current focus, adopting the focus's
presentation section. Other visible items retain relative order. The catalog's title behavior is
unchanged. This selects work; it does not start a flow or record completion.

Later moves an item after the last visible item in its presentation section. If already last,
it moves after the next section's items (including empty sections): MORNING, MIDDAY, EVENING,
LATER. From the end of LATER it is hidden until the next local calendar day, then returns at the
end of its original section. Even a single remaining item can be deferred. A manually placed
focus shows its current section, so advancing through empty sections has visible feedback.

## Ownership and persistence

Schema 26 adds `today_placements`, keyed by the closed item kind and ID. It stores visibility
date, presentation section and queue position. No existing table or occurrence due date is
rewritten by the migration. `Integer.MAX_VALUE` represents returning at a section's end.

`TodayQueue` owns the combined normal/task/flow projection for app, widget and commands. Without
overrides it retains the previous date/section/order/identity ordering. Commands reload the queue
and persist its new positions in one transaction. Previously unseen items retain baseline ordering;
saved positions are applied to that baseline. A future placement excludes only its card, not its
runtime. Flow placement identity and original schedule section remain unchanged.

`MoveTodayItem` replaces both old defer use cases, including the widget action route. Errors
roll back the entire placement change and use the existing save-error UI. Missing/stale targets
are no-ops against the latest dashboard; normal database invalidations refresh the presentation.
Placement invalidation is included in the existing observable database table set.

The Room adapter removes orphaned/completed placements when placements are read. A flow with
active candidates or an uncollected run retains its placement while waiting, even if its sheet
has no currently actionable steps. Hidden work does not acquire resources or modify timer state.

## Verification

- `TodayPlacementRoomTest`: overdue work, empty sections, tomorrow visibility, first placement,
  app/widget agreement, reopened file database, stale targets, completion cleanup, transaction rollback;
  runs on Robolectric API 26 and 35.
- `StepFlowRuntimeRobolectricTest`: mixed normal/flow ordering with unchanged candidates/resources.
- `DatabaseMigrationRobolectricTest`: schema 25 to 26 Room validation and retained XP, plus historical
  upgrade coverage on API 26 and 35.
- `FocusTaskViewTest`: accessible title target emits the typed bring-first action without completion
  or menu actions. Existing coordinator, view, architecture and visual checks remain in the full gate.

Delivery evidence will distinguish local checks, PR checks, merged main and publication. No physical
Android device was attached during initial implementation.

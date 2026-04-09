# Review Backlog — task/ui/list

## Open Issues

### [coupling] TaskViewModel — direct `TaskWidgetProvider` dependency @skill:review-architecture

`TaskViewModel.refreshList()` calls `TaskWidgetProvider.notifyWidgetUpdate(getApplication())` directly.
Two other call sites in the *application* layer are tracked in `application/REVIEW_BACKLOG.md` under the
same pattern. When the widget notification abstraction is eventually introduced to invert the
application-to-UI dependency, this call must also be migrated — or it will continue to hold a concrete
reference to the widget class.

**Why it matters here:** The ViewModel ideally should not know about a specific concrete widget
implementation. Any new widget type added later requires touching the ViewModel in addition to the
use-case and alarm receiver.

**Fix:** Same abstraction proposed in `application/REVIEW_BACKLOG.md` (`WidgetRefreshNotifier`);
this call site is a client of the same abstraction. Requires coordinated cross-layer change — deferred.

---

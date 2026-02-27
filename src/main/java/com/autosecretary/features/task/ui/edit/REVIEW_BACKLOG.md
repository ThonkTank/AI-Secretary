# Review Backlog — task/ui/edit

## Open Issues

[warning] TaskEditPresenter.java (FormInput:231-265) + TaskEditState.java:16-54 + TaskEditStateMapper.java:71-127 — Four parallel field walks: `FormInput` declares ~25 fields, `applyForm()` copies them one-to-one into `TaskEditState`, `toTask()` walks the same list again, `fromTask()` walks it in reverse. Every new field requires four synchronized edits; a missed one silently drops the value on save. Already diverging: `deadline` is mutated via `presenter.setEditableDeadline()` and bypasses `FormInput` entirely, making `FormInput` an incomplete representation of the form despite its name.

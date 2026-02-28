# REVIEW_BACKLOG: Budget Importing

## [consider] resolveCategoryForTransaction method could be inlined — BudgetImportUseCase.java:194-202

**Issue:** The `resolveCategoryForTransaction()` method is only called once (in the `buildTransactions()` loop) and performs straightforward logic: check if a category is known, and pick either the provided category or a default. At 4 lines of logic, it's a candidate for inlining.

**Potential concern:** The extracted method name is descriptive and keeps the loop body cleaner. Inlining would expand the loop and make it slightly harder to scan. The extracted method provides semantic clarity.

**Verdict:** Borderline. Keep as-is for readability—the name adds intent. Inlining would make the loop longer and less scannable without significant benefit.

## [consider] normalize() utility in StatementFileParser called 4 times — StatementFileParser.java:146-148

**Issue:** The `normalize()` method is a one-liner that's called 4 times (`isPdf()` and `isCsv()` each call it twice). At 1 line of logic, it could be inlined directly into the call sites.

**Potential concern:** The extracted method name clarifies intent. Inlining would duplicate the normalization logic. The current approach avoids duplication and keeps the intent clear.

**Verdict:** Borderline. The extraction avoids duplication and aids readability. Keep as-is.

## [keep] ImportPipelineException design is justified — BudgetImportUseCase.java:285-296

**Issue:** `ImportPipelineException` is a custom exception with a single field (`importId`) that wraps a user-visible message and a cause. It might seem over-engineered for a simple wrapper.

**Why it's justified:** The exception is used to thread `importId` through the exception path so that error handling can mark the failed import in the database. A custom exception type provides type safety and makes the contract explicit to callers. The alternative (throwing a generic exception and then extracting importId from call context) would be more fragile.

**Verdict:** Keep. The type adds safety and clarity.

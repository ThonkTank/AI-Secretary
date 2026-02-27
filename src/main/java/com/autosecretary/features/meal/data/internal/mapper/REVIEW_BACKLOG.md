# Review Backlog — meal/data/internal/mapper

## Open Issues

### [nit] RecipeRowMapper: unescaped delimiters in ingredient/rating serialization
**File:** `RecipeRowMapper.java:106-143`

`serializeIngredients` joins fields with `|` and records with `;`. `serializeRatings` joins with
`,` and `|`. None of these separators are escaped, so if an `ingredientName` or `unit` ever
contains `|` or `;`, `parseIngredients` will silently produce wrong data (wrong field alignment
or dropped records). Same risk applies to ratings if a future field contains separator characters.

The storage is in-memory so no migration is needed, but the serialized strings are also read from
legacy import rows where external data is the input.

**Suggested fix:** Either validate that ingredient names/units cannot contain the delimiters, or
switch to a safer format. At minimum, add a comment in the serialization methods stating the
delimiter-free constraint.

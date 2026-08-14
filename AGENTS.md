# Repository workflow

- After completing an implementation request, run the full local Android gate:
  `./gradlew --no-daemon --max-workers=1 qualityGate`.
- If and only if that gate succeeds and the work is complete, commit the intended changes and push
  the current branch. Feature-branch pushes run verification only and never publish an APK.
- Do not manually dispatch, invent, or maintain a second release channel. A relevant commit is
  released automatically only after it reaches `main`. Do not merge a feature branch into `main`
  unless the user explicitly requested that merge.
- When a push to `main` is in scope, monitor `Handy-Update veröffentlichen` through publication and
  verify that GitHub `Latest` targets the pushed commit and contains `AutoSecretary.apk` plus
  `release-metadata.json`.
- Do not publish for read-only, diagnostic, review, planning, incomplete, or failing work.
- Keep package ID `com.autosecretary`, the monotonically increasing Android version sequence and
  the permanent release signer unchanged so installed phones can update in place.

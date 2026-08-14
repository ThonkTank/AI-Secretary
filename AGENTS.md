# Repository workflow

- After completing an implementation request, run the full local Android gate:
  `./gradlew --no-daemon --max-workers=1 checkArchitecture lintDebug lintRelease assembleDebugAndroidTest assembleRelease`.
- If and only if that gate succeeds and the work is complete, commit the intended changes, push
  the current branch, dispatch `.github/workflows/android-release.yml` for that exact branch, and
  monitor it through publication of the normal latest GitHub Release.
- Verify that the published release targets the pushed commit and contains `AutoSecretary.apk`
  plus `release-metadata.json` before reporting completion.
- Do not publish for read-only, diagnostic, review, or planning work. Do not publish an incomplete
  implementation or a build with failed checks.
- Keep the package ID, monotonically increasing Android version code, and permanent release signer
  unchanged so installed phones can update in place.

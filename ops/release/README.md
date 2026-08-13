# Production release gate

No APK is stored in this directory. `version.properties` is the sole version input and clean CI
produces immutable GitHub Release assets.

`upgrade-gate.properties` deliberately blocks publishing until a real Build-4 upgrade report has
been reviewed. Run `ops/device_release_gate.sh`, archive its report externally, then update the gate
in a separate reviewed change. The manual release workflow requires exact evidence, signing mode,
database v34, reboot, day rollover, calendar refresh and next-version update results.
It also requires proof that the device was restored to the code-6 target from the same externally
secured source archive after the ephemeral code-7 update check.
The reviewed report binds the source archive, the temporary code-7 APK and the code-6 target APK by
SHA-256. Production CI rebuilds code 6 and refuses to publish unless its APK is byte-identical to
the candidate that passed the device gate.
It also runs the `fullDebug` device suite and records the 20 typed German proposal cases, a real
bundled-model inference, complete rejection of invalid output and the
no-mutation-before-confirmation invariant.

Starting with versionCode 6, the workflow publishes `signer-sha256.txt` and requires every later
stable release to use the same certificate. Selecting a different signing mode cannot silently
fork the production update channel.

The separately manual `Android upgrade-gate APKs` workflow breaks the otherwise circular gate: it
creates signed code-6 and ephemeral code-7 APKs as a one-day, non-release artifact. It has no write
permission and performs no publishing. The APKs exist only to run `ops/device_release_gate.sh` on an
authorized physical device; a production release still requires this reviewed gate file.

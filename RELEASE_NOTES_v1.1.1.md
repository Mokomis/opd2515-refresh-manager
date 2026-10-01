# v1.1.1 — Restore per-app refresh overrides

Saved 144 Hz selections could appear correct in Refresh Manager while streaming stayed at 60 Hz in Moonlight X or 120 Hz in Sloplight. This release fixes the module startup and final refresh-policy override; it does not change client decoding.

## Why it broke

Two independent failures were observed on the OPD2515 with ColorOS 16:

- The APK had been installed on Android's incremental-install filesystem. At boot, Vector could not open it (ENOENT), so the system-process module never loaded. Installing the same APK without incremental installation resolved that failure.
- Once loading was repaired, the original interception of `reviseWinPreferredIdIfNeeded()` still did not change the final display selection. Parsing a diagnostic reason string at that intermediate point did not reliably cover the final policy decision. The precise internal bypass was not independently isolated; replacing the interception point resolved the observed cap.

## How it was fixed

- Install the hooks when Vector loads the module as well as on the system-server startup callback, with a guard against duplicate installation.
- Apply the saved policy at `PickRefreshRateData.getPickPreferredId()`, after the stock decision, identifying the selected window directly with `candidateWinPkgName()`.
- Keep the stock result for packages without a saved override, and retain the earlier helper hook for compatibility.
- Document non-incremental ADB installation and keep hook installation/failure logs visible.

## Verification

After reboot on the connected OPD2515:

- Moonlight X: physical panel 144.00002 Hz, measured refresh 144.0743 Hz, pacing target about 144.06 FPS. Subsequent 10-second output intervals were 143.36, 143.56, and 142.76 FPS.
- Sloplight: foreground streaming panel 144.00002 Hz instead of the previously observed 120 Hz; client reported 144 Hz and requested 144 FPS on its surface.

These verify refresh and pacing. They are not a new comparative decode/latency benchmark. The functional source was tested on-device; this release rebuild changes version metadata to 1.1.1 (version code 3).

## Updating

Install the APK over the existing app, then reboot so Vector reloads the system-process hook. Saved selections are preserved. Keep the module enabled with only System Framework (`system`) in scope.

For ADB installation:

```sh
adb install --no-incremental -r opd2515-refresh-manager-v1.1.1.apk
```

A normal package-installer installation can also be used. The new source does not make an incremental APK available earlier in boot; the installation method is part of the fix.

This remains specific to the tested OPD2515/ColorOS firmware. No firmware XML edits, framework upgrade, or GPU clock changes were needed. APK signing uses the same certificate as v1.1.0.

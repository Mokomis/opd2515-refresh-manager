# OPD2515 Refresh Manager

Per-app **Default (Adaptive) / 60 Hz / 120 Hz / 144 Hz** display control for the rooted OPPO Pad Mini (OPD2515) on ColorOS 16.

## What this is

The OPPO Pad Mini has a real 144 Hz display mode, but ColorOS normally limits most applications to 120 Hz. The stock firmware contains a curated package list whose approved apps may use display mode ID 4 (144 Hz). Apps outside that list can request 144 Hz and briefly reach it, but ColorOS revises the request to mode ID 3 (120 Hz).

This project supplies an on-device manager and a systemless Vector/libxposed hook. It offers four policies for each launchable app:

- **Default (Adaptive):** use the normal ColorOS refresh policy, which may switch among supported rates.
- **60 Hz:** lock the app to the existing 60 Hz display policy.
- **120 Hz:** select the existing 120 Hz display policy for that app.
- **144 Hz:** select the existing physical 144 Hz mode for that app.

It does not overclock the panel, replace the kernel, edit the firmware XML, or modify `/my_product` or any other system partition.

## Tested configuration

- OPPO Pad Mini OPD2515 / OP6548L1
- ColorOS 16 / Android 16
- KernelSU 3.2.5
- Zygisk Next 1.5.0
- Vector 2.2 with libxposed API 102
- Stock `/my_product/etc/refresh_rate_config.xml` version `20260430`
- Stock XML SHA-256: `719ae1134472cc9e9b3ff410da49f6073f42354621199e2e163eaabad1f91ec6`

The physical modes reported by `dumpsys display` on the tested tablet are:

| Physical mode | Refresh rate |
| --- | ---: |
| 1 | 120.00001 Hz |
| 2 | 60.000004 Hz |
| 3 | 90.0 Hz |
| 4 | 144.00002 Hz |

The stock ColorOS configuration explicitly maps application-policy rate ID 2 to 60 Hz and rate ID 3 to 120 Hz. The firmware's 144 Hz policy uses rate ID 4. This project is based on that exact observed firmware behavior. Do not assume compatibility with another device or firmware revision.

## How it works

Vector loads the module only in Android's `system_server`. The module hooks:

```text
com.android.server.wm.OplusRefreshRatePolicyImpl$PickRefreshRateData
    .reviseWinPreferredIdIfNeeded(int, int, String)
```

ColorOS calls this method while revising its preferred refresh mode for the active window. The hook extracts the package name from the supplied policy reason and checks that package's saved override:

- Stored value `2`: return the ColorOS 60 Hz policy ID.
- Stored value `3`: return the ColorOS 120 Hz policy ID.
- Stored value `4`: return the 144 Hz policy ID.
- No override: call the original ColorOS method unchanged.

The manager stores each rule in a persistent Android property named `persist.opdrr.<package-hash>` and asks the Oplus screen-mode service to reevaluate the app immediately. KernelSU root is required to write the property and call that service. The hook itself is supplied systemlessly by Vector.

Package-name hashes keep property names short. A Java `String.hashCode()` collision is theoretically possible, although unlikely; v1.1.0 does not include collision handling.

## Installation

Prerequisites:

1. A rooted OPD2515 in the tested ColorOS 16 firmware family.
2. KernelSU with **Kernel unmount enabled**.
3. Zygisk Next.
4. Vector 2.2 or newer with libxposed API 102.

Installation:

1. Download and install the APK from the latest GitHub Release.
2. Open Vector and enable **OPD2515 Refresh Manager**.
3. Scope it only to **System Framework** (`system`).
4. Reboot once.
5. Open Refresh Manager and allow ColorOS's **Read your app list** permission.
6. Open **KernelSU → Superuser → OPD2515 Refresh Manager** and enable **Superuser**.
7. Search for an app and select **Default (Adaptive)**, **60 Hz**, **120 Hz**, or **144 Hz**.

Selections apply immediately and persist across reboots.

## Verification

Keep the selected app in the foreground and run:

```bash
adb shell dumpsys display | grep -E 'mActiveModeId|mActiveRenderFrameRate'
```

For 60 Hz, expect:

```text
mActiveModeId=2
mActiveRenderFrameRate=60.000004
```

For 144 Hz, expect:

```text
mActiveModeId=4
mActiveRenderFrameRate=144.00002
```

For 120 Hz, expect:

```text
mActiveModeId=1
mActiveRenderFrameRate=120.00001
```

The 120 Hz and 144 Hz choices were verified on the test OPD2515 with a previously non-whitelisted application in the foreground. The 60 Hz policy ID is explicitly defined by the tablet's stock ColorOS configuration, and the v1.1.0 UI/property path has been verified on-device. A reboot is required before Vector loads the updated hook that honors the new value, so the final live 60 Hz lock remains to be confirmed after that reboot.

## Gaming frame-pacing note

The manager controls the **display refresh rate**, not a game's rendering frame rate. For games that offer a stable 40 FPS cap, select **120 Hz** in Refresh Manager and select **40 FPS** inside the game. At 120 Hz, every 40 FPS game frame is displayed for exactly three panel refreshes (`120 / 40 = 3`), providing even frame pacing without requiring VRR.

Do not interpret this as a 40 Hz display mode: the OPD2515 exposes only fixed 60, 90, 120, and 144 Hz modes, and Android reports no true adaptive-refresh-rate support. If the game cannot maintain its 40 FPS cap, frame-time fluctuations can still be visible. A 48 FPS cap similarly pairs evenly with 144 Hz (`144 / 48 = 3`).

## Rollback

1. Disable **OPD2515 Refresh Manager** in Vector.
2. Reboot.
3. Optionally uninstall the APK.

The saved properties are inert when the hook is disabled. There is no firmware partition or XML backup to restore because nothing on those partitions is changed.

If a boot problem occurs, use Vector/KernelSU safe mode to disable the module and reboot.

## Limitations and safety

- This is a device- and firmware-specific project, not a universal Android refresh-rate tool.
- 144 Hz consumes more display power and may increase heat.
- Thermal, hardware, or emergency power protections elsewhere in the firmware may still take precedence.
- A 144 Hz panel mode does not make an app render 144 unique frames per second. App rendering, streaming settings, decoding, and network performance are separate limits.
- Selecting a fixed rate in v1.1.0 holds the selected display policy at that rate. Only **Default (Adaptive)** delegates rate selection to ColorOS.
- Root and system-process hooks carry risk. Keep a known-good way to disable Vector modules.

## Building

Requirements:

- JDK 17 or newer
- Android SDK Platform 36

Build:

```bash
./gradlew assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The published release APK is signed separately. Private signing keys are not included.

## Source layout

- `MainActivity.java`: app list, search UI, selectors, and root commands.
- `ModuleEntry.java`: API 102 hook for the Oplus refresh policy.
- `RefreshConfig.java`: package-to-property mapping shared by the UI and hook.
- `META-INF/xposed/`: Vector entry point, static scope, and metadata.

## Privacy

The package-list permission is used only to populate the local app selector. The APK requests no network permission and contains no analytics, advertising, or telemetry. Vector scope is statically limited to Android's system process.

## v1.0 release integrity

```text
SHA-256: 3efc24aeba83d2809c0172d5dae2bc2f3f67973f0621d17f8968e44e796049b4
```

## v1.1.0 release integrity

```text
SHA-256: 5f8a66791483a9d481347f4bd4ca19ee0d980ef5de820c32e828eb7a22ba6191
```

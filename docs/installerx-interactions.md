# InstallerX interaction adaptation

Source: `wxxsfxyzm/InstallerX-Revived`, revision
`f6ffcd8ea84e629bc14160414e7343f07a4d3d76` (GPL-3.0-only).
Local miuix reference: `5157b503e86e2bfc2db61db00fff5df41326394a`
(Apache-2.0; consumed through the existing includeBuild).

## Mapping

| Upstream implementation | Quieta adaptation |
| --- | --- |
| `ui/page/miuix/settings/home/MiuixHomePage.kt` | `PressableCard`: miuix Tilt + indication for status, stats and actionable license cards |
| `MiuixHomePage.kt` / `MiuixPrivPage.kt` scroll setup | `QuietaPage`: miuix TopAppBar, MiuixScrollBehavior, overScrollVertical, native overscroll disabled |
| `ui/theme/Backdrop.kt` | `ui/glass/PageTopBarBackdrop.kt`: separate content backdrop, 25px texture blur, 80% theme surface tint; no liquid lens |
| `ui/page/miuix/settings/home/priv/MiuixPrivPage.kt` | Two blue notices, SmallTitle, one Card of BasicComponent rows and circular Checkbox indicators |
| `ui/page/miuix/widgets/MiuixCards.kt` | Theme-aware blue notices with body2 semibold text and 12dp/8dp outer spacing |
| Home/config/history text roles and miuix `TextStyles` | System font; status 20sp semibold/14sp medium; stats 15sp medium/26sp semibold; settings 17sp medium/14sp normal; rules 18sp medium; records 17sp medium/16sp normal |

The global authorizer remains single-choice: the entire row exposes radio-button
semantics, while the decorative checkbox has no separate click or accessibility
node. Quieta retains AUTO instead of copying InstallerX's custom shell command.
No package-installation text or behavior is imported.

miuix supplies the underlying tilt, spring, indication, checkbox and app-bar
implementations. Its existing Apache-2.0 disclosure is retained; InstallerX's
in-app entry identifies the adapted surfaces and revision. Existing KernelSU and
AndroidLiquidGlass disclosures remain separate for earlier imported code.

## Regression checklist

- Press near opposite corners of status and stat cards; verify directional tilt,
  tint, release settling, and drag cancellation without navigation.
- Drag each main page up: large title collapses to a centered small title, actions
  remain reachable, content blurs behind the top bar when enabled.
- Drag past both boundaries and release, including short/empty pages: content
  translates with resistance and settles without Android stretch or stuck gaps.
- Change tabs and open/back out of secondary pages: scroll positions are retained.
- Select an authorizer by its label or indicator; exactly one remains selected,
  the shared home state updates, and selection survives restarting the activity.
- Verify notices, rows, back arrow and the last option at small screen sizes,
  large font scale and in dark mode. Verify opaque top bars when blur is disabled.

## Verification

Validated on the connected Redmi K40S, Android 14 / API 34, 1080x2400:

- `:app:assembleDebug check` passed; 11 core tests passed. Existing nonfatal
  lint/build warnings remain, including the already-known AboutLibraries plugin
  integration warning. No new third-party dependency was added.
- Installed the debug APK. Checked expanded/collapsed titles on home, config,
  records, settings, privileges and licenses.
- Captured held directional tilt and press tint; dragging cancels the click.
- Captured top/bottom overscroll on the short config list, and settled frames
  after release. Content translates; the top bar and bottom navigation stay put.
- Tapped the Shizuku indicator, verified a single checked row, then restored the
  original ROOT preference. The home state followed the selection. No batch
  mute operation or rule change was performed.
- Compared settings screenshots before entering licenses and after returning:
  the 1080x1900 content crop is identical (SSIM 1.0).
- Checked dark mode, 1.3x font scale and scrolling to the final authorizer option;
  restored light mode and default font scale. Rechecked the dark back arrow after
  fixing Material content-color propagation inside the miuix top bar.
- Checked the opaque top-bar fallback with blur disabled, then restored blur.

Visual and animation acceptance on the reference K90/HyperOS device remains a
user acceptance step; K40S verification does not replace the multi-ROM matrix.

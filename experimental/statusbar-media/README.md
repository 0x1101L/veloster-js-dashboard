# Experimental OEM bar and fullscreen music

Private exact-input builder for the captured API17/800×480 SystemUI. This is not
part of the public dashboard release and does not install or modify a device.
Supply your own captured OEM inputs and Android build tools. Do not redistribute
the OEM APK/ODEX or generated APK in this repository.

- `StatusBarMedia`: fixed back/home/menu slots, media artwork/title/artist/time,
  existing clock/date and connection icons. OEM key handlers remain intact.
- `FullscreenMusic`: a separate non-touchable, non-focusable music window only
  for `com.mnsoft.navi` while the OEM bar is hidden. A 248×54 dp compact card
  expands to 300×105 dp for the shared media model's five-second track notice.
  It shares the existing media subscription and decoded artwork; it creates no
  second Bluetooth nonce. Unknown OEM state, rear camera, screen off, lock,
  missing consent, foreground popup, other foreground app or empty title hides it.
  Rear-view/screen broadcasts remove it immediately; other state is polled every
  500 ms. Actual vehicle behavior is unverified.
- The optional Bluetooth `REQUEST_STATE_V2` requires the matching experimental
  publisher. An older publisher ignores it instead of replacing the dashboard
  subscription. Artwork transport success is separate from rendering support.

`build.py --help` lists required local tools and exact original inputs. Version20
keeps the bar at the top and adds fullscreen music. `--bottom-bar --version-code 21`
also changes the original status-bar window gravity to BOTTOM.

**The bottom variant alone must not be installed against the original framework.**
It requires the matching `PhoneWindowManager` bottom-inset patch, which is only an
offline candidate. Boot-classpath ODEX dependency invalidation and recovery are
not yet verified. Changing gravity alone leaves a top gap and covers bottom app
content. The builder deliberately does not perform framework installation.

Both variants preserve original resources byte-for-byte and compare all original
DEX classes, fields and methods. Only the media view creation hook and, for the
bottom variant, the window-gravity method may change. Manifest changes are limited
to versionCode and the two required permission uses. Signing is a separate step.

`tests/FullscreenMusicTest.java` runs on a host JVM with `MediaState.java` and
`FullscreenMusicPolicy.java`. It checks foreground/fullscreen/camera/lock/popup
gates and real media-model notice expiry/time-only updates. Passing these checks
does not establish Android window, touch, boot or camera runtime behavior.

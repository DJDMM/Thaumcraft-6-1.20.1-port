# Isolated Windows client QA

From the project, run `scripts/run_theory_smoke_isolated.ps1`. The wrapper prepares
the `-PtheoryCompleteSmokeTest` JavaExec task without executing its game launch
action, writes a Java17 argument file, and launches the final JVM on a uniquely
named non-input Win32 desktop. The desktop is chosen in `CreateProcessW` before
Minecraft creates a window. The helper retains its desktop handle until that
exact JVM exits. No desktop switch, window activation or global input is used.

The wrapper waits for completion and requires a successful preparation build,
the capture success marker, direct JVM exit0, a positive count of windows
owned by that JVM on the named desktop, and the client smoke/renderer-audit
success markers without a smoke failure marker. Logs alone do not prove visual QA;
review the smoke screenshots and assertions separately.

`-PrepareOnly` performs preparation/export without launching Minecraft. Optional
`-RunTag` selects a unique evidence prefix; existing evidence paths are rejected.
`-JavaHome` defaults to the existing Adoptium Java17 installation. The actual
client executable comes from Gradle's selected Java launcher and is checked as
Java17. The init script is pinned to Gradle8.8/ForgeGradle6.0.54; unexpected APIs
fail before the replaced run action can execute.

All capture JSON, argument files, environment overrides, preparation/client logs,
desktop status and run summary files go to the project's ignored `validation/`
directory with a fresh run prefix. Only environment differences from the Gradle
process's inherited environment are exported; inherited values are not dumped.
Differences are applied to the child environment block without changing the
caller's environment. The Java argument-file generator quotes every original
argument, escapes backslashes/quotes, writes no BOM and rejects non-ASCII
arguments pending local Java17 code-page verification.

Only `run/theory-complete-smoke/options.txt` receives these fixture settings:

- `soundCategory_master:0.0`
- `fullscreen:false`
- `maxFps:30`

Other options are preserved. Other run directories and manual gameplay saves
are not inspected or modified. The helper uses `NUL` standard input and a file
for stdout/stderr. `EnumDesktopWindows` and `GetWindowThreadProcessId` provide
read-only proof of window placement; they never activate or move a window.

This Win32 desktop shares CPU, RAM and GPU with the user's game. It is not a
Task View virtual desktop, a VM or a security sandbox. If GLFW/WGL initialization
fails, inspect the log and stop; switching desktops is not a fallback. Do not
use Computer Use inputs because they activate their target. The theory smoke
hooks supply automated interaction and screenshot capture instead.

The three helpers are intentionally separated:

- `capture-javaexec.init.gradle`: ForgeGradle preparation and final command dump.
- `New-JavaArgumentFile.ps1`: safe Java argument-file and environment delta export.
- `Start-IsolatedJava.ps1`: native named-desktop launch and exact-JVM lifetime.

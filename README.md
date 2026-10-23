# Compukters: Propulsion

Independent standard Liquid Thruster, Creative Thruster and Creative Vector Thruster control for Minecraft 1.21.1.

Installation, upstream versions, Guest Kotlin examples, device lifetime and development commands are maintained in the
[canonical Wiki guide](https://certifiedbadideas.github.io/Compukters/PROPULSION/).

See [addon versioning](https://certifiedbadideas.github.io/Compukters/ADDON-DEVELOPMENT/#first-party-addon-versions)
for the independent `x.y` API/update version and Compukters target line in production JAR names. For combined
Create/Sable/Propulsion and Aeronautics development, use the
[all-addon stand](https://certifiedbadideas.github.io/Compukters/DEV-STAND/).


## Build dependencies

Local co-development explicitly uses `compuktersDependencyMode=workspace`. In the pinned main workspace,
run `./gradlew stageAddonDevelopmentDependencies` in Compukters first, then build this addon with `./gradlew build`.
For a separate checkout, pass `-PcompuktersWorkspace=/path/to/Compukters`.

To build without the main sources, use `./gradlew build -PcompuktersDependencyMode=release`.
This downloads the exact public GitHub SDK selected by `compuktersRelease`, verifies its SHA256 and uses
its local file repository. No GitHub token or external Compukters Maven service is needed.
An unpacked SDK can be supplied with `-PcompuktersDependencyMode=release -PcompuktersSdkDirectory=/path/to/sdk`.
The downloadable SDK is available starting with Compukters 0.5.1.
There is no automatic fallback between source and released dependencies.

Only pushed version tags publish this addon. Its release contains the production JAR, named development JAR,
Guest API bundle and SHA256 inventory. Ordinary commits produce no GitHub Releases.

Standalone `runGameTestServer` verifies Propulsion devices and Guest records. The snapshot-to-nozzle latency
probes additionally require the Compukters Sable addon (not only upstream Sable), and register when it is loaded,
as in the all-addon development stand. Standalone fixtures use the current runtime's Ctrl+C cancellation and
check competing ownership at peripheral lookup.

## Standard liquid engines (API 3.1)

`propulsion.thrusters.LiquidThruster` supports independent ordinary single-block liquid thrusters.
Use the normal wired/named peripheral network and `LiquidThruster.named("engine")` or `all()`.
`mount()` reports the construction, offset from the computer and neutral force axis. `state()`
reports server tick, native throttle/startup, current and nominal thrust (kN), fuel ID and finite
stock/capacity (mB). Nominal thrust excludes startup, throttle and obstruction; current native thrust
is authoritative. No observation advances the engine or consumes fuel.

`setThrottle(0.0..1.0)` acquires exclusive program control. Zero clears even tiny digital commands.
Propulsion owns fuel consumption, startup, obstruction and force application. Dry engines remain
observable, with zero usable nominal thrust; the Guest program decides how to handle depletion.
`close()`, cancellation, disconnect, replacement and unload release the lease and restore native
redstone. Native redstone may therefore power an engine after program control ends. Program leases
are not serialized into ship/chunk payloads; compatible VM hibernation checkpoints restore them
through the normal peripheral identity checks. Solid, ion, vector-liquid and multiblock engines are
outside this API. Existing Creative operation IDs remain unchanged.

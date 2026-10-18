# Porting notes: Create Fly: Crafts & Additions 26.2 (Fabric) → NeoForge 26.3

Written 2026-10-18. Checked against Minecraft 26.3, NeoForge 26.3.0.89, Java 25.0.1, and Zoompack 5's NeoForge port of
Create Fly (`create-fly-neoforge-6.0.9+neoforge-26.3`, mod id `create`, package `com.zurrtum.create`).

Branch `zp5/26.3` is a Zoompack 5 fork of [chaevsfe/createaddition](https://github.com/chaevsfe/createaddition). It starts
from upstream branch `fabric-26.2`, commit `0da7933d`, which is release `1.7.2.1+fly-mc26.2`. That port is itself
based on [mrh0/createaddition](https://github.com/mrh0/createaddition) 1.7.0. The code is MIT-licensed, and the sounds
come from pixabay.com and freesound.org (CC0); see `LICENSE` and `NOTICE`.

- **Mod id:** `createaddition`, the same as upstream, so zp5-tech's `@createaddition` unlock entries match it.
- **Jar:** `build/libs/createaddition-neoforge-26.3-1.7.2.1-zp5.1.jar`. The version is `<mc>-<upstream>-<port build>`.

## Why a native port and not Sinytra Connector

- **No Fabric Create on 26.3.** Upstream is a Fabric mod built against Fabric Create Fly 26.2, and Create Fly has no
  Fabric 26.3 build. Under Connector, the jar would have to link against ZP5's NeoForge Create port. That port
  replaced Create's Fabric transfer API with NeoForge capabilities and changed many signatures for 26.3.
- **Energy would be stranded.** Upstream stores and moves energy with Team Reborn Energy, a Fabric API, bundled
  jar-in-jar. Under Connector nothing bridges it to NeoForge's `Capabilities.Energy`. Pipez energy pipes, AE2 energy
  acceptors and every other NeoForge energy mod in the pack would not see the alternator, accumulator or motors.
- **Data and API breaks.** The 26.2 data (loot tables, advancements) no longer loads on 26.3, and the code uses
  26.2-only API: `FuelValues`, block codecs, `RedStoneWireBlock`, `drop` without a `Prediction`. Both need source
  changes either way.

The Create Fly port already showed that Connector needs more than trivial fixes for Create-based Fabric mods (see
`create-fly-native-vs-connector-26.3.md` in the shared notes). This mod has 178 classes and moderate Fabric API use,
so a native port was the smaller job.

## How the port is organised

It follows ZP5's Create Fly port, `mods/create-fly-neoforge` in the ZP5 repo:
- **New code lives in new packages.** `com.mrh0.createaddition.neoforge` (main and client) holds the entrypoints, the
  fluid types and the GameTests. `neoforge.energy` holds the energy layer.
- **Upstream files are edited in place only where they must be.** Larger edits carry a `// NeoForge:` or `// 26.3:`
  comment.
- **Fabric-only files are excluded in `build.gradle`, not deleted:**
  - the Create REI Viewer plugins (`compat/viewer`). The EMI plugin in `compat/emi` replaces them (see
    [Recipe viewer (EMI)](#recipe-viewer-emi))
  - Fabric recipe sync (`compat/fabric`)
  - the configuration-phase handshake (`CAHandshake`, `HandshakePayload`, `CAClientHandshake`)
  - `fabric.mod.json`

### Build

ModDevGradle 2.0.148, Gradle 9.2.1 and Java 25, the same as the Create port. The split `main`/`client` source sets go
into one mod and one jar.

The build compiles against the Create port's **jar**, which it does not fetch. Copy it and its compile-time view of
vanilla into `libs/` (gitignored):

| File | From |
|---|---|
| `libs/create-fly-neoforge-6.0.9+neoforge-26.3.jar` | `mods/create-fly-neoforge/build/libs/` in ZP5, or the jar in the pack |
| `libs/create-accesstransformer.cfg` | `META-INF/accesstransformer.cfg` inside that jar |
| `libs/create-interfaces.json` | `mods/create-fly-neoforge/interfaces.json` in ZP5 |

Create widens some vanilla members with its ATs, for example `BlockBehaviour#getShape` and `#rotate`, which become
public. The build applies the same ATs, so the addon compiles against what it sees at runtime.

```bash
./gradlew build
```

```bash
./gradlew runGameTestServer
```

`-PwithEmi` adds SEMI to the dev runs (see [Recipe viewer (EMI)](#recipe-viewer-emi)), and `-PwithCC` adds CC: Tweaked.

On a machine where `JAVA_HOME` is unset, point it at JDK 25 first (`export JAVA_HOME=~/.jdks/openjdk-25.0.1`).

### Generated and converted files

| Script | What it does |
|---|---|
| `tools/fabric_conditions_to_neoforge.py src/main/resources/data` | Rewrites `fabric:load_conditions` (`all_mods_loaded`, `tags_populated`, ...) to `neoforge:conditions`. Used on 11 files |
| `tools/data_26_3.py src/main/resources/data src/main/resources/assets` | The Create port's 26.2 → 26.3 data converter: loot tables, advancements, a smoking recipe, and model `shade` |
| `tools/check_recipe_refs.py <26.3 MC jar>` | Lists advancements that point at recipes which don't exist. 26.3 makes these fatal. Reports 0 |
| `tools/check_mixins.py <MC jar> <NeoForge universal> <Create jar>` | Static mixin check (the Create port's tool, pointed at `createaddition.mixins.json`). Reports 0 problems |

Re-run the first two after a rebase. Both are idempotent.

## Loader and entrypoints

| Fabric | NeoForge |
|---|---|
| `main` entrypoint `CreateAddition#onInitialize` | `@Mod neoforge.CreateAdditionNeoForge`. Construction runs `CreateAddition.onInitialize()` (config, listeners). Content registers from the first `RegisterEvent` (`CreateAddition.registerContent()`), because NeoForge freezes vanilla registries between bootstrap and its register events |
| `create_plugin` entrypoint | `META-INF/services/com.zurrtum.create.api.registry.CreateRegisterPlugin`. Blocks and fluids still register during vanilla bootstrap through Create's hook |
| `client` entrypoint | `@Mod(dist = CLIENT) neoforge.CreateAdditionNeoForgeClient`. Upstream's client init uses the mod's items and block entity types, so it runs at the first `RegisterEvent` too, after the content |
| `FabricLoader#isModLoaded` | Create port's `com.zurrtum.create.neoforge.Platform` |
| `fabric.mod.json` | `src/main/templates/META-INF/neoforge.mods.toml` (requires `create` `[6.0.9,)`; `computercraft` optional) |

## Fabric API replacements

| Fabric / Team Reborn Energy | NeoForge 26.3 |
|---|---|
| TR Energy `EnergyStorage` (long, transactional) | `neoforge.energy.EnergyStorage`: the same long methods (`insert`, `extract`, `getAmount`, `getCapacity`, `supportsInsertion/Extraction`), extending NeoForge's `EnergyHandler`. The int overloads saturate |
| `EnergyStorage.SIDED.registerForBlockEntities` | `RegisterCapabilitiesEvent`, `Capabilities.Energy.BLOCK`, for the same 11 block entity types (`CATransfer`) |
| `EnergyStorage.SIDED.find` / `BlockApiCache` | `CAEnergy.find` / `EnergyLookupCache`: `Level#getCapability` on every lookup, the way Fabric's cache re-asks the provider. Other mods' handlers are wrapped in `EnergyHandlerStorage` |
| `EnergyStorageUtil.move` | `CAEnergy.move`, the same simulate-then-move logic |
| TR `SimpleEnergyItem` (capacitor) | `Capabilities.Energy.ITEM` with NeoForge's `ItemAccessEnergyHandler`, stored in a new `createaddition:energy` component (`CADataComponents`) |
| `EnergyStorage.ITEM.find(stack, ContainerItemContext)` | `CAEnergy.find(stack, ItemAccess)`: `ItemAccess.forStack` on the tesla coil's belt stack, `ItemAccess.forPlayerInteraction` for the amulet |
| Fabric `Transaction` / `TransactionContext` | NeoForge `transfer.transaction.*` (`openOuter` → `openRoot`) |
| `SnapshotParticipant` | `SnapshotJournal` (`readSnapshot` → `revertToSnapshot`, `onFinalCommit` → `onRootCommit`) |
| `TransactionContext#addOuterCloseCallback` (portable energy interface keep-alive) | A small `SnapshotJournal` whose `onRootCommit` calls `keepAlive()` |
| `ItemStorage` / `FluidStorage` for the rolling mill / liquid blaze burner | `Capabilities.Item.BLOCK` / `Fluid.BLOCK` through the Create port's `neoforge.transfer.Cached*InventoryBehaviour` |
| Payload registry + `ServerPlayNetworking` / `ClientPlayNetworking` | `RegisterPayloadHandlersEvent` (`CANetwork`), `PacketDistributor`, `ClientPacketDistributor`. Client handlers come from `RegisterClientPayloadHandlersEvent` |
| Configuration-phase handshake (`CAHandshake`) | The payloads are registered `versioned("<protocol>-<mod version>")`, so NeoForge refuses a client with a different Crafts & Additions version |
| `ServerTickEvents`, `ServerChunkEvents`, `ServerLevelEvents`, `UseBlockCallback` | `LevelTickEvent.Pre`, `ServerTickEvent.Pre`, `ChunkEvent.Load`, `LevelEvent.Unload`, `PlayerInteractEvent.RightClickBlock` (`GameEvents`) |
| `CommandRegistrationCallback` | `RegisterCommandsEvent` |
| `ClientTickEvents.END_CLIENT_TICK`, `ResourceManagerHelper` | `ClientTickEvent.Post`, `AddClientReloadListenersEvent` |
| `FabricCreativeModeTab.builder()` | `CreativeModeTab.builder()` |
| `ConventionalItemTags.SHEAR_TOOLS` | `Tags.Items.TOOLS_SHEAR` |
| `FuelValueEvents` (biomass pellet and pallet) | 26.3's `cooking_fuel` component, set in `ModifyDefaultComponentsEvent` (the same as the Create port) |
| CC: Tweaked `PeripheralLookup` | `PeripheralCapability.get()` in `RegisterCapabilitiesEvent` |
| Fluids without a `FluidType` | `neoforge.CAFluidTypes`, with seed oil and bioethanol properties from upstream's NeoForge 1.21.1 branch. Create's `FlowableFluid#getFluidType` only knows Create's own fluids, so `CAFluidEntry` overrides it |
| `fabric:load_conditions` | `neoforge:conditions` (`tools/fabric_conditions_to_neoforge.py`) |

## Minecraft 26.2 → 26.3 changes handled

- **Block codecs.** `simpleCodec` and the `codec()` overrides are gone (`CreativeEnergyBlock`, `LiquidBlazeBurnerBlock`).
- **Renames and new parameters.**
  - `RedStoneWireBlock` is now `RedstoneWireBlock`.
  - `Player#drop` and `Inventory#placeItemBackInInventory` take a `Prediction` (`SERVER_ONLY`).
  - `PoseStack#mulPose(Quaternionf)` is now `rotate`.
- **Fuel.** `Level#fuelValues()` is gone. The liquid blaze burner reads burn time with the Create port's `FuelHelper`.
- **Data.** Loot tables and advancements were converted to the 26.3 formats (`tools/data_26_3.py`).

## Mixins retargeted to NeoForge's patched code

NeoForge patches `FireBlock`, so `FireBlockMixin` follows it:
- `checkBurnOut` gained a `Direction face` parameter and reads `BlockState#getFlammability(level, pos, face)` instead
  of `getBurnOdds`.
- `tick`'s infiniburn check is `BlockState#isFireSource(level, pos, UP)` instead of `is(HolderSet)`.

`canBurn` and `getIgniteOdds` still match. NeoForge's fire code doesn't call `canBurn` everywhere vanilla did, so
igniting fluids (the `createaddition:ignites` fluid tag) may light fire in fewer places. This is untested in game. The
other four mixins (`BaseFireBlockMixin`, `FireBlockInvoker`, `FlowingFluidMixin`, `PumpBlockEntityMixin`) needed no
change.

## Recipe viewer (EMI)

ZP5's recipe viewer is SEMI, the user's Seasonal EMI port for 26.3 ([Pandaismyname1/SEMI](https://github.com/Pandaismyname1/SEMI),
branch `26.3`). It keeps EMI's API and the `emi` mod id. Upstream only integrates with chaevsfe's Fabric Create REI
Viewer, so `compat/emi` (client source set) is new code, laid out after upstream's NeoForge 1.21.1 `compat/jei`
categories (`git show origin/1.21.1:src/main/java/com/mrh0/createaddition/compat/jei/...`).

- **Build.** `clientCompileOnly dev.emi:semi-neoforge:<emi_version>:api` from SEMI's maven
  (`https://raw.githubusercontent.com/Pandaismyname1/SEMI/maven`, an `exclusiveContent` repository for `dev.emi`).
  `emi_version` is `1.1.24-SNAPSHOT+26.3`, the newest 26.3 build in its `maven-metadata.xml`. `-PwithEmi` puts the
  full jar in the dev runs. `neoforge.mods.toml` declares `emi` as an optional client dependency
  (`emi_version_range`, `[1.1.24-SNAPSHOT,)`).
- **Loading.** `CreateAdditionEmiPlugin` is an `@EmiEntrypoint`. Only EMI loads it, so without EMI no EMI class is
  touched. It reads the recipes from `EmiRegistry#getRecipeMap`, which the server fills through
  `CreateAdditionNeoForge#sendRecipes`.

| Category | Workstations | Page |
|---|---|---|
| `createaddition:charging` | Tesla coil | Input, tesla coil above the item, output. The energy is shown as `1.0M⚡`; its tooltip gives the exact FE and the max charge rate in FE/t. Create's sequenced-assembly step recipes (`create:sequenced_assembly_*`) are left out, as upstream does |
| `createaddition:rolling` | Rolling mill | Input, rolling mill, output (with chance, if any) |
| `createaddition:liquid_burning` | Blaze burner, straw | Straw (catalyst), the fluid's buckets, and the fluid in mB; heat bar, heated or superheated blaze burner, and burn time per bucket (tooltip: exact ticks) |

Create Fly counts fluids in droplets (81 000 per bucket). EMI on NeoForge counts millibuckets, so fluid amounts are
divided by 81. The machines are drawn with the Create port's GUI renderers: `GuiGameElement` for the tesla coil and
rolling mill (one cached builder per machine), and `BasinBlazeBurnerRenderState` for the blaze burner, the same one
Create's own basin categories use. Category titles use the existing `createaddition.recipe.*` keys. Three English
keys are new: `createaddition.recipe.charging.energy`, `.charging.max_charge_rate` and `.liquid_burning.ticks`.

Not ported from upstream's viewer plugins:
- **Diamond grit sandpaper** as a workstation for Create's sandpaper polishing, and **charging as a sequenced-assembly
  step.** Both need Create's own EMI categories, and the Create port has no EMI plugin yet.
- **Animation.** The rolling mill is drawn without upstream's turning shafts, and nothing animates.

## Verified

On 2026-10-18 (Windows, JDK 25.0.1):
- **Build.** `./gradlew build` passes.
- **EMI plugin.** `./gradlew build` compiles `compat/emi` against SEMI `1.1.24-SNAPSHOT+26.3`'s API jar.
  `./gradlew runGameTestServer -PwithEmi` loads SEMI next to the mod and still passes 5/5. The pages themselves were
  not seen in a client (see below).
- **Static checks.** `tools/check_mixins.py` reports 0 problems against patched MC 26.3, NeoForge 26.3.0.89 and the
  Create port. `tools/data_26_3.py` finds 0 legacy constructs, and `tools/check_recipe_refs.py` 0 dangling references.
- **Game-test server.** `./gradlew runGameTestServer` loads Create and Crafts & Additions and creates the world. The
  log has no data-load errors or warnings from either mod. All 5 required tests pass:

| Test | What it shows |
|---|---|
| `createaddition:alternator_generates_fe` | A creative motor at 64 RPM turns an alternator, which makes 90 FE/t and pushes it into a modular accumulator. A stand-in for another mod's pipe pulls 100 FE out of the accumulator through `Capabilities.Energy.BLOCK` (tick 10: 800 FE stored) |
| `createaddition:electric_motor_runs_on_fe` | FE inserted through `Capabilities.Energy.BLOCK`, the way Pipez would, starts an electric motor. It and the shaft in front turn at 32 RPM |
| `createaddition:rolling_mill_rolls_iron_rods` | An iron ingot inserted through `Capabilities.Item.BLOCK` becomes 2 iron rods, extracted the same way (tick 31). 22 blocks, 48 items and 15 block entity types are registered |
| `create:water_wheel_drives_press` | The Create port's own test still passes |

## Not verified, disabled or approximated

- **Client.** Not run yet: no title screen, renderers, Flywheel visuals, ponder scenes, sounds or goggle tooltips.
  The client code compiles against 26.3 and the Create port's client API, but launching a client needs the user's OK.
- **Recipe viewers.** The EMI plugin for SEMI ([Recipe viewer (EMI)](#recipe-viewer-emi)) compiles and loads, but
  no client has opened its pages yet. Unchecked: the three pages in EMI, the placement and angle of the tesla coil
  and rolling mill blocks (`CAEmiWidgets`), the blaze burner render, and the fluid amounts in mB. JEI and REI are
  not supported.
- **ComputerCraft.** The peripherals compile against CC: Tweaked 1.120.3 for NeoForge, but were not loaded with CC in a
  test run. Use `-PwithCC` to add it to the dev runs.
- **Upstream handshake.** It is replaced by NeoForge's payload version check. That check compares versions, not
  upstream's block-state fingerprint.
- **Untested in game:**
  - wires and connectors (`WireGraph`), the tesla coil, the electric pump and the servo motor
  - the portable energy interface on contraptions, and the energy schedule condition on trains
  - the liquid blaze burner and fluid burning, and the capacitor item's energy
- **First config load.** The first boot logs a `NoSuchFileException` stack trace from the Create port's catnip config
  `Builder` before it writes `config/createaddition-common.json`. Later boots don't. Create's own config does the same.

## Tracking upstream

1. `git fetch origin`, then rebase `zp5/26.3` onto `origin/fabric-26.2`, or onto a `fabric-26.3` branch once one
   exists.
2. Conflicts should stay in lines marked `// NeoForge:` or `// 26.3:`, plus `CATransfer`, `CANetwork`,
   `CreateAddition`, `CreateAdditionClient` and the energy classes that use `neoforge.energy`.
3. Re-run `tools/fabric_conditions_to_neoforge.py` and `tools/data_26_3.py`, then `./gradlew build`, then
   `tools/check_mixins.py`, then `runGameTestServer`.
4. When the Create port changes, copy its new jar (and AT file) into `libs/`, and bump `create_jar` in
   `gradle.properties` if the name changed.

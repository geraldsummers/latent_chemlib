# Latent ChemLib

Latent ChemLib is a Forge `1.20.1` mod for file-configured ChemLib matter
simulation. It provides chemical cloud blocks, containment machinery, high
energy reaction math, gas item escape behavior, and heavy-element neutron
economy hooks for expert modpacks.

The design goal is emergent behavior from numeric traits and curve intercepts,
not hard-coded per-element special cases. Pack authors can tune chemical
identity through datapack JSON while the mod derives fallback traits from
ChemLib registry data.

## Current Features

- Transparent, walkable `latent_chemlib:chemical_cloud` block entity carrying
  chemical id, mass, density, temperature, charge, and energy.
- Cloud diffusion, cooling, dissipation, and heat-driven block erosion.
- Gas item escape handling for item entities and player inventories.
- Heavy element neutron flux simulation for ChemLib element stacks.
- Create-style machine blocks:
  - `latent_chemlib:gas_capture`
  - `latent_chemlib:gas_tank`
  - `latent_chemlib:gas_reaction_chamber`
  - `latent_chemlib:gas_release`
- File-based datapack reload support for:
  - `data/latent_chemlib/chemical_traits/*.json`
  - `data/latent_chemlib/scheduler_profiles/default.json`
- Server tick budget scheduler for cloud and neutron workloads.
- Unit tests for numeric curves and emergent simulation math.

## Tech Stack

- Minecraft `1.20.1`
- Forge `47.4.10`
- Java `17`
- Kotlin for Forge `4.11.0`
- Create `6.0.8`
- Create: New Age `1.1.7f`
- ChemLib `2.0.19`
- Alchemistry `2.3.4`
- EMI and JEI as optional client integrations

## Development

Common tasks:

```bash
./gradlew test
./gradlew jacocoTestCoverageVerification
./gradlew runGameTestServer
./gradlew build
./gradlew verifyAll
./gradlew runClient
./gradlew runServer
```

The JVM unit coverage gate is intentionally focused on the pure simulation and
configuration core. Forge event handlers and block entities are integration
boundaries. The bundled Forge GameTests cover the current in-world block entity
surfaces: cloud state, machine block entity creation, capture, release, and
reaction chamber agitation.

GameTests are wired through ForgeGradle and can be included with:

```bash
./gradlew verifyAll -PwithGameTests=true
```

## Pack Configuration

Pack-side datapack examples are expected under:

```text
data/latent_chemlib/chemical_traits/
data/latent_chemlib/scheduler_profiles/
```

Traits expose numeric levers such as atomic number, atomic mass, base state,
phase energy, volatility, thermal conductivity, heat capacity, instability,
absorption, neutron yield, and curve definitions. Scheduler profiles cap per
dimension and per second simulation work so large packs can tune the system
without recompiling the mod.

## Notes

- Mod metadata is sourced from `gradle.properties`.
- The mod currently provides the simulation foundation and block/item registry.
  Pack-specific recipes, progression gates, and datapack tuning live in the
  consuming pack.

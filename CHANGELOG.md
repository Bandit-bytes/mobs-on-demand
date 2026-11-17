# Changelog

## 4.0.0+26.3

A full rework: the magnet-style attractors are gone, replaced by a mob crate and delivery system.

### Added
- **Mob Crate**: right-click a passive mob to pack it up (name, variant, age, villager profession and trades are kept), right-click a block to release it. Place crates as blocks (sneak with a full crate); sneak and look at a placed crate to see what's inside.
- **Ranch Order Board**: a 2x3 notice board where you order animals and villagers for emeralds and feed. Orders queue up and arrive in Mob Crates, even while the area is unloaded.
- Order boards generate in villages (plains, desert, savanna, snowy, taiga), with a per-village cap and blacklist.
- Data-driven shop (`data/<namespace>/mobs_on_demand/shop/*.json`) and capturable mob list (`#mobs_on_demand:capturable`), both reloadable with `/reload`.
- FTB Chunks claim protection (when installed and updated for this Minecraft version).
- Config file `config/mobs_on_demand.json`.

### Removed
- Chicken, Cow, Pig, Sheep and Villager Attractors. Existing attractor items disappear from worlds when updating.

### Changed
- Updated to Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.2 and Java 25.

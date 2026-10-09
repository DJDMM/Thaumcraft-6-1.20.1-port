# ORE, Crystal Farmer and Vis Battery: BETA26 progression and recipes

Authority: the pinned `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
The reference checkout at commit `954022bb777b7546281fb36df8522f0ba6b43f81`
is navigation support. The original `basics.json` and `auromancy.json` determine
the stages/parents/payments; released bytecode determines scanner registrations,
stage advancement/XP and runtime recipe constructors.

## Scanner and hidden root

`ConfigResearch` registers `ScanBlock("ORE", ...)` for exactly nine original
blocks: Amber Ore, Cinnabar Ore and the seven Air/Fire/Water/Earth/Order/Entropy/
Flux crystal clusters. It then registers the distinct `!OREAMBER`,
`!ORECINNABAR` and `!ORECRYSTAL` families. The `ScanBlock` constructor also
registers `ScanItem` for every corresponding block item. Held and dropped block
items therefore qualify, as does a real server-ray block scan. Loose
`crystal_essence`, the compatibility `vis_crystal_*` items, Quartz Ore, amber
and quicksilver do not belong to those families.

The first eligible actual scan completes the canonical one-stage hidden root
ORE to stored stage2 and pays5 XP. It independently records only its own
addendum. ORE has no parents and no siblings; scanning does not complete
CRYSTALFARMER or VISBATTERY. Repeated scans produce no further ORE XP.
Read-only HUD snapshots run the same predicates but do not mutate research.

The authoritative book rejects an arbitrary undiscovered ORE request, matching
the existing hidden pearl/firebat protection. This is server hardening of the
hidden GUI acquisition path; original `ResearchManager` itself permits a
direct internal call to the parentless root. It is not an additional gameplay
stage or a new ORE cost.

## Canonical stage contracts

| Entry | Required completed parents | Stage1 payment | Stored completion / XP |
| --- | --- | --- | --- |
| ORE | Actual eligible scan; no graph parents | None |2 /5 XP|
| CRYSTALFARMER | ORE, INFUSION, `!ORECRYSTAL` |16 raw Observation Auromancy +16 raw Observation Basics; one exact `crystal_essence` of each six primal aspects |3 /5 start +5 payment XP|
| VISBATTERY | RECHARGEPEDESTAL, CRYSTALFARMER |16 raw Observation Auromancy +16 raw Observation Artifice |3 /5 start +5 payment XP|

Both paid entries retain the original two chapters: their empty concluding
chapter advances directly into completion. They have no siblings or Warp.
An entered-but-uncompleted parent does not substitute for strict completion.
The Farmer payment searches main inventory only. Each crystal must contain the
complete original one-entry `Aspects` list with amount1; mixed aspects and
amount2 do not match. Unrelated extra root NBT is rejected by the existing
audited found-to-template comparison; offhand crystals do not pay. Earlier-port
primal `vis_crystal_*` IDs normalize to the corresponding original tagged crystal
for obtain payment, preserving the established compatibility adaptation. They
still do not substitute for the original ore-block scanner registrations.

The 0.26 implemented set is the preserved79 canonical records plus ORE,
CRYSTALFARMER and VISBATTERY: **82/148**. FLUX, FLUXRIFT, RIFTCLOSER,
UNLOCKELDRITCH, BASEELDRITCH, MATSTUDVOID and MIRRORESSENTIA stay unsupported.

## Seven cluster infusion recipes

`ConfigRecipes` bytecode registers each with the bare research key
`CRYSTALFARMER`, so actual entry into stage1 is the original recipe access gate;
completed research is not an invented additional crafting restriction.

Every recipe consumes the corresponding exact one-unit aspect crystal centrally,
one Wheat Seed and one Salis Mundus. Costs are10 of the corresponding aspect,
10 Vitreus and5 Vinculum. Output is one corresponding block item without
extra NBT. The six primal recipes have instability0; Flux has instability4.
All seven existing datapack definitions match the release and were retained.
The book's archived recipe definitions remain read-only display data.

All seven have ordinary ingredient paths. Six primal central crystals are
harvested from actual naturally generated clusters, or crystallized with the
existing BASEALCHEMY two-aspect/sliver crucible recipes. Flux does not need a
late rift or an invented taint acquisition unlock: ordinary Nether Wart has
Herba1/Vitium2/Alkimia3 in the pinned aspects. The existing
`vis_crystal_vitium` crucible recipe consumes2 Vitium and one Quartz Sliver to
produce the exact one-unit tagged central crystal. One vanilla Quartz crafts
nine slivers through the existing ordinary recipe. In a mixed wart crucible,
the first equal-cost native recipe crystallizes Alkimia and the next sliver
crystallizes Vitium; that actual path is covered explicitly by the new source
test. Nether Wart can also supply Vitium essentia for the Flux infusion.
Ordinary glass crafted from sand supplies Vitreus5, and an ordinary wooden
door supplies Vinculum5 through the established smelter/alembic production
path. Amber is another naturally available source of both aspects. Wheat seeds
are the infusion component, not the source of its Vinculum essentia.

Consequently the research/crafting census increases16->23 of56 and the
established ordinary-source census14->21. Remaining unsupported research
gates decrease40->33. These counts do not claim a finished survival playthrough
or complete late-world ecology.

## Vis Battery arcane recipe

Released `ConfigRecipes` bytecode at offsets9035..9181 registers:

- bare `VISBATTERY` research,50 vis;
- two of each Aer/Ignis/Aqua/Terra/Ordo/Perditio crystal:12 crystals total;
- exact3x3 `SSS / SRS / SSS` pattern;
- S is the original Arcane Stone Slab, R the Vis Resonator;
- one `vis_battery` block item, without stored charge NBT.

The native bench recipe uses those original items and prices. It accepts the
started bare research gate and retains existing atomic insufficient-aura or
insufficient-crystal rejection. Battery runtime storage/random-tick/redstone
behavior has its separate audit and tests.

## Verification boundaries

`CrystalFarmerProgressionGameTests` adds11 tests covering all nine physical
block and item scan families plus dropped-item acquisition, no-hover/replay
reward, negative families, original hidden root behavior, strict parents,
exact main-inventory/NBT/knowledge payments, legacy primal normalization,
replay/reload, native50vis/12crystal
bench crafting, all seven original plans and actual paid matrix outputs,
read-only book prices, and ordinary Nether-Wart-to-Vitium crystallization.

The matrix test starts with actually paid Farmer; raw essentia, preceding
INFUSION, altar and supported candle pairs are explicit fixtures. Sixteen real
color candle pairs preserve the Flux recipe's original instability4 while
providing stability, rather than weakening that recipe. Finished clusters or
finished batteries are not injected as output substitutes. Explicit raw
observation balances isolate stage-payment costs after actual scan rewards.

Compilation succeeded during implementation. Full server/client results are
recorded by the root validation workflow after integration; this audit alone
does not certify a complete run.

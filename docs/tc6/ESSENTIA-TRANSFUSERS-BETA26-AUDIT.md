# TC6 BETA26 essentia transfusers

This contract uses Thaumcraft 6.1.BETA26, not TC4/TC5. The pinned
`work/Thaumcraft-1.12.2-6.1.BETA26.jar` SHA-256 is
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Reference sources are at commit `954022bb777b7546281fb36df8522f0ba6b43f81`;
the JAR bytecode wins over decompiler guesses.

The inspected original class hashes are:

| Original JAR class | SHA-256 |
| --- | --- |
| `common/tiles/essentia/TileEssentiaInput.class` | `cfcd37139ef07b908c4fce2bb6e79be8bd803fb71fcab35d6819dfcc1d5abae5` |
| `common/tiles/essentia/TileEssentiaOutput.class` | `15edf117a82e3c33cbfbee707d6dbd170a3bd1f6c03135baa9234f75b09745cb` |
| `common/blocks/essentia/BlockEssentiaTransport.class` | `8f05b3fcc0ef598f02b6d942a11b001947ba7999e98e52bc2a108468be526083` |
| `common/lib/events/EssentiaHandler.class` | `6eeec4d53fae4fe18b8d16306e61e942f1f3b1e27f6ce4b5de8cdd02ba362614` |

## IDs, study and manufacture

- `thaumcraft:essentia_input` is the **Filling Essentia Transfuser**:
  physical rear source to airborne jar. `essentia_output` is the
  **Emptying Essentia Transfuser**: airborne jar to physical rear demand.
- Original save IDs are `thaumcraft:TileEssentiaInput` and
  `thaumcraft:TileEssentiaOutput`. This port registers a shared
  `thaumcraft:essentia_transfuser` BE type accepting both unchanged block IDs.
- Canonical `ESSENTIATRANSPORT` has completed parents `THAUMATORIUM` and
  `INFUSION`, category ALCHEMY, position (12,-2). First-stage payment is one
  Observation Alchemy and one Observation Artifice, or **16 raw units each**.
  The second stage is the empty concluding recipe chapter; full completion is
  stored at stage3. Start and paid completion each award the ordinary5 XP,
  total10 across the two accepted requests. No invented theory payment,
  item obtain, scan or Eldritch prerequisite is added.
- `thaumcraft:EssentiaTransportIn` and `EssentiaTransportOut` cost100 vis
  plus Aer1 and Aqua1 each. Both are 3x3 arcane recipes:
  `"   "/"BQB"/"IGI"`, B=brass plate, I=iron plate,
  G=Alchemical Construct; Q=dispenser for In and hopper for Out.
  Archived lowercase book IDs are `essentiatransportin/out`; the port's
  gameplay JSON IDs are `arcane/essentia_input` and `arcane/essentia_output`.
  Keep the original empty top row and shared whole-ingredient crystal payment.
- The archived book recipe records already contain both recipes. Plates,
  dispenser/hopper and the TUBES Alchemical Construct recipe are already
  ordinary-source ingredients; no late-world material is needed. One supported
  canonical study raises78 to79, without opening MATSTUDVOID, BASEELDRITCH,
  mirrors or changing the16/56 accessible infusion research paths.

## Physical block and public transport API

Placement uses the **clicked face**, not player pitch, horizontal facing,
or sneak inversion. Front direction is FACING; only its opposite rear face
connects. Original inherited device rotation cycles
DOWN, UP, NORTH, SOUTH, WEST, EAST. Both constructors discard their
`withProperty(UP)` result, leaving the original metadata0/DOWN base state.
Existing catalogue saves retain their explicitly stored direction. Modern
structure rotation/mirroring preserves the front and old six-state names.

Original body boxes in sixteenths are:

| Front | Bounding/collision box |
| --- | --- |
| DOWN | (4,8,4) to (12,16,12) |
| UP | (4,0,4) to (12,8,12) |
| NORTH | (4,4,8) to (12,12,16) |
| SOUTH | (4,4,0) to (12,12,8) |
| WEST | (8,4,4) to (16,12,12) |
| EAST | (0,4,4) to (8,12,12) |

Metal sound/material, hardness1, effective explosion resistance6 preserve
the original overridden `setResistance(10)` conversion. There is no correct-tool
harvest gate. Every support face is UNDEFINED/non-sturdy. They are not full or
opaque cubes; the existing original baked models and item parents remain in use.
Each broken device drops its ordinary item; neither device stores essence to
spill or carries an inventory to drop.

Filling accepts a rear input connection and exposes suction128, with no suction
type. Emptying accepts a rear output connection and exposes suction0, also
without a type. Both report minimum suction0, null essentia type and amount0
on every queried face. `setSuction` is inert; `takeEssentia` always returns0.
The original `addEssentia` reports its requested amount without storing
anything, even on other faces or with a null aspect. This unusual advisory API
is retained for positive amounts; nonpositive amounts are hardened to0.
No fictitious buffer, inventory or GUI is introduced.

## Actual five-tick transfer

Only the server increments the transient counter; every fifth tick attempts
at most one unit. There is **no redstone gate** in either pinned tile.
Facing/pipe ports are recomputed from the physical blockstate each time.
The source/demand peer is exactly front.opposite; its queried API face is front.

For Filling, the rear peer must connect and permit output, contain positive
essentia with a nonnull type, have suction below128, and minimum suction at most128.
Original order first inserts into an airborne destination then calls the rear
peer's one-unit take. The modern guarded manager instead reserves/validates a
real destination and performs a conserved native one-unit transaction:
source refusal cannot create jar contents. Device uses
`AirborneEssentiaManager.transferToSources(consumer, peer, aspect, front, front, 16, 5)`.

For Emptying, the rear peer must connect and permit input, have positive suction
and a **nonnull requested suction type**. It does not invent a type from an
untyped pipe. The original handler previews a stored airborne unit, then asks
the rear peer to accept1, then calls global confirmDrain. The port uses an
owner-scoped one-shot confirmation within
`transferFromSources(consumer, peer, requested, front, front, 16, 5)`.
A refused or full native target retains the source unit. Neither path makes a
payment through the device's public no-storage API.

The runtime manager admits exact audited native transport classes for paid
two-endpoint transactions. Unknown addon/callback subclasses fail closed;
the generic legacy handler's standalone source API remains separate.
This bounded compatibility limit is deliberate because arbitrary callbacks
do not supply an exact rollback contract.

## Airborne discovery and compatibility

Both devices use the same level-identity/consumer-position cache as the matrix,
not an independent jar search. Range16 is the original **33x33x16 forward box**:
two transverse coordinates -16 through+16, forward0 through15, origin excluded.
Same-plane lateral jars and corners are included; the exact forward16 plane
and rear plane are excluded. Walls, labels and suction do not replace squared
distance ordering. Stable ties retain the original aa/bb/cc traversal order.

Insertion first tries nonempty compatible sources in that distance order,
then deferred empty compatible sources. A full normal jar refuses;
a compatible void jar may consume excess according to its existing API.
Brace-blocked jars are skipped. Jar labels govern insertion, whereas draining
uses actual stored type. A stale early coordinate breaks that pass, preserving
the pinned behavior. Exhausted search invalidates the list and imposes the shared
10-second wall-clock delay; successful source lists have no TTL.
Refreshing clears the list without silently removing its delay.
The transfer scans only already-present FULL chunks and never loads/promotes one.
Essentia mirrors and cross-dimensional sources stay unsupported.

Only committed transfers emit their colored trail, using device position
and jar position, ext5. The existing server-routed native dust trail is an
explicit visual adaptation; this slice does not claim the original
`FXEssentiaStream` ribbon renderer. Matrix trails keep their altar-below target.

## Saves, migration and safeguards

The original count and storage state are not serialized; count restarts at0
on load. The port ignores forged count/Amount/Aspect fields and stores no essence.
Removal forgets the device's runtime cache. Server-thread identity, physical
consumer identity, already-loaded rear peer, positive typed transfers and
operation reentry are checked. Device callback reentry cannot increment its
counter or produce a second transfer.

Earlier catalogue versions saved these as ordinary blocks, usually with no BE
tag. `LegacyTransfuserMigration` uses the shared fair loaded-chunk queue:
64 detached END callbacks, bounded200 retries, no forced chunks. It scans only
section palettes containing transfusers, restores only missing/catalogue anchors,
preserves their blockstate and already operational tiles, creates no inventory
or essence, marks the chunk dirty and refreshes tracked client chunk data.
No destroy/setBlock callbacks, loot, research or recipes run during migration.

## Verification boundaries

`EssentiaTransfuserGameTests` defines15 server cases covering all six real tube
directions, rear API quirks, original support/shape/stats, five-tick cadence,
actual alembic->jar and jar->demand-jar transfers, typed demand/suction/closed ports,
unavailable air destination, native buffer refusal by a stronger competing
consumer, full native target refusal, foreign query reentry, reload, redstone,
stale consumer and missing-tile migration.

These are scenario definitions, not a claim that QA has passed. Final server,
hidden-client, visual review and packaged-artefact evidence belongs in
VALIDATION.md after root completes the integrated run. Full manual survival
and independent-client multiplayer are not established by these fixtures.

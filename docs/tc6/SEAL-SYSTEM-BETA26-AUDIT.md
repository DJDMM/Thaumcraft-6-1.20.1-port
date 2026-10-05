# Seal storage, tasks, configuration and bell logistics — BETA26

Authority is the pinned `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
The source snapshot is `954022bb777b7546281fb36df8522f0ba6b43f81`.
Core bytecode inspection is retained outside the release in
`work/seal-core-023-pinned-javap.txt`. This document describes implementation;
it does not assert an integrated server/client run has passed.

## Original roster and world storage

ConfigItems.initSeals registers **16** behaviors, plus the blank placer item at
metadata zero: Pickup, Advanced Pickup, Fill, Advanced Fill, Empty, Advanced
Empty, Harvest, Butcher, Guard, Advanced Guard, Lumber, Breaker, Use, Provider,
Stock and Advanced Breaker. The provider's key is `thaumcraft:provider`.
Item metadata 1..16 follows exactly that order. Modern flattened item IDs and
the existing item models retain those forms; blank and invalid forms cannot
be placed. There is no seventeenth nonblank original seal.

A seal is attached to the **position and face of an existing block**. Placement
does not substitute an invisible block or discard a chest's contents. The
release's placer checks canPlayerEdit, the behavior endpoint and duplicate
faces, then pays one item outside creative mode; placement itself has no
research predicate. Original recipe research controls acquisition instead.
The port additionally checks alive/spectator, loaded position, physical range,
world edit permission and detached held-stack identity around callbacks.

The release writes seals through chunk NBT and an in-memory dimension map.
The port uses a `thaumcraft_seals` SavedData **per dimension**. This is a storage
adaptation, not a global cross-dimension map. It preserves pos, face, type,
owner UUID, priority, color, locked, redstone, area, ghost item/tag filters,
stack size limits, blacklist and toggle values. Original Harvest replant
instructions are the one persisted behavior cache and are included through
the behavior's custom writer. Scan counters, active tasks, reservations and
provision requests remain transient. Malformed types/faces/owners are rejected;
collections and settings have explicit bounds. Snapshots return detached NBT
and item stacks.

Only loaded seal positions tick. The endpoint is rechecked each twentieth
server tick; invalid support removes the seal, suspends its tasks and drops
its original item once. Recursive removal cannot produce a second drop.
No forced chunk loading is used. Login, dimension changes and newly watched
chunks synchronize seal state; later changes/removals synchronize the actual
dimension. Client caches are cleared when their level changes.

## Filters and area

Original empty blacklist accepts all nonempty stacks; empty whitelist accepts
none. Meta and NBT checks default true, ore/mod checks false. Mod mode returns
the registry-domain match before the other checks. Ore mode tests material
groups, then ordinary matching remains the fallback. Exact NBT compares the
whole non-damage tag, not an arbitrary subset. Modern `Damage` is separated
because legacy durability/metadata was not stored as part of ItemStack NBT.
Original wildcard damage 32767 still bypasses the metadata comparison.

The removed OreDictionary is adapted to **material-specific** Forge/common
item tags such as `forge:ores/iron`; broad `forge:ingots` or
`forge:ores_in_ground/stone` are deliberately not equivalence groups. The
release's ore-candidate NBT gate is retained for untagged standard dictionary
entries. Original catalogue metadata variants are matched using their pinned
legacy item/metadata identity, so Ignore Meta still works after flattening.
The six original vanilla plank types, color families and former dye forms
also retain their legacy family comparison. New vanilla types without an
original family use their real registry identity.

Area defaults to 3 on axes tangent to the seal face and 1 along its normal.
Each component is bounded 1..8. The tangent width is `1+(size-1)*2`, whereas
the normal extends only forward from the face. Bounds and incremental
positions preserve GolemHelper.getBoundsForArea/getPosInArea, including
signed negative face progression. They do not replace this with a symmetric
cube around the block.

## Tasks and actual golem execution

The release stores transient block/entity tickets, defaults to **300 seconds**
and cleans them once every 20 ticks. A reservation adds 120 seconds; setting
completion adds one second, including the release's travelling
setCompletion(false) updates. A ticket with one positive second reaches zero
on one cleanup and is removed on the following cleanup. Suspended/expired
tickets invoke the owning behavior's suspension callback. The default queue
bound is 10000; monotonic IDs and deterministic oldest eviction replace
millisecond-hash collisions and arbitrary map eviction. Signed-short overflow
is hardened with a 32767-second maximum.

Entity tickets are selected before block tickets: pinned EntityThaumcraftGolem
installs AIGotoEntity/AIGotoBlock at priorities 2/3 initially and 3/4 after
property initialization. This preserves Provider delivery to an assigned player
when an eligible Store ticket could otherwise take the same cargo. Work already
claimed is not preempted when an entity ticket arrives. Within either type,
candidate order is squared block-center distance **minus priority × 256**.
Owner locks, required/forbidden traits, home bounds and colors apply before
claim. Color zero is a wildcard: only two different nonzero colors conflict.
A claim is exclusive to the actual golem UUID; repeated claims, stale tickets,
foreign completion and callback reentry cannot run the same work twice.
The physical goal finds an actual navigation path, uses a nearby cardinal
stand position for block tasks, retries navigation every fifth travelling
tick and tries a bounded random step when stuck. It abandons work after 1000
active ticks, returns reserved tickets, and uses the original ten-tick
completion retry. Entity reach uses `3.5 + (width/2)^2`; block completion also
checks the source range corresponding to the original adjacent stand position.
Unloaded or missing targets do not produce remote work.

Redstone sensitivity tests both the attached block and its outward neighbor.
Entering the powered state suspends the seal's existing tasks once; the seal
does not run its behavior while powered. Resuming can create fresh tasks.
`SealWorker` is implemented by the real placed golem, not by the earlier
catalogue NoAI mob. Inventory movement and all world work occur in the owning
behavior only after actual claim/navigation/completion.

## Physical menu and bell logistics

Seal settings retain the original 176×232 layout, radial GUI categories,
filter centers at (88,72), 27 main slots at (8,150) and hotbar at y208.
Filter slots are ghosts: they copy a one-count cursor template without taking
or creating a real item. The port keeps stable ghost indices and activates
them only in filter category 1, instead of rebuilding every container slot
list when changing tabs. This avoids modern container-state desynchronization.
Size limiter clicks retain the original initial size zero, increment/decrement
by cursor count, and empty-cursor +/-1 or shift +/-10 behavior. Ghost requests
derive the template from the authoritative server cursor stack.

Priority is -5..5, color 0..16 and area axes 1..8. Existing original button
numbers are retained: 20/21 blacklist, 25/26 owner lock, 27/28 redstone,
30+/60+ toggles, 80..83 priority/color and 90..95 area. The original GUI
allows other players to edit filters/priority/color; this is retained. Owner
checks for lock/redstone, previously only enforced by the original client
UI, are enforced on the server. All requests also require the physical menu,
current revision, same seal object, live player, valid edit permission and
range. Bell **removal** is restricted to the owner as an explicit multiplayer
hardening; original BETA26 removal permitted any player.

Shift bell use with known GOLEMLOGISTICS opens the original logistics catalog
at the clicked target face, or toward the player for air use. It has **81
ghost slots** at (19+column×19,19+row×19), no player inventory, and a 215×215
GUI. It aggregates loaded owned Provider inventories within **32 blocks**,
applies their real filters, groups exact items/tags and preserves large counts
through an explicit integer network count. This also fixes the original
same-total refresh and display-name collision problems. Search and scrolling
remain server-owned, and displayed counts do not create deliverable items.

A request names a current displayed slot and amount, **never a client item
template or world target**. The target/face is saved in the physical menu.
The current catalogue availability is rechecked, and the amount is split
into physical maximum-stack-size provisioning requests. Requests do not take
items directly or credit the player: Provider tasks must reserve a real
golem, extract actual inventory and deliver via a linked block/entity task.
Requests default to **10 seconds**, or **120 seconds** after a linked task.
The original maximum pending list of 1000 is retained; malicious amounts,
search strings, menus, dimensions and revisions are bounded. The request
amount is bounded to 65536 and current actual availability as a packet
hardening, rather than accepting an unbounded client integer.

## Validation definitions and scope

`SealCoreGameTests` adds eleven meaningful server scenarios: exact roster,
physical paid placement/duplicates/creative, ghost cursor/NBT/stale requests,
owner configuration and numeric limits, filter modes/flattening, SavedData
and transient-state boundaries, priority/color/exclusive claims, expiry and
powered suspension, real support-loss item drop, and owned-provider logistics
with large counts/exact request splitting. The Provider/Player/Store regression
uses an indexed real player recipient, an actual logistics request, physical
source extraction, a competing priority-5 Store task, the real task goal and
player pickup. It asserts two apples reach the player exactly once, four remain
in the source and none enter Store. Queue tests explicitly pause the
real golem AI to isolate reservation state; they do not count as proof of
travel or full survival. Behavior and entity suites exercise their own
physical execution. Final integrated counts, hidden desktop client runs and
reviewed images belong in VALIDATION.md only after they actually finish.

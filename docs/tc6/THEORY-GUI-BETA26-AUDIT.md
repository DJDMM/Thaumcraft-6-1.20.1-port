# Research table GUI — TC6 6.1.BETA26 audit

Baseline: pinned official `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`,
and the matching `GuiResearchTable.java`/`ModelResearchTable.java`. The local
`work/theory-gui-javap.txt` contains the official GUI bytecode inspected for this port.

## Confirmed original behavior

- The 255×255 wooden desk, ordinary/gilded parchment, inspiration bulbs,
  bonus sheets and aid-selection highlight reuse original resources.
- Cards leave the paper stack with a seeded tilt, the last offer moves first,
  hovering enlarges/lifts an offer, and the chosen offer moves to the right-hand
  stack while rejected offers disappear. The original writes the selected card
  after that motion and displays the last resolved card between draws.
- Aid objects are shown as block/item icons in rows of at most six. Selection
  requires `selected.size() + 1 < availableInspiration`, leaving at least one
  inspiration. The port supports all 14 registered definitions; only 13 have
  detectable aid objects in BETA26. The null Eldritch object remains dormant.
- Category percentages approach their new values one point per player tick,
  with golden sparkles on increases. Rejected categories and reduced rewards
  use different colors. Percentages may exceed 100; displayed reward units
  are taken from the authoritative session's original reward calculation.
- Required items display their exact initialized templates. A golden `!`
  identifies consumed items. Celestial notes, aspect crystals and filled
  phials retain their metadata/NBT requirements; inventory counts use the
  same `TheoryCard.matchesRequirement` predicate as server payment.
- Enchantment requires five levels. Spellbinding spends up to five available
  levels; Dark Whispers consumes all levels and clears remaining XP. Scripting
  performs extra table callbacks: its second ink callback clamps at exhaustion,
  and the original ignored failure to consume extra paper is preserved.
- Eight original OGG files back `page`, `pageturn`, `clack` and `write`.
  The source sound clips are copied byte-for-byte from the pinned JAR.

## Explicit modern adaptations

- Paper/card content is fitted to the desk and the external category panel;
  three simultaneous offers use narrower papers. Full descriptions and item
  details remain available in tooltips rather than overflowing the layout.
  The complete layout scales to small Minecraft GUI dimensions, preserving
  slot and aid hitboxes. Portal aids without a BlockItem use an Obsidian,
  Eye of Ender or lesser-portal spawn-egg symbol.
- Card motion uses monotonic elapsed time with fixed deal/resolve durations,
  rather than the original render-frame/partial-tick increments. It preserves
  the direction, stagger, seeded tilt, hover lift and right-stack resolution;
  it does not promise frame-for-frame timing parity with the old renderer.
- Percentages also show a bounded 0–100 progress strip and the actual raw
  reward. Sparkles are GUI pixels rather than the old global FX dispatcher.
- There is no client "animation complete" payment packet. Selection is paid
  atomically and persisted on the server first; only an authoritative changed
  `lastCard`/`placedCards` snapshot starts cosmetic resolution of detached old
  offers. Closing the GUI, lag, rejected/stale requests and interrupted
  animation cannot refund, repeat or postpone a server transaction.
- Paper/ink/XP/item shortages and request status are explicit, translated UI
  states. Local guards improve feedback; server ownership/revision/payment
  checks remain authoritative. Original empty-extra-paper/final-ink Scripting
  quirks do not become invented local resource gates.
- Discovered aid icons form compact rows, without reserving a gap for the
  original dormant Eldritch definition. During paid-card resolution, resource
  requirements and costs are hidden: depleted current resources must not make
  an already paid card appear unpaid or free. Choice/completion buttons wait
  until the cosmetic motion finishes so they do not overlap moving papers.
  Available controls render above the offered papers, preserving readable
  labels and matching their unchanged click regions.

## Isolated integrated client fixture

`runClient -PtheoryCompleteSmokeTest` enables the property
`thaumcraft.theoryCompleteSmokeTest` and uses `run/theory-complete-smoke`.
`TheoryCompleteClientSmokeTest` creates a fresh survival world. Late research
stages and exact initialized offers are explicit fixtures, never manual saves
or evidence of a full survival playthrough.

The fixture captures 22 scenes. Actual menus and C2S/S2C packets exercise
START with all 13 detected aids, DRAW/paper, SCRAP, paid SELECT and FINISH.
It checks deal/hover/resolution motion, GUI scales 2/4, missing XP/notes/ink/paper,
foreign ownership, stale revisions, unchanged rejected transactions and exact
completion rewards. New-deck scenes cover wrong/paid Synthesis aspect templates
and compound output; wrong/paid Infuse phial NBT and ingredient payment;
Dark Whispers XP reset and normal Warp; and Scripting extra callbacks at final
ink. Paid captures await the current request's accepted revision/card, a new
resolution animation, exact server/client inventory and vanilla XP equality,
and a completed GUI/HUD render. They never assign client payment states to
produce a screenshot. A world scene verifies the registered BER uses actual synced active-table
state. Only a successful marker in the root-run client log establishes a pass;
the fixture definition itself does not claim runtime success.

# BETA26 theory table behavior audit

Checked on 2026-09-30 against `work/Thaumcraft-1.12.2-6.1.BETA26.jar` using Java `javap -c -p`, with the pinned decompiled tree used for readable context. The official jar is the authority. These notes cover the first port slice: nine API generic cards and Bookshelf.

## Integration findings

- **No research gate on starting or converting.** The official `PacketStartTheoryToServer$1.run` reaches `TileResearchTable.startNewTheory` after checking the target tile type. It does not check `THEORYRESEARCH`, ink, paper, distance, existing session, or selected aid availability. `BlockTable.onBlockActivated` similarly converts a wooden table whenever the held item implements `IScribeTools`, then fires the research table crafting event. The port should preserve the absence of a research gate while adding server context, owner, revision, and transaction checks.
- **Resource timing:** start costs no paper or ink; the GUI requires supplies and usable tools before allowing it. The port checks that eligibility server-side. Draw costs exactly one paper for either two or three choices; a successfully activated selection costs exactly one ink. Ink uses direct damage `+1` while damage is below 100, so the tools remain as a nonempty item at damage 100. Do not use a damage routine that destroys the exhausted stack. The original selection handler ignores `consumeInkFromTable()` failure after activation; the port fixes this to avoid free cards and knowledge debits when ink is missing.
- **Study and Notation are both aid-only.** Study does not occur in the ordinary generic draw pool. Bookshelf is a six-entry weighted pool: Balance once, Notation twice, Study three times. Repeated bookshelves contribute one aid key, not repeated pools.
- **A fresh unaided session in the nine-card slice can offer only Experimentation.** The other seven ordinary cards fail eligibility before totals/observations exist or are aid-only. Do not require every successful draw to fill two/three choices: the official 10,000-attempt loop permits partial draws, and paper is still spent once. The original full registry has additional eligible card classes outside this slice.
- **Verified original Analyze bug:** its initialization loops over each non-Basics category but looks up `researchCategories.get(this.cat)` while `cat` is still null. `PlayerKnowledge.getKey` maps a null category to uncategorized knowledge. Thus normal category observations do not make Analyze initialize in the official binary. This is not merely a decompiler error. This port preserves normal-draw rejection because its knowledge model has only named categories; activation remains available for restored initialized cards. A future change to check each candidate category's observations would be an intentional correction requiring explicit documentation.
- **Verified aid draw quirk:** the `aidDrawn` local remains false throughout `drawCards`; the binary never sets it after a successful aid choice. The 25% aid branch is retried for each attempt, so more than one distinct aid card may occur in a draw. Do not silently impose a one-aid-card limit. A successfully drawn aid occurrence is removed from its weighted pool immediately, even if the card is later discarded.
- **Card inventory count correction:** `ConfigResearch.init` registers **33 total card classes** in BETA26, of which these nine API generic cards are the first port slice. Therefore **24 registered cards** remain outside this slice. Additional unregistered classes in the source tree (for example CardTruth and CardDragonEgg) do not increase the active BETA26 registry. The source has 14 registered aid classes including three Portal variants; only Bookshelf is covered here.
- **Refill NBT preservation is a port improvement:** official `scribingtoolsrefill` is an ordinary `ShapelessOreRecipe` with a fresh zero-damage output, wildcard-damage tools plus `dyeBlack`. It has no custom NBT copying. Preserving name and unrelated NBT in the 1.20.1 refill recipe is sensible, but is not original BETA26 behavior.

## Session and draw rules

`ResearchTableData.getAvailableInspiration` starts with float 5, adds 0.5 for each strictly completed SPIKY research and 0.1 for each strictly completed HIDDEN research, then returns `min(15, Math.round(total))`. Event-only facts lacking a ResearchEntry do not increase inspiration. A completed entry with both metadata flags contributes both. Starting inspiration is this value minus the **number of selected aid keys**, even though only recognized keys produce cards in the original implementation. The port validates the aid set before charging it.

Aid blocks are searched inclusively at offsets X/Z −4…4 and Y −1…1: a 9 × 3 × 9 box. A HashMap keyed by aid class deduplicates matching objects. The binary also scans entity aids within radius 5, outside the Bookshelf-only slice.

Ordinary drawing randomly picks registered card classes, rejects initialization failure, aid-only cards, cards whose positive cost exceeds current inspiration, and cards with a non-null category that is unavailable. Available categories exclude blocked ones and require each category's unlock research to be strictly completed (unless the unlock key is null). There is no percentage-weighted category selection in generic drawing. Category totals are a TreeMap, so iteration is alphabetic. Duplicate card **classes** in the same draw are rejected independently of their seeds. The official fail-safe allows at most 10,000 attempts.

A normal draw requests two cards. A bonus draw requests three and consumes one bonus credit; if no bonus credit remains, the original silently falls back to two. Both still cost one paper. Every new original card receives an absolute seed from the player's RNG. Seeded `java.util.Random` initializes Study/Reject targets; original activation rewards use the player's RNG. The port persists a session-owned RNG seed for **both draws and activation effects**, so choices, effects, and subsequent RNG state survive unload/reload and failed selections leave the random stream unchanged. This is an explicit replay-safety adaptation to original RNG ownership.

The selected card's presentation remains in choices until the original GUI sends button 1 after its animation. That handler appends the **previous** `lastDraw` seed to `savedCards`, replaces `lastDraw` with the selected choice, and clears choices. Neither `savedCards` nor `lastDraw` influences future random card eligibility. The port may finalize this presentation server-side in one atomic selection; it must retain the selected card/seed and avoid replay.

Completion means inspiration <= 0. `addInspiration` caps increases at `inspirationStart` (the full pre-aid value), so Rethink can recover the inspiration originally invested in aids. Scrap is allowed only while incomplete in the original handler and grants no knowledge.

## Nine generic cards

All names refer to `thaumcraft.api.research.theorycraft.Card*`. Unless mentioned, card category is null and it is not aid-only.

| Card | Cost | Eligibility / target | Successful effect |
| --- | ---: | --- | --- |
| Analyze | 2 | Official initialization bug described above. Intended corrected target requires >=1 completed Observation in a non-Basics category; ordinary draw still requires that category unlocked and unblocked. | Spend one Observation (16 raw); Basics +5; chosen category +25…50. Must preflight ink before any debit in port. |
| Balance | 1 | Original predicate is `blocked.size() < totals.size() - 1` and unblocked sum >= unblocked count. | Replace each unblocked total with integer floor(sum/count); Basics +5; `penaltyStart++`. |
| Experimentation | 2 | Always initializes. | Choose uniformly from **all** registered category keys, including locked and blocked categories; category +15…30 and Basics +1…10. |
| Inspired | 2 | At least one positive total. Highest total wins; alphabetically first wins equal totals. It reports that target as its category and ordinary draw filters unlock/block status. | Snapshot amount `10 + highest/2`, using integer division, then add it to the saved target. |
| Notation | 1 | Aid-only; >=2 totals; alphabetically first strict min and strict max; fails if target keys equal or min<=0. Does not exclude blocked totals. | Remove low category's entire current total; highest receives `low/2 + random(0…low/2)`. |
| Ponder | 2 | `blocked.size() < totals.size()` in original. | Add 25 points in alphabetical round-robin across unblocked totals; Basics +5; bonus credit +1. Original contains unusual fail-safe/return checks; do not copy mutation-while-iterating hazards. |
| Reject | 0 | Pick seeded uniformly among currently unblocked total keys; fails if none. Reports no draw category. | Basics +5; block the saved target. Blocking does not erase its total or prevent finish reward. |
| Rethink | −1 | Sum of all totals >=10, including blocked. | Remove 10 points in alphabetical round-robin from all totals; bonus credit +1; Basics +1…10; refund one inspiration capped at full start. Original avoids iterator invalidation by breaking whenever removal deletes a key. |
| Study | 1 | Aid-only; seeded uniform target from available (unlocked, unblocked) categories. | Target +15…25. |

Card persistence stores the seed plus initialized targets/amounts, so reload must restore the presented choice, not reinitialize it from current totals or knowledge.

## Finish formula and ties

The official `TileResearchTable.finishTheory` sorts entries by descending percentage only. The source TreeMap supplies alphabetic encounter order and Java's ordered stable stream sort preserves that order for equal totals. Thus equal percentages are ordered alphabetically before penalties.

For zero-based sorted index `i`, first calculate `round(percent / 100.0f * 32)` raw theory knowledge. If `i > penaltyStart`, replace it with integer truncation of `max(1.0, raw * 0.666666667d)`. There is no clamp of percent to 100. With default penaltyStart 0, the first category receives its full reward, every later category gets the penalty. Balance extends the unpenalized prefix by one per activation. A top category with tiny percentage may round to zero; a penalized category rounds/truncates to at least one. Blocked totals still award theory knowledge.

Examples: 100% yields 32 raw for the top category and 21 after penalty; equal 50% Alchemy/Artifice/Basics yields Alchemy 16, Artifice 10, Basics 10. With penaltyStart 1, Alchemy and Artifice both receive 16. A 1% top total yields 0 raw while a later 1% yields 1 raw. The port must preflight all credits against integer overflow and finish/clear exactly once.

## Port GameTests ready for central execution

`research/theory/TheoryGameTests.java` contains 16 meaningful tests covering failed-resource atomicity, owner/context/revision rejection, retained tools/refill NBT, exact draw/selection accounting, restored session/choices/effects/RNG/revision, replay-safe rewards, real wooden-table conversion crafting proof, aid boundary/deduplication, independent observations and card selection costs, and reward ties/rounding/penalty rules. One seeded Bookshelf test runs the actual start/draw/select/reload/finish lifecycle, deliberately reaches one inspiration, and bounds it at 16 selections. The complete pinned catalogue has 10 SPIKY and 43 HIDDEN entries and gives rounded inspiration 14, below the formula's ceiling 15.

The two additional regressions cover another player's normal pickup, shift move, number-key swap, cursor replacement, and collect-all against an owned session; they also verify the owner can retrieve/refill/return supplies and resume. Breaking an unfinished active theory must drop exactly one table, one tools stack retaining damage/NBT, and its remaining paper once, grant no theory knowledge, and leave a replacement table without a session.

Gradle and GameTest execution are centralized by the root agent to avoid competing builds. The initial full suite passed 69/69, including the first 14 theory tests; the two additional inventory/break tests are ready for the next central run. The audit itself is binary/source verification, not a claim of running the original game or of implementing the other 24 cards.

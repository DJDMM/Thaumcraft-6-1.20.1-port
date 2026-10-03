# Book inserts, unread discoveries and addenda — BETA26

Behavioral baseline is the pinned official `Thaumcraft-1.12.2-6.1.BETA26.jar`.
The matching source checkout is `work/thaumcraft6-reference` at
`954022bb777b7546281fb36df8522f0ba6b43f81`.

## Original behavior

- `GuiResearchPage` constructs its aspect list from the player's `!aspect`
  facts, sorts by aspect name and displays five aspects per page. A compound's
  undiscovered components use `_unknown.png`; their names are not displayed.
  Primals have the original `tc.aspect.primal` caption. Opening the insert
  requires completed `FIRSTSTEPS`.
- The knowledge insert requires completed `KNOWLEDGETYPES`. `drawKnowledges`
  uses observation and theory textures, category icon overlays, completed
  points and a 16-pixel progress strip for the raw remainder. Positive raw
  balances from closed categories are still displayed; zero balances do not
  manufacture knowledge. BETA26 progression is 16 raw units per observation
  and 32 per theory, as already preserved by the port.
- Addenda appear only on a completed base entry and when every addendum's
  research requirement passes `knowsResearchStrict`. Ordinary recipe aliases
  do not substitute for the canonical requirement.
- `ResearchManager.progressResearch` sets the normal RESEARCH/POPUP markers
  on completion, not every ordinary intermediate stage. It marks an existing
  completed entry PAGE when new prerequisite research reveals an addendum.
  `GuiResearchBrowser` clears RESEARCH and PAGE when that entry is opened.
- `ResearchToast` samples `hud.png` at `(0,224)` for a 160×32 banner, draws
  the research icon at `(6,8)`, heading at `(30,7)`, scaled title at `(30,18)`
  and remains visible for five seconds.

## Port implementation and explicit adaptations

`ThaumonomiconKnowledgeScreen` is a read-only paper insert over the retained
book. It preserves discovered-only aspect pagination, component concealment,
original artwork, knowledge icons and raw balances. The knowledge insert
uses one row per positive category and two columns for observation/theory to
keep the seven categories legible with modern Russian text. Tooltip numbers
include the exact raw remainder; hidden categories retain their actual totals.
The insert itself sends no progression, payment, scan or discovery request.

`ResearchBookState` supplies strict addendum masks to both notifications and
read acknowledgments. The archive does not acknowledge pages. Persistent
bookmarks live in optional `BookRead` NBT alongside the unchanged v2 gameplay
data. Stages are watermarks; addenda are individually acknowledged bits. An
old save with no bookmarks seeds its existing pages as read. Future completion
and newly unlocked addenda remain unread after migration and restart.

The original flags packet permitted the client to write all flag bits.
The modern `ResearchNetwork.Read` only acknowledges existing server facts,
requires a held book, validates the exact displayed stage and rejects any
unavailable addendum bit as one operation. It cannot create or advance
research. A mask from an old view cannot clear an addendum unlocked after
that view. Replay is idempotent. Protocol 4 requires matching client and
server versions because message 3 is new.

Notifications compare authoritative snapshots within one connection. The
login snapshot is a silent baseline. Research completion and additional
pages are distinct notices; knowledge balances, aspect discovery, ordinary
stage advances, read acknowledgments and repeated snapshots never fabricate
research toasts. Existing unread map markers survive relogging even though
old discoveries do not replay as a wall of toasts.

The toast uses the original HUD artwork, position, color and duration. Long
localized headings also scale to fit. The modern accessibility notification
duration multiplier is honored. Additional-page toasts are a port adaptation
of the original PAGE marker and `tc.addaddendum` chat notice.

## Verification owned by this module

Eight `ResearchBookKnowledgeGameTests` cover bookmark-only mutation, per-player
SavedData round trips, ordinary-stage/completion separation, strict addenda,
read races, invalid and unavailable masks, silent legacy migration, malformed
NBT, first-snapshot/delta notifications, held-book context and Read codec/replay.
Client rendering and actual C2S book integration are verified by the root's
isolated Thaumonomicon fixture. Registration or this document alone does not
claim those client checks passed.

These views do not implement the remaining canonical research mechanics.
The archive continues to be reference data, independently of any screenshot
or displayable item.

# Thaumometer first-person grip — 0.27.1

This pose is an explicit user-requested adaptation for the 1.20.1 port.
The supplied visual reference shows the scanner held centrally with both
hands. The pinned Thaumcraft 6.1.BETA26 JAR does not register a special
first-person hand renderer: ItemThaumometer supplies scanning feedback,
and its OBJ uses ordinary first-person display transforms. Therefore this
change does not claim to restore a verified BETA26 two-hand callback.

## Runtime contract

- A displayed Thaumometer with an empty opposite inventory hand uses the
  central two-hand pose. Main and offhand work identically.
- With another item in the opposite hand, the scanner uses its physical
  left/right hand and a side pose. The other item's native rendering stays
  active. Two scanners each use one distinct hand.
- The player's dominant arm determines physical main/offhand mapping.
  PlayerRenderer renders the actual default/slim skin arms and sleeves;
  invisible players keep the item without visible arms.
- RenderHandEvent's cached display stack is retained through vanilla equip
  transitions. Native equip and swing progress move the grip. The empty
  native main-arm event is suppressed for an offhand two-hand scanner.
- First-person only; F1, spectator and scoping guards preserve native
  behavior. Item GUI, ground and third-person models are unchanged.
- The original OBJ and media remain intact. ItemDisplayContext.NONE avoids
  applying its old first-person transform twice. Packed light, native
  buffers and balanced pose stacks are retained.
- This renderer never scans, grants research or mutates inventory. Existing
  aura HUD, target overlays and C2S scan mechanics remain separate.

## Native validation

The isolated Thaumometer smoke profile retains eleven original scanning,
HUD, container, F1 and scanner-removal scenes. Eleven additional scenes
use server inventory changes, native client-settings packets and a real
C2S swing: main/offhand with empty/occupied opposite hand for each dominant
arm; two scanners; swinging; and ordinary sword/torch restoration.

Read-only samples and counters are recorded after actual PlayerRenderer
and ItemRenderer calls. They establish which physical arm was rendered;
screenshots must also be reviewed to establish visual contact and framing.
The fixture supplies the scanner, comparison items, arena, aura and targets;
it is not a complete survival playthrough. Final run results belong to
VALIDATION.md and the artifact report, not to this expected test contract.

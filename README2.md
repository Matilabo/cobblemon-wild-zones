# A Wild Cobblemon Appeared!

A Fabric mod for Cobblemon 1.8 (Minecraft 1.21.1). Walking through a **Wild Zone** plays one of the
Cobblelocke encounter animations and starts a battle with a wild Cobblemon, like the tall grass of the games.
It can also play the animation before **every wild battle**, or run the Cobblelocke **event lock** once in
every new biome.

## Making Wild Zones

Operators (or the host of a single player world with cheats on) hold a **Bone**:

| Action | What it does |
| --- | --- |
| Right click | Sets corner 1 on the block you look at (up to 64 blocks away), or where you stand |
| Left click | Sets corner 2 the same way |
| Crouch twice quickly | Turns the selection into a Wild Zone (two taps; holding crouch counts once) |
| Right click looking through a zone | Opens its menu, whether you look at a block in it, below it or past it |
| Crouch + right click | Sets corner 1 even when looking through a zone |

While holding a Bone, zones show as faint outlines in their color (white by default), with a board floating
inside each nearby zone that shows the icons of the first 30 Cobblemon it can produce right now (most likely
first). Nobody else sees them. The **Wild Zones** button in the config menu lists every zone, to edit, teleport to
or delete.

Where zones overlap, the one made last wins: its lists and odds are used there, and its menu opens.

The zone menu has its name, outline color, size (width, height, length), its own Cobblemon **Whitelist** and
**Blacklist** (used instead of the config menu's filters when it has any), **Biome Rule**, **Encounter Chance**
and **Spawning**.

### Encounter Chance

Lists every Cobblemon the zone can produce right now, with its chance, in one of three modes:

- **Cobblemon's Odds**: as Cobblemon weighs them (their spawn weights with Natural Spawns, otherwise all alike).
- **Rarity Tiers**: each Cobblemon is Common, Uncommon, Rare or Very Rare (click to change, right click to go
  back). A tier is picked by its odds (60 / 30 / 7 / 3 by default, editable) among the tiers that have Cobblemon
  there, then one of its Cobblemon.
- **Custom Weights**: each Cobblemon gets a weight like in Cobblemon's spawn files (10 unless changed, 0 never
  turns up); its chance is its weight over the total.

The odds count for the zone's encounters, its spawns and its floating board.

### Spawning

With Spawning on, the zone also spawns its own Cobblemon inside itself (picked like its encounters) while a player
is within 64 blocks: one every **Cooldown** (1 second to 1 hour) while fewer than **Cap** (1 to 32) of them are
around. They are ordinary wild Cobblemon, so Every Wild Battle plays the animation for them. They leave after five
minutes once nobody is within 24 blocks, and when the zone is deleted or its Spawning turned off.

### Zone Banner

Walking into a Wild Zone (with or without a Bone, for every player) shows a banner at the top of the screen for a
few seconds: the zone's name, how many Cobblemon can turn up there, and the icons of the first 20 (most likely
first) with each one's chance under it. It is the same list as the zone's floating board and Encounter Chance.
Stepping out and back in within five seconds does not show it again. **Zone Banner** in the config menu turns it
off; **Show Rates** hides only the chances.

## Species lists

Every Cobblemon whitelist and blacklist opens the same picker: a text box (comma separated, Tab completes ids,
forms and `#cobblemon:` groups) and, under it, 30 Cobblemon to click. With nothing typed they are the first 30 by
national dex number; while typing, those whose id starts with the text, alphabetically. The picker grows while the
Tab list is open, so the list never covers them. Cobblemon already on the list are left out. Forms are entries of
their own, like `cobblemon:pikachu form=alola-bias`; a plain species entry covers all of its forms.

Everything on a whitelist can turn up, and the floating icons show exactly that. With Natural Spawns, the
listed Cobblemon that spawn there naturally keep Cobblemon's odds between them, and the rest are as likely as an
average one of those.

**Biome Restriction** decides how a group on a whitelist is read:

- **Natural Biome Species** (default): only the group's members that Cobblemon spawns naturally in the
  encounter's biome. With `#cobblemon:starter` whitelisted, Mudkip does not turn up in a forest zone.
- **Any Cobblemon**: every member of the group, in any biome.

Each Wild Zone can override it from its menu's **Biome Rule** row: Config Menu's (follow the setting above),
Natural Biome Species or Any Cobblemon. Species named on their own are never restricted. When nothing on a list can turn up somewhere, no encounter
starts there, rather than playing the animation for nothing.

## Config

`/wildencounter config` opens the menu (read only for non-operators). Rules can apply to every dimension
(**ALL**) or to each dimension on its own, and species can be filtered per biome.

- **Encounters**: Wild Zones (chance per block walked, cooldown, grass only, no natural spawns in zones, the
  Zone Banner and its rates),
  Every Wild Battle, Encounter Events (per biome or per square region), auto-start, removal after the battle,
  shiny rarity, chat messages, repels.
- **Species & Levels**: natural spawns at the player's position or any species, whitelists and blacklists
  (per dimension or per biome), and the event lock's level modes.
- **Animations**: the Cobblelocke Minecraft and Retro presets, or one clip per environment, with
  previews, and your own animations made from GIFs (see below). A GIF at `config/awildencounter/<clip>.gif`
  also still replaces that clip.

The ALL rules are also written to `config/awildencounter/config.json5`, which new worlds start from. It is
JSON5, with a short comment above each option. Older `config.json` and `presets.json` files are moved to the
`.json5` files on the first start.

The menu, the messages and the Zone Banner are translated to Spanish (Spain and Argentina) and Italian.

- **Presets**: the switcher at the top of the menu goes through the presets in
  `config/awildencounter/presets.json5`. It starts with **Wild Cobblemon Default**; **Export** saves the rules
  being edited as a new preset (the same name replaces one).
- **Manage Multiple Dimensions**: the square next to the Affected Dimensions selector lists every dimension, to
  rename it (only inside this mod), copy one's rules and paste them into another, or open its rules.

## Custom animations

The Animations tab has two rows for your own encounter animations:

- **Custom Animations Folder** opens `config/awildencounter/custom encounter animations`.
- **Import A GIF** lists the GIFs in that folder. Importing one turns it into frames (made small enough to cover a
  480×270 screen, with its own timing and transparency), then shows it: play it, step through it (← →, or the
  timeline), and mark the **appearing frame**, where the screen counts as covered, the Cobblemon appears and the
  battle starts (M, or Set Appearing Frame; Show Appearing Frame goes back to it). Saving writes the animation to
  its own folder, `custom encounter animations/<name>/` (PNG frames and a `meta.json`, the same format as the
  mod's own clips). Animations already in the folder can be opened again to move their appearing frame.
- **Chroma Key** (while importing): **Pick Color** (or K), then click a color in the preview, and that color
  becomes see-through, like a green screen. **Intensity** (1 to 100, drag or scroll) sets how close a color must be
  to go too: low takes only that color, high also takes its shades. Edges fade out, and the key color is taken
  out of them. The preview shows the result on a checkerboard; **Clear** removes the key. The key is saved in the
  animation's `meta.json`, and importing the same GIF again starts from it.

Every folder there with a `meta.json` is picked up on its own, without importing, so a folder copied from someone
else works straight away. They show in each animation row after the mod's own animations, and the config file
names one as `"custom:<folder name>"`. GIFs can be up to 600 frames and 30 seconds long.

Custom animations play from that folder on each player's computer. A player who does not have the folder sees the
default animation instead, so give the folder to everyone on a server. The server times the battle from the
folder when it has it too (single player, or a copy on the server); otherwise it waits for the player's game to
say the appearing frame was reached.

## Repels

With **Respect Repels** on, no zone encounter or Encounter Event starts:

- near a Repel, Super Repel or Max Repel block from Cobblemon Repel (by deuli), within the same range it keeps
  spawns away (its `repelRange` gamerule at 0 turns this off, as it does for spawns);
- while the player has any status effect whose id ends in `repel`;
- while the player has the `repel` tag (`/tag @s add repel`).

## Cobblelocke

Cobblelocke is not needed. When both are installed, A Wild Cobblemon Appeared! steps back where they would overlap:

- Every Wild Battle does not replay the animation for Cobblelocke's event lock Cobblemon, which already had one.
- No Wild Zone encounter or Biome Event starts while Cobblelocke's event lock is playing for that player.
- While a Cobblelocke run with the event lock is active for a player, Encounter Events are left to Cobblelocke, so a
  new biome never gets two encounters. Wild Zones and Every Wild Battle keep working.

## Commands

`/awildencounter` works the same as `/wildencounter`.

- `/wildencounter config`
- `/wildencounter zones`: lists every zone, with a click to teleport
- `/wildencounter zone delete <name>`
- `/wildencounter test`: starts an encounter where you stand
- `/wildencounter reset events [player]`: Encounter Events start over
- `/wildencounter pos1 [x y z]` and `/wildencounter pos2 [x y z]`: set the corners of a zone selection without a
  Bone, where you stand or at the coordinates (`~` works). The selection shows, and crouching twice makes it a Wild
  Zone, like with a Bone.
- `/wildencounter zonedit [on|off]`: zone edit mode. The zones show, and a right click while looking through one
  opens its menu, whatever you hold. Elsewhere right click works as usual; crouch to use an item or place a block
  inside a zone. It ends when you turn it off or leave the world.

## Building

```
./gradlew build
```

Gradle 8.10 needs Java 21 to run (set `JAVA_HOME` to a JDK 21 if your default Java is newer). The jar is in
`build/libs`.

The Cobblemon icons come from the E19 Cobblemon Minimap Icons pack (MPL-2.0, see NOTICE). To refresh them, for
example after a Cobblemon update adds forms:

```
python tools/import_icons.py <Cobblemon jar> icons src/main/resources
```

It copies only the icons that match a species or form in that Cobblemon jar, and rewrites
`assets/awildencounter/icons/index.json`.

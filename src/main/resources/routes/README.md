# Bundled walking route data

Upstream: https://github.com/Skretzo/shortest-path
Pinned commit: `31c2e313a348a00fb49dfbee53461610681f94d6`

The original files are committed here unchanged. Their upstream paths, sizes,
and SHA-256 checksums are recorded in `upstream-manifest.json`. The exact
upstream BSD 2-Clause license is retained in `UPSTREAM-LICENSE.txt`; its
unfilled copyright placeholders are present in the upstream original.

Runtime loading is entirely offline and uses Java's ZIP and text readers.
No separate plugin, custom Gradle repository, native library, or runtime
download is needed.

* `collision-map.zip`: 1,624,418 bytes compressed, 2,783 regions, 10,044,676
  bytes of trimmed region flags (10,758,144 bytes if padded to full planes).
* `transports.tsv`: 5,610 directed physical passage records for doors,
  gates, stairs, ladders, entrances, exits, and other local links.
* `agility_shortcuts.tsv`: 661 directed shortcut records.
* `item-variations.tsv`: named item families used by the requirement parser.
* `config-items.tsv`: upstream descriptions documenting special unlock
  declarations; it is reference data and does not enable manual unlocks.

The other upstream transport families are deliberately not loaded: a route
starts at one confirmed POH arrival, then uses ordinary movement and the
two physical passage tables. It cannot add another inventory, spellbook,
minigame, fairy ring, spirit tree, or POH teleport halfway through a route.
Boats, charters, gliders, and similar separate network tables are also omitted.

## Collision encoding

Each ZIP entry is named `regionX_regionY`. Its bytes encode a little-endian
BitSet, with the bit index

```
((plane * 4096) + localY * 64 + localX) * 4 + flag
```

Flag 0 is an open north edge; flag 1 is an open east edge. Flags 2 and 3
record structural north/east boundaries, even when an ordinary door is open
for routing. South and west use the adjacent tile's north/east edge.
A diagonal requires all four cardinal edges around that corner. Missing
regions, planes, and trailing zero bits grant no movement.

Some catalogued teleport landings occupy a solid fixture tile in the map.
Only those explicit arrival tiles may exit to a walkable cardinal neighbor,
and structural walls and passage locks still block that exit. Solid task
objects may be reached from a mapped interaction face within their configured
radius; every intervening boundary must be free of structural walls and locks.
Neither rule grants ordinary walking through an occupied tile.

The dumper intentionally opens normal doors. Every adjacent explicit
passage pair is therefore blocked for ordinary walking before requirements
are evaluated, in both directions. Only eligible directed passage records
can cross it. This also applies when a passage's requirements cannot be
parsed, so an unsupported lock cannot create an unrestricted door bypass.

## Passage schema and conservative requirements

Headers determine column positions. Coordinates are `x y plane`; blank
rows and comments are ignored. Separate records provide the two directions.
No reversal, global-origin permutation, or implicit teleport is invented.

Supported condition columns are Skills, Items, Quests, Varbits, and VarPlayers.
All conditions must pass against an immutable account snapshot. Unknown
requirements fail closed. Item quantities retain each OR branch's own
quantity; bank contents do not count as carried equipment. Unobservable
permanent unlocks are withheld unless an independently verified alternative
passes. A missing duration or zero duration costs at least one tick.

These files describe a static routing graph, not the complete live server.
New maps, dynamic instances, unlisted locks, and changed server mechanics
need a reviewed data update. Unreachable or incomplete searches must remain
unknown rather than reverting to straight-line ranking.

## Updating

1. Select and record one new upstream commit for code-format review and all
   resources together.
2. Review `pathfinder/SplitFlagMap.java`, `CollisionMap.java`,
   `transport/parser/`, requirement handling, and changes to these data files.
3. Copy the original files and license unchanged, and regenerate the manifest
   checksums and footprint figures.
4. Run the finite route, requirement, gate, stairs, and real-map regression
   tests. Check representative routes in a user-started client separately.

The local `.upstream/shortest-path-<commit>/` checkout is a research snapshot,
not a source or runtime dependency, and should remain ignored by Git.


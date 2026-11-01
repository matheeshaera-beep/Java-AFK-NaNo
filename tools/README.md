# tools

`gen_packet_ids.py` regenerates
`android/engine/src/main/kotlin/dev/mstheesha/afk/engine/PacketIds.kt` from
PrismarineJS `minecraft-data`: every release from 1.8 through the newest
protocol that upstream has packet data for, mapped to a flat slot index.

```sh
# needs the minecraft-data package checked out (or npm-installed) somewhere
python tools/gen_packet_ids.py --data /path/to/minecraft-data
```

The output is committed, so a normal build never needs Node or network access.

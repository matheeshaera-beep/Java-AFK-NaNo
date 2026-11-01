#!/usr/bin/env python3
"""Generate PacketIds.kt from PrismarineJS minecraft-data.

Usage:
    python tools/gen_packet_ids.py [--data PATH] [--out PATH]

`--data` points at a minecraft-data checkout/package root (the directory that
contains `data/dataPaths.json`). Default: ../.mcdata or the MCDATA_DIR env var.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

RELEASE_NAME = re.compile(r"^\d+\.\d+(\.\d+)?$")

# (state, direction, SLOT_NAME, [candidate type names in priority order])
SLOTS = [
    ("play", "toClient", "PLAY_S2C_KEEP_ALIVE", ["packet_keep_alive"]),
    ("play", "toClient", "PLAY_S2C_JOIN_GAME", ["packet_login"]),
    ("play", "toClient", "PLAY_S2C_DISCONNECT", ["packet_kick_disconnect"]),
    ("play", "toClient", "PLAY_S2C_POSITION", ["packet_position"]),
    ("play", "toClient", "PLAY_S2C_UPDATE_HEALTH", ["packet_update_health"]),
    ("play", "toClient", "PLAY_S2C_RESPAWN", ["packet_respawn"]),
    ("play", "toClient", "PLAY_S2C_DEATH_COMBAT", ["packet_death_combat"]),
    ("play", "toClient", "PLAY_S2C_CHAT", ["packet_chat", "packet_player_chat"]),
    ("play", "toClient", "PLAY_S2C_SYSTEM_CHAT", ["packet_system_chat"]),
    ("play", "toClient", "PLAY_S2C_DISGUISED_CHAT",
     ["packet_profileless_chat", "packet_disguised_chat"]),
    ("play", "toClient", "PLAY_S2C_PING", ["packet_ping"]),
    ("play", "toClient", "PLAY_S2C_RESOURCE_PACK",
     ["packet_resource_pack_send", "packet_add_resource_pack",
      "packet_common_add_resource_pack"]),
    ("play", "toClient", "PLAY_S2C_REMOVE_RESOURCE_PACK",
     ["packet_common_remove_resource_pack", "packet_remove_resource_pack"]),
    ("play", "toClient", "PLAY_S2C_COOKIE_REQUEST",
     ["packet_common_cookie_request", "packet_cookie_request"]),
    ("play", "toClient", "PLAY_S2C_TRANSFER",
     ["packet_common_transfer", "packet_transfer"]),
    ("play", "toClient", "PLAY_S2C_START_CONFIGURATION",
     ["packet_start_configuration"]),
    ("play", "toClient", "PLAY_S2C_CHUNK_BATCH_START", ["packet_chunk_batch_start"]),

    ("play", "toServer", "PLAY_C2S_KEEP_ALIVE", ["packet_keep_alive"]),
    ("play", "toServer", "PLAY_C2S_CHAT", ["packet_chat", "packet_chat_message"]),
    ("play", "toServer", "PLAY_C2S_CHAT_COMMAND", ["packet_chat_command"]),
    ("play", "toServer", "PLAY_C2S_SETTINGS",
     ["packet_settings", "packet_client_information", "packet_common_settings"]),
    ("play", "toServer", "PLAY_C2S_POSITION", ["packet_position"]),
    ("play", "toServer", "PLAY_C2S_POSITION_LOOK", ["packet_position_look"]),
    ("play", "toServer", "PLAY_C2S_LOOK", ["packet_look"]),
    ("play", "toServer", "PLAY_C2S_FLYING", ["packet_flying"]),
    ("play", "toServer", "PLAY_C2S_PONG", ["packet_pong"]),
    ("play", "toServer", "PLAY_C2S_RESOURCE_PACK_RECEIVE",
     ["packet_resource_pack_receive"]),
    ("play", "toServer", "PLAY_C2S_CUSTOM_PAYLOAD", ["packet_custom_payload"]),
    ("play", "toServer", "PLAY_C2S_TELEPORT_CONFIRM", ["packet_teleport_confirm"]),
    ("play", "toServer", "PLAY_C2S_CLIENT_COMMAND", ["packet_client_command"]),
    ("play", "toServer", "PLAY_C2S_TICK_END", ["packet_tick_end"]),
    ("play", "toServer", "PLAY_C2S_CONFIGURATION_ACK",
     ["packet_configuration_acknowledged"]),

    ("login", "toClient", "LOGIN_S2C_DISCONNECT", ["packet_disconnect"]),
    ("login", "toClient", "LOGIN_S2C_ENCRYPTION_REQUEST", ["packet_encryption_begin"]),
    ("login", "toClient", "LOGIN_S2C_SUCCESS", ["packet_success"]),
    ("login", "toClient", "LOGIN_S2C_COMPRESS", ["packet_compress"]),
    ("login", "toClient", "LOGIN_S2C_PLUGIN_REQUEST",
     ["packet_custom_query", "packet_login_plugin_request"]),
    ("login", "toClient", "LOGIN_S2C_COOKIE_REQUEST",
     ["packet_common_cookie_request", "packet_cookie_request"]),

    ("login", "toServer", "LOGIN_C2S_LOGIN_START", ["packet_login_start"]),
    ("login", "toServer", "LOGIN_C2S_ENCRYPTION_RESPONSE", ["packet_encryption_begin"]),
    ("login", "toServer", "LOGIN_C2S_PLUGIN_RESPONSE",
     ["packet_custom_query_answer", "packet_login_plugin_response"]),
    ("login", "toServer", "LOGIN_C2S_COOKIE_RESPONSE",
     ["packet_common_cookie_response", "packet_cookie_response"]),
    ("login", "toServer", "LOGIN_C2S_LOGIN_ACKNOWLEDGED",
     ["packet_login_acknowledged"]),

    ("configuration", "toClient", "CONFIG_S2C_DISCONNECT", ["packet_disconnect"]),
    ("configuration", "toClient", "CONFIG_S2C_FINISH", ["packet_finish_configuration"]),
    ("configuration", "toClient", "CONFIG_S2C_KEEP_ALIVE", ["packet_keep_alive"]),
    ("configuration", "toClient", "CONFIG_S2C_PING", ["packet_ping"]),
    ("configuration", "toClient", "CONFIG_S2C_CUSTOM_PAYLOAD", ["packet_custom_payload"]),
    ("configuration", "toClient", "CONFIG_S2C_RESOURCE_PACK",
     ["packet_common_add_resource_pack", "packet_add_resource_pack",
      "packet_resource_pack_send"]),
    ("configuration", "toClient", "CONFIG_S2C_REMOVE_RESOURCE_PACK",
     ["packet_common_remove_resource_pack", "packet_remove_resource_pack"]),
    ("configuration", "toClient", "CONFIG_S2C_COOKIE_REQUEST",
     ["packet_common_cookie_request", "packet_cookie_request"]),
    ("configuration", "toClient", "CONFIG_S2C_TRANSFER",
     ["packet_common_transfer", "packet_transfer"]),
    ("configuration", "toClient", "CONFIG_S2C_REGISTRY_DATA", ["packet_registry_data"]),
    ("configuration", "toClient", "CONFIG_S2C_FEATURE_FLAGS", ["packet_feature_flags"]),
    ("configuration", "toClient", "CONFIG_S2C_SELECT_KNOWN_PACKS",
     ["packet_common_select_known_packs"]),

    ("configuration", "toServer", "CONFIG_C2S_SETTINGS",
     ["packet_settings", "packet_client_information", "packet_common_settings"]),
    ("configuration", "toServer", "CONFIG_C2S_FINISH", ["packet_finish_configuration"]),
    ("configuration", "toServer", "CONFIG_C2S_KEEP_ALIVE", ["packet_keep_alive"]),
    ("configuration", "toServer", "CONFIG_C2S_PONG", ["packet_pong"]),
    ("configuration", "toServer", "CONFIG_C2S_RESOURCE_PACK_RECEIVE",
     ["packet_resource_pack_receive"]),
    ("configuration", "toServer", "CONFIG_C2S_CUSTOM_PAYLOAD", ["packet_custom_payload"]),
    ("configuration", "toServer", "CONFIG_C2S_SELECT_KNOWN_PACKS",
     ["packet_common_select_known_packs"]),
    ("configuration", "toServer", "CONFIG_C2S_COOKIE_RESPONSE",
     ["packet_common_cookie_response", "packet_cookie_response"]),
    ("configuration", "toClient", "CONFIG_S2C_CODE_OF_CONDUCT",
     ["packet_code_of_conduct"]),
    ("configuration", "toServer", "CONFIG_C2S_ACCEPT_CODE_OF_CONDUCT",
     ["packet_accept_code_of_conduct"]),

    ("status", "toClient", "STATUS_S2C_SERVER_INFO", ["packet_server_info"]),
    ("status", "toClient", "STATUS_S2C_PING", ["packet_ping"]),
    ("status", "toServer", "STATUS_C2S_PING_START", ["packet_ping_start"]),
    ("status", "toServer", "STATUS_C2S_PING", ["packet_ping"]),
    ("handshaking", "toServer", "HANDSHAKE_C2S_SET_PROTOCOL", ["packet_set_protocol"]),
]


def packet_table(protocol: dict, state: str, direction: str) -> dict[str, int]:
    """type name -> packet id for one state/direction."""
    try:
        types = protocol[state][direction]["types"]
    except KeyError:
        return {}
    packet = types.get("packet")
    if not packet:
        return {}
    fields = packet[1]
    name_field = next(f for f in fields if f["name"] == "name")
    mapper = name_field["type"][1]["mappings"]
    switch = next(f for f in fields if f["name"] == "params")["type"][1]["fields"]
    out: dict[str, int] = {}
    for raw_id, enum_name in mapper.items():
        pid = int(raw_id, 16) if raw_id.lower().startswith("0x") else int(raw_id)
        type_name = switch.get(enum_name)
        if isinstance(type_name, str):
            out[type_name] = pid
    return out


def version_key(v: str):
    parts = []
    for chunk in v.split("."):
        num = ""
        for ch in chunk:
            if ch.isdigit():
                num += ch
            else:
                break
        parts.append(int(num) if num else 0)
    return tuple(parts)


def main() -> int:
    repo = Path(__file__).resolve().parent.parent
    default_data = Path(os.environ.get("MCDATA_DIR", repo / ".mcdata"))
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", type=Path, default=default_data)
    ap.add_argument("--out", type=Path,
                    default=repo / "android/engine/src/main/kotlin/dev/mstheesha/afk/engine/PacketIds.kt")
    args = ap.parse_args()

    data_root = args.data
    if not (data_root / "data/dataPaths.json").is_file():
        print(f"error: {data_root}/data/dataPaths.json not found", file=sys.stderr)
        return 2

    data_paths = json.loads((data_root / "data/dataPaths.json").read_text())["pc"]
    versions = json.loads((data_root / "data/pc/common/protocolVersions.json").read_text())

    # protocol -> best release version name, for every release >= 1.8 with data
    best: dict[int, str] = {}
    for entry in versions:
        name = entry["minecraftVersion"]
        if entry.get("releaseType") not in (None, "release"):
            continue
        if not RELEASE_NAME.match(name):
            continue
        if version_key(name) < (1, 8):
            continue
        if name not in data_paths:
            continue
        proto = int(entry["version"])
        if proto not in best or version_key(name) > version_key(best[proto]):
            best[proto] = name

    missing = [v["minecraftVersion"] for v in versions
               if v.get("releaseType") == "release"
               and version_key(v["minecraftVersion"]) >= (1, 8)
               and int(v["version"]) not in best]
    if missing:
        print(f"note: no protocol data yet for {', '.join(sorted(missing, key=version_key))}",
              file=sys.stderr)

    protocols = sorted(best)
    rows: list[tuple[int, str, list[int]]] = []
    cache: dict[str, dict[str, int]] = {}
    for proto in protocols:
        name = best[proto]
        proto_dir = data_paths[name]["protocol"]
        if proto_dir not in cache:
            cache[proto_dir] = json.loads(
                (data_root / "data" / proto_dir / "protocol.json").read_text())
        protocol = cache[proto_dir]
        ids: list[int] = []
        for state, direction, _slot, candidates in SLOTS:
            table = packet_table(protocol, state, direction)
            ids.append(next((table[c] for c in candidates if c in table), -1))
        rows.append((proto, name, ids))

    by_content: dict[tuple, str] = {}
    lines: list[str] = []
    lines.append("// GENERATED FILE - DO NOT EDIT.")
    lines.append("// Source: PrismarineJS minecraft-data (pc protocol data).")
    lines.append("// Regenerate with: python tools/gen_packet_ids.py")
    lines.append("package dev.mstheesha.afk.engine")
    lines.append("")
    lines.append("/** Flat slot indices into [PacketIds.ids]. */")
    lines.append("object P {")
    for index, (_state, _direction, slot, _c) in enumerate(SLOTS):
        lines.append(f"    const val {slot} = {index}")
    lines.append(f"    const val COUNT = {len(SLOTS)}")
    lines.append("}")
    lines.append("")
    lines.append("/** Packet ids for one protocol version; -1 means the packet does not exist there. */")
    lines.append("class PacketIds internal constructor(")
    lines.append("    val protocol: Int,")
    lines.append("    val version: String,")
    lines.append("    private val ids: IntArray,")
    lines.append(") {")
    lines.append("    fun id(slot: Int): Int = ids[slot]")
    lines.append("    fun has(slot: Int): Boolean = ids[slot] >= 0")
    lines.append("}")
    lines.append("")
    lines.append("object PacketTable {")
    names: list[str] = []
    for proto, name, ids in rows:
        key = tuple(ids)
        field = "v" + name.replace(".", "_").replace("-", "_")
        if key in by_content:
            lines.append(f"    private val {field} = {by_content[key]}")
        else:
            body = ", ".join(str(i) for i in ids)
            lines.append(f"    private val {field} = PacketIds({proto}, \"{name}\", intArrayOf({body}))")
            by_content[key] = field
        names.append((proto, name, field))

    lines.append("")
    lines.append("    private val byProtocol: Map<Int, PacketIds> = mapOf(")
    for proto, _name, field in names:
        lines.append(f"        {proto} to {field},")
    lines.append("    )")
    lines.append("")
    lines.append("    private val byVersionName: Map<String, Int> = mapOf(")
    for proto, name, _field in names:
        lines.append(f"        \"{name}\" to {proto},")
    lines.append("    )")
    lines.append("")
    lines.append("    fun forProtocol(protocol: Int): PacketIds? = byProtocol[protocol]")
    lines.append("")
    lines.append("    fun protocolForName(name: String): Int? = byVersionName[name]")
    lines.append("")
    lines.append(f"    val latest: PacketIds = {names[-1][2]}")
    lines.append(f"    val supportedVersions: String = \"{names[0][1]} - {names[-1][1]}\"")
    lines.append("}")
    lines.append("")

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {args.out} ({len(rows)} protocols, {len(SLOTS)} slots)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

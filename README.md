# OC2R Wireless PubSub

OC2R addon for Forge `1.20.1` that adds:
- `Wireless Relay` block (powered broker node).
- `Wireless Card` (OC2R card-slot RPC device).
- Topic-style long-range messaging through a sender-side local relay.

## Verification
- `./gradlew verifyFast`
- `./gradlew verifyFull`

`verifyFull` currently matches `verifyFast`; this repo has JVM tests and coverage gates, but no separate Forge GameTest lane yet.

## Runtime Requirements
- Minecraft Forge `1.20.1`.
- OpenComputers II: Reimagined (`oc2r`) for `1.20.1`.

## Wireless Card RPC API
The card exposes these methods through OC2R's `devices` Lua library:
- `open(topic)` -> `topic`
- `send(topic, payload)` -> `{energyCost, payloadBytes}`
- `push(topic, payload)` -> alias of `send`
- `poll(topic, max, consumerId?)` -> `{...messages}`
- `pop(topic, max, consumerId?)` -> alias of `poll`
- `pollMatch(pattern, maxPerTopic, consumerId?)` -> `{ \"topic|payload\", ... }`
- `listTopics()` -> `{...topics}`
- `getTopicDepth(topic)` -> `int`

Notes:
- Topics are persisted separately for each Minecraft dimension. A sender needs one loaded relay within 16 blocks; receivers need no relay, and there are no relay chains, receiver charges, distance tolls, or cross-dimension access.
- Accepted `send` calls charge only the sender-side relay once. Polling, listing, and delivery do not charge energy. Rejected topic/payload/storage requests charge nothing.
- Topics, payload bytes, backlog, consumer offsets, and wildcard query work are bounded: 128 topics, 4096 UTF-8 payload bytes, 256 queued messages/topic, 128 consumers/topic, and 32 wildcard-matched topics/call.
- Empty polls never create persistent topic or consumer state. Relay associations are cached only while their chunks are loaded; moved or removed relays are rediscovered without chunk loading.
- Legacy global broker records without a trustworthy dimension are retained in a quarantine field and are not delivered. Delivery is at-least-once per consumer offset until that offset is persisted; a crash may repeat the final delivered batch.
- `consumerId` enables lightweight consumer groups with independent offsets. Wildcards in `pollMatch` support `*` and `?`.

### Lua Example
```lua
local devices = require("devices")
local wifi = devices:find("wireless_card")[1]

wifi:send("alerts", "hello from node A")

local msgs = wifi:poll("alerts", 8, "ops-console")
for i, msg in ipairs(msgs) do
  print(i, msg)
end
```

## Lua Helpers
Helper scripts are provided in [assets/oc2r_wireless_pubsub/lua](src/main/resources/assets/oc2r_wireless_pubsub/lua):
- `wifi.lua` library
- `wifi-send.lua`
- `wifi-topics.lua`
- `wifi-tail.lua`

## Community and support

For modpack and mod discussion, playtest feedback, and bug reports, join the [Better Content Discord](https://discord.gg/EkRnZbzqS9).

## Identity

The clean-break canonical identity is repository/artifact `oc2r-wireless-pubsub`, mod ID and resource namespace `oc2r_wireless_pubsub`, and Maven group `com.bettercontent`. Legacy `oc2rwireless` worlds and configs are not migrated.

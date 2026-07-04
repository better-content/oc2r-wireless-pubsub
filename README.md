# OC2R Wireless

OC2R addon for Forge `1.20.1` that adds:
- `Wireless Relay` block (powered broker node).
- `Wireless Card` (OC2R card-slot RPC device).
- Topic-style long-range messaging with linear energy cost by distance.

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
- Topic space is global and JVM-memory-only (not persisted to world saves).
- `consumerId` enables lightweight consumer groups with independent offsets.
- Wildcards in `pollMatch` support `*` and `?`.

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
Helper scripts are provided in [assets/oc2rwireless/lua](/home/gerald/mcmods/deferred/oc2rwireless/src/main/resources/assets/oc2rwireless/lua):
- `wifi.lua` library
- `wifi-send.lua`
- `wifi-topics.lua`
- `wifi-tail.lua`

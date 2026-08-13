local devices = require("devices")

local M = {}

local function card()
  local cards = devices:find("wireless_card")
  if not cards or #cards == 0 then
    error("no wireless_card found")
  end
  return cards[1]
end

function M.topics()
  return card():listTopics()
end

function M.send(topic, payload)
  return card():send(topic, payload)
end

function M.poll(topic, max, consumer)
  max = max or 16
  return card():poll(topic, max, consumer)
end

function M.tail(topic, consumer)
  while true do
    local messages = M.poll(topic, 16, consumer)
    for _, msg in ipairs(messages) do
      print(msg)
    end
    os.sleep(0.25)
  end
end

function M.poll_match(pattern, max_per_topic, consumer)
  max_per_topic = max_per_topic or 8
  return card():pollMatch(pattern, max_per_topic, consumer)
end

return M

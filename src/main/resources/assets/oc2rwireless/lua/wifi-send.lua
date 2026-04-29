local wifi = require("wifi")

if #arg < 2 then
  print("usage: wifi-send <topic> <message>")
  return
end

local topic = arg[1]
local message = table.concat(arg, " ", 2)
local result = wifi.send(topic, message)
print(string.format("sent: topic=%s energy=%d bytes=%d", topic, result[1], result[2]))

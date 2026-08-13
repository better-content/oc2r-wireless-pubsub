local wifi = require("wifi")

if #arg < 1 then
  print("usage: wifi-tail <topic> [consumerId]")
  return
end

wifi.tail(arg[1], arg[2])

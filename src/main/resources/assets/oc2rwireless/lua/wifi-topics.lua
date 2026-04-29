local wifi = require("wifi")

for _, topic in ipairs(wifi.topics()) do
  print(topic)
end

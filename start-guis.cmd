@echo off
echo Opening 3 GUI clients...
start "gui9091" java -cp "target\classes;target\easy-db-server.jar" client.gui.GuiClient 127.0.0.1 9091
start "gui9092" java -cp "target\classes;target\easy-db-server.jar" client.gui.GuiClient 127.0.0.1 9092
start "gui9093" java -cp "target\classes;target\easy-db-server.jar" client.gui.GuiClient 127.0.0.1 9093
echo Done!

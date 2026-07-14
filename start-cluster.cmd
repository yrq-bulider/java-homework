@echo off
echo Starting 3 node cluster...
echo.

start "node1" cmd /k java -cp target\easy-db-server.jar server.EasyDbServer --port 9091 --cluster --cluster-config cluster-node1.json
start "node2" cmd /k java -cp target\easy-db-server.jar server.EasyDbServer --port 9092 --cluster --cluster-config cluster-node2.json
start "node3" cmd /k java -cp target\easy-db-server.jar server.EasyDbServer --port 9093 --cluster --cluster-config cluster-node3.json

echo All 3 nodes started!
echo Close each cmd window to stop the cluster.

#!/bin/bash
set -e

echo "========================================"
echo "JADE Main Container Startup"
echo "========================================"

# Show network config
echo "Container IP:"
hostname -i

# Compile agents
echo ""
echo "Compiling agents..."
javac -cp lib/jade.jar -d classes \
  src/agents/CentralServerAgent.java \
  src/agents/LocalAgent.java \
  src/agents/MobileAuditAgent.java 2>&1

if [ $? -eq 0 ]; then
    echo "✓ Compilation successful"
else
    echo "✗ Compilation failed"
    exit 1
fi

echo ""
echo "========================================"
echo "Starting JADE Main Container..."
echo "========================================"
echo "Configuration:"
echo "  - Host: 172.20.0.10"
echo "  - Local Port: 1099 (JICP)"
echo "  - Services Port: 1100"
echo ""

# Start JADE main container with JICP (no separate RMI registry needed)
exec java \
  -Djava.net.preferIPv4Stack=true \
  -cp lib/jade.jar:classes \
  jade.Boot \
  -host 172.20.0.10 \
  -local-port 1099 \
  -services jade.core.mobility.AgentMobility \
  server:agents.CentralServerAgent
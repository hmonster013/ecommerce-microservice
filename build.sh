#!/bin/bash

echo "Building E-commerce Microservices Platform..."

echo ""
echo "[1/3] Cleaning previous builds..."
./mvnw clean

echo ""
echo "[2/3] Building all modules..."
./mvnw package -DskipTests

echo ""
echo "Checking JAR files..."
find . -name "*.jar" -path "*/target/*"

echo ""
echo "[3/3] Building Docker images..."
docker-compose build

echo ""
echo "Build completed successfully!"
echo ""
echo "To start the platform:"
echo "  docker-compose up -d"
echo ""
echo "To view logs:"
echo "  docker-compose logs -f"
echo ""
echo "To stop the platform:"
echo "  docker-compose down"

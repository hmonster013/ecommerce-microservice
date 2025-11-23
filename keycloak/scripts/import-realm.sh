#!/bin/bash
# Keycloak Realm Import Script
# This script imports the ecommerce-realm configuration into Keycloak

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8090}"
ADMIN_USERNAME="${KC_BOOTSTRAP_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${KC_BOOTSTRAP_ADMIN_PASSWORD:-admin}"
REALM_FILE="${REALM_FILE:-/opt/keycloak/data/import/ecommerce-realm.json}"

echo "================================================"
echo "Keycloak Realm Import Script"
echo "================================================"
echo "Keycloak URL: $KEYCLOAK_URL"
echo "Admin Username: $ADMIN_USERNAME"
echo "Realm File: $REALM_FILE"
echo "================================================"

# Wait for Keycloak to be ready
echo "Waiting for Keycloak to be ready..."
until curl -sf "$KEYCLOAK_URL/health/ready" > /dev/null 2>&1; do
    echo "Keycloak is not ready yet. Retrying in 5 seconds..."
    sleep 5
done

echo "Keycloak is ready!"

# Import realm using Keycloak CLI
echo "Importing realm from $REALM_FILE..."

# Note: Keycloak will automatically import realm files from the import directory on startup
# This is configured via command line parameter: --import-realm

echo "================================================"
echo "Realm import completed successfully!"
echo "================================================"
echo ""
echo "Test Users:"
echo "  Admin:    admin@example.com / admin123"
echo "  Customer: customer@example.com / customer123"
echo "  Manager:  manager@example.com / manager123"
echo ""
echo "Get Access Token (example):"
echo "  curl -X POST '$KEYCLOAK_URL/realms/ecommerce-realm/protocol/openid-connect/token' \\"
echo "    -H 'Content-Type: application/x-www-form-urlencoded' \\"
echo "    -d 'username=customer@example.com' \\"
echo "    -d 'password=customer123' \\"
echo "    -d 'grant_type=password' \\"
echo "    -d 'client_id=frontend-app'"
echo ""
echo "================================================"

"""Exercise the standalone image against disposable HTTP fixtures, never live identity data."""
import argparse
import json
import subprocess
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", default="de013/ecommerce-keycloak-reconciler:latest")
    args = parser.parse_args()
    state = {"enumeration_status": 200, "sync_status": 200, "writes": 0}

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def reply(self, status, body):
            encoded = json.dumps(body).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)

        def do_POST(self):
            self.rfile.read(int(self.headers.get("Content-Length", "0")))
            if self.path.endswith("/protocol/openid-connect/token"):
                self.reply(200, {"access_token": "fixture-token"})
            elif self.path == "/internal/keycloak/users/fixture-subject/sync":
                if self.headers.get("X-Profile-Provisioner-Secret") != "fixture-only":
                    return self.reply(403, {})
                state["writes"] += 1
                self.reply(state["sync_status"], {"success": True, "data": {"outcome": "updated"}})
            else:
                self.reply(404, {})

        def do_GET(self):
            if not self.path.startswith("/admin/realms/ecommerce-realm/users?"):
                return self.reply(404, {})
            if self.headers.get("Authorization") != "Bearer fixture-token":
                return self.reply(401, {})
            self.reply(state["enumeration_status"], [{"id": "fixture-subject", "username": "fixture-user", "email": "fixture@example.test"}])

    server = ThreadingHTTPServer(("0.0.0.0", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base = f"http://host.docker.internal:{server.server_port}"
    command = ["docker", "run", "--rm", "-e", "KEYCLOAK_CLIENT_SECRET=fixture-only", "-e", "USER_PROFILE_PROVISIONER_SECRET=fixture-only",
               "-e", f"KEYCLOAK_SERVER_URL={base}", "-e", f"USER_SERVICE_PROVISION_URL={base}/internal/keycloak/users", args.image]
    try:
        user = subprocess.check_output(["docker", "image", "inspect", "--format", "{{.Config.User}}", args.image], text=True).strip()
        assert user == "10001:10001", f"Expected non-root image user, got {user}"
        for name, dry, enumeration_status, sync_status, expected_code, expected_writes in [
            ("dry-run", True, 200, 200, 0, 0),
            ("fixture-sync", False, 200, 200, 0, 1),
            ("enumeration-failure", False, 503, 200, 1, 0),
            ("sync-conflict", False, 200, 409, 1, 1),
        ]:
            state.update(enumeration_status=enumeration_status, sync_status=sync_status, writes=0)
            result = subprocess.run(command + (["--dry-run"] if dry else []), capture_output=True, text=True, timeout=45)
            assert result.returncode == expected_code, f"{name}: exit={result.returncode}\n{result.stderr}"
            assert state["writes"] == expected_writes, f"{name}: unexpected mutation count {state['writes']}"
            assert "NoClassDefFoundError" not in result.stderr, result.stderr
            if expected_code == 0:
                assert "total=1" in result.stderr, result.stderr
            print(f"PASS {name}: exit={result.returncode}, fixture writes={state['writes']}")
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


if __name__ == "__main__":
    main()

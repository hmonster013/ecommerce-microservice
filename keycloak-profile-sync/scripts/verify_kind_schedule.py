"""Opt-in local kind acceptance: real scheduled recovery of one owned fixture.

Never use against a shared/production cluster. SQL is used only for read-only
preflight and deliberate corruption/cleanup of the freshly created fixture.
Credentials stay in memory; output/artifacts contain counters, not profile data.
"""
import argparse
import hashlib
import json
import pathlib
import re
import secrets
import subprocess
import threading
import traceback
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone


def scheduled_job_metadata(event, owner_uid, previous_uids):
    """Manual --from=cronjob Jobs have an owner but no scheduled timestamp."""
    meta = event.get('object', {}).get('metadata', {})
    timestamp = meta.get('annotations', {}).get('batch.kubernetes.io/cronjob-scheduled-timestamp')
    if event.get('type') == 'ADDED' and timestamp and meta.get('uid') not in previous_uids and any(
            owner.get('uid') == owner_uid and owner.get('controller')
            for owner in meta.get('ownerReferences', [])):
        return meta
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--kubeconfig', required=True)
    parser.add_argument('--artifact', required=True)
    parser.add_argument('--test-mode', action='store_true')
    parser.add_argument('--allow-fixture-db-drift', action='store_true')
    parser.add_argument('--resume-cronjob', action='store_true')
    args = parser.parse_args()
    assert args.test_mode and args.allow_fixture_db_drift and args.resume_cronjob
    backend = pathlib.Path(__file__).resolve().parents[2]
    values = {}
    for line in (backend / '.env').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip().strip('"\'')
    assert values['KEYCLOAK_PROFILE_REALM'] == 'ecommerce-realm'
    k = ['kubectl', '--kubeconfig', args.kubeconfig, '--context',
         'kind-ecommerce-profile-sync', '-n', 'profile-sync-dev']
    cron = 'keycloak-profile-reconciliation'
    run = 'kind-schedule-' + secrets.token_hex(4)
    evidence = {'run': run, 'checks': {}, 'jobs': [], 'started': datetime.now(timezone.utc).isoformat()}
    artifact = pathlib.Path(args.artifact)
    owned = None
    resumed = False
    succeeded = False
    token = None

    def save():
        artifact.write_text(json.dumps(evidence, indent=2), encoding='utf-8')

    def command(argv, data=None, timeout=60):
        result = subprocess.run(argv, input=data, text=True, capture_output=True, timeout=timeout)
        if result.returncode:
            raise RuntimeError('Command failed: ' + argv[0] + ' (output withheld)')
        return result.stdout

    def docker_env(container):
        entries = json.loads(command(['docker', 'inspect', '--format', '{{json .Config.Env}}', container]))
        return dict(entry.split('=', 1) for entry in entries)

    pg = docker_env('ecommerce-postgres')

    def sql(statement):
        result = command(['docker', 'exec', '-i', 'ecommerce-postgres', 'psql', '-X',
                          '-v', 'ON_ERROR_STOP=1', '-U', pg['POSTGRES_USER'],
                          '-d', 'user_service_db', '-Atq'], statement)
        return json.loads(result) if result.strip() else None

    def rows():
        return sql("BEGIN READ ONLY; SELECT COALESCE(json_agg(t),'[]'::json) FROM "
                   "(SELECT * FROM users ORDER BY id) t; COMMIT;")

    def literal(value):
        return "'" + str(value).replace("'", "''") + "'"

    def http(url, method='GET', body=None, headers=None):
        request = urllib.request.Request(url, method=method, data=body, headers=headers or {})
        with urllib.request.urlopen(request, timeout=20) as response:
            data = response.read()
            return response.status, dict(response.headers), json.loads(data) if data else None

    def admin(path, method='GET', body=None):
        nonlocal token
        # Refresh short-lived admin credentials per operation; never store a token on disk.
        kc = docker_env('ecommerce-keycloak')
        form = urllib.parse.urlencode({
            'client_id': 'admin-cli', 'grant_type': 'password',
            'username': kc.get('KC_BOOTSTRAP_ADMIN_USERNAME', kc.get('KEYCLOAK_ADMIN')),
            'password': kc.get('KC_BOOTSTRAP_ADMIN_PASSWORD', kc.get('KEYCLOAK_ADMIN_PASSWORD')),
        }).encode()
        token = http('http://localhost:8090/realms/master/protocol/openid-connect/token', 'POST', form)[2]['access_token']
        return http('http://localhost:8090/admin/realms/ecommerce-realm' + path, method,
                    json.dumps(body).encode() if body is not None else None,
                    {'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})

    def sync(subject):
        return http('http://localhost:8081/internal/keycloak/users/' + subject + '/sync', 'POST',
                    headers={'X-Profile-Provisioner-Secret': values['USER_PROFILE_PROVISIONER_SECRET']})

    def run_manual(name, dry):
        job = json.loads(command(k + ['create', 'job', name, '--from=cronjob/' + cron,
                                      '--dry-run=client', '-o', 'json']))
        job['spec']['ttlSecondsAfterFinished'] = 3600
        if dry:
            job['spec']['template']['spec']['containers'][0]['args'] = ['--dry-run']
        command(k + ['create', '-f', '-'], json.dumps(job))
        return complete(name)

    def complete(name):
        command(k + ['wait', '--for=condition=complete', 'job/' + name, '--timeout=250s'], timeout=270)
        log = command(k + ['logs', 'job/' + name])
        summary = next((line for line in log.splitlines() if 'Reconciliation: total=' in line), None)
        assert summary and 'conflicts=0,' in summary and 'failed=0,' in summary
        counters = {key: int(value) for key, value in re.findall(r'(total|created|updated|unchanged|conflicts|skipped|failed)=(\d+)', summary)}
        evidence['jobs'].append({'name': name, 'counters': counters, 'dryRun': 'dryRun=true' in summary})
        save()
        return counters

    def next_scheduled_job(uid, previous_uids):
        # Watch actual controller events instead of sleeping/polling for a schedule.
        process = subprocess.Popen(k + ['get', 'jobs', '--watch', '--output-watch-events', '-o', 'json'],
                                   stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        timer = threading.Timer(390, process.kill)
        timer.start()
        buffer = ''
        decoder = json.JSONDecoder()
        try:
            while True:
                char = process.stdout.read(1)
                if not char:
                    raise RuntimeError('Scheduled Job watch ended before a controller-created Job appeared')
                buffer += char
                try:
                    event, end = decoder.raw_decode(buffer.lstrip())
                except json.JSONDecodeError:
                    continue
                buffer = buffer.lstrip()[end:]
                meta = scheduled_job_metadata(event, uid, previous_uids)
                if meta:
                    evidence['scheduledAt'] = meta['annotations']['batch.kubernetes.io/cronjob-scheduled-timestamp']
                    evidence['scheduledJobUID'] = meta['uid']
                    save()
                    return meta['name']
        finally:
            timer.cancel()
            if process.poll() is None:
                process.terminate()
            process.wait(timeout=10)
            process.stdout.close()

    try:
        nodes = json.loads(command(k + ['get', 'nodes', '-o', 'json']))['items']
        assert len(nodes) == 1 and nodes[0]['metadata']['name'] == 'ecommerce-profile-sync-control-plane'
        config = json.loads(command(k + ['get', 'cronjob', cron, '-o', 'json']))
        assert config['spec']['suspend'] and config['spec']['schedule'] == '*/5 * * * *'
        assert config['spec']['concurrencyPolicy'] == 'Forbid'
        baseline = rows()
        users = []
        offset = 0
        while True:
            page = admin('/users?first=' + str(offset) + '&max=50')[2]
            users.extend(page)
            if len(page) < 50:
                break
            offset += 50
            assert offset < 10000
        by_subject = {row['keycloak_id']: row for row in baseline}
        assert len(by_subject) == len(baseline) == len(users), 'Missing/unlinked profiles: stop for drift approval'
        for user in users:
            row = by_subject[user['id']]
            assert user.get('email'), 'No-email candidate: stop for review'
            for field, column in [('username', 'username'), ('email', 'email'), ('firstName', 'first_name'), ('lastName', 'last_name')]:
                assert user.get(field) == row[column], 'Existing drift: stop before enabling writes'
        evidence['checks']['readOnlyPreflight'] = {'existingUsers': len(users), 'drift': 0, 'missing': 0}
        dry = run_manual(run + '-dry', True)
        assert dry['total'] == len(users) and dry['created'] == dry['updated'] == 0
        assert rows() == baseline
        smoke = run_manual(run + '-smoke', False)
        assert smoke['unchanged'] == len(users) and smoke['created'] == smoke['updated'] == 0
        assert rows() == baseline
        evidence['checks']['manualSmoke'] = 'PASS: full realm unchanged; dry-run no DB mutation'
        save()

        realm = admin('')[2]
        email = run + '@example.test'
        username = email if realm.get('registrationEmailAsUsername') else run
        status, headers, _ = admin('/users', 'POST', {'username': username, 'email': email, 'enabled': True,
                                                     'emailVerified': True, 'firstName': 'CanonicalSchedule',
                                                     'lastName': 'OwnedFixture'})
        subject = headers.get('Location', headers.get('location', '')).rstrip('/').split('/')[-1]
        assert status == 201 and re.fullmatch(r'[a-f0-9-]{36}', subject)
        owned = {'subject': subject, 'username': username, 'email': email}
        evidence['fixtureSubject'] = subject
        evidence['fixtureSubjectHash'] = hashlib.sha256(subject.encode()).hexdigest()[:12]
        save()
        assert sync(subject)[0] == 200
        matches = [row for row in rows() if row['keycloak_id'] == subject]
        assert len(matches) == 1
        fixture = matches[0]
        predicate = 'id=' + str(int(fixture['id'])) + ' AND keycloak_id=' + literal(subject) + ' AND username=' + literal(username) + ' AND email=' + literal(email)
        # Fault injection is limited to this newly owned row; no schema/global failure.
        modified = sql("WITH changed AS (UPDATE users SET first_name='InjectedFixtureDrift', "
                       "phone='0912345678', address='Owned scheduler fixture' WHERE " + predicate +
                       ' RETURNING *) SELECT COALESCE(json_agg(changed),\'[]\'::json) FROM changed;')
        assert len(modified) == 1
        injected = modified[0]
        evidence['checks']['fixtureDriftInjected'] = 'PASS: only owned fixture first_name changed; phone/address set for preservation assertion'
        previous_uids = {job['metadata']['uid'] for job in json.loads(command(k + ['get', 'jobs', '-o', 'json']))['items']}
        resumed = True
        command(k + ['patch', 'cronjob', cron, '--type=merge', '-p', '{"spec":{"suspend":false}}'])
        scheduled = next_scheduled_job(config['metadata']['uid'], previous_uids)
        counters = complete(scheduled)
        assert counters['total'] == len(users) + 1
        assert counters['updated'] == 1 and counters['unchanged'] == len(users) and counters['created'] == 0
        current = rows()
        assert [row for row in current if row['keycloak_id'] != subject] == baseline, 'Non-fixture data changed'
        repaired = next(row for row in current if row['keycloak_id'] == subject)
        assert repaired['first_name'] == 'CanonicalSchedule'
        for column in ['id', 'keycloak_id', 'username', 'email', 'last_name', 'phone', 'address', 'created_at']:
            assert repaired[column] == injected[column], 'Fixture field not preserved: ' + column
        evidence['checks']['scheduledRecovery'] = 'PASS: actual CronJob controller repaired only fixture drift and preserved phone/address/ID/creation'
        evidence['checks']['existingRowsUnchanged'] = len(baseline)
        succeeded = True
        save()
    except Exception as error:
        # Do not persist response bodies, credentials, SQL or profile data on errors.
        evidence['failure'] = type(error).__name__ + ': acceptance failed; inspect bounded step checks'
        evidence['failureLine'] = traceback.extract_tb(error.__traceback__)[-1].lineno
        raise RuntimeError(evidence['failure']) from None
    finally:
        if resumed and not succeeded:
            command(k + ['patch', 'cronjob', cron, '--type=merge', '-p', '{"spec":{"suspend":true}}'])
            evidence['checks']['failureSuspended'] = True
        try:
            if owned:
                active = json.loads(command(k + ['get', 'jobs', '-o', 'json']))['items']
                assert not any(job.get('status', {}).get('active', 0) for job in active), 'Active job: leave fixture for operator cleanup'
                identity = admin('/users/' + owned['subject'])[2]
                assert identity['username'] == owned['username'] and identity['email'] == owned['email']
                assert identity['username'].startswith(run)
                removed = sql('WITH removed AS (DELETE FROM users WHERE keycloak_id=' + literal(owned['subject']) +
                              ' AND username=' + literal(owned['username']) + ' AND email=' + literal(owned['email']) +
                              ' RETURNING id) SELECT count(*) FROM removed;')
                assert removed == 1
                assert admin('/users/' + owned['subject'], 'DELETE')[0] == 204
                evidence['checks']['fixtureCleanup'] = 'PASS: owned profile and Keycloak identity deleted'
        except Exception:
            evidence['checks']['fixtureCleanup'] = 'FAIL: owned fixture requires operator inspection'
            # If teardown fails, do not leave future schedules running unattended.
            command(k + ['patch', 'cronjob', cron, '--type=merge', '-p', '{"spec":{"suspend":true}}'])
            raise RuntimeError('Fixture cleanup incomplete; CronJob suspended') from None
        finally:
            evidence['finished'] = datetime.now(timezone.utc).isoformat()
            save()
    print(json.dumps(evidence, indent=2))


if __name__ == '__main__':
    main()

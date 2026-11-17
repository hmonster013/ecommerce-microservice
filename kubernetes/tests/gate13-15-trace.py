"""Verify exported causal trace and durable restart evidence on the disposable LOCAL cluster."""
import argparse
import base64
import json
import subprocess
from datetime import datetime
from pathlib import Path

parser = argparse.ArgumentParser()
for flag in ["kubectl", "kubeconfig", "context", "namespace", "evidence-dir"]:
    parser.add_argument("--" + flag, required=True)
args = parser.parse_args()
if not args.context.startswith("kind-ec-g13-g15-"):
    parser.error("Refusing a non-fixture cluster")
BASE = Path(args.evidence_dir)
K = [args.kubectl, "--kubeconfig", args.kubeconfig, "--context", args.context,
     "-n", args.namespace]

def run(command, data=None):
    result = subprocess.run(command, input=data, capture_output=True, encoding="utf8", timeout=45)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout.strip()

def sql(db, query):
    return run(K + ["exec", "-i", "deployment/postgres", "--", "psql", "-q", "-U", "fixture",
                    "-d", db, "-v", "ON_ERROR_STOP=1", "-At"], query)

def http(url, headers):
    output = run(K + ["exec", "probe", "--", "curl", "--max-time", "30", "-sS",
                      "-H", "Accept: application/json", "-w", "\n%{http_code}", url])
    body, status = output.rsplit("\n", 1)
    return int(status), body

state=json.loads((BASE/"trace-state.json").read_text())
assert state['podBefore'] != state['podAfter'] and state['pendingAfterRestart']
audit=state['unlockEvidence']
assert audit['beforeUnlock']['state']=='PENDING' and audit['beforeUnlock']['traceparent']==state['traceparent']
assert audit['podUID']==state['podAfter'] and datetime.fromisoformat(audit['checkedAt']) < datetime.fromisoformat(audit['unlockedAt'])
assert state['notificationBefore']['traceparent']==state['traceparent']
code,body=http("http://tempo:3200/api/traces/"+state["trace"],headers={"Accept":"application/json"})
assert code==200,(code,body[:500])
trace=json.loads(body);spans=[]
for batch in trace.get("batches",[]):
    attrs=batch.get("resource",{}).get("attributes",[])
    service=next((a["value"].get("stringValue") for a in attrs if a['key']=="service.name"),None)
    for group in batch.get("scopeSpans",batch.get("instrumentationLibrarySpans",[])):
        for span in group.get("spans",[]):spans.append(dict(span,service=service))
assert spans,"No exported spans"
def hexid(value):
    if not value:return ""
    try:return value.lower() if len(value) in (16,32) and all(c in "0123456789abcdefABCDEF" for c in value) else base64.b64decode(value).hex()
    except ValueError:return value
assert {"api-gateway","payment-service","order-service"} <= {s['service'] for s in spans},{s['service'] for s in spans}
producer=[s for s in spans if s['service']=='payment-service' and 'payment.succeeded.v1' in s['name'] and s.get('kind') in ('SPAN_KIND_PRODUCER',3)]
consumer=[s for s in spans if s['service']=='order-service' and 'payment.succeeded.v1' in s['name']]
assert producer and consumer,[(s['service'],s['name'],s.get('kind')) for s in spans]
parent=state['traceparent'].split('-')[2]
assert any(hexid(s.get('parentSpanId'))==parent for s in producer),"Producer is not child of durable HTTP span"
http_parent = next((s for s in spans if hexid(s['spanId']) == parent), None)
assert http_parent and http_parent['service'] == 'payment-service'
assert http_parent.get('kind') in ('SPAN_KIND_SERVER', 2)
by_id = {hexid(s['spanId']): s for s in spans}
ancestor = http_parent
seen = set()
while ancestor and ancestor['service'] != 'api-gateway':
    key = hexid(ancestor.get('parentSpanId'))
    assert key not in seen, "Cyclic span graph"
    seen.add(key)
    ancestor = by_id.get(key)
assert ancestor, "Payment HTTP span has no Gateway ancestor"
unlocked_ns = int(datetime.fromisoformat(audit['unlockedAt']).timestamp() * 1_000_000_000)
assert any(int(s['startTimeUnixNano']) >= unlocked_ns for s in producer)

producer_ids={hexid(s['spanId']) for s in producer}
assert any(hexid(s.get('parentSpanId')) in producer_ids or any(hexid(link.get('spanId')) in producer_ids for link in s.get('links',[])) for s in consumer),"Consumer has no causal edge to producer"
assert sql('payment_service_db',"SELECT state FROM payment_outbox WHERE event_id='"+state['event']+"';")=='PUBLISHED'
assert sql('order_service_db',"SELECT result FROM payment_event_inbox WHERE event_id='"+state['event']+"';")=='ACCEPTED'
assert sql('order_service_db','SELECT status FROM orders WHERE id='+str(state['order'])+';')=='PAID'
redacted=[{k:s.get(k) for k in ['service','name','kind','spanId','parentSpanId','startTimeUnixNano','endTimeUnixNano']} for s in spans]
(BASE/'causal-trace-spans.json').write_text(json.dumps({'trace':state['trace'],'spans':redacted},indent=2))
print('Exported causal HTTP/outbox/restarted relay/Kafka consumer graph and DB end-state PASS')

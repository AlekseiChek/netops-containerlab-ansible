# netops-lab — IS-IS/iBGP fabric + safe Ansible change pipeline

A self-contained **NetOps lab**: a service-provider-style FRR fabric built with **containerlab**, plus an **Ansible** pipeline that changes it the way production networks do — **make-before-break drain → precheck → deploy → postcheck → undrain**, one node per wave, with a **deterministic health gate** that stops the rollout on regression.

Docker only, **no KVM/licenses**. Runs on a laptop or NAS.

> Design notes & rationale: based on Google SRE / Orion progressive-rollout practice and differential ("impact report") verification. Lab is **Stage 1** of a larger NetOps CI/CD design (NetBox → render → Batfish → test → deploy).

---

## Network scheme

```
   ce1 (FRR, AS65001)                 ce2 (FRR, AS65002)
   198.51.100.0/24                     203.0.113.0/24
        │ eBGP                              │ eBGP
      [pe1]──────[p1]───────────[p2]──────[pe2]
                   │  \         /  │
                   │   \       /   │
                   │    \     /    │
                   │     [rr1]     │        rr1 = iBGP Route Reflector
                   └── IS-IS L2 core (FRR) ─┘
```

- **IGP:** IS-IS Level 2, area `49.0001`, all core links.
- **Overlay:** iBGP AS `65000`, `rr1` reflects to all clients (pe1/pe2/p1/p2).
- **Edge:** `ce1`/`ce2` are eBGP customers; their /24s are reachable end-to-end across the fabric.

| Node | Role | Loopback | IS-IS system-id |
|------|------|----------|-----------------|
| pe1 | PE | 192.0.2.1 | 1920.0000.2001 |
| pe2 | PE | 192.0.2.2 | 1920.0000.2002 |
| p1  | P  | 192.0.2.11 | 1920.0000.2011 |
| p2  | P  | 192.0.2.12 | 1920.0000.2012 |
| rr1 | RR | 192.0.2.101 | 1920.0000.2101 |
| ce1 | CE | 198.51.100.1 | (eBGP only) |
| ce2 | CE | 203.0.113.1 | (eBGP only) |

---

## What this demonstrates
- Lab-as-code: a multi-node SP fabric from one YAML file (containerlab).
- IS-IS + iBGP + Route Reflector + eBGP customer edge on FRR.
- **Make-before-break** change safety: IS-IS overload-bit drain/undrain.
- **Progressive rollout** (blast-radius control): `serial: 1` + `any_errors_fatal`.
- **Evidence-based health gate:** precheck snapshot → deploy → postcheck asserts (IS-IS up, BGP not degraded, change present) — rollback on *measured* regression, not a timer.
- Idempotent, role-structured, multi-vendor-ready Ansible (FRR today; Junos/IOS-XR/EOS branches noted).

---

## Repo layout
```
netops-lab/
├── clab/                       # containerlab topology + FRR node configs
│   ├── stage1.clab.yml         # 7-node topology (nodes + links)
│   ├── daemons                 # FRR daemons (zebra+bgpd+isisd), mounted to every node
│   ├── vtysh.conf              # silences vtysh warning
│   └── configs/<node>/frr.conf # per-node config
└── ansible/                    # safe-change pipeline
    ├── ansible.cfg
    ├── requirements.yml        # community.docker, ansible.netcommon
    ├── inventory/hosts.yml      # docker-exec connection → clab-stage1-* (no SSH)
    │   └── group_vars/all.yml
    ├── roles/{drain,precheck,deploy,postcheck,compliance}/
    └── playbooks/{site.yml,compliance-check.yml}
```

---

## Prerequisites & install

**1. Docker** (engine running):
```bash
docker --version
```

**2. containerlab:**
```bash
bash -c "$(curl -sL https://get.containerlab.dev)"
clab version
```

**3. Ansible + collections:**
```bash
python3 -m pip install --upgrade ansible          # or: pipx install --include-deps ansible
ansible --version
cd ansible
ansible-galaxy collection install -r requirements.yml   # community.docker, ansible.netcommon
```
> The Ansible→device path uses **`community.docker.docker`** (docker exec) — no SSH on the FRR containers. The docker connection plugin uses the `docker` CLI, no Python SDK needed.

---

## Run the lab
```bash
cd clab
sudo containerlab deploy -t stage1.clab.yml
sudo containerlab inspect -t stage1.clab.yml
```

Verify the control plane:
```bash
docker exec clab-stage1-pe1 vtysh -c "show isis neighbor"
docker exec clab-stage1-rr1 vtysh -c "show bgp ipv4 unicast summary"   # 4 clients Established
docker exec clab-stage1-pe1 vtysh -c "show ip route 203.0.113.0/24"    # ce2 prefix via RR
docker exec clab-stage1-ce1 ping -c2 -I 198.51.100.1 203.0.113.1       # customer-to-customer
```
> Fresh deploys can hit an iBGP startup race (BGP starts before IS-IS converges). Fix once: `docker exec clab-stage1-rr1 vtysh -c "clear bgp *"`.

Teardown:
```bash
sudo containerlab destroy -t stage1.clab.yml --cleanup
```

---

## Run the Ansible pipeline
```bash
cd ansible
ansible all -m ping                       # docker-exec reachability
ansible-playbook playbooks/site.yml       # full safe change on the core (serial:1)

# selective:
ansible-playbook playbooks/site.yml --tags precheck       # snapshot only
ansible-playbook playbooks/site.yml -e target=p1          # one node
ansible-playbook playbooks/compliance-check.yml           # audit (read-only)
```
Artifacts (per node) land in `ansible/artifacts/` (gitignored): `precheck_*.txt`, `postcheck_*.txt`, `compliance_*.txt`.

---

## Ansible structure & hierarchy

**Inventory groups** (`inventory/hosts.yml`): `core` = {rr1,p1,p2,pe1,pe2} (IS-IS+iBGP, drain applies), `ce` = {ce1,ce2} (eBGP only). Connection = `community.docker.docker`, `ansible_host` = container name.

**Vars hierarchy:** `group_vars/all.yml` (thresholds, waits, demo route) → role `defaults/` → `-e` overrides. Connection vars set at group level so playbooks stay vendor-agnostic.

**Roles (one job each):**
| Role | Action | Touches device |
|------|--------|----------------|
| `drain` | IS-IS `set-overload-bit` ↔ `no set-overload-bit` (make-before-break) | write |
| `precheck` | snapshot IS-IS/BGP/routes → artifact; assert adjacency Up | read |
| `deploy` | push change (demo: blackhole `100.64.0.0/24`; prod slot = rendered config) | write |
| `postcheck` | re-read; **gate**: IS-IS up, BGP not degraded, change present | read |
| `compliance` | semantic checks: IS-IS / router-id / loopback | read |

---

## The change pipeline (what `site.yml` does)

```
        per node, serial:1, any_errors_fatal
   ┌───────────────────────────────────────────────┐
   │ DRAIN ─► PRECHECK ─► DEPLOY ─► POSTCHECK ─► UNDRAIN │
   │  (overload   (snapshot  (push    (GATE:        (clear   │
   │   -bit)      +assert)   change)   assert)       overload)│
   └───────────────────────────────────────────────┘
            │ gate PASS                 │ gate FAIL
            ▼                           ▼
        next node                  STOP rollout (any_errors_fatal)
```

- **Drain first (make-before-break):** the node stops being IS-IS transit before it's changed, so the change is non-disruptive. Undrain restores it after the gate passes.
- **Deterministic gate:** the `postcheck` asserts own go/no-go. On FRR the drain primitive is `set-overload-bit`; on a production SP core it's IS-IS overload + BGP graceful-shutdown (RFC 8326).
- **Blast radius:** `serial: 1` changes one device per wave; `any_errors_fatal` halts the whole rollout on the first failed gate.

This mirrors the Google/Meta/Microsoft pattern: **render → validate → canary → wave → fleet** with automatic stop/rollback.

---

## Production mapping (FRR lab → real SP)
| Lab (FRR) | Production |
|-----------|------------|
| docker-exec + vtysh | NETCONF/RESTCONF/gNMI |
| IS-IS `set-overload-bit` | + BGP graceful-shutdown (RFC 8326) |
| `deploy` blackhole demo | rendered config from NetBox+Jinja2 (`junos_config`/NAPALM) |
| postcheck asserts | gNMI golden-signal health gate + Batfish differential / impact report |
| `serial:1` | canary → wave-by-failure-domain → fleet |

---

## License
MIT — see `LICENSE`.

# netops-lab — IS-IS/iBGP fabric + safe Ansible change pipeline

A self-contained **NetOps lab**: a service-provider-style FRR fabric built with **containerlab**, plus an **Ansible** pipeline that changes it the way production networks do — **make-before-break drain → precheck → deploy → postcheck → undrain**, one node per wave, with a **deterministic health gate** that stops the rollout on regression.

Docker only, **no KVM/licenses**. Runs on a laptop or NAS.

> Design notes & rationale: based on Google SRE / Orion progressive-rollout practice and differential ("impact report") verification. Lab is **Stage 1** of a larger NetOps CI/CD design (NetBox → render → Batfish → test → deploy).

---

## Network scheme

![Network topology](docs/topology.svg)

<sub>Editable source: [`docs/topology.drawio`](docs/topology.drawio) (open in [draw.io](https://app.diagrams.net)).</sub>

- **Two sites:** site-A = `ce1`↔`pe1`/`pe2`, site-B = `ce2`↔`pe3`/`pe4`. Lets you roll changes **wave-by-site**.
- **IGP:** IS-IS Level 2, area `49.0001`; all four PEs and both RRs are **dual-attached** to both P routers (RRs carry control-plane only, not transit).
- **Overlay:** iBGP AS `65000`, **two route reflectors** `rr1`+`rr2` (separate cluster-ids); every client peers both; `rr1`↔`rr2` peer too → no RR single point of failure.
- **Edge:** `ce1`/`ce2` are **dual-homed** eBGP customers → a PE can be drained without dropping the customer.

| Node | Role | Site | Loopback | IS-IS system-id |
|------|------|------|----------|-----------------|
| pe1 | PE | site-A | 192.0.2.1 | 1920.0000.2001 |
| pe2 | PE | site-A | 192.0.2.2 | 1920.0000.2002 |
| pe3 | PE | site-B | 192.0.2.3 | 1920.0000.2003 |
| pe4 | PE | site-B | 192.0.2.4 | 1920.0000.2004 |
| p1  | P  | core | 192.0.2.11 | 1920.0000.2011 |
| p2  | P  | core | 192.0.2.12 | 1920.0000.2012 |
| rr1 | RR | core | 192.0.2.101 | 1920.0000.2101 |
| rr2 | RR | core | 192.0.2.102 | 1920.0000.2102 |
| ce1 | CE (dual-homed → pe1/pe2) | site-A | 198.51.100.1 | (eBGP only) |
| ce2 | CE (dual-homed → pe3/pe4) | site-B | 203.0.113.1 | (eBGP only) |

---

## What this demonstrates
- Lab-as-code: a multi-node SP fabric from one YAML file (containerlab).
- IS-IS + iBGP + **redundant Route Reflectors (2)** + **dual-homed** eBGP customer edge on FRR.
- **Make-before-break** change safety: IS-IS overload-bit **+ BGP graceful-shutdown (RFC 8326)** drain/undrain — meaningful *because* the fabric is redundant (drain pe1 → CE fails over to pe2; drain rr1 → clients keep rr2).
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
docker exec clab-stage1-pe1 vtysh -c "show isis neighbor"               # pe1 sees p1 AND p2
docker exec clab-stage1-rr1 vtysh -c "show bgp ipv4 unicast summary"    # rr1: 4 clients + rr2
docker exec clab-stage1-rr2 vtysh -c "show bgp ipv4 unicast summary"    # rr2: 4 clients + rr1
docker exec clab-stage1-pe1 vtysh -c "show ip route 203.0.113.0/24"     # ce2 prefix (ECMP via pe2 paths)
docker exec clab-stage1-ce1 ping -c2 -I 198.51.100.1 203.0.113.1        # customer-to-customer
```
> Fresh deploys can hit an iBGP startup race (BGP before IS-IS converges). Fix once: `for n in rr1 rr2 p1 p2 pe1 pe2 pe3 pe4; do docker exec clab-stage1-$n vtysh -c "clear bgp *"; done`.

**Redundancy / drain demo** (what 2 RRs + dual-homing + 2 sites buys you):
```bash
cd ../ansible
# drain one PE (IS-IS overload + BGP graceful-shutdown) — CE traffic shifts to its pair, no outage:
ansible-playbook playbooks/site.yml -e target=pe1
# roll a whole site as a wave (one PE at a time, gate between):
ansible-playbook playbooks/site.yml -e target=site_b
# meanwhile customer-to-customer keeps working (ce1 site-A ↔ ce2 site-B):
docker exec clab-stage1-ce1 ping -c3 -I 198.51.100.1 203.0.113.1
```

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

### Safe rolling reboot (zero customer-traffic loss)
```bash
ansible-playbook playbooks/safe-reboot.yml            # reboot every core node, one at a time
ansible-playbook playbooks/safe-reboot.yml -e target=site_b
```
Per node (`serial:1` + `any_errors_fatal`): **start a continuous ce1↔ce2 probe in the background → drain → restart FRR (RP reload) → wait until FRR + all BGP sessions are back → undrain → stop the probe → GATE: the probe that spanned the whole window must show 0% loss.** The ICMP measurement runs **in parallel** with the convergence window (0.2 s interval → a single lost packet shows), so it proves zero loss *during* the reboot, not just after. "Reboot" restarts the routing stack only — container/netns/interfaces stay up (real RP-reload analog; swap for `docker restart` for a full power-cycle). Zero loss holds because the fabric is redundant **and** transit nodes carry `set-overload-bit on-startup 90` (a freshly-restarted node stays out of transit until BGP converges).

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

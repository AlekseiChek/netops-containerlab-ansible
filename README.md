# netops-lab — redundant SP fabric (VyOS + FRR) with a safe Ansible change pipeline

A self-contained **network operations lab** you can run on a laptop or NAS — **no licenses, no KVM, just Docker**.
It builds a small but realistic **service-provider fabric** (IS-IS wide-metric + **SR-MPLS** + **TI-LFA** + route reflectors + **MPLS L3VPN** for dual-homed customers) and ships an **Ansible pipeline** that changes and reboots it the way real carriers do: **drain traffic → change → verify → restore**, one device at a time, stopping automatically if anything degrades.

> New to this? You only need three tools — **Docker** (runs the routers as containers), **containerlab** (wires them into a topology from one file), and **Ansible** (automates changes). Step-by-step install is below; you can copy-paste everything.

---

## ⚡ Quick start (3 commands)

> Prereqs: **Docker**, **containerlab**, and **Ansible** installed (see [§1 Install the tools](#1-install-the-tools-one-time) if you don't have them).

```bash
# 1) get the lab
git clone https://github.com/AlekseiChek/netops-containerlab-ansible
# 2) build the whole topology (10 routers + wiring, ~1–2 min)
cd netops-containerlab-ansible/clab && sudo containerlab deploy -t stage1.clab.yml
# 3) prove a zero-loss rolling reboot of the core
cd ../ansible && ansible-galaxy collection install -r requirements.yml && ansible-playbook playbooks/safe-reboot.yml
```

Want to watch it live? In a second terminal, run a non-stop customer-to-customer ping while step 3 reboots the core:
```bash
docker exec clab-stage1-ce1 ping -I 198.51.100.1 203.0.113.1
```
The playbook drains → reboots → restores each router one at a time and **asserts 0% packet loss** at the end. Tear it all down with `sudo containerlab destroy -t clab/stage1.clab.yml --cleanup`.

The rest of this README explains every step in detail — start there if you're new to Ansible or containers.

---

## Network scheme

![Network topology](docs/topology.svg)

<sub>Editable source: [`docs/topology.drawio`](docs/topology.drawio) — open in [draw.io](https://app.diagrams.net).</sub>

- **Core (8 × VyOS):** `p1`/`p2` (P routers), `rr1`/`rr2` (route reflectors), `pe1`–`pe4` (provider edge).
- **Customers (2 × FRR):** `ce1`, `ce2` — each **dual-homed** to two PEs.
- **Two sites:** site-A = `ce1`↔`pe1`/`pe2`, site-B = `ce2`↔`pe3`/`pe4` → you can roll changes **site by site**.
- **IGP:** IS-IS Level 2 (area `49.0001`), **wide metrics**; every PE and RR is **dual-attached** to both P routers.
- **Transport:** **Segment Routing MPLS** (SRGB `16000–23999`, a prefix-SID per loopback) + **TI-LFA** fast-reroute on every core link.
- **Overlay:** iBGP AS `65000`, **two route reflectors** (no single point of failure) reflecting both IPv4 and **VPNv4**.
- **Customer service:** **MPLS L3VPN** — `ce1` and `ce2` live in **one shared VRF `CUST`** (route-target `65000:100`), so they reach each other over the VPN, not the global table.

| Node | Role | NOS | Site | Loopback | SR prefix-SID |
|------|------|-----|------|----------|---------------|
| pe1–pe2 | PE | VyOS | site-A | 192.0.2.1 / .2 | 16001 / 16002 |
| pe3–pe4 | PE | VyOS | site-B | 192.0.2.3 / .4 | 16003 / 16004 |
| p1 / p2 | P (core) | VyOS | core | 192.0.2.11 / .12 | 16011 / 16012 |
| rr1 / rr2 | Route Reflector | VyOS | core | 192.0.2.101 / .102 | 16101 / 16102 |
| ce1 | Customer (VRF CUST → pe1/pe2) | FRR | site-A | 198.51.100.1 | — |
| ce2 | Customer (VRF CUST → pe3/pe4) | FRR | site-B | 203.0.113.1 | — |

---

## SP feature set (and how to verify it)

The core runs a realistic operator feature stack. **Why these:** wide metrics are a prerequisite for SR; SR-MPLS gives every loopback a global label so L3VPN doesn't need LDP; TI-LFA gives sub-50 ms local repair; L3VPN keeps customer routes in a VRF instead of the global table.

| Feature | Where | Verify |
|---------|-------|--------|
| IS-IS **wide metric** | all core | `docker exec clab-stage1-p1 vtysh -c "show isis database detail"` |
| **Segment Routing (SR-MPLS)** | all core | `docker exec clab-stage1-pe1 vtysh -c "show isis segment-routing prefix-sids"` |
| **TI-LFA** fast-reroute | all core links | `docker exec clab-stage1-p1 vtysh -c "show isis fast-reroute summary"` |
| **MPLS** label switching | all core | `docker exec clab-stage1-p1 vtysh -c "show mpls table"` |
| **L3VPN (VPNv4)** | PEs + RRs | `docker exec clab-stage1-pe1 vtysh -c "show bgp ipv4 vpn summary"` |
| **same VRF CUST** | PEs | `docker exec clab-stage1-pe1 vtysh -c "show ip route vrf CUST"` |

**Proof the two customers share one VRF** — `ce1` should learn `ce2`'s prefix (and vice-versa) and ping it end-to-end over the L3VPN:
```bash
docker exec clab-stage1-ce1 vtysh -c "show ip route 203.0.113.0/24"   # learned via L3VPN
docker exec clab-stage1-ce1 ping -c2 -I 198.51.100.1 203.0.113.1      # CE1 -> CE2 across the VPN
```

> **EVPN-MPLS?** Not available: VyOS/FRR implement EVPN over **VXLAN only** (no MPLS data plane for L2VPN/EVPN). So customers use **MPLS L3VPN**, which VyOS fully supports.
>
> **SR-TE** is only at *initial* support in VyOS rolling (May 2026) and the policy-attach syntax isn't documented yet — so it's shipped as an optional, clearly-marked demo in [`clab/configs/sr-te-demo.set`](clab/configs/sr-te-demo.set) (paste-in, **not** in the boot config, so it can never break the lab).

---

## 1. Install the tools (one-time)

```bash
# Docker — must be installed and running
docker --version

# containerlab — builds the topology
bash -c "$(curl -sL https://get.containerlab.dev)"
clab version

# Ansible — automates changes
python3 -m pip install --upgrade ansible paramiko    # paramiko = SSH for VyOS
ansible --version

# pull the router images (first time only)
docker pull ghcr.io/sysoleg/vyos-container:latest    # core (VyOS)
docker pull frrouting/frr:latest                     # customers (FRR)
```

---

## 2. Start the lab

```bash
cd clab
sudo containerlab deploy -t stage1.clab.yml          # boots all 10 routers + wiring (~1–2 min)
sudo containerlab inspect -t stage1.clab.yml         # list nodes + status
```
VyOS takes ~30–60 s to boot. If iBGP looks stuck right after deploy (a startup race), reset it once:
```bash
for n in rr1 rr2 p1 p2 pe1 pe2 pe3 pe4; do docker exec clab-stage1-$n vtysh -c "clear bgp *"; done
```

**Check it works** (customer-to-customer reachability across the whole fabric):
```bash
docker exec clab-stage1-ce1 ping -c2 -I 198.51.100.1 203.0.113.1
```

**Stop / delete the lab:**
```bash
sudo containerlab destroy -t stage1.clab.yml --cleanup
```

---

## 3. Connect to a node manually

containerlab adds `/etc/hosts` entries, so every node is reachable by name `clab-stage1-<node>`.

**VyOS core nodes — via SSH** (user `vyos`, password `vyos`):
```bash
ssh vyos@clab-stage1-rr1
# then, in the VyOS prompt:
show bgp summary            # BGP neighbors / state
show isis neighbor          # IS-IS adjacencies
show ip route               # routing table
configure                   # enter config mode
  set ...                   # make a change
  commit                    # apply
  exit
exit
```

**Any node — via Docker (no SSH needed), using FRR's `vtysh`** (works on VyOS too, FRR runs underneath):
```bash
docker exec -it clab-stage1-pe1 vtysh        # interactive routing CLI
# or one-off:
docker exec clab-stage1-rr1 vtysh -c "show bgp ipv4 unicast summary"
docker exec clab-stage1-ce1 vtysh -c "show ip route"
```

**Customer (FRR) nodes** have no SSH — use docker exec:
```bash
docker exec -it clab-stage1-ce1 vtysh
docker exec clab-stage1-ce1 ping -c2 -I 198.51.100.1 203.0.113.1
```

> Tip: container names are `clab-stage1-<node>` (e.g. `clab-stage1-pe3`). `docker ps` lists them all.

---

## 4. The Ansible automation

```bash
cd ../ansible
ansible-galaxy collection install -r requirements.yml   # vyos.vyos + community.docker + ansible.netcommon (one-time)
```

Ansible reads `inventory/hosts.yml`, which knows two groups:
- **`core`** (the 8 VyOS routers) — reached over **SSH** with the `vyos.vyos` driver,
- **`ce`** (the 2 FRR customers) — reached via **docker exec**.

You don't pass IPs or passwords on the command line — they live in the inventory. You just run a playbook.

### The four playbooks

| Playbook | What it does | Changes the network? | Run it when… |
|----------|--------------|----------------------|--------------|
| `facts.yml` | Connectivity check — logs into every core router and prints its version + BGP summary. | No | You want to confirm Ansible can reach the lab. |
| `site.yml` | **Safe config change** — for each node, one at a time: drain → snapshot → change → verify → restore. Stops the whole run if any health check fails. | Yes (a demo route) | You want to push a change safely. |
| `safe-reboot.yml` | **Zero-loss rolling reboot** — reboots every router one at a time while a non-stop ping proves not a single packet is lost. | Reboots only | You need to restart/upgrade routers without an outage. |
| `compliance-check.yml` | **Audit** — checks every router has the mandatory config (IS-IS, BGP, loopback). Read-only. | No | Daily/scheduled hygiene check. |

#### `facts.yml` — "can Ansible see the lab?"
```bash
ansible-playbook playbooks/facts.yml
```
Logs into each VyOS node over SSH, prints `… reachable — VyOS … (rr1)` and a BGP line. If this works, everything else will. **Always run this first.**

#### `site.yml` — safe change, one node at a time
```bash
ansible-playbook playbooks/site.yml                 # all core nodes, one by one
ansible-playbook playbooks/site.yml -e target=pe1   # just one node
ansible-playbook playbooks/site.yml -e target=site_b   # just site-B's PEs
ansible-playbook playbooks/site.yml --tags precheck    # only take the "before" snapshot
```
Per node, in order: **drain** (steer traffic away) → **precheck** (snapshot IS-IS/BGP, assert healthy) → **deploy** (push the change — a harmless blackhole route in the demo) → **postcheck** (re-check; if IS-IS dropped, BGP degraded, or the change didn't apply → **fail and stop**) → **undrain** (put it back). `serial: 1` + `any_errors_fatal` = one device at a time, halt on the first problem (blast-radius control).

#### `safe-reboot.yml` — reboot without dropping a packet
```bash
ansible-playbook playbooks/safe-reboot.yml                 # whole core, one at a time
ansible-playbook playbooks/safe-reboot.yml -e target=site_b
```
Per node: starts a **continuous ce1↔ce2 ping in the background**, drains the node, **restarts its routing stack** (`systemctl restart frr` inside VyOS — container & SSH stay up), waits for all BGP sessions to come back, undrains, then stops the ping and **asserts 0% loss across the whole window**. Watch it live in another terminal:
```bash
docker exec clab-stage1-ce1 ping -I 198.51.100.1 203.0.113.1
```

#### `compliance-check.yml` — audit
```bash
ansible-playbook playbooks/compliance-check.yml
```
Pulls each router's config and asserts the must-haves (IS-IS, BGP, loopback) are present; writes a report to `artifacts/`. Doesn't change anything.

Run results/snapshots are written to **`ansible/artifacts/`** (`precheck_*.txt`, `postcheck_*.txt`, `compliance_*.txt`).

---

## Roles (reusable building blocks)
The playbooks are assembled from small single-purpose roles in `ansible/roles/`:

| Role | Job | Touches device |
|------|-----|----------------|
| `drain` | make-before-break: set/clear IS-IS overload-bit + BGP graceful-shutdown | write |
| `precheck` | snapshot IS-IS/BGP/routes → artifact; assert adjacency Up | read |
| `deploy` | push the change (demo: blackhole `100.64.0.0/24`) | write |
| `postcheck` | re-read state; **gate**: IS-IS up, BGP not degraded, change present | read |
| `compliance` | assert mandatory config present (IS-IS / BGP / loopback) | read |

`drain`/`undrain` are pure traffic-steering (no reboot). `site.yml` puts **deploy** between them; `safe-reboot.yml` puts a **reboot** between them.

---

## Repo layout
```
netops-containerlab-ansible/
├── clab/
│   ├── stage1.clab.yml             # topology: 10 nodes + 17 links
│   ├── daemons / vtysh.conf        # FRR helpers (CE nodes)
│   └── configs/<node>/             # per-node config:
│       ├── <core>/config.boot      #   VyOS (set-style) for pe*/p*/rr*
│       └── <ce>/frr.conf           #   FRR for ce1/ce2
├── ansible/
│   ├── ansible.cfg
│   ├── requirements.yml            # vyos.vyos, community.docker, ansible.netcommon
│   ├── inventory/hosts.yml         # core=VyOS/SSH, ce=FRR/docker
│   │   └── group_vars/all.yml
│   ├── roles/{drain,precheck,deploy,postcheck,compliance}/
│   └── playbooks/{facts,site,safe-reboot,compliance-check}.yml
└── docs/topology.{svg,drawio}      # network diagram
```

---

## Production mapping (lab → real SP)
| Lab | Production |
|-----|-----------|
| VyOS / FRR containers | Juniper MX / Cisco IOS-XR / Arista |
| SSH `vyos.vyos` / docker exec | NETCONF / RESTCONF / gNMI |
| `deploy` blackhole demo | rendered config from NetBox + Jinja2 |
| postcheck asserts | gNMI golden-signal health gate + Batfish differential |
| `serial: 1` | canary → wave-by-failure-domain → fleet |

---

## License
MIT — see `LICENSE`.

# "Field Notes" poster — image-gen prompt

Copper-steampunk LinkedIn poster, 3:4. Paste into Midjourney / DALL·E / SDXL.
**Per post, change only:** the `no.NN` counter, the headline + subtitle, and the diagram nodes/labels.
Keep everything else fixed → recognizable series.

## Master prompt (router icons, leaf-spine, clean)

Vertical 3:4 industrial-steampunk technical poster. Background: one continuous weathered
oxidized-copper plate, rust mottling + verdigris-teal patina, top-left light, photoreal PBR
metal, fine grain, soft vignette. Clean, orderly, symmetrical composition — no tangled or
crossing wires.

NODES — all identical rugged metal ROUTER devices (NOT gears): a low rectangular brass-and-steel
router chassis, front face with a neat row of network ports + status LEDs, two short antennas,
riveted corners, an engraved nameplate strip. Reuse this exact router for every node; only the
nameplate label and accent-glow change.

LAYOUT — leaf-spine fabric, two tidy tiers:
- small INTERNET cloud icon at the very top, centred.
- SPINE row (upper): two identical routers PE1 and PE2, evenly spaced. PE1 nameplate "DRAINING",
  tarnished copper-orange, a small spark on its uplink; PE2 nameplate "CARRYING", emerald-teal glow.
- LEAF/customer row (lower): two identical routers CE1 and CE2, evenly spaced, neutral steel glow.
- Full-mesh leaf-spine wiring: every lower router connects to BOTH upper routers with straight,
  evenly-spaced riveted pipes — parallel and orderly, like a real spine-leaf diagram. Drained links
  (via PE1): dim dashed copper. Live links (via PE2): glowing emerald-teal pipes. Internet joins both
  spines with two short pipes (PE1 dashed/dim, PE2 live emerald).

TYPE & BRAND FURNITURE: top-left small hexagon 'AC' monogram + monospace kicker "NETOPS · SRE" and
"FIELD NOTES /// no.01". Headline bold slab-serif, embossed copper: "Make-before-break", subtitle
"Drain the node — then reboot." Bottom brass instrument panel with monospace readout "0% packet loss
— proven, not promised" and wordmark "ALEKSEI CHEKHUN · netops.engineer".

PALETTE — restricted: copper #E08A3C, emerald-teal #2EA67A, oxidized graphite, bone-white text.
Premium, cohesive, all text sharp and legible.

--ar 3:4 --style raw --v 6.1

## Negative / avoid
gears, cogs, clockwork, mismatched node shapes, crossing/tangled wires, diagonal spaghetti,
blurry text, gibberish letters, neon, cyberpunk pink, flat UI, watermark, extra text.

## Notes
- Most image generators still mangle long text. Plan B: generate the poster with EMPTY nameplates,
  then overlay headline/labels yourself (Rockwell + Cascadia) for crisp type.
- Brand constants (never change): identical metal router device, copper+emerald palette, AC monogram,
  slab headline, "FIELD NOTES no.NN", brass footer wordmark, clean leaf-spine wiring.
- SVG fallback (editable, free to regenerate): docs/linkedin-drain-rust.svg

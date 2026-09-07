#!/usr/bin/env python3
"""Build the PBL report document (components 1-7) for the Zone project as a .docx."""
from docx import Document
from docx.shared import Pt, Inches, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

OUT = "/Users/arunodoybanerjee/Desktop/TheZone/docs/Zone_PBL_Report_Components_1-7.docx"

doc = Document()

# ---- base styles ----
normal = doc.styles["Normal"]
normal.font.name = "Calibri"
normal.font.size = Pt(10.5)
for lvl, sz in [("Heading 1", 16), ("Heading 2", 13), ("Heading 3", 11.5)]:
    st = doc.styles[lvl]
    st.font.name = "Calibri"
    st.font.size = Pt(sz)
    st.font.color.rgb = RGBColor(0x0B, 0x3D, 0x33)

sec = doc.sections[0]
sec.left_margin = sec.right_margin = Inches(0.9)
sec.top_margin = sec.bottom_margin = Inches(0.8)


def h1(t): doc.add_heading(t, level=1)
def h2(t): doc.add_heading(t, level=2)
def h3(t): doc.add_heading(t, level=3)


def p(t, bold=False, italic=False, size=None):
    par = doc.add_paragraph()
    r = par.add_run(t)
    r.bold = bold
    r.italic = italic
    if size:
        r.font.size = Pt(size)
    return par


def bullets(items, level=0):
    for it in items:
        par = doc.add_paragraph(style="List Bullet")
        par.paragraph_format.left_indent = Inches(0.25 + 0.25 * level)
        if isinstance(it, tuple):
            lead, rest = it
            r = par.add_run(lead + " ")
            r.bold = True
            par.add_run(rest)
        else:
            par.add_run(it)


def nums(items):
    for it in items:
        par = doc.add_paragraph(style="List Number")
        if isinstance(it, tuple):
            r = par.add_run(it[0] + " "); r.bold = True
            par.add_run(it[1])
        else:
            par.add_run(it)


def mono(text, size=8):
    par = doc.add_paragraph()
    par.paragraph_format.left_indent = Inches(0.1)
    par.paragraph_format.space_after = Pt(6)
    for i, line in enumerate(text.rstrip("\n").split("\n")):
        run = par.add_run(("" if i == 0 else "\n") + line)
        run.font.name = "Consolas"
        run.font.size = Pt(size)
    # shading
    pPr = par._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear"); shd.set(qn("w:fill"), "F2F2F2")
    pPr.append(shd)
    return par


def table(headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Light Grid Accent 1"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = True
    hdr = t.rows[0].cells
    for i, htext in enumerate(headers):
        hdr[i].text = ""
        run = hdr[i].paragraphs[0].add_run(htext)
        run.bold = True
        run.font.size = Pt(9)
    for row in rows:
        cells = t.add_row().cells
        for i, val in enumerate(row):
            cells[i].text = ""
            run = cells[i].paragraphs[0].add_run(str(val))
            run.font.size = Pt(8.5)
    if widths:
        for r in t.rows:
            for i, w in enumerate(widths):
                r.cells[i].width = Inches(w)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    return t


def pagebreak():
    doc.add_page_break()


# ======================================================================
# TITLE
# ======================================================================
title = doc.add_paragraph()
title.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = title.add_run("Project Based Learning (PBL) — Panel Presentation Content\n")
r.bold = True; r.font.size = Pt(13)
r2 = title.add_run("Components 1–7 (Abstract through Results & Discussion)")
r2.font.size = Pt(11)

sub = doc.add_paragraph(); sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
sr = sub.add_run(
    "\nZONE — An Infrastructure-Free Bluetooth Store-Carry-Forward Network for\n"
    "Disaster Zones, with Silence-Based Damage Inference\n")
sr.bold = True; sr.font.size = Pt(14); sr.font.color.rgb = RGBColor(0x0B, 0x3D, 0x33)

meta = doc.add_paragraph(); meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
meta.add_run(
    "Department of Computer Science & Engineering · "
    "Sikkim Manipal Institute of Technology\n"
    "5th Semester PBL · Panel Presentation: 9 September 2026\n"
    "Native Android (Kotlin + Jetpack Compose) · minSdk 26 · 82 JVM unit tests · "
    "validated on three physical phones in airplane mode"
).font.size = Pt(9)

doc.add_paragraph()
note = doc.add_paragraph()
nr = note.add_run(
    "This document contains the full content for PPT components 1–7. Each section is "
    "mapped to its slide budget from the PBL circular. All design diagrams are in "
    "Component 5; algorithm / pseudocode is in Component 6; result tables, comparison "
    "and discussion are in Component 7.")
nr.italic = True; nr.font.size = Pt(9)

pagebreak()

# ======================================================================
# 1 — ABSTRACT
# ======================================================================
h1("Component 1 — Abstract  (Max. 1 slide)")
p("Project summary, objectives, methodology and key outcomes.", italic=True, size=9)
bullets([
    ("Problem.", "In the first 72 hours after an earthquake or flood, cellular and internet "
     "infrastructure is down or saturated. Trapped survivors cannot call for help, and "
     "incident command cannot tell a collapsed block from a lightly-flooded one."),
    ("Objective.", "A single native-Android application that (i) forms an infrastructure-free "
     "mesh between ordinary smartphones over Bluetooth Low Energy (BLE), and (ii) infers "
     "where damage is worst from the pattern of devices that fall silent."),
    ("Methodology.", "Connectionless dual-PHY BLE advertising (1M + Coded); a fixed 31-byte "
     "packet; epidemic store-carry-forward relaying with content-addressed de-duplication "
     "(a CRDT by construction); a battery-adaptive duty cycle; and a novel “Dead Man’s "
     "Packet” evaluator in which every packet declares its own next transmission time, so "
     "that silence becomes a measurable signal — expected (dying battery, announced) versus "
     "unexpected (promised to speak, then vanished = destroyed)."),
    ("Key outcomes.", "One APK with three role-specific UIs (Citizen / Responder / Map-EOC); "
     "82 JVM unit tests, all passing, run in CI on every push; validated on three physical "
     "phones in airplane mode — 2-hop message delivery with the source’s Bluetooth switched "
     "off, correct discrimination of expected versus unexpected silence, and detection of "
     "simultaneous cell-wide silence (“CELL_LOSS”) reproduced against a 500-node simulation "
     "of the 2015 Gorkha earthquake casualty curve (22 → 95 → 469 → 626 deaths over 72 h)."),
])

pagebreak()

# ======================================================================
# 2 — INTRODUCTION
# ======================================================================
h1("Component 2 — Introduction  (Max. 1 slide)")
p("Background of the topic, context, motivation and significance of the work.", italic=True, size=9)
bullets([
    ("Disasters sever telecom.", "In the 2015 Gorkha (Nepal) earthquake, communications, power "
     "and roads failed together; coordination between district and central emergency operations "
     "centres was ineffective because of “a weak database and an absence of modern technology,” "
     "while roughly 1.4 million people needed food, water and shelter within days."),
    ("The golden 72 hours.", "Survival probability for trapped victims falls sharply after about "
     "72 hours; rescue effort is squandered when responders cannot localise victims or rank "
     "sites by need."),
    ("Existing tools do not fit.", "Satellite phones are scarce and need a sky view. "
     "Mesh-messenger apps (Bridgefy, FireChat) are built for chat between two conscious users "
     "and have documented security failures. None of them treats a device going offline as "
     "information — yet that is the single most important event in a collapse."),
    ("Motivation and significance.", "Every modern phone already carries the radio (BLE 5), the "
     "sensors (barometer, accelerometer, GNSS) and the battery to be a disaster node. The "
     "missing piece is software that needs no infrastructure and no user action, and that turns "
     "the absence of signal into a live damage map. Zone converts “search this whole block” "
     "into “search this 100 m cell — 12 phones went silent together at 04:12.”"),
])

pagebreak()

# ======================================================================
# 3 — LITERATURE REVIEW
# ======================================================================
h1("Component 3 — Literature Review  (Max. 2 slides)")
p("Paper name and publication details, inferences, relevance and research gaps "
  "(seven research papers).", italic=True, size=9)

h3("3.1  DTN routing and mesh foundations")
table(
    ["#", "Paper / Publication", "Key inference", "Relevance to Zone", "Research gap"],
    [
        ["1",
         "A. Vahdat, D. Becker, “Epidemic Routing for Partially Connected Ad Hoc Networks,” "
         "Tech. Report CS-2000-06, Duke University, 2000.",
         "Flood every message to every contact; nodes exchange summary vectors and pull what "
         "they lack. Guarantees eventual delivery under intermittent connectivity.",
         "Zone’s relay is epidemic flooding — the right choice when contact patterns are chaotic "
         "and unpredictable.",
         "Unbounded replication drains battery and buffers; no dedup identity is defined for a "
         "real byte-level format."],
        ["2",
         "A. Lindgren, A. Doria, O. Schelén, “Probabilistic Routing in Intermittently Connected "
         "Networks” (PRoPHET), ACM MobiHoc 2003 (poster); IRTF RFC 6693, 2012.",
         "Use history of encounters plus transitivity to forward selectively — more deliveries "
         "than epidemic at lower overhead.",
         "Confirms the trade-off Zone must manage: replication versus cost.",
         "Needs stable, repeated encounters to learn from — absent in the first hours of a "
         "disaster; assumes trustworthy peers."],
        ["3",
         "Z. Lu, G. Cao, T. La Porta, “TeamPhone: Networking Smartphones for Disaster Recovery,” "
         "IEEE Trans. Mobile Computing, 16(12):3554–3567, 2017.",
         "Bridges cellular + ad-hoc + opportunistic networks; a “self-rescue” module groups and "
         "positions trapped survivors’ phones; about 40% lower transmission energy.",
         "Validates smartphone-only disaster networking and the idea of a survivor-side "
         "sub-system.",
         "Positioning needs GPS or manual grouping; no notion of inferring damage; requires user "
         "interaction."],
        ["4",
         "S. M. Darroudi, C. Gomez, “Bluetooth Low Energy Mesh Networks: A Survey,” Sensors "
         "(MDPI), 17(7):1467, 2017.",
         "BLE’s native topology is star / point-to-point with short range; multi-hop mesh needs "
         "an added protocol layer.",
         "Justifies Zone’s connectionless advertising flood (no GATT, no pairing) as a robust "
         "mesh substrate.",
         "Surveyed schemes assume GATT connections or the heavy standard BLE-Mesh stack — "
         "fragile under churn."],
    ],
    widths=[0.3, 2.0, 1.7, 1.6, 1.4],
)

h3("3.2  Radio range, sensing and security")
table(
    ["#", "Paper / Publication", "Key inference", "Relevance to Zone", "Research gap"],
    [
        ["5",
         "A. R. Sheikh et al., “Adaptive Physical Layer Selection for Bluetooth 5: Measurements "
         "and Simulations,” Wireless Communications and Mobile Computing (Wiley/Hindawi), vol. "
         "2021, Art. 8842919.",
         "Measured BLE 5: 1M PHY ≈ 655 m, Coded PHY ≈ 1300 m line-of-sight at 0 dBm; realistic "
         "urban ≈ 300 m. Adapting PHY to conditions improves reliability.",
         "Directly motivates Zone’s battery→PHY ladder (Coded when affordable for reach, 1M in "
         "survival mode).",
         "No link to duty-cycle or energy budgeting; no disaster application."],
        ["6",
         "W. Falcon, H. Schulzrinne, “Predicting Floor-Level for 911 Calls with Neural Networks "
         "and Smartphone Sensor Data,” arXiv:1710.11122, 2017.",
         "Barometric pressure change from building entry gives floor level — 100% correct across "
         "63 tests in 5 New York towers, with no beacons or building maps.",
         "Grounds Zone’s relative-altitude signal (basement versus rooftop; rising trend = "
         "drowning).",
         "Needs a clean “entered building” event from GPS; inference is centralised at a call "
         "centre, not on the mesh."],
        ["7",
         "M. R. Albrecht, J. Blasco, R. B. Jensen, L. Mareková, “Mesh Messaging in Large-Scale "
         "Protests: Breaking Bridgefy,” Topics in Cryptology – CT-RSA 2021, LNCS 12704, "
         "Springer.",
         "Bridgefy: plaintext sender/receiver IDs (tracking), no authenticity, trivial "
         "impersonation from a sniffed BLE handshake, whole-network denial of service from one "
         "malformed message.",
         "Sets Zone’s security bar: per-install hashed identity, auth-shape check on every relay, "
         "content-addressed dedup that drops malformed / duplicate frames, Sybil-resistant "
         "scoring, a pre-shared responder key.",
         "Shows current disaster/mesh apps are unsafe; no work on making the inference layer "
         "robust to spoofed nodes."],
    ],
    widths=[0.3, 2.0, 1.7, 1.6, 1.4],
)

h3("3.3  Consolidated research gap")
p("Delay-tolerant routing is solved in theory but assumes cooperative, trackable peers; "
  "disaster smartphone systems still require user action and GPS; BLE mesh work ignores "
  "battery-honest duty cycling; and no prior system treats device silence as a first-class "
  "sensing signal for damage assessment. Zone targets exactly this intersection.")

pagebreak()

# ======================================================================
# 4 — PROBLEM IDENTIFICATION
# ======================================================================
h1("Component 4 — Problem Identification  (Max. 1 slide)")
p("Clearly defined problem statement from the existing limitations / gaps, and the need "
  "for the proposed work.", italic=True, size=9)

h3("Limitations of the current state of the art")
nums([
    ("Infrastructure dependence and user burden.", "Existing tools need a tower, a satellite, "
     "dedicated hardware, or two conscious users actively messaging. A person who is unconscious "
     "or buried, or a command post with a dead uplink, gets nothing."),
    ("Silence is discarded.", "Every DTN / mesh design treats a node dropping off as noise. In a "
     "collapse it is the signal — but only if each node has promised when it would speak next, "
     "which no packet format currently carries."),
    ("No trustworthy situational picture.", "Reports are fragmented, contradictory and unverified "
     "(Bridgefy-class spoofing); responders cannot rank sites or localise victims vertically."),
])

h3("Problem statement")
p("Design and build an infrastructure-free, action-free communication and situational-awareness "
  "system for the first 72 hours of a disaster, that (a) relays a compact distress signal "
  "between ordinary smartphones over BLE with no pairing and no servers; (b) makes every packet "
  "declare its next transmission so that silence can be classified as expected or catastrophic; "
  "(c) detects area-wide destruction from correlated silence; and (d) stays useful and "
  "trustworthy on a dying, possibly hostile, network.", italic=True)

h3("Need for the proposed work")
p("Convert “search this whole block” into “search this 100 m cell — 12 phones went silent "
  "together at 04:12,” with a confidence-scored map and a vertical (floor) triage axis that GPS "
  "cannot provide.")

pagebreak()

# ======================================================================
# 5 — SOLUTION STRATEGY  (design diagrams)
# ======================================================================
h1("Component 5 — Solution Strategy  (Max. 3 slides)")
p("Proposed approach, methodology, system design / architecture and planned solution. "
  "All design diagrams are included here.", italic=True, size=9)

h2("5.1  System architecture and the three roles")
p("Diagram 1 — Layered architecture:", bold=True)
mono(r"""
+--------------------------------------------------------------------+
|  UI LAYER  (Jetpack Compose)                                      |
|   CitizenScreen    ResponderScreen + DigHere    MapScreen / EOC   |
+--------------------------------------------------------------------+
|  CORE LOGIC  (pure Kotlin, JVM unit-tested, zero Android imports) |
|   SilenceEvaluator | TriageScorer | CorroborationScorer          |
|   ReportStore (CRDT) | Barometry | StillnessTracker | GridCells   |
+--------------------------------------------------------------------+
|  TRANSPORT  (interface: advertise / onPacket / start / stop)      |
|   BleTransport    .    SimulatedTransport    .    FileTransport    |
+--------------------------------------------------------------------+
|  ANDROID EDGE   BLE adv+scan | Sensors | GNSS | FG service | KV   |
+--------------------------------------------------------------------+
""")
bullets([
    "Transport is an interface (BLE / 500-node simulator / JSON file) so every algorithm runs "
    "identically on any of them — if BLE fails, the demo still runs on the simulator.",
    "The core/ package has no Android imports and is fully unit-testable on the JVM.",
    "A foreground service (connectedDevice type) keeps the radio alive when the screen is off.",
])
p("Diagram 2 — One binary, three physical contexts:", bold=True)
mono(r"""
  CITIZEN                 RESPONDER                    MAP / EOC
  dark, one number     sunlight, gloved, one hand     projector-facing
  "You are being       triage list sorted by need    100 m severity grid
   heard  /  3"        + "Dig Here" proximity bar     + silence layer + replay
  panicking user       metal-detector, not a map      CELL_LOSS is visible
""")

h2("5.2  Data path: store-carry-forward and the Dead Man’s Packet")
p("Diagram 3 — The relay (a message crosses a gap on foot):", bold=True)
mono(r"""
  P1 (trapped)            P2 (carrier, walks)          P3 (responder / EOC)
  +---------+  advertise   +---------+  advertise+relay  +---------+
  | 31-byte | ----------> | store   | ----- walk -----> | store   |
  | beacon  |  hop = 0    | hop = 1 |                   | hop = 2 |
  +---------+             +---------+                   +---------+
  BLE OFF after pickup   dedup by contentId, cap hop 15   converged picture
""")
p("Diagram 4 — Dead Man’s Packet state machine (maintained per known device):", bold=True)
mono(r"""
        heard within grace
   +----------------------------+
   v                            |
 ALIVE --past grace--> OVERDUE --+--> UNEXPECTED_SILENCE
   |                     |            (battery had headroom -> DESTROYED, escalate)
   |  battery <= 10%     |
   +--------------------- +------> EXPECTED_SILENCE
                                  (it announced this -> deprioritise)

 CELL RULE:  >= 3 devices in one ~100 m grid cell  AND  >= 80% of them enter
             UNEXPECTED_SILENCE within a 120 s window   ==>   CELL_LOSS (timestamped)
""")
p("Diagram 5 — Battery-driven duty-cycle ladder:", bold=True)
table(
    ["Battery", "Interval", "PHY", "next_expected_tx broadcast"],
    [["> 60%", "1 s", "Coded (maximum reach)", "1"],
     ["30–60%", "10 s", "alternate Coded / 1M", "10"],
     ["10–30%", "60 s", "1M only", "60"],
     ["< 10%", "300 s", "1M, sparse", "300  (still honest)"]],
    widths=[0.9, 0.9, 2.4, 2.2],
)

h2("5.3  Packet format, de-duplication and trust model")
p("Diagram 6 — The 31-byte packet:", bold=True)
table(
    ["Off", "Len", "Field", "Notes"],
    [
        ["0", "1", "version + type", "type 0 = STATUS, 1 = RESOLVE"],
        ["1", "6", "device_id", "first 6 B of SHA-256(per-install random key)"],
        ["7", "4", "position", "lat/lon delta ×10^5 from a per-incident origin; sentinel = no fix"],
        ["11", "1", "status", "UNKNOWN / SAFE / TRAPPED / RISING_WATER / INJURED / RESOURCE / RESPONDER"],
        ["12", "1", "severity · casualties", "4 bits each"],
        ["13", "2", "timestamp", "minutes since event epoch"],
        ["15", "1", "battery · hops", "4 bits each"],
        ["16", "2", "next_expected_tx (s)", "the field the USP runs on"],
        ["18", "1", "alt_delta", "signed metres versus baseline; sentinel = no barometer"],
        ["19", "4", "auth", "first 4 B of SHA-256(key ‖ payload) — anti-spoof, not full crypto"],
        ["23", "1", "alt_trend", "metres over last 3 transmissions (+ = climbing)"],
        ["24", "7", "reserved", "a RESOLVE packet carries the target’s content-id prefix here"],
    ],
    widths=[0.5, 0.5, 1.7, 3.7],
)
p("Diagram 7 — CRDT de-duplication:", bold=True)
mono(r"""
  contentId = SHA-256( payload with hop nibble masked, auth bytes excluded )
      -> the same message heard at any hop count is ONE record
      -> merging two stores is set-union over contentId = conflict-free, no library
""")
p("Trust model:", bold=True)
bullets([
    "Hashed per-install identity; the auth shape is verified on every relay.",
    "Malformed and duplicate frames are dropped at ingestion.",
    "CorroborationScorer weights each cell by distinct devices, independent hop paths, "
    "physical plausibility and a key-verified RESPONDER report.",
    "A fabricated-ID flood cannot inflate a cell (Sybil-resistant); an optional path-diversity "
    "guard protects CELL_LOSS itself.",
])

pagebreak()

# ======================================================================
# 6 — IMPLEMENTATION
# ======================================================================
h1("Component 6 — Implementation  (Max. 3 slides)")
p("Technologies / tools used, development methodology, implementation details and major "
  "modules (algorithm / pseudocode).", italic=True, size=9)

h2("6.1  Technologies, tools and methodology")
table(
    ["Area", "Choice"],
    [
        ["Language / UI", "Kotlin 1.9, Jetpack Compose (Material 3), single-Activity"],
        ["SDK", "minSdk 26, targetSdk 34, compileSdk 34; AGP 8.7, Gradle 8.9"],
        ["Radio", "BluetoothLeAdvertiser + BluetoothLeScanner; manufacturer-specific data "
         "(company ID 0xFFFF); AdvertisingSetParameters for 1M and LE Coded (S=8); ScanSettings "
         "legacy=false + PHY_LE_ALL_SUPPORTED"],
        ["Sensors", "TYPE_PRESSURE (relative altitude, EMA-smoothed); TYPE_LINEAR_ACCELERATION "
         "(immobility); LocationManager GPS / PASSIVE — no Play Services, no network"],
        ["Persistence", "SharedPreferences (identity, config) + atomic JSON snapshot of the "
         "store and detected collapses in filesDir"],
        ["Lifecycle", "Foreground service (connectedDevice type); coalesced state pump at "
         "≤ 4 Hz"],
        ["Testing / CI", "JUnit4, 82 JVM unit tests; GitHub Actions runs the tests plus "
         "assembleDebug on every push"],
        ["Hard constraints", "No network calls, no crypto library, no downloaded map tiles — "
         "airplane-mode native"],
        ["Methodology", "Phased, checkpoint-gated build H0→H8 (capability probe → codec + tests "
         "→ advertiser / scanner → store / relay → Dead Man’s Packet → barometer → three UIs "
         "→ 500-node simulator → three-phone field validation), then tiered hardening"],
    ],
    widths=[1.4, 5.0],
)

h2("6.2  Core algorithm (pseudocode)")
mono(r"""
# --- build this device's heartbeat (every duty-cycle tick) ---
function BUILD_HEARTBEAT(now):
    batt      = effectiveBatteryPercent()
    interval  = LADDER_INTERVAL(batt)            # 1 / 10 / 60 / 300 s
    phy       = LADDER_PHY(batt)                 # Coded / alternate / 1M
    fix       = freshGpsFix(maxAge = 10 min)     # else NO_FIX
    status    = userAssertion()
                or (RESPONDER if hasResponderKey and mode == RESPONDER)
                or (RISING_WATER   if altimeter.rising)
                or (TRAPPED_DEBRIS if stillness.isStill(now, 5 min))
                or UNKNOWN
    pkt   = Packet(v=1, type=STATUS, deviceId, deltaPos(origin, fix),
                   status, severity, casualties = SelfReport.headcount,
                   stampMinutes(now), battNibble, hop = 0,
                   nextExpectedTx = interval,
                   altDelta = altimeter.deltaByte(), altTrend = altimeter.trend())
    bytes = ENCODE_31B(pkt)
    signingKey = responderKey if status == RESPONDER else deviceKey
    bytes[19..22] = SHA256(signingKey || bytes[0..18])[0..3]
    ADVERTISE(bytes, phy)

# --- on every received advertisement ---
function ON_PACKET(bytes, rssi, tRx):
    if len(bytes) != 31 or not AUTH_SHAPE_OK(bytes): return
    cid = SHA256( maskHopAndDropAuth(bytes) )
    if STORE.contains(cid):
        STORE.fold(cid, rssi, tRx); return             # dedup: CRDT no-op
    STORE.put(cid, bytes, tRx)                          # new record
    pkt = DECODE(bytes)
    if pkt.deviceId != myId:
        SILENCE.onPacket(pkt.deviceId, pkt, tRx)
    if IS_RESOLVE(bytes) and VERIFY(bytes, responderKey):
        RESOLVE_LOG.add(prefix(bytes))                  # mark that report handled

# --- relay pump (round-robin; never starves this device's own signal) ---
every PUMP_PERIOD (2 s):
    result = SILENCE.tick(now)
    for transition in result: LOG(transition, wallClock(now))
    if pumpTick mod 3 == 0:
        p = STORE.nextCarried(skip = devices believed EXPECTED / UNEXPECTED silent)
        if p and hop(p) < 15: ADVERTISE( INCREMENT_HOP(p) )   # copy; original untouched
    else:
        ADVERTISE( BUILD_HEARTBEAT(now) )

# --- Dead Man's Packet: classify one device (each tick) ---
function RECLASSIFY(track, now):
    promised = track.nextExpectedTx * 1000
    grace    = max(8 s, 2 * promised)
    overdue  = now - (track.lastHeard + promised)
    next = ALIVE               if overdue < grace
         = EXPECTED_SILENCE    if track.battery <= 10
         = UNEXPECTED_SILENCE  if overdue > 3 * promised
         = OVERDUE             otherwise
    if next != track.state:
        LOG(track.id, track.state, "->", next, wallClock(now))
        track.state = next
        track.unexpectedSince = now if next == UNEXPECTED_SILENCE else null

# --- area destruction from correlated silence ---
function DETECT_CELL_LOSS(now):
    for (cell, members) in tracks grouped by ~100 m grid cell:
        silent = members where unexpectedSince != null
        if |members| >= 3
           and |silent| >= 0.8 * |members|
           and (max(silentSince) - min(silentSince)) <= 120 s
           and cell not already flagged:
              FLAG CELL_LOSS(cell, |members|, |silent|,
                             firstSilent, lastSilent, detectedAt = now)
""")

h2("6.3  Major modules")
table(
    ["Module", "Responsibility"],
    [
        ["PacketCodec", "encode / decode 31 B, contentId, incrementHop, verifyAuthWithKey, buildResolve"],
        ["ReportStore", "content-addressed set, dedup, lowest-hop-wins, round-robin relayBatch, union mergeFrom"],
        ["SilenceEvaluator", "Dead Man’s Packet — per-device state, timestamped transition log, detectCellLoss"],
        ["TriageScorer", "single rank: rising-water+climbing → just-lost → trapped-below-grade → about-to-go-silent → severity"],
        ["CorroborationScorer", "0–1 cell confidence from distinct devices, hop-path diversity, plausibility, key-verified responder"],
        ["Barometry / RelativeAltimeter", "pressure → smoothed relative altitude + trend; graceful no-barometer"],
        ["StillnessTracker", "linear-acceleration magnitude → sustained immobility → inferred TRAPPED"],
        ["SimulatedTransport", "500 nodes on Rasuwagadhi geography, scripted mass-silence event"],
        ["TransportController / BleForegroundService", "keep the radio alive; own the store and evaluators; drive the pump; live EOC auto-export"],
    ],
    widths=[1.9, 4.5],
)

pagebreak()

# ======================================================================
# 7 — RESULTS AND DISCUSSION
# ======================================================================
h1("Component 7 — Results and Discussion  (Max. 3 slides)")
p("Results obtained, analysis of findings, comparison where applicable, and discussion of "
  "outcomes (tabular form, graphs, screenshots).", italic=True, size=9)

h2("7.1  Functional validation — three real phones, airplane mode")
p("Devices: Samsung Galaxy S23 (trapped), Realme 7 (carrier), Motorola Edge 50 Pro (responder). "
  "Single-phone re-check on the Realme 7 on 2026-09-04.", size=9)
table(
    ["#", "Test", "Result", "Evidence"],
    [
        ["T1", "Direct link, 1 hop", "PASS",
         "Responder shows both phones, 1 hop, LIVE"],
        ["T2", "Store-carry-forward (the demo)", "PASS",
         "S23 with Bluetooth OFF after pickup; its TRAPPED signal still reached the responder at "
         "2 hops, relayed by the carrier"],
        ["T3", "Unexpected silence", "PASS",
         "Healthy battery, powered off -> row turns SILENT (red) with the wall-clock time it "
         "went dark"],
        ["T4", "Expected silence", "PASS",
         "8% battery (declares 300 s), powered off -> EXPECTED (grey), no escalation — the two "
         "are told apart"],
        ["T5", "Barometric floor", "PASS (±2 m)",
         "one floor read +2 m (spec ~±1 m); sign and magnitude correct"],
        ["T6", "Drowning escalation", "PASS",
         "“Water rising” + climbing altitude -> jumps to top of the triage list"],
        ["T7", "Dig Here proximity", "PASS",
         "bar and WARMER / COLDER trend track walking toward and away"],
        ["T8", "CELL_LOSS", "PASS",
         "500-node simulator — one cell flips to a hatched, timestamped “12 dark” hole"],
        ["—", "Coded PHY reception", "PASS",
         "rxByPhy = {CODED, ONE_M} confirmed on the Motorola"],
        ["—", "Capability probe (H0)", "—",
         "S23 / Realme 7 / Motorola: isLeCodedPhySupported, isLeExtendedAdvertisingSupported, "
         "isMultipleAdvertisementSupported all true; max adv data 192 B; barometer present only "
         "on the S23"],
    ],
    widths=[0.4, 1.7, 0.9, 3.4],
)

h2("7.2  Quantitative results and comparison")
p("Table A — engineering results (this build):", bold=True)
table(
    ["Metric", "Value"],
    [
        ["Packet size", "31 B core (BLE legacy-safe) — “a life in 31 bytes”"],
        ["Unit tests / CI", "82 tests, 100% pass, on every push"],
        ["Simulator scale", "500 nodes; Rasuwagadhi toll curve 22 -> 95 -> 469 -> 626 over 72 h, "
         "replayed in ~90 s, no dropped frames"],
        ["Duty-cycle range", "1 s -> 300 s; PHY Coded -> 1M as battery falls"],
        ["CELL_LOSS latency", "detected within the 120 s correlation window; transition "
         "timestamped to the second"],
        ["Heartbeat on Realme 7 @ 27%", "interval 60 s, PHY 1M, next_expected_tx = 60, position "
         "encoded after origin set — ladder behaving exactly as specified"],
        ["Crash safety", "store + collapses + silence tracks reload after an OS kill (24 h age "
         "cutoff)"],
    ],
    widths=[2.0, 4.4],
)

p("Table B — comparison with prior art:", bold=True)
table(
    ["Capability", "Bridgefy / FireChat", "TeamPhone [3]", "Epidemic / PRoPHET [1,2]", "Zone (ours)"],
    [
        ["Works with zero infrastructure", "Yes", "Partial (uses cellular when up)", "Yes (theory)", "Yes"],
        ["No pairing / no GATT connection", "No (GATT)", "Yes", "n/a", "Yes (connectionless adv.)"],
        ["Works when the user is unconscious", "No", "No (needs grouping)", "No", "Yes (sensor-derived status)"],
        ["Dual-PHY (Coded for range)", "No", "No", "No", "Yes"],
        ["Battery-honest duty cycle", "No", "Partial", "No", "Yes (ladder + honest next_expected_tx)"],
        ["Infers area destruction from silence", "No", "No", "No", "Yes (Dead Man’s Packet + CELL_LOSS)"],
        ["Vertical (floor) triage", "No", "No", "No", "Yes (relative barometry)"],
        ["Spoof / Sybil resistance", "Broken [7]", "Limited", "None", "Yes (hashed ID, auth-shape, plausibility, key-verified responder)"],
        ["Confidence-scored situational map", "No", "No", "No", "Yes"],
    ],
    widths=[1.7, 1.1, 1.0, 1.2, 1.4],
)

p("Suggested graphs for the slide:", bold=True)
bullets([
    "Line graph — replayed toll curve (deaths vs. hours: 22, 95, 469, 626) with map "
    "“cells collapsed” overlaid, showing the map converging in ~90 s of stage time.",
    "Bar chart — advertised interval and active PHY versus battery % (1 s/Coded, 10 s/alt, "
    "60 s/1M, 300 s/1M).",
    "Timeline — T3 versus T4: ‘device powered off’ marker, then time-to-classification, one "
    "bar ending in UNEXPECTED_SILENCE (escalate) and one in EXPECTED_SILENCE (quiet).",
])

h2("7.3  Discussion of outcomes")
bullets([
    ("The USP is validated end-to-end.", "T3 versus T4 on real hardware proves the core claim "
     "— the network distinguishes an announced silence (dying battery) from a catastrophic one "
     "(device destroyed), which no prior mesh system does."),
    ("The range / energy trade-off is real and managed.", "Literature puts Coded PHY at ~300 m "
     "urban and > 1 km ideal [5]; Zone spends that reach only while battery allows, then retreats "
     "to 1M — keeping next_expected_tx truthful at every rung."),
    ("“Search this block” becomes “search this 100 m cell.”", "CELL_LOSS plus confidence "
     "scoring convert fragmented reports into a ranked, timestamped picture; the barometric "
     "delta adds the vertical axis GPS cannot [6]."),
    ("Security posture.", "Against the Bridgefy failure modes [7], Zone removes plaintext social "
     "graphs (broadcast-only, hashed IDs), rejects malformed frames at the relay, and makes a "
     "fabricated-node flood unable to move the map."),
    ("Limitations (stated honestly).", "2.4 GHz penetrates rubble poorly — mitigated by "
     "days-long low-duty advertising until a rescuer walks within ~10 m; iOS cannot advertise in "
     "the background (Apple restriction); auth is a truncated MAC, not per-device Ed25519 (a "
     "deliberate scope cut); full three-phone RESOLVE-propagation and a 30-minute soak test "
     "remain to be run."),
    ("Future work.", "LoRa / satellite bridge on the responder phone for the last mile; "
     "per-device signatures; full-app localisation; standard GeoJSON export for agency GIS."),
])

pagebreak()

# ======================================================================
# REFERENCES
# ======================================================================
h1("References")
refs = [
    "A. Vahdat and D. Becker, “Epidemic Routing for Partially Connected Ad Hoc Networks,” "
    "Technical Report CS-2000-06, Duke University, 2000.",
    "A. Lindgren, A. Doria and O. Schelén, “Probabilistic Routing in Intermittently Connected "
    "Networks,” Proc. ACM MobiHoc 2003 (poster); formalised as IRTF RFC 6693, 2012.",
    "Z. Lu, G. Cao and T. La Porta, “TeamPhone: Networking Smartphones for Disaster Recovery,” "
    "IEEE Transactions on Mobile Computing, vol. 16, no. 12, pp. 3554–3567, 2017.",
    "S. M. Darroudi and C. Gomez, “Bluetooth Low Energy Mesh Networks: A Survey,” Sensors "
    "(MDPI), vol. 17, no. 7, art. 1467, 2017.",
    "A. R. Sheikh et al., “Adaptive Physical Layer Selection for Bluetooth 5: Measurements and "
    "Simulations,” Wireless Communications and Mobile Computing, vol. 2021, art. 8842919, "
    "Wiley/Hindawi.",
    "W. Falcon and H. Schulzrinne, “Predicting Floor-Level for 911 Calls with Neural Networks "
    "and Smartphone Sensor Data,” arXiv:1710.11122, 2017.",
    "M. R. Albrecht, J. Blasco, R. B. Jensen and L. Mareková, “Mesh Messaging in Large-Scale "
    "Protests: Breaking Bridgefy,” in Topics in Cryptology – CT-RSA 2021, LNCS vol. 12704, "
    "Springer; IACR ePrint 2021/214.",
    "T. Watanabe et al., “Delay-Tolerant Networking for Tsunami Evacuation on the Small Island "
    "of Hachijojima: A Study of Epidemic and Prophet Routing,” arXiv:2601.00109, 2026.",
    "“The earthquake impact on telecommunications infrastructure in Nepal: a preliminary "
    "spatial assessment,” International Journal of Disaster Risk Reduction (Elsevier), 2023.",
]
for i, rtext in enumerate(refs, 1):
    par = doc.add_paragraph()
    par.paragraph_format.left_indent = Inches(0.3)
    par.paragraph_format.first_line_indent = Inches(-0.3)
    rr = par.add_run(f"[{i}]  ")
    rr.bold = True
    par.add_run(rtext).font.size = Pt(9)

doc.save(OUT)
print("wrote", OUT)

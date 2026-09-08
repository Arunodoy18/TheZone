#!/usr/bin/env python3
"""Zone — Results figure for the PBL Results & Discussion slide.  python3 scripts/build-results-chart.py"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, GREEN, TEAL = "#14181b", "#0B3D33", "#0E8C77"
MUTE, AMBER, ALARM, GRID = "#9AA3A8", "#C77A16", "#B5311B", "#E3DECF"

plt.rcParams.update({
    "font.family": "Helvetica", "font.size": 9,
    "axes.edgecolor": "#B9C0C4", "axes.linewidth": 0.8,
    "axes.titlesize": 10.5, "axes.titleweight": "bold", "axes.titlecolor": GREEN,
    "axes.labelcolor": INK, "text.color": INK,
    "xtick.color": "#565D63", "ytick.color": "#565D63",
    "figure.facecolor": "white", "axes.facecolor": "white",
})

fig, ax = plt.subplots(2, 2, figsize=(12, 8.2))
fig.suptitle("ZONE  —  RESULTS  (Component 7)", fontsize=13, fontweight="bold", color=GREEN, y=0.975)
fig.subplots_adjust(left=0.115, right=0.975, top=0.90, bottom=0.135, hspace=0.62, wspace=0.28)

# ------------------------------------------------- A: prior-art comparison
a = ax[0, 0]
systems = ["Bridgefy /\nFireChat", "Epidemic /\nPRoPHET", "TeamPhone\n[Lu 2017]", "ZONE\n(ours)"]
met  = [2, 2, 3, 9]
cols = [MUTE, MUTE, MUTE, TEAL]
bars = a.barh(systems, met, color=cols, height=0.58)
a.set_xlim(0, 9.8); a.set_xlabel("capabilities satisfied  (out of 9)")
a.set_title("A · Comparison with prior art", pad=10)
a.invert_yaxis(); a.grid(axis="x", color=GRID, lw=0.7); a.set_axisbelow(True)
for bar, v in zip(bars, met):
    a.text(v + 0.18, bar.get_y() + bar.get_height() / 2, f"{v}/9",
           va="center", fontsize=9, fontweight="bold", color=TEAL if v == 9 else "#565D63")

# ------------------------------------------------- B: battery -> duty cycle
b = ax[0, 1]
xb = [100, 60, 60, 30, 30, 10, 10, 0]
yb = [1,   1, 10, 10, 60, 60, 300, 300]
b.step(xb, yb, where="post", color=GREEN, lw=2.2)
b.fill_between(xb, yb, 0.6, step="post", color=TEAL, alpha=0.12)
b.set_yscale("log"); b.set_xlim(102, -2); b.set_ylim(0.6, 620)
b.set_xlabel("battery  (%)"); b.set_ylabel("advertised interval  (s, log)")
b.set_title("B · Battery-adaptive duty cycle  (measured, Realme 7)", pad=10)
b.set_yticks([1, 10, 60, 300]); b.set_yticklabels(["1", "10", "60", "300"])
b.grid(True, color=GRID, lw=0.7, which="both"); b.set_axisbelow(True)
for xpos, ipos, phy, c, dy in [(80, 1, "Coded PHY", GREEN, 8),
                               (45, 10, "alternate Coded / 1M", GREEN, 8),
                               (16, 60, "1M only", AMBER, 8),
                               (5, 300, "1M, sparse", ALARM, 8)]:
    b.annotate(phy, (xpos, ipos), textcoords="offset points", xytext=(0, dy),
               ha="center", fontsize=7.2, fontweight="bold", color=c)
b.scatter([27], [60], color=ALARM, zorder=5, s=30)
b.annotate("device test @ 27 %\n60 s · 1M PHY", (27, 60), textcoords="offset points",
           xytext=(14, -30), ha="center", fontsize=6.9, color=ALARM)

# ------------------------------------------------- C: functional validation
c = ax[1, 0]
tests = ["T1  direct link", "T2  store-carry-forward",
         "T3  unexpected silence", "T4  expected silence",
         "T5  barometric floor", "T6  drowning escalation",
         "T7  Dig Here proximity", "T8  CELL_LOSS",
         "Coded PHY reception"]
detail = ["1 hop, LIVE", "relayed by carrier", "SILENT + timestamp", "EXPECTED, no escalation",
          "+2 m vs ~+/-1 m", "jumps to top of list", "hot / cold trend tracks", "hatched 'hole' + time",
          "{ CODED, 1M } on Motorola"]
tcol = [TEAL] * 9; tcol[4] = AMBER
cb = c.barh(range(len(tests)), [1] * 9, color=tcol, height=0.6)
for i, bar in enumerate(cb):
    c.text(0.03, bar.get_y() + bar.get_height()/2, detail[i], va="center",
           fontsize=6.8, color="white", fontweight="bold")
c.set_yticks(range(len(tests))); c.set_yticklabels(tests, fontsize=7.6)
c.set_xlim(0, 1.62); c.set_xticks([]); c.invert_yaxis()
c.set_title("C · Functional validation — 3 phones, airplane mode\n"
            "8 / 8 tests PASS   (* T5 borderline: +2 m vs ~±1 m spec)", pad=10, fontsize=9.6)
for i, bar in enumerate(cb):
    c.text(1.04, bar.get_y() + bar.get_height() / 2, "PASS*" if i == 4 else "PASS",
           va="center", fontsize=7.8, fontweight="bold", color=AMBER if i == 4 else TEAL)
for s in ("top", "right", "bottom"):
    c.spines[s].set_visible(False)

# ------------------------------------------------- D: replayed toll curve
d = ax[1, 1]
hours, toll = [0, 24, 48, 72], [22, 95, 469, 626]
d.plot(hours, toll, "-o", color=GREEN, lw=2, ms=5)
d.fill_between(hours, toll, color=TEAL, alpha=0.12)
d.set_xlabel("hours after event"); d.set_ylabel("cumulative toll  (nodes silenced)")
d.set_title("D · 500-node simulation — real 2015 Rasuwagadhi curve", pad=10)
d.set_xticks(hours); d.set_ylim(0, 700)
d.grid(True, color=GRID, lw=0.7); d.set_axisbelow(True)
for h, t in zip(hours, toll):
    d.annotate(str(t), (h, t), textcoords="offset points", xytext=(5, 7),
               fontsize=7.6, fontweight="bold", color=GREEN)
d.text(0.04, 0.90, "72 h replayed in ~90 s of stage time\nno dropped frames · one cell -> timestamped CELL_LOSS 'hole'",
       transform=d.transAxes, fontsize=6.9, color="#565D63", va="top")

# ------------------------------------------------- footnotes
fig.text(0.5, 0.062,
         "9 capabilities scored:  no infrastructure  ·  no pairing / GATT  ·  works when the user is unconscious  ·  "
         "dual-PHY (Coded)  ·  battery-honest duty cycle  ·  infers area destruction from silence  ·  "
         "vertical (floor) triage  ·  Sybil resistance  ·  confidence-scored map",
         ha="center", fontsize=7.0, color="#565D63")
fig.text(0.5, 0.030,
         "Engineering:  31-byte packet  ·  87 JVM unit tests (100 % pass, CI on every push)  ·  "
         "500-node simulator  ·  duty cycle 1 s to 300 s  ·  CELL_LOSS detected within the 120 s window  ·  "
         "state survives an OS kill (24 h cutoff)",
         ha="center", fontsize=7.0, color="#565D63")

out = "/Users/arunodoybanerjee/Desktop/TheZone/docs/diagrams/results"
fig.savefig(out + ".png", dpi=200)
fig.savefig(out + ".pdf")
print("wrote results.png / results.pdf")

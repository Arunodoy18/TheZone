// Serverless proxy for NASA FIRMS (active fire hotspots).
//
// The FIRMS MAP_KEY is a personal, rate-limited credential tied to one NASA
// Earthdata account. It is never placed in dashboard.html or committed to
// git — it lives only as a Vercel environment variable (FIRMS_MAP_KEY),
// read here, server-side, and never sent to the browser. The client calls
// this same-origin endpoint instead of NASA's directly.
//
// Scoped to South Asia (roughly India/Nepal/Bhutan/Bangladesh/N. Myanmar) —
// the region this project is actually built for, and small enough to render
// as individual map markers (~dozens, not the ~70k a world query returns).

const BBOX = "68,6,97,36"; // west,south,east,north
const SOURCE = "VIIRS_SNPP_NRT";
const DAY_RANGE = 1;

export default async function handler(req, res) {
  const key = process.env.FIRMS_MAP_KEY;
  if (!key) {
    res.status(500).json({ error: "FIRMS_MAP_KEY not configured" });
    return;
  }

  try {
    const url = `https://firms.modaps.eosdis.nasa.gov/api/area/csv/${key}/${SOURCE}/${BBOX}/${DAY_RANGE}`;
    const r = await fetch(url);
    if (!r.ok) throw new Error(`FIRMS HTTP ${r.status}`);
    const csv = await r.text();

    const lines = csv.trim().split("\n");
    const header = (lines.shift() || "").split(",");
    const latI = header.indexOf("latitude");
    const lonI = header.indexOf("longitude");
    const confI = header.indexOf("confidence");
    const frpI = header.indexOf("frp");
    const dateI = header.indexOf("acq_date");
    const timeI = header.indexOf("acq_time");

    const points = lines
      .filter(Boolean)
      .map((line) => line.split(","))
      .filter((c) => latI >= 0 && lonI >= 0 && c[latI] && c[lonI])
      .map((c) => ({
        lat: parseFloat(c[latI]),
        lon: parseFloat(c[lonI]),
        confidence: confI >= 0 ? c[confI] : null,
        frp: frpI >= 0 ? parseFloat(c[frpI]) : null,
        acqDate: dateI >= 0 ? c[dateI] : null,
        acqTime: timeI >= 0 ? c[timeI] : null,
      }));

    // Fire data refreshes every few hours on NASA's side, not every second —
    // cache at the edge so this doesn't burn the personal key's rate limit.
    res.setHeader("Cache-Control", "public, max-age=1800, s-maxage=1800");
    res.status(200).json({ count: points.length, points });
  } catch (err) {
    res.status(502).json({ error: String(err && err.message || err) });
  }
}

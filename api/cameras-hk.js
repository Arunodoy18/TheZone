// Hong Kong Transport Department traffic snapshot cameras — official open
// data published through data.gov.hk for public reuse (no key required).
//
// Their camera-location index is an XML file served without CORS headers, so
// a browser can't read it directly. This function fetches it server-side and
// returns compact JSON. The snapshot images themselves (tdcctv.data.one.gov.hk)
// are loaded by the browser straight into <img> tags, which needs no CORS.
//
// Only URLs on the official snapshot host are passed through, so a malformed
// or tampered index can't smuggle arbitrary URLs into the dashboard.

const INDEX = "https://static.data.gov.hk/td/traffic-snapshot-images/code/Traffic_Camera_Locations_En.xml";
const IMAGE_HOST = "https://tdcctv.data.one.gov.hk/";

const decode = (s) =>
  s.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"')
   .replace(/&apos;/g, "'").replace(/&amp;/g, "&").trim();

export default async function handler(req, res) {
  try {
    const r = await fetch(INDEX);
    if (!r.ok) throw new Error(`data.gov.hk HTTP ${r.status}`);
    const xml = await r.text();

    const field = (block, tag) => {
      const m = block.match(new RegExp(`<${tag}>([\\s\\S]*?)</${tag}>`));
      return m ? decode(m[1]) : "";
    };

    const cameras = xml
      .split("<image>")
      .slice(1)
      .map((b) => ({
        key: field(b, "key"),
        desc: field(b, "description"),
        region: field(b, "region"),
        district: field(b, "district"),
        lat: parseFloat(field(b, "latitude")),
        lon: parseFloat(field(b, "longitude")),
        url: field(b, "url"),
      }))
      .filter(
        (c) =>
          c.key &&
          Number.isFinite(c.lat) &&
          Number.isFinite(c.lon) &&
          c.url.startsWith(IMAGE_HOST)
      );

    res.setHeader("Cache-Control", "public, max-age=3600, s-maxage=3600");
    res.status(200).json({ count: cameras.length, cameras });
  } catch (err) {
    res.status(502).json({ error: String((err && err.message) || err) });
  }
}

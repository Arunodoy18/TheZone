# Zone — 6–12 Month Pre-Incubation Budget Breakdown
Researched 2026-10-23. Office space, desk, internet, and SMIT IIC mentorship are already covered and excluded below. All prices are flagged **Verified** (found a specific current/recent listing or official page) or **Estimate** (inferred, aggregated, or dated — treat as directional only).

---

## 1. Device lab (BLE diversity testing)

### Takeaway
Spec sheets cannot reliably tell you whether a phone supports Coded PHY — it has to be tested at runtime with `BluetoothAdapter.isLeCodedPhySupported()` or the nRF Connect app, because real chipsets diverge from their claimed capability. Barometer presence is a separate, independently-varying spec, and the two together (budget phone + Coded PHY + barometer) could not be confirmed for any single current model from available sources — this is a real gap, not just a sourcing inconvenience.

### Cited findings
- A Nordic Semiconductor DevZone thread (dated, ~2020 but methodologically still valid) found a **Galaxy S10 detected a Coded PHY device while an iPhone XS/11 did not**, a **Galaxy Tab S6 confirmed Coded PHY support**, and a **Xiaomi Pocophone F1 claimed long-range compatibility on paper but failed to detect the device in practice** — i.e., advertised LR support is not reliable evidence of working Coded PHY. — [Nordic DevZone](https://test-devzone.nordicsemi.com/f/nordic-q-a/33060/is-there-any-smartphone-with-supporting-of-bluetooth-5-long-range-feature/229036)
- The same thread notes Coded PHY depends on Extended Advertising support as well — both must be checked, not just one. — [Nordic DevZone](https://devzone.nordicsemi.com/f/nordic-q-a/33060/is-there-any-smartphone-with-supporting-of-bluetooth-5-long-range-feature/126917)
- Bluetooth.com confirms Coded PHY / 2M PHY support depends on whether the device's Bluetooth 5 hardware implements the optional features — it is not guaranteed just because a phone says "Bluetooth 5.0/5.x". — [Bluetooth.com](https://www.bluetooth.com/?p=3599)
- **GSMArena sensor listings found in this research:**
  - Nothing Phone (2a) — Barometer: **No**. — [Smartprix spec comparison](https://smartprix.com/mobiles/oppo_a2_vs_nothing_phone_2a-cpd1gt5shjzv_pd17fz86vh0.php)
  - Samsung Galaxy A34 (5G) — Barometer: **No** (GSMArena sensor line: fingerprint, accelerometer, gyro, compass only). — [GSMArena](https://m.gsmarena.com/samsung_galaxy_a34-12074.php)
  - Samsung Galaxy A54 (5G) — Barometer: **Yes, but GSMArena marks it "USA only"** — regional variant dependent, so the Indian retail unit is not confirmed to have one. — [GSMArena](https://m.gsmarena.com/samsung_galaxy_a54-12070.php)
  - Poco X6 5G — sensor list (Smartprix) is proximity, ambient light, accelerometer, e-compass, gyroscope — **no barometer listed**. — [Smartprix](https://smartprix.com/mobiles/xiaomi_redmi_note_13_5g_vs_poco_x6_5g-cpd1y4yzrj6s_pd1s6xg4m8e.php)
  - Redmi Note 13 Pro / Pro+ — barometer marked **"No"** in GSMChoice comparisons against the Poco X6. Base Redmi Note 13 (non-Pro) sensor list was not found. — [GSMChoice](https://www.gsmchoice.com/en/compare/poco-x6-vs-redmi-note13pro/)
  - A GSMArena reader-comment thread on the Motorola Edge 60 Fusion review states users believe the **Indian retail unit of the Edge 50 Neo omits the barometer entirely** even where other-region units may have it — user testimony, not a spec sheet. — [GSMArena comments](https://m.gsmarena.com/newscomm-67214.php)

### Inferences
- Barometers in this ₹15,000–30,000 band skew toward Samsung's upper-A-series and Google's A-series Pixels (both brands have historically included barometers more consistently than MediaTek-chipset budget phones from Xiaomi/Redmi/Poco/Realme), but **region-locked variation (as seen with the A54) means the Indian unit must be independently verified, not assumed from a global spec sheet.**
- Because neither Coded PHY nor barometer presence can be trusted from spec pages alone, the only reliable method is: buy 1 unit of each candidate model, test with `nRF Connect` + a small `isLeCodedPhySupported()`/`isLeExtendedAdvertisingSupported()` probe app (this is exactly the H0 capability-probe step already mandated first in the build order), and return/resell any that fail before buying the rest of the pool.
- A realistic device-lab pool for a 4-person team covering "varied chipsets/BLE stacks" at this budget tier would reasonably include: 1 Snapdragon-chipset phone (e.g. a Poco/Redmi Snapdragon variant), 1 MediaTek Dimensity-chipset phone (e.g. Redmi Note 13 series), 1 Samsung Exynos/Snapdragon A-series phone (for barometer + Samsung's BLE stack), and 1 older/legacy-PHY-only phone to validate the "fall back to legacy advertising" path mandated in CLAUDE.md. That's 4 incremental devices beyond the team's existing 2–3, at roughly ₹15,000–25,000 each.

### Gaps
- No current (2026), authoritative, aggregated list of "mid-range Android phones with confirmed Coded PHY support" exists in public sources — the last concrete device-level reports found date to ~2020 (Galaxy S10, Tab S6, Redmi Note 9S). This is a genuine information gap, not just a search miss; it should be resolved empirically by the team, not by more desk research.
- No single current model was confirmed to have **both** Coded PHY and a barometer at the ₹15,000–30,000 price point. Recommend budgeting for return/exchange risk (India has a 7–10 day return window on most e-commerce electronics) rather than assuming first-purchase success.

### Budget line
| Item | Estimate | Basis |
|---|---|---|
| 4 additional diversity-test phones (1 Snapdragon, 1 MediaTek, 1 Samsung/barometer-capable, 1 legacy/no-Coded-PHY control), ₹15,000–25,000 each | **₹70,000–90,000** | Estimate, built from Redmi Note 13 Pro+ / Poco X6 / Galaxy A34 class pricing found above; exact models to be confirmed by runtime test, not spec sheet |
| Contingency for 1 return/exchange (device fails BLE requirement after purchase) | **₹5,000** (estimate, buffer only) | Estimate |

---

## 2. Google Play Console developer registration fee

### Takeaway
The fee is confirmed at a flat, one-time **US$25**, paid by credit/debit card (no prepaid cards), with no recurring annual charge, directly from Google's own support page.

### Cited findings
- Google's official help page states: "There is a US$25 one-time registration fee," payable by credit or debit card; prepaid cards are not accepted; the page does not mention any recurring Play Console cost. — [Google Play Console Help](https://support.google.com/googleplay/android-developer/answer/113468)
- Multiple 2026-dated secondary sources corroborate the $25 one-time figure and no renewal charge. — [afkarsoftware.com](https://afkarsoftware.com/en/blog-detail/google-play-console-account-2026-one-time-25-fee/)
- The fee is non-refundable if identity/payment verification fails, and payment does not guarantee app approval or production access. — [afkarsoftware.com](https://afkarsoftware.com/en/blog-detail/google-play-console-account-2026-one-time-25-fee/)
- A personal developer account created after Nov 13, 2023 must run a closed test with ≥12 opted-in testers for 14 continuous days before it can apply for production/public release — a process cost (time/testers), not a cash cost. — [afkarsoftware.com](https://afkarsoftware.com/en/blog-detail/google-play-console-account-2026-one-time-25-fee/)

### Inferences
- None needed — this is a confirmed flat fee.

### Gaps
- None of real substance; the only open item is the exact INR conversion on the day the team pays, since banks apply their own forex markup.

### Budget line
| Item | Cost | Basis |
|---|---|---|
| Google Play Console one-time developer registration fee | **US$25 ≈ ₹2,375** (at ≈₹95/USD, see §exchange rate below) | **Verified** (Google official page) |

---

## 3. Barometer-capable test hardware

### Takeaway
Confirmed barometer presence in this research was inconsistent and often regional even within the same model line (Galaxy A54 marked barometer "USA only" on GSMArena); confirmed **absence** was easier to establish (A34, Poco X6, Redmi Note 13 Pro/Pro+, Nothing Phone 2a all lack one). No budget (₹15–30k) phone was positively confirmed in this research to have a barometer in its Indian retail SKU — this needs direct verification against the Indian GSMArena/Samsung India spec page or in-hand testing (e.g. via a sensor-reading app) before purchase.

### Cited findings
- Galaxy A54 5G: barometer listed by GSMArena but flagged "USA only" variant-dependent. — [GSMArena](https://m.gsmarena.com/samsung_galaxy_a54-12070.php)
- Galaxy A34 5G: no barometer in sensor list. — [GSMArena](https://m.gsmarena.com/samsung_galaxy_a34-12074.php)
- Nothing Phone (2a): barometer explicitly "No". — [Smartprix](https://smartprix.com/mobiles/oppo_a2_vs_nothing_phone_2a-cpd1gt5shjzv_pd17fz86vh0.php)
- Poco X6 5G / Redmi Note 13 Pro/Pro+: no barometer in available sensor lists. — [Smartprix](https://smartprix.com/mobiles/xiaomi_redmi_note_13_5g_vs_poco_x6_5g-cpd1y4yzrj6s_pd1s6xg4m8e.php), [GSMChoice](https://www.gsmchoice.com/en/compare/poco-x6-vs-redmi-note13pro/)
- Google Pixel 7a: search did not surface a direct barometer confirmation either way; historically Pixel phones (6a, 7, 7a) have included barometers, but this specific claim could not be sourced in this research pass. — Gap, see below.
- Galaxy A54 India price: launch price ₹38,999 (128GB)/₹40,999 (256GB) in March 2023; a Bajaj Finserv 2026-dated price list shows ₹41,999 for 8GB+128GB (likely stale/MRP, not street price); a Smartprix aggregator shows a much lower ₹20,825 which could not be cross-verified — **price range is genuinely uncertain, treat as ₹21,000–42,000 pending a live Flipkart/Amazon.in check.** — [sammobile.com](https://www.sammobile.com/news/galaxy-a34-a54-launched-available-india/), [Bajaj Finserv](https://www.bajajfinserv.in/samsung-galaxy-a54-price-in-india), [Smartprix](https://www.smartprix.com/mobiles/samsung-galaxy-a54-5g-ppd1acdj19od)

### Inferences
- The most defensible plan is to treat barometer acquisition as its own small, separate purchase decision rather than bundling it into the general device-lab pool: buy ONE phone specifically chosen for barometer confirmation (ideally after a 5-minute in-store or pre-return-window check with a sensor-list app like "Sensor Box" or sensor-reading via Android's `SensorManager.TYPE_PRESSURE`), rather than trusting any spec sheet found here.
- Given Samsung's historically broader sensor inclusion versus MediaTek-chipset budget phones, a Samsung A5x-series unit is the better bet than a Redmi/Poco/Realme unit — but this is a probabilistic recommendation, not a confirmed fact for India-market stock.

### Gaps
- Could not confirm barometer presence for Pixel 7a, Galaxy M34, or any single 2025/2026-released budget model (e.g. Galaxy A55, Redmi Note 14 series) in this research pass — would need a direct, India-specific GSMArena or Samsung India spec-sheet check before purchase.

### Budget line
This overlaps with the device-lab pool in §1 — no separate line item is needed beyond what's already budgeted, provided the Samsung A5x-class unit in the pool above is the one earmarked for barometer testing. **Added cost: ₹0 beyond §1**, but flag risk: if the unit purchased turns out not to have a barometer in its Indian SKU, budget a second attempt.

| Item | Estimate | Basis |
|---|---|---|
| Contingency: second barometer-test phone purchase if first unit's Indian SKU lacks one | **₹15,000–20,000** | Estimate / risk buffer |

---

## 4. Hardware-beacon prototyping (nRF52-series) — speculative, conservative estimate

### Takeaway
A single nRF52840 Development Kit costs roughly **$43–48 USD (≈₹4,000–4,600)** from authorized international distributors (Mouser/Future Electronics), excluding India import duty/GST; a cheaper nRF52840 USB dongle is available on Amazon.in for **₹2,900** (discounted from ₹3,999 MRP). For a conservative first prototyping pass (2–3 boards + batteries + basic enclosures), budget **₹15,000–25,000** total.

### Cited findings
- Future Electronics lists the nRF52840-DK at **$43.17** per unit (dropping to $41.12 at 75+ units). — [Future Electronics](https://www.futureelectronics.com/p/semiconductors--wireless-rf--rf-wireless/nrf52840-dk-nordic-semiconductor-3205207)
- Mouser.de lists the same DK at **€41.37** per unit. — [Mouser.de](https://www.mouser.de/de/ProductDetail/949-NRF52840-DK)
- An OEMsTrade aggregator and FindChips list it at **$46.37** and **$48.34** respectively. — [OEMsTrade](https://www.oemstrade.com/search/NRF52840), [FindChips](https://findchips.com/search/nrf52840)
- A price-tracking site shows the **Nordic nRF52840 USB dongle on Amazon.in at ₹2,900** (list price ₹3,999, ~27% off), snapshot dated July 2026 — but this is a dongle, not a full dev board with GPIO/sensor breakout. — [pricehistory.app](https://pricehistory.app/p/nordic-semiconductor-nrf52840-dongle-small-usb-dongle-oDzJJD2h)
- An IndiaMART Jaipur seller lists the **SparkFun Pro nRF52840 Mini at ₹5,999/unit**, excluding taxes. — [IndiaMART](https://m.indiamart.com/proddetail/sparkfun-pro-nrf52840-mini-bluetooth-development-board-25750407773.html)
- An Indian hobbyist guide gives unverified estimate ranges: nRF52840 DK **₹4,000–6,000**, Adafruit Feather nRF52840 Express **₹2,500–3,500** — flagged explicitly by the source itself as estimates, not live listings. — [microcontrollerslab.com](https://microcontrollerslab.com/best-nrf52840-development-boards-buying-guide/)

### Inferences
- International-distributor DK boards (Mouser/Future Electronics/DigiKey) will likely incur Indian customs duty + IGST on import (commonly an additional 15–30%+ on electronics import value, though this specific rate was not verified in this research and should be checked against current customs tariff schedules before ordering) — so landed cost per DK board is more realistically **₹4,500–6,500**, not the raw $43–48 sticker price.
- Domestic sellers (IndiaMART, Amazon.in) avoid this friction and are the more realistic sourcing channel for a cash-strapped student team, even at a modest per-unit premium over the raw US distributor price.
- This line item should stay small and exploratory: 2–3 boards is enough to prove the beacon concept (pinned, low-power BLE broadcaster) without committing real money to an enclosure/battery supply chain before the core app's H0–H8 build order is validated on real phones.

### Gaps
- Battery pack and enclosure costs were not researched in this pass (out of the explicit search scope reached) — a conservative placeholder of ₹500–1,500 per unit (generic LiPo pack + basic 3D-printed or off-the-shelf enclosure) is used below as an estimate, not a sourced price.

### Budget line
| Item | Qty | Unit cost (estimate unless noted) | Total |
|---|---|---|---|
| nRF52840 DK or SparkFun Pro Mini board (domestic seller, avoids import duty friction) | 2 | ₹5,000–6,000 (**Verified** range via IndiaMART/Amazon.in listings) | ₹10,000–12,000 |
| Battery pack + basic enclosure per unit | 2 | ₹500–1,500 (**Estimate**, not sourced) | ₹1,000–3,000 |
| Buffer for a 3rd board if the concept needs multi-hop testing | 1 | ₹5,000–6,000 | ₹5,000–6,000 |
| **Subtotal** | | | **₹16,000–21,000** |

---

## 5. Field-pilot logistics (Sikkim local travel)

### Takeaway
No source gave a direct Gangtok↔Rangpo fare. The closest real numbers are full-route Siliguri/Bagdogra↔Gangtok shared-taxi fares (₹400–800/person) and Gangtok local full-day car-with-driver hire (₹1,400–3,000/day depending on vehicle and source) — both of which are **Estimate-grade** (aggregator listings or personal trip-report figures, not an official rate card), and the Rangpo-specific figure is this report's own pro-rata inference, not a sourced number.

### Cited findings
- A 2026-dated travel guide puts shared cabs from Bagdogra airport to Gangtok at **₹400–600 per person**; a separate source gives shared taxis from Siliguri's SNT stand at the same **₹400–600 per person**. — [discoverwithdheeraj.com](https://discoverwithdheeraj.com/gangtok-weekend-trip-guide/), [darjeeling-tourism.com](https://darjeeling-tourism.com/darj_00021f.htm)
- A traveler forum post budgets a shared cab from Bagdogra/NJP to Gangtok at **₹600–800 per person**. — [iQOO community](https://community.iqoo.com/in/thread/116943)
- CabBazar (an aggregator, undated) lists Gangtok full-day car-with-driver rental at roughly **₹1,400 (hatchback) / ₹1,500 (sedan) / ₹2,000 (SUV)**, inclusive of driver and night charges. — [CabBazar](https://cabbazar.com/city/car-rental-gangtok)
- A personal trip-planning post on the same forum budgets **₹2,500–3,000 for a full day of local taxi sightseeing**, plus roughly ₹200 in entry fees — a traveler's own estimate, not a quoted fare. — [iQOO community](https://community.iqoo.com/in/thread/116943)

### Inferences
- Since Rangpo sits roughly a third of the way along the Siliguri/Bagdogra–Gangtok road (via Rangpo and Singtam), a Gangtok-to-Rangpo-area day trip for range testing is very likely well under the full-route fare — but this is this report's own pro-rata inference, not a sourced figure, and should be confirmed with a local Gangtok taxi stand before the team commits to a plan.
- For a 4-person team doing a single-day round-trip test (Gangtok base → test site → back), hiring one vehicle for the whole day (₹1,500–3,000, per CabBazar/forum estimates) is both cheaper and operationally better (keeps the team together, phones can be tested at fixed GPS waypoints) than 4 individual shared-taxi fares.
- Budget 2–3 such day trips across the 6–12 month window (one for initial range calibration, one mid-build re-test after H2/H3, one pre-demo full validation aligned with the H8 "three-phone validation" build step) rather than one single trip.

### Gaps
- No official/current Gangtok or Rangpo-area local taxi tariff card was found; all figures are aggregator or traveler-forum estimates and may be stale (CabBazar page undated). The team should get a direct local quote before finalizing this line.
- No research was done on inter-city bus/SUMO shared-jeep fares specifically between Gangtok and Rangpo (the more common budget option locally); this is a gap given the search returned only tourist-route pricing.

### Budget line
| Item | Qty | Unit cost (Estimate) | Total |
|---|---|---|---|
| Full-day vehicle hire with driver, Gangtok-area range test (sedan/SUV) | 3 trips | ₹2,000–3,000/day | ₹6,000–9,000 |
| Incidentals (fuel buffer for driver detours, snacks/water for 4-person team, entry fees if any) | 3 trips | ₹500/trip | ₹1,500 |
| Basic field-test equipment: measuring tape or laser rangefinder, portable battery packs/power banks to keep test phones charged in field, a simple compass/GPS app (no extra device cost — use team's own phones) | 1 set | ₹2,000–3,500 (**Estimate**, not sourced — generic rangefinder + 2 power banks) | ₹2,000–3,500 |
| **Subtotal** | | | **₹9,500–14,000** |

---

## 6. Pre-incorporation compliance / legal

### Takeaway
Bluetooth's 2.4 GHz band is confirmed license-exempt in India, so there is no spectrum-licensing fee. However, "license-exempt spectrum" and "equipment approval" are two different things in Indian telecom law — standalone wireless **equipment** (like a prototype nRF52 beacon) generally still needs WPC Equipment Type Approval (ETA) before commercial sale, though a 2019 DoT exemption specifically covers test/prototype units, which is the team's actual situation right now. The Zone **app itself** runs on already-WPC-approved, commercially sold phones and has no backend/server processing personal data off-device, so the realistic compliance cost for the next 6–12 months, at this stage, is **₹0** — this is this report's own reasoned inference from the sourced facts below, not independent legal advice, and should be confirmed with an actual lawyer before any public launch or beacon hardware sale.

### Cited findings
- A certification-india.com article describes a "revised SRRF exemption list" confirming equipment in the **2400–2483.5 MHz band (used by Bluetooth and Wi-Fi) can operate without a license**, subject to power/bandwidth limits — dated as a notice from January 18, 2024, though the hosting page itself carries a January 2026 date, so the underlying DoT/WPC notice date should be independently verified. — [certification-india.com](https://www.certification-india.com/en/revised-srrf-exemption-list-facilitates-easier-wpc-compliance-for-wireless-devices-in-india/)
- Separately, sources describe an **Equipment Type Approval (ETA)** requirement for Bluetooth *products* sold in India — i.e., license-exemption on spectrum use does not remove the equipment-certification step for hardware that is manufactured/imported/sold. — [alephindia.in](https://alephindia.in/product/bluetooth-headphone.php), [taxguru.in](https://taxguru.in/corporate-law/equipment-type-approval-eta-license.html)
- A 2019 Department of Telecommunications office memorandum **exempts test/prototype/sample/pre-launch units — explicitly including short-range devices — from the WPC ETA requirement**, via a self-declaration from the importer, covering up to 1,000 units per model, provided the units are not sold in India and are either re-exported or destroyed under WPC guidelines after testing. — [DoT/C-DOT memorandum, 26-02-2019](https://dotws.cdot.in/sites/default/files/Exemption%20of%20ETA%20and%20Import%20for%20Test-Prototype%20Sample%20Units%20dt%2026-02-2019_0.pdf)
- Import licensing (separate from ETA) is still governed by DGFT's EXIM policy, and "Restricted"-category devices face a separate scrutiny-based ETA + import-license track — not believed to apply to nRF52 dev boards bought as commercial off-the-shelf components, but not independently confirmed in this research. — [DoT compendium of orders](https://www.stagingeservices.dot.gov.in/sites/default/files/circular-notifications/Compendium%20of%20Orders%20related%20import%20licence%20-signed%20copy%20060722.pdf)

### Inferences
- The Android phones the team buys for the device lab (§1) are already commercially sold, WPC-ETA-approved devices in India — the team is not importing new radio equipment by buying them, so no additional telecom compliance cost attaches to the device lab.
- The nRF52 beacon boards (§4), bought in small quantities (2–3 units) purely for internal prototyping and never sold or placed into public/commercial service, plausibly fall within the 2019 DoT test/prototype exemption's spirit — but this report found a *general* prototype-exemption memorandum, not a nRF52-specific or Nordic-specific ruling, so this should be treated as a reasonable-but-unconfirmed reading, not a guaranteed exemption. If the team later wants to deploy beacons in a real pilot (even a non-commercial field test with members of the public present), that is the point at which real legal review becomes necessary — budget for it then, not now.
- The app itself (per CLAUDE.md: no network calls, no backend, no personal data leaves the device, device ID is a local truncated hash) does not appear to trigger India's Digital Personal Data Protection Act (DPDP Act, 2023) obligations in any way that costs money pre-incorporation, since there is no data fiduciary relationship being created (no data is collected, stored centrally, or transmitted to the team) — but this is this report's inference from publicly known DPDP Act structure, not a verified legal opinion specific to Zone's packet contract, and was not independently re-researched against the Act's current 2026 rules/text in this pass.

### Gaps
- No DPDP Act-specific research was done in this pass (out of explicit scope — the task framed this as "likely zero" and asked for confirmation of the Bluetooth/WPC angle specifically); if the team wants certainty here before any public pilot with real bystanders' devices in range, a short paid consultation with a tech lawyer (typically ₹5,000–15,000 for a single consultation in India, **this exact range is an unverified estimate, not sourced in this research**) is the realistic cost to budget for at the point they move toward a public field pilot, not for the 6–12 month pre-incorporation window covered here.

### Budget line
| Item | Cost | Basis |
|---|---|---|
| Spectrum licensing (Bluetooth ISM band use) | **₹0** | Verified license-exempt |
| WPC ETA for device-lab phones | **₹0** | Already-approved commercial devices |
| WPC ETA for nRF52 prototype boards (internal use only, not sold) | **₹0** (relies on 2019 test/prototype exemption — unconfirmed for this specific case) | Estimate/inference |
| DPDP/data-handling review | **₹0** for now | Inference — app has no backend/off-device data per CLAUDE.md |
| Contingency: one legal consultation before any public pilot with bystanders | ₹5,000–15,000 (**not included in the 6–12 month total below** — only trigger this if a public pilot is planned) | Estimate |

---

## Exchange rate used

- USD/INR hovered around **₹94.8–96.0 per $1** across multiple late-August to mid-September 2026 quotes (PTI-reported rupee close ₹95.46 on Aug 25, 2026; Dollarrupee ₹94.84 on Sep 8, 2026; Longforecast ₹95.95 on Sep 16, 2026); October 2026 forecasts cluster around ₹93.8–95.9. — [HDFC Sky/PTI](https://hdfcsky.com/news/rupee-rises-24-paise-to-close-at-95-46-against-us-dollar), [dollarrupee.in](https://dollarrupee.in/), [longforecast.com](https://longforecast.com/usd-to-inr-forecast-2017-2018-2019-2020-2021-indian-rupee)
- This report uses **₹95/USD** as a round working rate. **This is an approximation** — no live October 23, 2026 rate was found; get the day-of rate from the bank/card used for the actual Play Console payment.

---

## Total 6–12 month budget summary (INR)

| Category | Low estimate | High estimate | Confidence |
|---|---|---|---|
| 1. Device lab (4 phones + contingency) | ₹75,000 | ₹95,000 | Estimate — model choice pending runtime verification |
| 2. Google Play Console fee | ₹2,375 | ₹2,375 | **Verified** |
| 3. Barometer hardware contingency (beyond device lab) | ₹0 | ₹20,000 | Estimate — only if first unit fails |
| 4. nRF52 beacon prototyping (2–3 boards + batteries/enclosures) | ₹16,000 | ₹21,000 | Mixed — board prices verified, battery/enclosure estimated |
| 5. Field-pilot logistics (3 day trips) | ₹9,500 | ₹14,000 | Estimate — no official local tariff found |
| 6. Pre-incorporation compliance/legal | ₹0 | ₹0 | Inference, not legal advice — budget ₹5,000–15,000 only if a public pilot is planned |
| **Total (excluding contingent items)** | **≈₹1,02,875** | **≈₹1,52,375** | |

**Realistic working number for a 6–12 month ask: ₹1.0–1.5 lakh (~US$1,050–1,600 at ₹95/USD).** The widest swing in this range is the device lab (§1), because the exact models cannot be chosen from spec sheets alone and must be confirmed by in-hand testing — budget the higher end if the team wants margin for a failed purchase or two.

---

## Key unresolved items for the team to verify directly (not found in desk research)

1. **No current, confirmed list of mid-range Android models with real (not just advertised) Coded PHY support exists publicly as of this research.** The team's H0 capability-probe step (already first in the build order per CLAUDE.md) is the actual source of truth here — treat any phone purchase as provisional until probed.
2. **No single ₹15–30k phone was confirmed to have both Coded PHY and a barometer.** Budget for buying the barometer-capable phone (likely Samsung A5x-class) and the Coded-PHY-capable phone (likely Snapdragon-chipset mid-ranger) as two separate purchases if one unit can't do both.
3. **Gangtok-to-Rangpo-area specific local fares were not found** — get a direct quote from a Gangtok taxi stand or SNT counter before finalizing the field-logistics line.
4. **Customs duty/IGST rate on importing nRF52 DK boards from Mouser/DigiKey/Future Electronics was not verified** — sourcing from an Indian domestic seller (IndiaMART/Amazon.in, both found and priced above) avoids this uncertainty entirely and is the recommended path.
5. **This report's reading of WPC ETA exemption and DPDP Act non-applicability is a reasoned inference from public regulatory text, not a legal opinion.** It should hold for the current "internal prototyping, no backend, no sale of hardware" stage, but get it checked by an actual lawyer before any public field pilot involving bystanders' phones, or before manufacturing/selling beacon hardware.

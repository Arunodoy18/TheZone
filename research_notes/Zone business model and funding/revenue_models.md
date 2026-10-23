# Revenue & Sustainability Models for Comparable Disaster-Tech / Public-Safety-Tech Products

## What revenue/funding models do real disaster-tech / public-safety-tech products use (B2G licensing, nonprofit/grants, freemium SaaS, hardware sales, CSR, NGO licensing)?

### Takeaway
Real precedent clusters into three distinct patterns depending on company type: (1) hardware vendors (goTenna) sell physical mesh radios and lean heavily on government/military contracts once consumer revenue proves insufficient; (2) consumer-mesh-messaging apps (Bridgefy) that started free-to-consumer ended up pivoting to B2B/B2G SaaS licensing of their SDK because a free consumer app alone could not sustain the company; (3) incumbent public-safety SaaS (Everbridge) runs a mature per-seat/per-feature subscription model sold directly to government and enterprise, billed annually, multi-year contracts, which is the closest model to a sustainable "mass notification" business. There is no clean example of a pure grant-sustained nonprofit disaster-messaging app surviving long-term on grants alone without some earned revenue or acquisition (Briar is the closest, and it runs on a patchwork of short-term grants, not big durable funding).

### Cited Findings
- goTenna's business model is hardware sales (consumer + enterprise + government), with free accompanying software — [Dealroom](https://dealroom.co/companies/gotenna/)
- goTenna Pro's customers include all branches of the U.S. military and most agencies within the U.S. Dept. of Homeland Security, plus local/state organizations, per the company's 2023 financing announcement — [EMS1](https://www.ems1.com/ems-products/communications/press-releases/gotenna-closes-24m-financing-led-by-founders-fund-OwnZn12atkZObh8z/)
- goTenna held a $15M Air Force STRATFI contract for mesh capabilities and a $99M IDIQ with US Customs & Border Protection for mesh networking for Border Patrol and other CBP units — [Tectonic Defense](https://www.tectonicdefense.com/exclusive-forterra-acquires-gotenna/)
- goTenna was also reported to have a 90-day, $74,691 SBIR (Small Business Innovation Research) contract — [Tectonic Defense](https://www.tectonicdefense.com/exclusive-forterra-acquires-gotenna/)
- Third-party estimate (Similarweb, not company-reported) puts goTenna's annual revenue at $50M–$75M — [Similarweb](https://www.similarweb.com/company/gotenna.com)
- goTenna was ultimately acquired by Forterra (defense/autonomy company), which retains its DHS, CBP, and Air Force contracts — [Tectonic Defense](https://www.tectonicdefense.com/exclusive-forterra-acquires-gotenna/); acquisition dated Oct 9, 2025 per CB Insights (cited secondhand in search result, not independently verified)
- Bridgefy pivoted from a free consumer offline-messaging app to a B2B model: licensing its patented SDK to enterprise clients on a recurring (SaaS-like) basis, while keeping the consumer app free — [Contxto](https://www.contxto.com/en/mexico/bridgefy-la-startup-mexicana-que-busca-conectarnos-sin-internet/), [Growjo](https://growjo.com/company/Bridgefy)
- Bridgefy's CEO stated in a 2024 podcast interview the company had a contract with one of the 15 largest cities in the world worth $6,000,000 over four years, plus paid pilots with Ford and the United Nations — [The Pitch](https://www.thepitch.show/144-bridgefy)
- Same interview: Bridgefy had generated $300K in revenue since launch at that point — [The Pitch](https://www.thepitch.show/144-bridgefy)
- Third-party revenue estimates for Bridgefy vary widely and are unreliable: ~$2.3M/year per one source, ~$500K TTM per another — [Growjo](https://growjo.com/company/Bridgefy); figures conflict and are not audited
- Bridgefy total funding reported as $1.1M across 2 rounds by one database, but $4.5M by another — sources conflict, neither independently verified
- Everbridge (publicly traded, NASDAQ: EVBG) is a SaaS "critical event management" company whose platform helps organizations respond to public safety threats (active shooters, severe weather) and business disruptions (IT outages) — [Everbridge 10-K FY2018, SEC EDGAR](https://www.sec.gov/Archives/edgar/data/1437352/000156459019005821/evbg-10k_20181231.htm)
- Everbridge pricing scales with number of subscribed applications, number of people/locations/devices connected, and message volume; premium data feeds (social media, threat intel, weather) are sold as add-ons — [Everbridge 10-K FY2018](https://www.sec.gov/Archives/edgar/data/1437352/000156459019005821/evbg-10k_20181231.htm)
- Everbridge contracts run 1–3 years (avg ~2 years), billed annually in advance; Mass Notification was >74% of revenue across the prior three fiscal years as of the FY2018 filing — [Everbridge 10-K FY2018](https://www.sec.gov/Archives/edgar/data/1437352/000156459019005821/evbg-10k_20181231.htm)
- Everbridge revenue grew from $76.8M (2016) to $104.4M (2017) to $147.1M (2018), though the company posted net losses each year — [Everbridge 10-K FY2018](https://www.sec.gov/Archives/edgar/data/1437352/000156459019005821/evbg-10k_20181231.htm)
- Over 90% of Everbridge's recognized revenue in the eight quarters before early 2018 came from prior contracts/renewals, showing high retention in the B2G/enterprise SaaS model — [Everbridge 10-Q, SEC EDGAR](https://www.sec.gov/Archives/edgar/data/0001437352/000156459018013027/evbg-10q_20180331.htm)
- Everbridge is listed as a vendor for Kerala's state-wide early-warning dissemination system and last-mile connectivity, and is also referenced for Odisha and Andhra Pradesh early-warning deployments — [Everbridge India case study PDF](https://www.everbridge.com/wp-content/uploads/2023/05/PS-INTL-CS-EN-Crisis-Management-References-India.pdf)
- Zello's business model is categorized as B2B with a mix of subscription SaaS, freemium, and government contracts — [Caplight](https://www.caplight.com/company/zello)
- Zello gates advanced features (dispatcher mode, multi-channel setups) behind a premium subscription, per a secondary/unverified forum citation of 404 Media — [Lemmy/404 Media secondary citation](https://lemmy.manganiello.tech/post/279212/243617) — **flagged as low-confidence, unverified primary source**
- Zello ran a "Zello for First Responders" program making its professional tier free for US emergency responders during COVID-19 (2020) — [Businesswire](https://www.businesswire.com/news/home/20200324005816/pt)
- FireChat (Open Garden) was a free mesh-messaging app; no source found describing any monetization strategy, government contract, or revenue model for FireChat or its parent Open Garden — gap, see below

### Inferences
- The only model among these peers that has produced durable, disclosed, growing revenue over multiple years is Everbridge's direct B2G/enterprise SaaS subscription with annual-in-advance multi-year contracts — this is the most credible template for Zone's own eventual B2G pitch (per-agency/per-district licensing, tiered by device count or coverage area), rather than consumer freemium.
- goTenna's trajectory (consumer hardware → mostly sustained by government/defense contracts) suggests that for infrastructure-dependent disaster tech, government procurement is often where the real revenue ends up concentrating, even if the initial market story is consumer-facing — relevant since Zone's own most credible buyer is plausibly SDMA/NDMA/district administration rather than individual citizens paying for an app.
- Bridgefy's pivot from consumer free app to B2B SDK licensing (with city governments and UN as actual paying "pilot" customers) is the single closest precedent to what Zone could become: an offline-mesh protocol/SDK licensed into other apps, government platforms, or NGO toolkits, rather than a consumer app monetized directly.

### Gaps
- No confirmed, audited, company-reported revenue figures exist publicly for goTenna, Bridgefy, or Zello — all figures above are third-party estimates, press statements, or CEO interview claims, explicitly flagged in each case.
- Could not find a case of a nonprofit/grant-sustained mesh-messaging app (Briar is covered separately below) surviving on grants alone for 5+ years without pivoting to earned revenue, hardware sales, or acquisition — this suggests pure-grant-sustained mesh messaging is not an empirically validated long-term model.
- No data found on FireChat/Open Garden's monetization or shutdown economics — the app is understood to have been discontinued, but no financial detail was found.

---

## How do mesh-messaging apps (Bridgefy, Briar, goTenna, FireChat/Zello) actually make money, if at all?

### Takeaway
Of the four, only goTenna (hardware sales + government contracts) and Bridgefy (B2B SDK licensing, with real but modest disclosed contract wins) have anything resembling a sustainable commercial model; Briar runs purely on a patchwork of short-term grants/donations with no earned revenue; FireChat had no identifiable monetization and is effectively defunct; Zello runs freemium/subscription plus direct government contracts.

### Cited Findings
- goTenna: hardware sales (devices), free software, government/enterprise contracts are the major revenue driver; see contract figures above — [Dealroom](https://dealroom.co/companies/gotenna/), [Tectonic Defense](https://www.tectonicdefense.com/exclusive-forterra-acquires-gotenna/)
- Bridgefy: free consumer app + B2B SDK licensing to enterprises/governments on a recurring basis; disclosed a $6M/4-year city contract and paid pilots with Ford and the UN — [The Pitch podcast](https://www.thepitch.show/144-bridgefy), [Contxto](https://www.contxto.com/en/mexico/bridgefy-la-startup-mexicana-que-busca-conectarnos-sin-internet/)
- Briar's public funders list (per its own website) includes Small Media, the Open Internet Tools Project, Access, the Open Technology Fund, the Prototype Fund, and Internews; an older snapshot of the same page also listed the NLnet Foundation — i.e., it is funded by a rotating set of digital-rights/open-internet grant programs, not by earned revenue — [Briar funding page](https://code.briarproject.org/briar/website/-/raw/c16aaf57ec1141d1dc5a6f8f3daa510b847664f0/layouts/shortcodes/funding.md)
- A ProPublica nonprofit filing for an entity called "Briar Foundation Tr" shows 2024 revenue ~$471K, expenses ~$696K, total assets ~$10.3M — but this could not be confirmed as the entity behind the Briar messenger app, so treat as unverified — [ProPublica Nonprofit Explorer](https://projects.propublica.org/nonprofits/organizations/356829452)
- Zello: freemium + subscription (premium features) + direct government contracts; ran a free-tier giveaway to US first responders during COVID — [Caplight](https://www.caplight.com/company/zello), [Businesswire](https://www.businesswire.com/news/home/20200324005816/pt)
- FireChat: free app, mesh networking over Bluetooth/Wi-Fi, notably used during protests/outages in Hong Kong, Iraq, Iran; no monetization model found in any source searched — [CityAM](https://cityam.com/?p=577283), [Global News](https://globalnews.ca/news/1248520/firechat-app-allows-users-to-have-off-the-grid-conversations/)

### Inferences
- The two mesh apps with no disclosed revenue model (Briar, FireChat) are also the two that remain smallest/most precarious or defunct; the two with disclosed revenue (goTenna, Bridgefy) both required pivoting toward institutional/government buyers rather than staying consumer-only. This is a strong signal that consumer-only free mesh messaging does not sustain a company, and the realistic paths are hardware sales + government contracts, or SDK/platform licensing to institutions.

### Gaps
- No information found on FireChat/Open Garden's shutdown, acquisition, or final financial state.
- No audited financials found for any of the four companies; all figures are self-reported in press/podcasts or third-party estimates.

---

## Are there real examples of a government disaster-management agency paying for or licensing third-party early-warning/citizen-alert technology — especially in India?

### Takeaway
Yes — this is the best-evidenced category in the research. Indian state disaster management authorities have real, named vendor contracts (Everbridge for Kerala's dissemination system; Previstar CPS for Gujarat's decision support; RIMES as system integrator for Odisha under a World Bank-supported program), and separately, India's National Disaster Response Force procures disaster-related training/services via the government e-marketplace (GeM). However, no concrete example was found of an Indian SDMA/NDMA contract going specifically to a pre-incubation-stage or early startup (as opposed to an established vendor like Everbridge, Previstar, or RIMES) — a real gap for Zone's own positioning.

### Cited Findings
- Everbridge's own case-study material states it provides Kerala's state-wide early-warning dissemination system and last-mile connectivity, and references additional deployments in Odisha and Andhra Pradesh — [Everbridge India Crisis Management References PDF](https://www.everbridge.com/wp-content/uploads/2023/05/PS-INTL-CS-EN-Crisis-Management-References-India.pdf) — **note: this is vendor-published marketing material, not independently confirmed by a government source; treat as a claim by the vendor**
- Previstar CPS (US decision-support vendor) has reportedly been selected by GSDMA (Gujarat State Disaster Management Authority) for decision support and preparedness — found via secondary news aggregation, not a primary government announcement
- Odisha State Disaster Management Authority (OSDMA) signed an MoU with Amrita Vishwa Vidyapeetham (academic institution) for a landslide early warning system — [Careers360](https://news.careers360.com/odisha-signs-mou-amrita-vishwa-vidyapeetham-for-developing-landslide-early-warning-system)
- OSDMA also signed an MoU with RIMES (Regional Integrated Multi-Hazard Early Warning System, a Bangkok-based intergovernmental body) as knowledge partner and system integrator for a World Bank-supported program — [GKToday](https://www.gktoday.in/current-affairs/odisha-government-collaborate-rimes-disaster-management)
- Kerala's KaWaCHaM early-warning system was developed by Kerala State Disaster Management Authority with funding support from NDMA, the Government of India, and the World Bank, as part of the National Cyclone Risk Mitigation Project — [Utkarsh Classes current affairs](https://utkarsh.com/current-affairs/state/government-schemes/kerala-cm-vijayan-launches-disaster-risk-warning-system-kawacham)
- Himachal Pradesh has budgeted for 48 automatic weather stations for early warning and continuous monitoring via a dedicated software platform, tied to strengthening the state disaster management authority and emergency operations centres — [Tribune India](https://www.tribuneindia.com/news/himachal/48-automatic-weather-stations-to-be-set-up-for-early-warning/amp)
- The National Disaster Response Force (NDRF) has posted GeM (Government e-Marketplace) bids for disaster-response training services, requiring 3 years' prior experience and minimum average turnover of ₹50 lakh — a real floor that would exclude a pre-registration-stage startup — [NDRF GeM bid PDF](https://www.ndrf.gov.in/sites/default/files/GeM%20Bid%20no%206068815%20and%20Corrigendium%20for%20Aquatic%20Disaster%20Response%20Course-ADRC.pdf)
- General Insurance Corporation of India awarded a catastrophe-modelling software tender via GeM in 2024 to Verisk Analytics India (an established international vendor, not a startup) — [GIC Re result PDF](https://www.gicre.in/images/2024/pdf/Result_-_GeM_tender_for_Global_Catastrophe_Modelling_Software.pdf)
- As of May 2025, roughly 31,000 startups were registered on GeM across all sectors (per a secondary Hindi-language source), and GeM registration is explicitly open to startups in technology, healthcare, manufacturing, and services — but no disaster-management-specific empanelment track was found — [Chronicle India secondary source]

### Inferences
- The realistic entry point for Zone into Indian government disaster-tech procurement is not a direct SDMA contract (those appear to go to established vendors — Everbridge, Previstar, RIMES — or to academic institutions via MoU) but rather GeM vendor registration as a startup, competing for smaller service/training-adjacent tenders, or acting as a sub-vendor/technology partner to an existing MoU holder (e.g., an academic institution like Amrita or IIT, which the OSDMA/landslide example shows is a real channel state governments use).
- The turnover and experience floors seen in the NDRF GeM tender (3 years, ₹50 lakh turnover) illustrate a real structural barrier for a pre-incorporation hackathon team — this should be flagged honestly in Zone's business-model writeup as a multi-year runway requirement, not glossed over.

### Gaps
- Could not find a single confirmed example of an Indian government disaster-management body contracting directly with an early-stage/pre-incorporation startup for early-warning or citizen-alert technology. This may mean such deals are rare, poorly publicized, or happen through intermediary bodies not surfaced in search.
- Could not independently verify Everbridge's claimed Kerala/Odisha/Andhra Pradesh deployments through a non-vendor (government) source.
- No specific contract values found for any Indian state's early-warning vendor deals (unlike goTenna's US figures).

---

## Does India's NDMA/SACHET integrate third-party technology providers — and how?

### Takeaway
SACHET (the pan-India Common Alerting Protocol / CAP platform) is built and maintained by C-DOT (Centre for Development of Telematics, a government R&D body under the Department of Telecommunications) under NDMA's aegis — it is a government-built and government-owned platform, not one that empanels private third-party technology vendors in a visible, documented way. Third parties appear to interface with it only as "alert-generating agencies" (IMD, CWC, INCOIS, GSI) feeding data in, not as technology vendors building or licensing pieces of the platform.

### Cited Findings
- C-DOT has been entrusted as the technology partner under the supervision of the Department of Telecommunications (DoT) "to Design and Develop a platform for Implementation of CAP System at Pan India level by integrating various Alert Generating, Authorizing and Dissemination agencies" — [C-DOT CAP brochure PDF](https://cdot.in/cdotweb/assets/docs/products/dms/cap.pdf)
- A 2025 NIDM (National Institute of Disaster Management) training workshop report states C-DOT designs, develops, and maintains SACHET under the aegis of NDMA — [NIDM workshop report PDF](https://nidm.gov.in/PDF/TrgReports/2025/February/Report_07February2025pk.pdf)
- SACHET integrates alert-generating agencies such as IMD (India Meteorological Department), CWC (Central Water Commission), INCOIS (Indian National Centre for Ocean Information Services), and GSI (Geological Survey of India), per a secondary exam-prep summary — [Shankar IAS Parliament summary](https://www.shankariasparliament.com/current-affairs/gs-iii/prelim-bits-5/integrated-alert-system-sachet)
- Dissemination channels include SMS, Cell Broadcast, Mobile App, TV, Radio, Social Media, RSS Feed, Browser Notification, and Satellite, per the C-DOT brochure — [C-DOT CAP brochure PDF](https://cdot.in/cdotweb/assets/docs/products/dms/cap.pdf)
- The public-facing app and web portal are available at sachet.ndma.gov.in and on Apple/Google app stores — [NIDM workshop report](https://nidm.gov.in/PDF/TrgReports/2025/February/Report_07February2025pk.pdf)

### Inferences
- SACHET's architecture is a government-to-government (G2G) model: one government R&D body (C-DOT) builds the core platform, and other government science/monitoring agencies feed hazard data into it. There is no documented procurement or empanelment track for private-sector or startup technology providers to plug directly into SACHET's core infrastructure.
- A realistic path for Zone would not be "integrate into SACHET" but rather position itself as a complementary last-mile/offline layer that operates where SACHET's SMS/cell-broadcast/app channels fail (i.e., no connectivity) — this is a differentiation point, not a competition point, and is consistent with Zone's actual technical niche (offline BLE mesh vs. SACHET's connectivity-dependent broadcast channels).

### Gaps
- No documentation found of any formal process (RFP, empanelment, API partnership) by which a private company or startup could integrate its technology into SACHET. This may not exist, or may not be publicly documented — worth a direct inquiry to NDMA/C-DOT if pursued further.
- No information found on whether SACHET has any "last-mile" offline/disconnected fallback mechanism at all, which would be directly relevant to how Zone could pitch itself as complementary rather than competitive.

---

## Does Indian CSR funding (Companies Act mandatory CSR) actually go to disaster-tech or public-safety startups? What does that process look like?

### Takeaway
CSR law permits disaster-relief spending and explicitly allows CSR funds to flow to startups, but only through a specific, narrow channel: government-recognized incubators or incubators housed in academic institutions approved by the Central Government — not direct company-to-startup grants or direct company-to-startup equity/procurement deals. No concrete case was found of CSR money reaching a disaster-tech startup specifically (as opposed to CSR funding established NGOs/corporate foundations running their own disaster programs).

### Cited Findings
- Section 135(1) of the Companies Act, 2013 requires CSR spending from companies meeting a threshold of ₹500 crore net worth, OR ₹1,000 crore turnover, OR ₹5 crore net profit — [Invest India blog](https://www.investindia.gov.in/team-india-blogs/mca-allows-COVID-19-under-csr-expenditure-can-will-startup-get-money)
- Disaster relief is a category under Schedule VII of the Companies Act and companies can direct CSR spend toward it if their CSR policy already includes it, or they amend their CSR policy to add it — [Invest India blog, 2020 analysis](https://www.investindia.gov.in/team-india-blogs/mca-allows-COVID-19-under-csr-expenditure-can-will-startup-get-money)
- CSR funds can flow to startups, but only via "any incubator that is supported by the state or central government/agencies," and a later amendment also permits technology incubators located within academic institutions approved by the Central Government — [Invest India blog](https://www.investindia.gov.in/team-india-blogs/mca-allows-COVID-19-under-csr-expenditure-can-will-startup-get-money)
- In-kind donations generally do not count toward CSR spend obligations — the rules require money to be spent, so a company cannot count a product donation at notional value — [Invest India blog]
- Up to 5% of CSR funds can go toward internal administrative/capacity costs — [Invest India blog]
- Reliance Foundation runs its own large-scale disaster management programme (risk reduction, response, community capacity building using ICT/GIS, ISO 9001:2015-certified response process) — but this is a corporate foundation's own operational program, not a grant/contract to an external startup — [Reliance Foundation Annual Report 2022-23](https://reliancefoundation.org/Annual-Report/AnnualReport-2022-2023/v3/files/basic-html/page85.html)
- Reliance Foundation and UN India convened a meeting in Bhuj, Gujarat focused on early-warning systems — a convening/advocacy event, not a funding deal — [India CSR](https://indiacsr.in/?p=166767)
- A 2024 Uttar Pradesh initiative combined technology and CSR for disaster management (referenced an entity "R2V2" in a photo caption, but the source does not confirm whether this is a startup) — [India CSR] — **unconfirmed, flagged as a gap**

### Inferences
- Given the legal requirement that CSR funds routed to startups must go through a government-recognized or academic incubator, Zone's most realistic CSR pathway (if pursued) would be to seek incubation/affiliation with a recognized incubator (e.g., an IIT TBI, a state-government-supported incubator, or similar) that itself has CSR-funded programs, rather than approaching a corporate CSR office directly for a grant.
- No evidence found of direct company-to-disaster-tech-startup CSR deals in India; the two concrete models found are (a) corporate foundations running their own disaster programs in-house (Reliance Foundation) and (b) CSR funds routed through recognized incubators to startups generally (not disaster-tech specifically). This should be stated as a flagged inference, not a confirmed precedent, in Zone's writeup.

### Gaps
- No confirmed real-world example found of CSR money reaching a disaster-tech or public-safety startup specifically (named company, named funder, disclosed amount). This is a genuine evidentiary gap, not just an oversight — multiple search queries did not surface one.
- Could not confirm whether "R2V2" (Uttar Pradesh CSR/disaster-tech initiative) is a startup, nor find further detail on that program.
- Current (2024-2026) CSR rule amendments were not fully verified — sources found were from 2020 and a 2025 corporate policy document; the exact current Schedule VII wording and Companies (CSR Policy) Rules should be checked against the MCA website directly before citing authoritatively.

---

## What do humanitarian-tech funders (UNDRR, OCHA, ICRC/Red Cross innovation arms) actually fund, and under what model (grant vs. equity vs. procurement)?

### Takeaway
These are grant-based, not equity or procurement models — but the specific funds found are either old (2015-16 rounds), small (a few thousand to a few hundred thousand dollars), or not startup-specific. No joint UNDRR/OCHA/ICRC fund exists; each agency (or Red Cross national societies) runs separate, typically modest grant-making programs, and the more interesting current pattern is Red Cross national societies pursuing "innovative finance" instruments (catastrophe bonds, insurance, debt swaps) rather than classic grants.

### Cited Findings
- OCHA's Humanitarian Innovation Fund is described (via a ReliefWeb/OCHA-service page) as an independent grant-making programme open to the whole humanitarian community, established in 2011 as the first of its kind — [ReliefWeb/OCHA](https://reliefweb.int/organization/hif) — **current operator/status not confirmed, may be outdated**
- The World Bank/GFDRR and UK DFID ran a Challenge Fund (2015-16 round found) backing technology-driven disaster risk projects, with winners including companies, NGOs, and universities; grants ranged $20,000–$150,000 — [GFDRR Challenge Fund winners 2015](https://www.gfdrr.org/en/winners-2015-challenge-fund) — **dated, may no longer be open**
- GSMA ran a fund for humanitarian crises offering grants up to £250,000 for mobile-based projects, funded by the UK's Foreign, Commonwealth & Development Office — [Mobile World Live](https://www.mobileworldlive.com/gsma/gsma-launches-scheme-for-humanitarian-crises/) — date of this program not confirmed in the source
- An OCHA Humanitarian Research and Innovation Grant caps out at a maximum of US$4,000, and is targeted at research/writing rather than technology development — found via search snippet, specific source page not separately captured
- 510 (Netherlands Red Cross data initiative, founded 2016) aims to improve speed, quality, and cost-effectiveness of humanitarian aid via data/digital products, and works with 36 Red Cross national societies, including a typhoon-impact forecasting model for the Philippines — [ORTEC](https://ortec.com/en/insights/ortec-and-510)
- No source found describing 510's own funding/revenue model (i.e., whether it runs on Red Cross core funding, restricted grants, or earned fees from national societies) — explicit gap
- Danish Red Cross has pursued "innovative financing" since 2018, launching 15 initiatives including trigger-based insurance, a catastrophe bond, and debt swaps — a December 2024 report is the primary source — [DRC Innovative Financing Short Report PDF](https://rodekors.dk/sites/rodekors.dk/files/2024-12/DRC%20Innovative%20Financing%20Short%20Report%20-%20FINAL_0.pdf)
- American Red Cross received up to $200,000 in unrestricted funding plus $5,000 in AWS credits and implementation support (an AWS grant, i.e. a corporate tech-in-kind/cash grant, not UNDRR/OCHA/ICRC) to prototype its "Clara AI" disaster-relief platform — [Red Cross press release, 2026](https://www.redcross.org/about-us/news-and-events/press-release/2026/red-cross-receives-aws-grant-to-prototype-aid-platform.html)

### Inferences
- None of the funds found (OCHA HIF, GFDRR/DFID Challenge Fund, GSMA fund) is confirmed as currently open or active as of 2025-2026 based on these search results; several appear to be from 2011-2016. This should be stated plainly as a limitation rather than presented as a currently-available funding avenue for Zone.
- The clearer 2023-2026-era pattern among established Red Cross entities (Danish Red Cross, American Red Cross) is a shift toward either (a) innovative finance instruments issued by the Red Cross itself (bonds, insurance), which is not applicable to an outside startup seeking funding, or (b) ad hoc corporate tech grants (e.g., AWS), which is a plausible model Zone could pursue (cloud/tech-company innovation grants) even though it's not a "disaster-tech fund" in the dedicated sense.

### Gaps
- Could not confirm current (2023-2026) status, application process, or typical award size for OCHA's Humanitarian Innovation Fund.
- Could not find a UNDRR-specific grant or procurement program for startups at all — UNDRR appears to function more as a policy/coordination body (hosting the Sendai Framework, GAR reports) than as a direct funder of technology solutions; this absence itself is a finding.
- Could not find ICRC-specific (as distinct from national Red Cross societies) grant-making for outside tech startups — the ICRC Innovation program's actual funding mechanism (if any) was not surfaced in these searches; a `100open.com` reference to "ICRC" appeared in results but was not fetched/verified for detail.
- No information found on 510's actual funding source — grant vs. core Red Cross budget vs. fee-for-service to national societies.

---

## Overall cross-cutting notes for the report writer

- **Strongest, most verifiable precedent for Zone's own narrative:** Everbridge's direct B2G SaaS subscription model (public company, SEC-filed financials, named India state deployments) is the best-documented, most credible comparable for "how a disaster-communication product actually gets paid for by government" — but Everbridge is a mature public company, and the gap between "hackathon pre-incubation team" and "Everbridge" should be acknowledged honestly rather than glossed over.
- **Strongest comparable for Zone's own current stage:** Bridgefy's trajectory (consumer mesh app → B2B SDK licensing with named city/UN/Ford pilots, disclosed in a podcast rather than audited filings) is the closest stage-appropriate precedent — a small team, mesh networking core tech, pivoting toward institutional licensing once consumer monetization proved weak.
- **Clear negative/cautionary precedent:** Briar (grant-only, no earned revenue, smallest footprint) and FireChat (free, no monetization, apparently defunct) show that pure free/grant-sustained mesh messaging without an institutional or hardware revenue line has not produced a durable business among the examples researched.
- **CSR and UNDRR/OCHA/ICRC funding for disaster-tech startups specifically is a genuine evidentiary gap** — multiple targeted searches did not turn up a concrete, named instance of either CSR money or UN/Red Cross humanitarian-innovation funding reaching a disaster-tech startup by name with a disclosed amount. The report should flag this rather than assert these are reliable funding channels.
- **SACHET/NDMA integration for third-party vendors is not documented as an open channel** — it is a government-built, government-run platform (C-DOT under NDMA/DoT), and there is no visible empanelment process for outside tech providers. Zone's positioning should be "complementary last-mile layer" rather than "integration partner," since no evidence supports the latter being achievable.

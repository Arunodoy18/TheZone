# Competitor and Market Landscape — Offline Mesh Communication & Disaster-Response Tech (2026)

## Does Bridgefy have any concept of expected-next-message, liveness, or silence detection?

### Takeaway
Bridgefy is a Bluetooth/Wi-Fi mesh messenger with no server, built for peer-to-peer and relayed text/location messages up to ~100m per hop. Independent cryptographic audits (2020, 2022) found it has no delivery confirmation mechanism, no cryptographic authentication at the protocol layer it originally used, and no concept of a device's expected transmission schedule — it is a best-effort store-and-forward chat/broadcast layer, not a telemetry/liveness system.

### Cited Findings
- Bridgefy connects phones directly over Bluetooth and Wi-Fi with no internet server; developer docs describe up to ~100m (330 ft) range per hop, with messages relaying through intermediate phones to extend reach — [Bridgefy Mesh Network Basics](https://bridgefy.notion.site/Bridgefy-Mesh-Network-Basics-002ecd4c7bb64986bf00beb6d43d3036)
- For private messages, intermediate relaying devices do not have access to the message content; broadcast messages go to everyone nearby — [Bridgefy docs via search summary](https://docs.bridgefy.me/sdk/android)
- Initial setup requires an internet connection to sync contacts before offline mesh messaging becomes available — [Alchemist Accelerator profile](https://www.alchemistaccelerator.com/blog/bridgefy-the-offline-messaging-app-revolutionizing-crisis-communication-worldwide)
- As of a 2025 profile, Bridgefy has over 12 million downloads — [Alchemist Accelerator](https://www.alchemistaccelerator.com/blog/bridgefy-the-offline-messaging-app-revolutionizing-crisis-communication-worldwide)
- During the 2019 Hong Kong protests, Bridgefy reported over 75,000 downloads in a single week from Hong Kong alone, and the app jumped from #973 to #6 on the Apple App Store (and #2 on Google Play) within days; usage reportedly grew 3,000–4,000% over two months — [Bridgefy blog](https://bridgefy.me/blog/hong-kong-citizens-adopt-bridgefy-amid-intense-protests/); [Silicon Republic](https://siliconrepublic.com/?p=837857); [soyacincau](https://www.soyacincau.com/2019/09/06/hong-kong-protesters-switching-from-wechat-bridgefy-firechat)
- Protesters used Bridgefy specifically to evade reliance on internet infrastructure that could be shut down or monitored by authorities — [Boing Boing](https://boingboing.net/2019/09/03/hong-kong-protestors-using-mes.html)
- Bridgefy was also widely downloaded (1M+ installs in 48 hours) during the 2021 Myanmar coup as citizens anticipated internet shutdowns — [MacVenture Capital](https://macventurecapital.com/?p=8849)
- A 2020 Royal Holloway cryptographic analysis ("Breaking Bridgefy") found the app allowed users to be tracked, offered no message authenticity, no effective confidentiality, and no resilience against adversarially crafted messages; a userID was transmitted in plaintext, and a single crafted message ("zip bomb") could disable the entire mesh network for a user — [USENIX/IACR summary via search](https://ia.cr/2021/214); [Royal Holloway news](https://venue.royalholloway.ac.uk/about-us/news/royal-holloway-cryptographers-urge-caution-over-use-of-secure-messaging-app)
- After Bridgefy adopted Signal's libsignal protocol to fix these issues, a 2022 USENIX Security follow-up paper ("Breaking Bridgefy, again: Adopting libsignal is not enough") found the revised app *still* failed to meet its own security claims — [USENIX Security 2022](https://www.usenix.org/conference/usenixsecurity22/presentation/albrecht)
- One source mentions a "live location" sharing feature between two Bridgefy users, but this is continuous location sharing while both are connected, not a scheduled-heartbeat/liveness protocol — [search summary, unverified against current app]

### Inferences
- Bridgefy is architecturally a chat/messaging layer (store-and-forward text, broadcast, location-share), not a telemetry protocol. It has no field or mechanism analogous to Zone's `next_expected_tx`: nothing in its packet format declares when a node will next transmit, so there is no way for a receiving device to distinguish "this peer scheduled a long gap" from "this peer vanished unexpectedly."
- Its documented security weaknesses (no delivery confirmation, spoofable plaintext IDs, a disabling zip-bomb attack) mean Bridgefy cannot even reliably confirm a message was *received*, let alone infer anything about the sender's physical status from silence. Any "silence" in Bridgefy is noise, not signal — exactly the ambiguity Zone's protocol is built to resolve.
- Bridgefy's dependence on an initial internet connection to sync contacts is a meaningful operational difference from Zone, which (per the Zone PRD) is designed to work from first launch in a pure offline/airplane-mode context.

### Gaps
- Could not confirm Bridgefy's exact current (2026) feature set from primary/official sources — official docs.bridgefy.me content wasn't directly fetchable in search snippets; findings rely on secondary summaries and may be stale (per the 2022 and earlier audits).
- No data found on whether a 2023–2026 version of Bridgefy added any device-status or "last seen" indicator.

---

## How does Briar's trust/delivery model differ from Zone's broadcast approach?

### Takeaway
Briar is an end-to-end encrypted, contact-based messaging app that routes over Tor by default and falls back to Bluetooth/Wi-Fi/USB only when internet is unavailable; it requires mutual, in-person (or pre-authenticated) contact verification before any message can be exchanged, and — unlike Zone — both parties generally need to be online/reachable simultaneously for delivery over its primary (Tor) transport. It has no scheduled-liveness or expected-silence concept; it is a 1:1/group trust-based messenger, not a broadcast telemetry system.

### Cited Findings
- Adding a Briar contact requires exchanging a link in person, typically via QR code, or through another already-trusted channel — this friction is deliberate, blocking unsolicited contact requests and keeping key exchange off any server — [Briar overview via search](https://en.wikipedia.org/wiki/Briar_(software))
- All Briar messages are end-to-end encrypted using the custom Bramble Transport Protocol, which provides forward secrecy — [search summary]
- Briar has no central server; by default it routes traffic over Tor hidden services (one per user), and falls back to direct Bluetooth, Wi-Fi, or even manual transfer via USB stick when the internet is unavailable — [search summary / Wikipedia]
- Because there is no server to hold undelivered messages, both parties must be reachable for delivery over Tor; Briar's optional "Briar Mailbox" (software run on a spare Android device the user controls) exists specifically to work around this store-and-forward gap — [search summary]
- A Cure53 security audit (dated to Briar's 2017 public beta) found the cryptography code "exceptionally clear and sound, with no vulnerabilities spotted" — [search summary, note: audit predates many later versions]

### Inferences
- Briar's trust model is the opposite of Zone's: Briar requires manual, pairwise, in-person-verified trust relationships before any data moves, which does not scale to an anonymous crowd of strangers relaying a disaster packet hand-to-hand. Zone's packet is explicitly designed to be relayed by any nearby stranger's phone without any pairing or identity verification step — which is what makes "hand it to a stranger in the street" viable during a disaster.
- Briar's offline fallback (Bluetooth/Wi-Fi local mode) is positioned as a secondary path for when its primary Tor transport is unavailable, whereas for Zone the offline/Bluetooth path is the only path, by design — there is no "internet mode" to fall back from.
- Neither Briar's online (Tor) nor offline (local) delivery model includes any "expected next transmission" declaration. Briar's unit of failure is "message didn't deliver because the recipient wasn't reachable" — it has no mechanism to distinguish a recipient who is offline-by-schedule from one who is incapacitated, because Briar isn't tracking recurring liveness heartbeats at all, only ad hoc message delivery.

### Gaps
- Could not fetch Briar's official project documentation (briarproject.org) directly; most of the findings rely on a Wikipedia-level secondary summary. Primary-source confirmation of exact protocol behavior (e.g., current Bramble Transport Protocol spec) was not independently verified.
- No information found on Briar's usage/adoption statistics (user counts, country-specific deployments) comparable to Bridgefy's Hong Kong/Myanmar figures.

---

## What exactly does goTenna do — hardware dependency, range, and buyer base?

### Takeaway
goTenna is fundamentally hardware-dependent: both the consumer "goTenna Mesh" and the military/public-safety-grade "goTenna Pro / Pro X" are physical LoRa/VHF/UHF radio devices that pair to a phone over Bluetooth (the phone itself has no long-range radio), and the company has shifted its business from consumer products toward government, military, and public-safety customers.

### Cited Findings
- The goTenna Pro line added a software-defined, tunable VHF/UHF radio (142–175 MHz and 445–480 MHz), 5W variable output power, and MIL-SPEC/IP68 ruggedization — [Armada International](https://www.armadainternational.com/2019/09/this-small-mesh-networking-device-from-gotenna-could-unblock-signal-denied-warfare-environments/)
- The Pro X unit weighs 79 grams and pairs to a phone via USB or Bluetooth; it is fielded by special and conventional U.S. military forces, with smaller deployments in other countries; pricing starts at $849 per unit, with a Deployment Kit that can store/charge/transport up to 30 units — [Armada International](https://www.armadainternational.com/2019/09/this-small-mesh-networking-device-from-gotenna-could-unblock-signal-denied-warfare-environments/)
- Nominal point-to-point network range is reported at approximately 4 km (terrain-dependent); the original consumer goTenna reached "a few miles" under favorable conditions — [Armada International](https://www.armadainternational.com/2019/09/this-small-mesh-networking-device-from-gotenna-could-unblock-signal-denied-warfare-environments/)
- goTenna devices support position data, text messaging, and map markings, integrating with ATAK (the military's Android Team Awareness Kit) and related tactical mapping apps — [Armada International](https://www.armadainternational.com/2019/09/this-small-mesh-networking-device-from-gotenna-could-unblock-signal-denied-warfare-environments/)
- goTenna raised a $24M Series C round in 2019 explicitly to expand public-sector (government/first-responder) mesh networking, marking a strategic pivot from the earlier consumer "goTenna Mesh" product line toward "tactical-grade" devices — [TechCrunch](https://techcrunch.com/2019/06/18/gotenna-is-ramping-up-public-sector-mesh-networking-with-a-24m-c-round); [Forterra/The Last Mile](https://thelastmile.forterra.com/gotennas-transition-from-consumer-to-tactical-grade-mesh-networking-devices/)
- goTenna Pro was pitched in 2017 specifically to deploy alongside rescue, fire, and security teams as a meshing radio — [TechCrunch, 2017](https://techcrunch.com/2017/03/27/gotenna-pro-meshing-radio-aspires-to-deploy-next-to-rescue-fire-and-security-teams/)
- After Hurricane Maria (2017), volunteers deployed goTenna Mesh devices across Puerto Rico to restore basic text communication where cellular networks were down; the "PR Reconnects" crowdfunding campaign raised roughly $15,000 to buy ~300 additional units; later use was also reported in Texas and Florida after other disasters — [TechCrunch](https://techcrunch.com/2017/11/14/a-mesh-network-spontaneously-erupts-in-the-us-and-helps-connect-puerto-rico/); [AVC blog](https://avc.com/2017/11/funding-friday-gotenna-puerto-rico-mesh/); [MacVoices](https://macvoices.com/?p=7792)

### Inferences
- goTenna's core limitation versus Zone is the hardware dependency: every participating device needs a separate ~$849 (Pro X) radio unit paired over Bluetooth to a phone. This caps network size to however many physical radios have been purchased and distributed — fundamentally different from Zone's "every phone already has the radio it needs (BLE), no extra hardware" model, which can scale to any bystander's existing Android phone at zero marginal hardware cost.
- goTenna's buyer base has moved decisively toward institutional buyers (military, government, public safety) rather than citizens, which cedes the "ordinary citizen's phone becomes part of the network for free" use case entirely to products like Zone and Bridgefy.
- No source found describing goTenna having an "expected next transmission" or liveness/dead-man's-switch concept — its network relies on explicit message relay (text, GPS pings, map markers) rather than a scheduled heartbeat protocol.

### Gaps
- No independent (non-marketing) field data found on actual achieved range in real disaster conditions, as opposed to nominal spec sheets.
- No confirmed current (2026) pricing, product line status, or company financial health found — the newest substantive goTenna sourcing found dates to 2019.

---

## Is FireChat still active? Does Zello work offline/mesh?

### Takeaway
FireChat is confirmed discontinued (last updated 2018, official site now 404). Zello is an internet-dependent push-to-talk app; it has no offline mesh mode — Bluetooth in Zello is used only for pairing accessory hardware (headsets/PTT buttons), not for peer-to-peer mesh relay.

### Cited Findings
- FireChat (Open Garden) is listed as discontinued; its official URL returns a 404 error, and the apps had not been updated since 2018 — [Wikipedia via search](https://en.wikipedia.org/wiki/FireChat); [AlternativeTo](https://alternativeto.net/software/firechat-by-open-garden/about)
- FireChat used wireless mesh networking (Bluetooth, Wi-Fi, or Apple Multipeer) to let smartphones pass messages peer-to-peer without internet, and — while not designed for the purpose — was used during civil protests (e.g., Hong Kong 2014) — [search summary]
- FireChat's Android and iOS versions were not mutually compatible in offline mode — a cross-platform limitation — [search summary]
- Zello requires an internet connection (Wi-Fi or mobile data) to function; Tom's Guide states this explicitly — [Tom's Guide](https://www.tomsguide.com/us/zello-app-guide,news-25805.html)
- Zello's Bluetooth support is for connecting PTT hardware accessories and headsets, not for offline peer-to-peer relay — [Common Sense Media via search](https://www.commonsensemedia.org/app-reviews/zello-walkie-talkie)
- One source explicitly warns not to treat Bridgefy's offline Bluetooth messaging as equivalent to Zello's live push-to-talk — they solve different problems — [search summary citing Tom's Guide/AirDroid]

### Inferences
- FireChat's discontinuation (circa 2018, with no company announcement found explaining why) suggests the consumer offline-mesh-chat category is difficult to sustain commercially absent a disaster/protest-driven usage spike — relevant context for Zone's own go-to-market and sustainability planning (episodic, event-driven usage vs. ongoing engagement).
- Because Zello has zero offline capability, it is not a direct competitor to Zone in disaster scenarios where cell/internet infrastructure is down — it is actually a tool that fails completely under the exact conditions Zone targets. This is a case where Zone doesn't compete with Zello but exposes where Zello cannot help.

### Gaps
- No official Open Garden/FireChat statement confirming the reason or exact date of discontinuation was found.
- Could not verify whether Zello has announced or piloted any BLE-mesh or offline mode as of 2026 beyond what general consumer tech reviews describe; a Zello-official source was not retrieved.

---

## Satellite/Starlink disaster deployments — competing or complementary?

### Takeaway
Starlink deployments after earthquakes (Colombia, Philippines/Mindanao, Venezuela — all 2026) restore wide-area internet/telecom backhaul for responders and telcos, typically within days, which is complementary to rather than competing with a phone-to-phone Bluetooth mesh like Zone: Starlink needs a dish, power, and often telco/ISP partnership to re-establish service over a region, while Zone works instantly, at zero infrastructure, in the first minutes/hours after a collapse when no dish has yet been flown in and power/connectivity is still down at the exact rubble site.

### Cited Findings
- After the August 2026 magnitude-7.4 earthquake in Colombia, Starlink made service free for affected customers through September 12, 2026, and deployed Starlink hardware directly to emergency response agencies to restore communications where traditional networks were damaged — [driveteslacanada.ca](https://driveteslacanada.ca/spacex/starlink-free-internet-colombia-earthquake/)
- After the June 2026 magnitude-7.8 earthquake in Mindanao, Philippines, telco Globe deployed Starlink (with a free 30-day offer) to support emergency communications and disaster response; PLDT and Smart also deployed Starlink broadband and direct-to-device service — [Globe newsroom](https://www.globe.com.ph/about-us/newsroom/corporate/globe-deploys-starlink-satellite-tech-in-mindanao); [Developing Telecoms](https://developingtelecoms.com/telecom-business/humanitarian-communications/20362-philippine-telcos-deploy-starlink-in-mindanao-after-deadly-7-8-quake.html)
- After the June 2026 earthquakes in Venezuela, Starlink offered free service through July 25 and said it was working to rapidly deploy additional terminals to the hardest-hit communities — [driveteslacanada.ca](https://driveteslacanada.ca/spacex/starlink-free-internet-venezuela-earthquake/)
- (Earlier reference point, outside the 2025–2026 window) After Japan's 2024 Noto Peninsula earthquake, telco KDDI provided Starlink service to support Japan's Self-Defense Forces, electricity companies, and local governments — [SDxCentral](https://www.sdxcentral.com/news/kddi-deploys-starlink-services-to-support-restoration-efforts-following-earthquake/)

### Inferences
- All documented Starlink disaster deployments operate at the regional/institutional level (telcos, government agencies, emergency responders) re-establishing bulk internet backhaul — they are not a person-to-person, no-infrastructure, immediate-response layer. Starlink terminals must be physically transported, powered, and pointed at sky; they do nothing for a person trapped under rubble in the first critical hours, nor do they carry any concept of "is this specific person still alive and transmitting on schedule."
- The natural product relationship is layered, not competitive: Zone's BLE mesh carries the person-level distress/status signal in the immediate aftermath and in collapsed buildings/dead zones; Starlink (once flown in) restores bulk connectivity for coordination, logistics, and internet access at the regional level, hours to days later. A mature disaster-response stack plausibly uses both.

### Gaps
- No evidence found of a Starlink-based system incorporating any device-level "silence as signal" or liveness-heartbeat mechanism — these are internet backhaul links, not individual distress telemetry. This remains a clean differentiation point but was not something sources directly addressed since it isn't Starlink's problem domain.

---

## Indian disaster-tech / mesh-networking startups and government projects (DRDO, IIT/IIM-incubated)

### Takeaway
Credible evidence of a funded Indian company or DRDO program building offline mesh disaster communication was not found. The most relevant Indian example found is a 2025 Smart India Hackathon student prototype (Team ARCNET) for a BLE-based disaster-resilient offline communication system — not a company, not funded, not fielded.

### Cited Findings
- Team ARCNET won first prize at Smart India Hackathon 2025 for a "Disaster-Resilient Offline Communication System using Mesh Networks," running on Bluetooth Low Energy so messages hop phone-to-phone without internet or cellular service; it also includes an admin dashboard for sending alerts/SMS fallback and a cyclone/flood risk prediction feature — [Vidya Academy news post](https://news.vidyaacademy.ac.in/?p=76352)
- IBM/The Weather Company launched "Mesh Network Alerts" in India in 2017 through The Weather Channel app, using peer-to-peer mesh connections to deliver critical weather alerts in areas with weak connectivity, citing a 2016 ITU estimate that ~75% of India's population lacked reliable internet access — [MIT Technology Review](https://www.technologyreview.com/2017/02/15/154004/new-weather-app-can-spread-urgent-alerts-even-when-cell-networks-are-down/); [VoiceNData](https://www.voicendata.com/india-receives-industrys-first-mobile-weather-alerting-technology-from-ibm-the-weather-company)
- A Bhubaneswar-based climate-tech startup, Aurassure, raised INR 4 crore in a seed round led by Unicorn India Ventures for an IoT-enabled real-time disaster data/forecasting system; it is not described as mesh-networking based — [YourStory via VC fund tracker](https://news.ventureintelligence.com/private-equity/climate-tech-startup-aurassure-attracts-rs.4-cr-led-by-unicorn-india-ventures)
- Direct searches for DRDO mesh-networking disaster projects returned no matching results — no DRDO program by that description was found in available sources.

### Inferences
- There does not appear to be a mature, funded Indian direct competitor to Zone as of this research. This is a genuine white-space finding, not merely an absence of search hits — multiple differently-worded searches (startup funding databases, hackathon news, government project searches) converged on the same conclusion.
- The Smart India Hackathon prototype (Team ARCNET) validates that the problem is recognized as worth solving within India's own student/engineering ecosystem, which is a useful signal for market timing, but it is not a competitive threat — it has no funding, team continuity, or production app associated with it in sources found.

### Gaps
- Could not confirm or rule out non-public/classified DRDO tactical mesh communications work (DRDO does extensive secure tactical radio/communications R&D generally, but no source tied any specific DRDO program to civilian disaster-response mesh networking).
- Did not find any IIT/IIM-incubated venture specifically in this space; a more targeted search of IIT incubation cell portfolios (e.g., IIT Delhi's Research Park, IIT Bombay's SINE, IIM Calcutta Innovation Park) was not performed and could surface something not found via general startup-funding search terms.

---

## What is NDMA's SACHET — SMS/app-push alert system, or does it have offline/mesh capability?

### Takeaway
SACHET is a top-down, one-to-many public alert broadcasting platform — not a mesh network — built by C-DOT for NDMA on the Common Alerting Protocol (CAP), delivering alerts via telecom-operator-dependent channels (location-based SMS and, increasingly, cell broadcast) plus app push, sirens, and broadcast media. It has no offline/device-to-device relay capability; it depends entirely on functioning mobile network infrastructure (specifically integration with Airtel, Jio, Vodafone Idea, and BSNL), making it something Zone complements rather than competes with — SACHET is government-to-citizen hazard warning before/during an event; Zone is citizen-to-citizen status telemetry when that same infrastructure has already gone down.

### Cited Findings
- SACHET was developed by the Centre for Development of Telematics (C-DOT), the Department of Telecom's premier R&D center, and is based on the Common Alerting Protocol (CAP) recommended by the ITU — [search summary, GSMA case study referenced](https://www.gsma.com/solutions-and-impact/connectivity-for-good/mobile-for-development/wp-content/uploads/2025/05/Indias-SACHET-Public-Warning-System.pdf)
- The platform is integrated with India's four largest mobile network operators (Airtel, Jio, Vodafone Idea, BSNL) to support both location-based SMS (LB-SMS, reaching all mobile users within a geographic area) and cell broadcast (CB) — [GSMA case study](https://www.gsma.com/solutions-and-impact/connectivity-for-good/mobile-for-development/wp-content/uploads/2025/05/Indias-SACHET-Public-Warning-System.pdf)
- The system includes real-time dashboards, GIS targeting, automated workflows, and multichannel dissemination across phones, sirens, broadcast media, and display boards; alerts are issued in 23 languages with accessibility features — [search summary]
- Cell broadcast capability is newer and still being operationalized: an ETV Bharat report describes a nationwide test in May 2026, with test messages possibly arriving multiple times, indicating the feature is still in rollout/testing — [ETV Bharat](https://www.etvbharat.com/en/bharat/ndma-tests-cell-broadcast-message-to-alert-citizens-on-severe-disaster-enn26050201915)
- Scale figures conflict across sources: UNDRR cites over 44 billion alerts issued across 30,000+ hazardous events, while another source (ETV Bharat) cites over 134 billion SMS sent — these numbers could not be reconciled and should be treated as approximate/unverified — [UNDRR](https://www.undrr.org/resource/case-study/mobile-technology-expanding-inclusive-early-warning-communication); [ETV Bharat]

### Inferences
- SACHET's entire delivery mechanism (SMS, cell broadcast, app push) requires the cellular network to be up. The moment cell towers go down or are overloaded — exactly the scenario in a major earthquake/building collapse — SACHET has no way to reach anyone, and no way to learn anything about the status of people in the affected area. This is the precise gap Zone is built to fill: SACHET broadcasts "a hazard is coming/occurring" from government to citizens pre-infrastructure-failure; Zone relays "I am alive / I am not responding" between citizens post-infrastructure-failure.
- A credible narrative for Zone positioning to government or NDMA-adjacent stakeholders: Zone is not a SACHET competitor, it's the system that keeps working in the exact window where SACHET's underlying telecom infrastructure has failed — i.e., complementary layers of the same disaster-communication stack (pre-event top-down warning vs. post-event bottom-up status/distress signaling).

### Gaps
- No low-level technical architecture details (message queuing, specific CAP-hub-to-agency APIs, database design) were found; the GSMA 2025 case study PDF appears to be the most detailed public source and was not directly fetched in full — only summarized via search snippet.
- Could not confirm whether SACHET or NDMA have any pilot or roadmap item for offline/mesh capability as backup to cellular failure.

---

## Does any competitor have an equivalent to Zone's expected-silence-vs-unexpected-silence distinction? Is "silence detection"/dead-man's-switch a known pattern elsewhere?

### Takeaway
No consumer or commercial disaster-communication product researched (Bridgefy, Briar, goTenna, FireChat, Zello) has anything resembling Zone's `next_expected_tx` mechanism — none of them declare a scheduled next-transmission time that lets the network mathematically distinguish expected vs. unexpected silence. The closest conceptual analogs exist in academic/industry networking literature (IoT liveness detection, distributed failure detectors) and in single-device personal-safety "check-in" apps, but neither of these is deployed as a crowd-relayed cluster-level "cell loss" inference, which appears to be Zone's genuinely novel application of the pattern.

### Cited Findings
- Bridgefy: no delivery confirmation or liveness mechanism found in any source; security audits focused on authentication/confidentiality flaws, not on any liveness/heartbeat feature, implying none exists to audit — [USENIX 2022](https://www.usenix.org/conference/usenixsecurity22/presentation/albrecht); [IACR 2021/214](https://ia.cr/2021/214)
- Briar: delivery depends on both parties being simultaneously reachable (no store-and-forward liveness schedule); no "expected next message" field found in its protocol description — [search summary / Wikipedia]
- goTenna: relays explicit messages/GPS pings/map markers; no scheduled-heartbeat or dead-man's-switch concept found in any sourced material — [Armada International, TechCrunch — see goTenna section above]
- Academic/industry literature does contain the general pattern applied to networking infrastructure, not disaster telemetry for people: "Disaster-FD," a 2024 fault detector designed for disaster-prone IoT environments, uses active and federated monitoring with reliability thresholds and confidence levels to distinguish genuine node failure from network degradation — [SBC/SOL paper](https://sol.sbc.org.br/index.php/wtf/article/view/29768)
- Cisco-linked U.S. patents (e.g., US10075360B2, "Monitoring node liveness in low-power lossy networks") describe liveness-checking mechanisms for sensor nodes in Low-power and Lossy Networks (LLNs) such as smart grid/smart city sensor deployments, including a related patent on "distributed liveness reporting" — [Google Patents](https://patents.google.com/patent/US10075360); [Justia](https://patents.justia.com/patent/10075360)
- A consumer personal-safety app implements a single-device "dead man's switch" check-in timer: if the user doesn't check in, an SOS fires automatically; a related fall-detection feature triggers an alarm if the person doesn't move for 8 seconds post-fall — this is a single-device, not networked/relayed, pattern — [search summary, specific app name not identified in results]

### Inferences
- The underlying engineering concept — a declared heartbeat interval used to distinguish planned vs. unplanned silence — is a known, generically-used pattern in distributed systems and IoT liveness/failure-detection research (going back decades to standard "heartbeat" and "failure detector" literature in distributed computing), and is explicitly discussed in disaster-specific IoT fault-detection research (Disaster-FD, 2024).
- However, none of the sourced material shows this pattern applied the way Zone applies it: (1) embedded directly in a 31-byte broadcast packet carried hand-to-hand with no server, (2) used at the level of an individual person's survival status rather than infrastructure node health, and (3) aggregated spatially across a cluster of devices in a small geographic cell to infer a physical event ("cell loss" / building collapse) distinct from ordinary no-data absence. This spatial-aggregation inference — turning synchronized unexpected silence across many nearby devices into a timestamped structural-collapse signal — was not found described anywhere in the competitor or academic literature surveyed.
- This supports a defensible claim that Zone's "silence-as-signal" mechanism is a novel application of a known distributed-systems primitive (heartbeat/liveness/dead-man's-switch) to a problem (person-level survival inference + building-collapse inference via phone-to-phone broadcast) that none of the surveyed disaster-communication products or literature currently address.

### Gaps
- This survey covered the most prominent public products and a narrow slice of academic literature; a fuller literature review (e.g., searching IEEE Xplore/ACM Digital Library directly rather than general web search) could surface closer academic analogs, particularly in search-and-rescue robotics, wearable-based lone-worker safety systems, or military "buddy check" telemetry systems, none of which were directly searched here.
- Could not verify whether any defense/military tactical mesh system (e.g., classified or restricted-access systems) includes a similar liveness-declaration mechanism, since such systems are generally not publicly documented.

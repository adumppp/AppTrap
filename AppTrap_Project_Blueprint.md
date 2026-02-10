# AppTrap / Android App Privacy & Trust Analyzer — Project Blueprint (Android 13+)

> Purpose of this document: **single source of truth** for the project logic, scope, threat model, scoring, evaluation plan, and implementation roadmap — so you can continue the project even if accounts/files are lost.

---

## 1) Project summary (what this is)

**AppTrap** is an **on-device Android 13+ app risk posture assessor** that scans installed apps and ranks them by **trust risk posture** using **static security signals**.

It does **not** claim “malware detection.”  
It claims **risk posture assessment**: *overreach*, *deception*, *spyware-like posture* based on static signals (capabilities + exposure + persistence/abuse patterns).

### Outputs
1) **Top-N risky apps** (default Top 10) ranked by **Trust severity (0–100)**  
2) Per app: **Explainable Trust Report** (role, capabilities, exposure signals, reasons, label, severity)  
3) **Guided mitigation actions** (App Info, Uninstall, Notification settings, Overlay settings)

---

## 2) Key definitions (use these in panel/Q&A)

### Risk posture (what severity means)
A **severity score** is **NOT** “probability the app is malware.”  
Severity is: **how risky the app’s posture looks** given static security signals that are often associated with abuse.

### Static analysis
No packet capture, no dynamic sandbox, no runtime behavior tracing.  
Signals are derived from:
- permissions
- component exposure (“attack surface”)
- persistence/abuse flags (boot receiver, overlay)

### Trust labels (4-class)
- **CONSISTENT** (0–19): matches role expectations; low risk signals  
- **OVERREACH** (20–39): extra capabilities beyond typical need  
- **DECEPTIVE** (40–69): strong mismatch role vs capabilities / exposure  
- **SPYWARE-LIKE posture** (70–100): persistence + sensitive + network/exposure/overlay patterns

---

## 3) User flow (product/demo flow)

1) User taps **Scan Installed Apps**
2) App scans non-system apps, extracts static signals, computes Trust verdict
3) Main screen shows **Top 10** apps ranked by **severity**
4) User taps an app → **Trust Report** screen
5) User sees reasons and chooses mitigation actions:
   - Open App Info
   - Uninstall
   - Notification settings
   - Overlay settings

---

## 4) Signals (inputs) — what you extract per app

### A) Permission signals (capabilities)
Extract full list of `requestedPermissions`.

Focus-sensitive groups:
- SMS: READ_SMS / RECEIVE_SMS / SEND_SMS
- Contacts: READ_CONTACTS
- Call log: READ_CALL_LOG
- Location: ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION
- Microphone: RECORD_AUDIO
- Camera: CAMERA
- Network: INTERNET

### B) Attack surface signals (extra signal that makes it cybersecurity)
Attack surface = **exposure of app components to external callers**.

Extract:
- **exportedActivityCount**
- **exportedServiceCount**
- **exportedReceiverCount**
- **exportedProviderCount**
- **exportedUnprotectedCount** (exported AND not guarded by a permission)
- **hasExportedProvider** (true if any provider exported)

Notes:
- “exported” components can be launched/invoked by other apps.
- “unprotected exported” components are higher risk.

### C) Persistence / abuse signals
- **hasBootPersistence** (receiver for BOOT_COMPLETED / RECEIVE_BOOT_COMPLETED)
- **hasOverlayCapability** (SYSTEM_ALERT_WINDOW)
- (Optional later) **installerSource** (Play Store vs sideload)

---

## 5) Claimed role inference (what the app “claims” to be)

Role is inferred heuristically (keywords + optional category):
- Cleaner/Utility: clean, junk, booster, optimize
- Finance/Banking: bank, wallet, pay, maybank, cimb, etc.
- Scanner: qr, scan, scanner
- Game: game, play, puzzle
- General: default fallback

Role inference is **auxiliary**; the final decision is driven by capabilities + exposure + persistence signals.

---

## 6) Trust Engine logic (rules → reasons → severity)

### TrustVerdict structure
- claimedRole: String
- observedCapabilities: List<String>
- signals: List<String>  (human-readable “why flagged”)
- label: String          (CONSISTENT / OVERREACH / DECEPTIVE / SPYWARE-LIKE)
- severity: Int          (0–100)

### Reasoning design requirement
Every scoring rule must:
1) add **points** to severity
2) add **reason text** to signals

No “silent scoring.” If you add points, you must add an explanation.

---

## 7) Scoring model (recommended, explainable)

Severity is sum of rule points (clamp 0..100).  
Rules are grouped into buckets to explain severity:

### Bucket 1: Role mismatch / deception
- Cleaner/Utility + (SMS or Contacts or CallLog)  
  **+40** — “Cleaner app requesting personal communications access”
- Scanner + (SMS or Contacts)  
  **+25** — “Scanner app requesting contacts/SMS”
- Game + (Mic or Location)  
  **+20** — “Game app requesting microphone/location”

### Bucket 2: Spyware-like posture combos
- Internet + (SMS or Contacts or Location) + Boot persistence  
  **+40** — “Internet + sensitive data + boot persistence (spyware-like posture)”
- Internet + (SMS or Contacts or Location) + Overlay  
  **+35** — “Internet + sensitive data + overlay capability”

### Bucket 3: Attack surface exposure
(implement at least Level 1 first)
- exportedUnprotectedCount >= 3  
  **+25** — “Multiple unprotected exported components (high exposure)”
- hasExportedProvider == true  
  **+25** — “Exports a content provider (potential data leak surface)”
- exportedCountTotal >= 6  
  **+15** — “Large exposed component surface (increased exploitability)”

> Tuning note: thresholds and points can be adjusted after dataset testing. The key is **consistency** and **explainability**.

### Label assignment
- severity >= 70 → SPYWARE-LIKE posture
- severity >= 40 → DECEPTIVE
- severity >= 20 → OVERREACH
- else → CONSISTENT

---

## 8) Risk Engine (optional baseline score)

A separate “RiskEngine” can produce a simple score from permission count + internet heuristics.  
**But ranking and project claim should use Trust severity**, not permission count.

---

## 9) Ranking policy (must)

Main list must be sorted by:
1) **TrustVerdict.severity** (descending) — primary ranking
2) Optional tie-breaker: permission-based score or dangerous-permission count

Default show:
- Top 10 risky apps (configurable)

---

## 10) Android 13+ considerations (important for demo defense)

### Package visibility
Android 11+ restricts app visibility. Using `QUERY_ALL_PACKAGES` is powerful but may be questioned.

**Defense posture:**
- Scan is **user-initiated**
- Processing is **on-device**
- No exfiltration; no background collection
- Document limitations and rationale

### Safe messaging to panels
- Do not claim “malware detection”
- Use “risk posture,” “exposure,” “overreach,” “deception,” “spyware-like posture”

---

## 11) Dataset & evaluation plan (how to prove it works)

### Why dataset exists
Not to “train” the rules.  
To **validate** and **tune** thresholds and report false positives/negatives.

### Dataset creation (device-based approach)
1) Install many apps on an Android 13+ test device:
   - Normal apps (50–80)
   - Utility apps (40–60) — scanners/cleaners/boosters
   - Sideloaded APKs (30–60) — modded/sketchy utilities, etc.
2) Run scanner, export results to CSV:
   - features + predicted severity/label + reasons
3) Add **manual ground-truth labels**:
   - CONSISTENT / OVERREACH / DECEPTIVE / SPYWARE-LIKE posture
4) Compare predictions vs ground truth:
   - confusion matrix
   - precision/recall for DECEPTIVE + SPYWARE-LIKE posture classes
   - false positive/negative analysis (with explanations)

### Labeling rubric (for ground truth)
Label based on:
- claimed role (store description/category/name)
- capability necessity (permissions vs role expectations)
- exposure/persistence signals (exported/unprotected/provider, boot, overlay)

### Credibility improvement (recommended)
Second rater labels 30–50 apps → report agreement (% agreement is acceptable).

---

## 12) Minimal build roadmap (efficient order)

### Milestone 1 — Minimal scanner + Trust severity ranking
- Scan non-system apps
- Extract permissions
- Run TrustEngine
- Store verdict in AppRisk
- Sort Top 10 by verdict.severity
- Detail page shows Trust Report reasons

### Milestone 2 — Add attack surface extraction
- Extract exported component stats + provider flags
- Add rules using these signals
- Show exposure section in Trust Report

### Milestone 3 — Dataset export / evaluation mode
- Export CSV
- Add manual label column workflow (Excel/Sheets or in-app)
- Show summary stats screen (optional)

### Milestone 4 — Polish demo
- UI improvements
- Clear report wording
- “Limitations & ethics” screen

---

## 13) Data model (recommended)

### AppRisk (passed between screens)
- packageName: String
- appName: String
- score: Int (optional baseline)
- level: String (optional baseline)
- verdict: TrustVerdict
- allPerms: List<String>
- signalProfile: (optional) exported counts, boot/overlay flags

### TrustVerdict
- claimedRole: String
- observedCapabilities: List<String>
- signals: List<String>
- label: String
- severity: Int

---

## 14) Demo script (60 seconds)

“AppTrap scans installed apps on Android 13+, extracts static security signals such as sensitive permissions, app exposure via exported components, and persistence/overlay capability. It computes a trust severity score with an explainable Trust Report that flags overreach, deception, and spyware-like posture. The main screen ranks the top risky apps and the detail view explains why and offers mitigation actions like uninstall or disabling overlay.”

---

## 15) Common Q&A defenses (short)

**Q:** Android already shows permissions. What’s new?  
**A:** AppTrap ranks risk posture using **permission combinations + exposure (exported components) + persistence**, and provides explainable reasons and mitigation actions.

**Q:** Are you detecting malware?  
**A:** No. This is **static risk posture assessment** and decision support.

**Q:** Rules are subjective.  
**A:** Rules are tied to a defined threat model and validated on a labeled dataset; we report false positives/negatives and tune thresholds.

**Q:** Apps can rename themselves.  
**A:** Name-based role inference is auxiliary; main signals are capabilities/exposure/persistence.

---

## 16) Safety / ethics note
Do not distribute suspicious APKs. Use them only for local testing and documentation. Do not claim definitive malware attribution.

---

## 17) Current status reminder (from latest code snapshot)
- Trust verdict must be stored into AppRisk and used for ranking.  
- “TrustVerdic.kt” should be a data class file, not a duplicate activity.

---

End of document.

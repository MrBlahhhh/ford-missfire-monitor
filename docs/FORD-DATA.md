# Ford misfire data definitions

Verified against Ford's published gasoline OBD operation summaries on 2026-10-14. These are protocol-family definitions, not a promise that each individual PCM calibration implements every result.

| Vehicle / transport | Request | Fields displayed | Scaling |
|---|---|---|---|
| 2004 F-150, CAN | `06A2` … `06A9` | TID 0C current/last-cycle; TID 0B weighted average | UASID 24 or 01: 1 count/bit for TIDs 0B/0C; others raw |
| 2017 V10, CAN | `06A2` … `06AB` | Same per-cylinder tests | Same, with returned unit validation |
| 2004 Ford, J1850 | `0653` | CID 1–8 stored per-cylinder rate | raw × 0.000015 percent; CID bit 7 distinguishes min/max limit |

The CAN response body is `46` followed by nine-byte records: MID, TID, UASID, value16, minimum16, maximum16. Each integer is big endian. Legacy TID 53 uses a seven-byte response including `46`, TID, CID/type, value16 and one limit16. Component zero is not mapped to cylinder one. Undefined or out-of-profile cylinders are discarded.

Only engine replies are used: 7E8 for 11-bit CAN, 18DAF110 for 29-bit CAN and source 10 for legacy Ford headers. A missing recognized engine response fails connection with exportable evidence instead of accepting a transmission response. The adapter uses H1/S1, no DLC, CAF1/CFC1; ISO-TP response frames are assembled independently per ECU and rejected if incomplete/out of sequence.

## Captured F-150 evidence / UASID 01

The phone's successful 2026-10-14 21:51 UTC session used engine ECU 7E8, CAN protocol A6 and valid multi-PID responses. Cylinder 5's A6 records were TID 0B/UASID 01/value 0012 (18) and TID 0C/UASID 01/value 0000 (0), with limits 0000–FFFF. The average is historical; the cycle value is not a lifetime total.

SAE J1979 Appendix E Table E1 defines 01 as an unsigned raw value with scaling 1/bit. Combined with the Ford-defined count test identities, this permits count display for these TIDs only. The chart uses positive changes of TID 0C after a local baseline; 0B never contributes new events.

## Primary sources

- [SAE J1979 incorporated standard](https://law.resource.org/pub/us/cfr/ibr/005/sae.j1979.2002.pdf), Appendix E Table E1: UASID 01 unsigned raw 1/bit.
- [Ford 2004 gasoline OBD operation summary](https://www.fordservicecontent.com/ford_content/catalog/motorcraft/OBDSM407.pdf), PDF p12: CAN and J1850 misfire test tables and legacy scaling.
- [Ford 2017 gasoline OBD operation summary](https://www.fordservicecontent.com/ford_content/catalog/motorcraft/OBDSM1704.pdf), revision December 21, 2016, PDF p26: per-cylinder CAN misfire results. Retrieved and verified locally; the web viewer's document size limit prevented rendering the full PDF.
- [ELM327 v2.2 data sheet](https://www.elmelectronics.com/wp-content/uploads/2017/01/ELM327DS.pdf), p43–46: header/PCI response formatting, automatic flow control and up-to-six CAN live PID requests.
- [AUTOSAR Diagnostic Communication Manager, release 4.3.1](https://www.autosar.org/fileadmin/standards/R4.3.1/CP/AUTOSAR_SWS_DiagnosticCommunicationManager.pdf), p183, SWS_Dcm_00956: multiple actual Mode 06 monitor IDs are ignored; availability-ID bundling is a separate operation.
- [OBDLink LX protocol specifications](https://www.obdlink.com/lx-bluetooth/): classic Bluetooth, J1850 PWM and ISO15765 CAN support. MS-CAN access is not required for the engine data requested here.
- [GM published J1979 Mode 06 definitions](https://gsi.ext.gm.com/gmspo/mode6/pdf/GM%20CAN%20mode%20%2406%20data%20final_dm.pdf): standardized UASID 24 count resolution. Ford-specific returned units are still checked rather than assumed.

Do not invent proprietary Ford $22 identifiers, convert legacy stored rates into event counts, or interpret all-zero unevaluated results as proof of no misfires. Extend definitions only with a documented mapping and captured replies from the relevant PCM.

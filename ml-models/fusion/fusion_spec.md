# Fusion Specification (late / decision-level)

Human-readable mirror of the frozen fusion decisions in `docs/06_ML_SYSTEM.md` (FD-9…FD-13). The
**machine contract is `fusion_config.json`**; this file introduces no new decisions.

## What fusion is

Fusion combines the three modality scores — **Facial**, **Voice**, **Motion**, each already on a
0–100 scale — into a single **Final Score (0–100)** and a display **category**. It is a
**scoring engine, not a trained model**, and it runs **on-device** (the backend never fuses).

## Weights

| Modality | Weight |
|----------|--------|
| Facial | 0.30 |
| Voice | 0.25 |
| Motion | 0.25 |

```
Final Score = 0.30·Facial + 0.25·Voice + 0.25·Motion      (all modalities present)
```

When all three modalities are present, the frozen base weights are normalized to sum to 1.0:

Final Score = 0.375·Facial + 0.3125·Voice + 0.3125·Motion

This is obtained by normalizing the base weights:

- Facial: 0.30 / 0.80 = 0.375
- Voice: 0.25 / 0.80 = 0.3125
- Motion: 0.25 / 0.80 = 0.3125

The same normalization principle applies whenever one or more modalities are missing: unavailable modalities are removed and the weights of the remaining modalities are renormalized to sum to 1.0.

## Renormalization on missing modalities

When a modality score is unavailable, drop its weight and rescale the remaining weights so they sum
to **1.0**, then take the weighted sum of the present scores.

- **Voice missing:** facial 0.30/0.55 ≈ 0.5455, motion 0.25/0.55 ≈ 0.4545.
- **Facial missing:** voice 0.25/0.50 = 0.50, motion 0.25/0.50 = 0.50.
- **Motion missing:** facial 0.30/0.55 ≈ 0.5455, voice 0.25/0.55 ≈ 0.4545.
- **All missing:** no Final Score is produced.

## Clipping and categories

Final Score is clipped to `[0, 100]`. Category bands (display only):

| Range | Category |
|-------|----------|
| [0, 20) | calm |
| [20, 40) | mild |
| [40, 60) | moderate |
| [60, 80) | high |
| [80, 100] | critical |

Bands are lower-inclusive / upper-exclusive; the top band includes 100. Exact boundary values map to
the higher band.

## Boundaries you must not cross

- **Behavioral analytics is not a fusion input** and not a fourth model (FD-12, FD-24). It may inform
  application logic *around* the score, never the score itself.
- **Category bands ≠ alert thresholds.** The backend alert thresholds (HIGH 70 / CRITICAL 85, FD-15)
  are a separate concern and must not be encoded here.
- **No backend inference / no raw stream upload** (FD-1, FD-2). Fusion consumes on-device modality
  scores and yields the `fusion_score` that Android uploads.

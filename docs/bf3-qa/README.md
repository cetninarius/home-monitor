# BF3 visual QA checklist

Capture screenshots on an Android TV emulator or device for each state. Prefer glass appearance + default Baghdad background.

| State | What to verify |
|-------|----------------|
| A Core only | Services off, NP idle, WF1 off — full-width Calendar/Todo, no empty right column |
| B + Services | Bottom Services\|Weather balanced |
| C + Now Playing | NP hero, compact home grid, no WF1 column |
| D Services + NP | Combined; no overlap; focus order sane |
| E WF1 connected | 66/34 columns, queue readable |
| F WF1 disconnected | Right column present with “No WF1 engine connected” + secondary hint; no “searching” |
| G Optionals off | Services off, NP off, WF1 off — ambient core dashboard |
| Settings | Integrations shows WF1 Experimental; Open SmartTube present |

Store captures in this folder as `state-a.png` … `state-g.png`, `settings-integrations.png`.

Do not treat compile success as visual validation.

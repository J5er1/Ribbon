# Ribbon — Brand & Identity Design Brief

**For:** the designer or design agent producing the logo, icon, and brand assets
**Status:** name, palette, type, and product vocabulary are settled. The mark is not.
**Written to be self-contained** — assume the recipient has no other context and cannot ask follow-up questions.

---

## 0. How to use this brief

**Settled — do not redesign these.** The name. The positioning. The color tokens. The type stack. The product vocabulary in §11. The never-ship list in §13.

**Your job — genuinely open.** The mark, the wordmark lockups, the app icon, and the small graphic system that follows from them (the fire states, the ember, the shelf).

**If you disagree with something marked settled,** say so explicitly in your handoff notes and design it as specified anyway. Don't silently reinterpret.

---

## 1. What the product is

Ribbon is a mobile app for reading the Bible with **one person or a few** — a partner on the same couch, or a friend four time zones away.

The three things it does:

1. **Presence.** You can see who else is reading, live, while you read.
2. **Notes left behind.** Voice memos and written thoughts pinned to specific verses, found later by the other person.
3. **A shared fire.** A single warm object the group is keeping alive together, in place of a streak counter.

The competition is not other Bible apps. It's the fact that reading Scripture with someone you love currently happens badly — over text message, out of sync, and with a low hum of guilt about the days you missed.

**The emotional target: a room, not a program.** Warm, dim, unhurried, and occupied by someone else. Every design decision is downstream of that sentence.

Two consequences that matter for you:

- **Dark is the hero condition, not a setting.** This app is opened at 6:40 a.m. and 10:15 p.m., not at noon. Design dark first, then adapt to light.
- **The visual world comes from the physical book** — ribbon, paper, margin, gutter, lamplight — because that's the only shared vocabulary the audience already trusts.

---

## 2. The name

**Ribbon.** From the bookmark ribbons sewn into the spine of a real Bible — thin markers that let you hold more than one place at once. Two ribbons in one book is the entire product in a single object: two people, one text, different places, still bound together.

- Domain: `ribbon.bible`
- Handle: `@readribbon`
- Tagline: **Read it together.**
- The name is final. Do not propose alternatives.

---

## 3. Positioning, audience, promise

**Positioning sentence.** Ribbon is a quiet, shared place to read Scripture with the people you'd actually want to read it with — present when you're together, patient when you're not.

**The one promise.** You will never be reading alone, and you will never be behind.

**Audience, in priority order.**

1. Couples — the primary case. Private, intimate, often long-distance.
2. Friend groups and small Bible studies — 3 to 6 people.
3. Youth ministry leaders running a group.

**A design constraint hiding in that list:** the brand must let a *hesitant* partner install the app without feeling recruited. It should read as literary and warm before it reads as religious. Someone should be able to have this on their home screen without it being a statement.

**What it is not.** Not a habit tracker with a cross on it. Not a study platform. Not a social network — no feed, no follower count, no strangers. It has no opinion about anyone's theology and no interest in their consistency.

---

## 4. Deliverables

**Primary**

- [ ] **Primary mark** — SVG, single path set, optimized, on a 64×64 grid
- [ ] **Mark, one-color version** — must hold as pure black and pure white
- [ ] **Mark, two-color version**
- [ ] **Wordmark** — "Ribbon." set in Fraunces, optically corrected (see §6)
- [ ] **Lockups** — horizontal (mark + wordmark), stacked, and mark-only
- [ ] **App icon** — 1024×1024 master plus the standard iOS/Android export set
- [ ] **Clear-space and minimum-size rules**

**Secondary — the graphic system**

- [ ] **The fire**, in four states: catching, burning, steady, banked (see §11). Abstract, not a cartoon flame.
- [ ] **The fire at three scales**, since its size encodes the length of a Bible book — small (Philemon), medium (Philippians), large (Isaiah)
- [ ] **The ember** — what a finished fire becomes
- [ ] **The shelf** — a row of embers of varying sizes, one per book completed
- [ ] **Note marks** — a solid dot (voice note) and an open ring (written note), legible at 6px

**Format.** SVG source for everything, plus PNG exports. Provide the geometry as editable vector, not rasterized. Include a single-file spec sheet showing every asset on both the dark and light grounds.

---

## 5. The mark

### Concept

**Two tapered ribbons falling from a common edge, angled so they cross low, each ending in a swallowtail notch.** The crossing point is the idea — two people, two places, one binding.

### Starting geometry

A rough version exists. Treat it as a statement of intent, not a solution — it is unrefined and the proportions have not been optically corrected.

```svg
<svg viewBox="0 0 64 64">
  <!-- back ribbon, leaning left -->
  <path d="M36 6 L46 6 L32 54 L27 46 L22 54 Z"/>
  <!-- front ribbon, leaning right -->
  <path d="M18 6 L28 6 L42 54 L37 46 L32 54 Z"/>
</svg>
```

### What must be true

- **Legible at 24px.** Test it there before anything else. This is the binding constraint.
- **Survives one color.** No effect, gradient, or overlap trick may be load-bearing.
- **Reads as ribbon**, not as an X, a check mark, a pennant, or an arrow. If people see a check mark, it has failed.
- **No cross.** The ribbon already signifies "Bible." A cross narrows the audience and cheapens the mark.
- **Occlusion, not transparency.** One ribbon passes behind the other with a hard edge. Do not use opacity blending at the crossing.
- **Asymmetry is welcome.** Two ribbons of slightly different length and angle read as two people. Perfect symmetry reads as a corporate logotype.

### What to explore

- Whether the ribbons hang **straight** (calm, bookish) or with a **slight curve or twist** (alive, cloth-like). The current geometry is straight and may be too rigid.
- The **swallowtail depth and angle** — this is the detail that will carry the craft.
- Whether the top edge is **flush** (both emerging from the same binding) or **staggered**.
- Whether the mark can imply the **fore-edge of a book** without drawing one.

### What to avoid

Ribbon-as-awareness-loop. Ribbon-as-gift-bow. Ribbon-as-banner-scroll. Anything that reads as a medal, an award, or a sale tag.

---

## 6. Wordmark

- Set in **Fraunces**, with the variable `SOFT` and `WONK` axes turned up (approximately `SOFT 60, WONK 1, opsz 144`), weight ~400, tight tracking around `-0.04em`.
- **The period is part of the wordmark and it is crimson (`#C9584E`).** It's a full stop on "Read it together." and it's a bead on the end of a ribbon. It's the cheapest distinctive thing in the identity — keep it.
- Optical corrections expected: the `Ribb` cluster needs manual kerning, and the period needs its own spacing decision rather than the default sidebearing.
- Deliver as outlines and as a live-text spec.

---

## 7. App icon

- **The mark on the Unlit ground (`#0A0806`)**, in Crimson and Lamp.
- A warm object in a near-black square. On a home screen full of saturated gradients this will look like nothing else in the category — that's the point.
- **No glow behind it.** No inner shadow. No gradient background. No bevel.
- It must survive the OS masking it to a circle, a squircle, and a rounded rect.
- Test it at 40px in a crowded home-screen mock before delivering.

---

## 8. Color

### The room

| Token | Dark (primary) | Light (paper) | Use |
|---|---|---|---|
| Ground | `#0A0806` | `#F0ECE2` | Page background |
| Surface | `#120F0B` | `#FAF7F0` | Cards, sheets |
| Raised | `#1B1710` | `#E6DFD1` | Bars, chips |
| Text | `#F1E8D9` | `#1E1913` | Primary text |
| Muted | `#8E8271` | `#6B6051` | Metadata |
| Rule | `#292118` | `#D5CCBA` | Borders, dividers |
| **Lamp** | `#E9A63F` | `#A96218` | **The single accent** |

**Rules.**

- The dark ground is near-black **with a brown bias held all the way through**. Never blue-black — that's what makes a dark UI read as a device instead of a room.
- **Light mode is not an inversion. It's paper** — the physical book in daylight.
- **The Lamp is the only accent** and it means warmth and presence. It is never a link, never a destructive action, and never a color a user can pick.

### The eight inks

Highlight and identity colors. Dark values first, light values in parentheses.

| Ink | Dark | Light |
|---|---|---|
| Crimson | `#C9584E` | `#A2332C` |
| Clay | `#C87A46` | `#9A5430` |
| Ochre | `#DCA846` | `#8C6412` |
| Moss | `#8AA77B` | `#4F6B45` |
| Teal | `#63A09A` | `#2F6360` |
| Indigo | `#7297CE` | `#3A578A` |
| Plum | `#B3849E` | `#74445D` |
| Rose | `#CE6B84` | `#9E4059` |

Eight is deliberate: enough for a room of five to be distinct with real choice left over, while staying separable at a 6px dot.

**How inks behave** (context for your comps — this is product logic, not yours to change):

- **Two people:** both draw from the whole palette freely, per highlight. Color is expressive — this one's a promise, this one's a question.
- **Three or more:** each person picks one ink and every highlight they make is that color. Color becomes identity.
- Highlights render as a translucent wash at ~24% with a soft bleed past the glyphs, so two overlapping highlights blend like real ink rather than fighting for a solid fill.

---

## 9. Typography

| Role | Face | Notes |
|---|---|---|
| Display | **Fraunces** | `SOFT` and `WONK` axes up. Warmth lives here. |
| Scripture & reading | **Literata** | Built for long-form screen reading. Must never feel like an interface. |
| Interface | **Alegreya Sans** | Humanist, cut as a companion to a book serif. |
| Metadata | **Alegreya Sans SC** | True small caps, the way a book sets a running head. |

**No monospace anywhere.** It was tried and rejected — it drags a terminal into a candlelit room. Everything a mono face would have carried (verse numbers, timestamps, states, durations) goes into small caps instead.

All four are on Google Fonts and free to embed.

---

## 10. Motion & texture

**Motion.** Everything breathes rather than snaps. 320–480ms, gentle ease-out, no bounce, no spring overshoot. The fire has a slow 3–4 second flicker. Nothing in the app is fast except the "thinking of you" haptic, which should feel like a tap on the shoulder.

**Texture.** A faint paper grain over the ground at very low opacity. This matters more than usual because the ground is so dark — without it, near-black reads as flat app chrome instead of an unlit room. Specify the grain as a tileable asset with a recommended opacity.

---

## 11. Product vocabulary

**These words are settled and load-bearing.** Any copy you put on a comp must use them correctly.

| Term | Meaning |
|---|---|
| **A fire** | One book of the Bible, one fire. Its size is set by the book's word count — Philemon is small, Isaiah is large. |
| **Catching / Burning / Steady / Banked** | The only four states a fire has. |
| **Banked** | What a missed day looks like: covered and kept low, still alive. Banking a fire is something a careful person does on purpose. Never framed as a loss. |
| **An ember** | What you keep when you finish a book. |
| **The shelf** | Every book read together, as a row of embers. The printed keepsake is made from it. |
| **Ink** | A highlight color. |
| **A note left** | A voice memo or written thought pinned to a verse. Always "left" — being found later is the emotional beat. |
| **The gutter** | The narrow margin where note marks sit. |
| **Opening the cards** | The reflection questions that unlock once both people have answered. |
| **A quiet day** | The grace mechanic. Marked by the person, never detected by the app. |

### The hardest rule in the product

**A fire reports a state, never a count.** No numbers, no fractions, no percentages, no "6 of 12," no day tallies — anywhere, ever.

The reasoning, because it will tempt you: a countable goal is an accusation. "Six of twelve" tells you exactly how much is missing and whose absence it was. An abstract one can only report a mood. This is the single decision that separates Ribbon from every gamified Bible app on the store, and it constrains your graphic system directly — **the fire must be readable as a state at a glance, with no numeral anywhere near it.**

---

## 12. Voice

Short sentences. Second person. Say the true small thing instead of the big warm thing — *"Ruth left you a note at verse 4"* beats *"Deepen your spiritual connection."* No exclamation points. Grace is expressed by the interface being unbothered, not by the interface reassuring you.

**Use:** together, beside, quiet, unhurried, keep, tend, leave, notice, room, banked, steady.

**Never use:** streak, don't break the chain, accountability partner, engagement, daily challenge, crush it, level up, unlock rewards, devotional journey, spiritual walk, "You missed yesterday."

---

## 13. Never ship

- A number attached to a fire — count, fraction, percentage, or day tally
- A cross in the logo
- A cartoon flame
- A purple-to-blue gradient, or any gradient hero
- Glass panels, frosted blur, glowing neon borders
- Sunbeams-through-clouds photography, or a mountaintop at golden hour
- Confetti, badges, trophies, medals, or ribbons-as-awards
- A red number badge on the app icon
- Stock "praying hands" or open-book-with-light-rays imagery
- Serif quote cards designed to be screenshotted for Instagram
- Anything that reads as a church bulletin

---

## 14. How the work will be judged

In this order:

1. **Does it hold at 24px?** Everything else is negotiable; this isn't.
2. **Does it read as a ribbon** to someone who wasn't told what it is?
3. **Does the app icon look unlike anything else on a home screen** without looking loud?
4. **Would a person who is unsure about the Bible still install this?**
5. **Does the fire feel warm and unhurried** rather than gamified?
6. **Is it obviously made by a person** rather than assembled from defaults?

---

## 15. Open questions — bring options, don't decide alone

1. **Straight vs. curved ribbons.** Straight reads calm and bookish; a slight twist reads alive. Show both.
2. **Does the mark need a container** (a circle or squircle) for small contexts like a favicon, or does the bare mark hold?
3. **How abstract should the fire be?** It has to work at 12px in a bottom bar and at 200px on the shelf, and it must never become a flame icon. Show a range.
4. **Does the shelf read as a shelf** without drawing a shelf? If a horizontal line is needed, propose the lightest possible one.

---

## 16. Context to read first

A full brand direction document exists with the visual system realized in context, including a mock of the reading screen. It shows the app's actual interface, which is unusual and worth understanding before designing for it: **no header, no back button, a single exit at the bottom, presence carried by a soft form half-emerged from the right edge of the screen, and note marks in the gutter.**

Ask the client for the "Ribbon Brand Direction" page before starting.

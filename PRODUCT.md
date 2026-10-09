# Product

Durable product context for design work, following [impeccable.style](https://impeccable.style) conventions. Visual rules live in `DESIGN.md`; behaviour rules live in `SPEC.md`.

## Audience

There are two audiences, and they use the same phone at different moments.

**Parents (operators).** They record things in passing: in the kitchen, at the school gate, with a child standing next to them. They are interrupted, one-handed, and short of time. They need to record "Tom did the bins" in under five seconds and trust that their partner's phone will agree later. Most are not finance people.

**Children aged 5 to 14 (viewers).** They are handed the phone to look, not to operate. The youngest can't read reliably yet but can count coins and recognise pictures. The oldest are sharp, will look for loopholes in the interest rule, and will notice if a number changes without explanation.

## Purpose

Make three ideas concrete through weekly repetition:
1. Work earns money.
2. Leaving money alone makes it grow.
3. The record is fair and open to you.

The app is a bank, not a game. Its credibility comes from being boringly reliable.

## Operating context

- Parents' Android phones only. There is no child device.
- No network. Two or more parents' phones sync when they are near each other. Sometimes that's days apart, so a phone may be stale.
- Two very different modes of use:
  - **Operate** (parents): fast entry, a queue of what's due, sync status.
  - **Experience** (child view): slow, proud, a moment of looking.
- Payday happens weekly, and it's the one ritual moment. Everything else is routine.

## Design principles

1. **One action wins each screen.** On the parent's home that action is Record; on the child view it's the balance. If two things compete for attention, one of them is wrong.
2. **Every number explains itself.** You can tap any balance and see the entries behind it. Interest shows the base it was calculated on. A change after a sync says why it happened.
3. **Never shame.** Cash-outs are spending, not failure, so they aren't red. Deductions state their reason plainly, without alarm styling or exclamation marks.
4. **Suit the reader, not the age.** Picture style and Number style are two real designs, not one design scaled down.
5. **Be truthful about the system.** Show staleness ("Last synced with Sam's phone 3 days ago") rather than hiding it. Never claim the child view is a lock.
6. **Earn the one moment of delight.** Payday gets a considered, restrained moment. Nothing else gets celebration effects.

## Voice

Plain, warm and specific. It talks like a fair-minded parent, not a brand.

- To parents: short and factual. "Tom · Bins · +20". "Synced with Sam's phone. 6 new entries."
- To children: second person, concrete, with the money shown. "You have 204 points. That's £2.04." "This week your money earned 2 points just by staying in your jar."
- Never: "Awesome!!", "Great job, superstar!", "Unlock rewards", "Oops!", or any generic praise. Praise belongs in the parent's note, in the parent's words.
- Interest is always explained the same way: "1% a week on the smallest amount you had all week."

## Accessibility goals

- WCAG 2.2 AA contrast, 48 dp touch targets, font scaling to 200%, TalkBack (SPEC NFR-A11Y-*).
- Picture style can be used without reading. Every picture has a spoken description.
- The typeface is chosen for telling characters apart (`1 l I`, `0 O`), because children are reading numbers.

## Evidence

- Expected rewards tend to reduce intrinsic motivation; unexpected rewards don't, and specific praise increases it (Deci, Koestner & Ryan, 1999). This is why there's no gamification, why awards need notes, and why behaviour has no price list.
- Planning ahead and delaying gratification develop early, around age 7 (Whitebread & Bingham, 2013; secondary coverage only). This is why Picture style exists.

# House Points: the concept

This document is for parents, not engineers. It explains what the app does and why it works the way it does. The precise rules are in `SPEC.md`.

## The idea in one line

Children earn points, the points grow if they leave them alone, and they can swap them for real money when they choose.

## Who uses it

Only parents. The app lives on parents' phones. Children don't need a device: when a child wants to see their account, a parent opens the **child view** and hands over the phone.

Any number of parents' phones can join one family. They stay in agreement by syncing directly, phone to phone, whenever they are near each other. No internet connection, account or server is involved.

## Earning points

There are three kinds of chores.

- **Expected jobs** are things everyone in the family does because they live there: making your bed, clearing your plate. These earn no points. Keeping them separate stops every request turning into "what do I get for it?" The app can show them as a simple tick list.
- **Paid chores** have a fixed points value and belong to a particular child. They can repeat (daily, on certain weekdays, or weekly) or happen once.
- **Bounty jobs** are extra jobs any child can do, such as washing the car. Each completion is paid.

**Behaviour awards** are points a parent gives when they notice something worth noticing. Each award is linked to one of the family's values (kindness, honesty, effort, courage, or whatever your family chooses) and carries a short note saying what happened. Over time a child's history becomes a record of who they are, not just what they did.

*Why awards should feel like surprises.* A large review of studies found that rewards children *expect* for an activity tend to reduce their own interest in it. Rewards that come *unexpectedly* didn't have that effect, and specific praise increased interest (Deci, Koestner & Ryan, 1999). So the app has no price list for good behaviour. Awards are occasional, specific and written down.

**Taking points away** is a family setting with three options:
- never
- only from what the child earned this week (the default)
- from anything

The default protects savings. If points in the bank can vanish, the bank stops being somewhere safe, and saving stops making sense.

## The bank

Points are worth real money at a rate the family chooses. The default is **1 point = 1p** (so 100 points = £1). Any currency works.

**Interest** is paid every week. Each child's balance grows by a set percentage (default **1% a week**), calculated on the **lowest** balance they held during that week. So a child can't collect interest by receiving a big award on Sunday night: the money has to sit there for the whole week.

- *Why so generous?* A real bank's rate is invisible to a child. At 1% a week, 200 points becomes about 335 in a year, and a 6-year-old can see it moving week to week.
- *Why a cap?* By default, interest is only paid on the first 2,000 points. That keeps the cost to parents predictable: at most 20 points (20p) per child per week, or about £10 per child per year.

**Savings goals.** A child can save towards something specific. The app shows two dates for when they'll get there:
- if they only earn interest
- if they keep earning at their recent pace

The gap between the two dates is the lesson.

## Cashing out

When a child wants money, a parent records the cash-out and hands over the money (cash, a bank transfer, whatever suits). The app never touches real money. It only keeps the record.

Each cash-out records the exchange rate that applied that day. If the family later changes the rate, past payouts still show what was actually paid.

## Fairness and trust

- **Nothing is ever edited or deleted.** A mistake is fixed by recording a *reversal*, which stays in the history with its reason. Children can see their whole history.
- **Both phones always agree once they've synced.** If one parent records something while apart, the other phone picks it up at the next sync. If that changes a past week's interest by a point, the app says so plainly instead of quietly changing the number.
- **Some things can only be resolved by talking.** If both parents pay out the same savings while their phones are apart, the balance can go below zero. The app shows this clearly and leaves it to the parents to decide what to do.

## Children's view

Each child gets one of two display styles, chosen by a parent (not worked out from age):

- **Picture style** (roughly ages 5 to 8): a jar that fills with coins, pictures for chores, and the goal shown as a picture with how far there is to go. Very little reading.
- **Number style** (roughly 9 and up): the real figures, the full history, interest by week, and "what if I wait?" projections.

**Payday** is the weekly moment when interest lands. The app produces a short statement for each child (what came in, what went out, what interest added), which can be shared or printed for the fridge.

*Why start this young?* A review of research for the UK's Money Advice Service (Whitebread & Bingham, 2013) reported that the ability to plan ahead and delay gratification develops early, and that money habits are largely forming by about age 7. We've only seen this study in secondary reports so far, and we'll check the original before relying on it further.

## What's deliberately left out (for now)

- Children's own devices, logins or accounts.
- Any server, cloud account or internet requirement.
- Moving real money or linking to banks.
- iPhone. Android only, because parents' phones sync using a Google Play Services feature.
- Locked savings (a fixed-term deposit at a higher rate). Planned for later.
- A rewards shop for non-cash treats like screen time. Planned for later.
- Streaks, badges, levels and confetti. These turn a bank into a game, and the point is that it's *not* a game.

## References

- Deci, E. L., Koestner, R., & Ryan, R. M. (1999). A meta-analytic review of experiments examining the effects of extrinsic rewards on intrinsic motivation. *Psychological Bulletin*, 125(6), 627–668.
- Whitebread, D., & Bingham, S. (2013). *Habit Formation and Learning in Young Children.* Money Advice Service. (Seen only in secondary coverage so far: [sueatkinsparentingcoach.com summary](https://sueatkinsparentingcoach.com/2013/07/your-childsmoney-habits-are-formed-by-the-age-of-7/), [MaPS 2022 financial literacy testing report](https://maps.org.uk/content/dam/maps-corporate/en/our-work/uk-strategy-for-financial-wellbeing/maps-measuring-financial-literacy-age-4-to-6-testing-2022.pdf).)

# planka-akka

Keeps a kanban board's columns and cards in order, and pushes every change to everyone
looking at that board.

A port of [plankanban/planka](https://github.com/plankanban/planka) onto **Akka**, built with
**Akka Specify**.

---

## Where it came from

planka is a project management tool where work is tracked as cards that move between columns
on a shared board. One part of it was rebuilt here: the rule that decides where a card or a
column sits when somebody drops it between two others, and the feed that tells everybody else
about it.

The port was the vehicle. The deliverable is the specification precise enough to rebuild that
behaviour on a different stack, and the record of how each claim in it was checked. Those live
in [TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness)
under `planka-port/`.

---

## plankanban/planka → this port

📉 300 lines → **612 lines**<br>
📁 1,197 files → **12 files**<br>
💾 21 MB of source → **1 MB of source**<br>
⚡ 292 nanoseconds → **71 nanoseconds** to place one card among its neighbours<br>
⚡ 3,841 nanoseconds → **272 nanoseconds** to place one card into a full column<br>
🎯 32 of 32 cases answered identically → **32 of 32**<br>
🧪 4 of 4 deliberately broken rules caught → **4 of 4**

Full method and the numbers that did *not* make this list:
[`bench/REPORT.md`](https://github.com/TylerJewell/akka-specify-harness/blob/main/planka-port/bench/REPORT.md).

---

## What it took to build

⏱️ **29.0 hours** from the first command to the published repository, **1.3** of them active<br>
💬 **354** exchanges with the model<br>
✍️ **254,494** tokens written by the model, **64,931,951** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **14** tests

```bash
python toolkit/tokens.py --port planka    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](https://github.com/TylerJewell/akka-specify-harness/tree/main/port-log).

---

## What it does

- **A card dropped into a gap wide enough for it stays exactly where it was dropped.** Nothing
  else on the board moves.
- **A card dropped into a gap too narrow for it pushes the cards above it along.** The pushing
  stops at the first card that already had room, so a board with a thousand cards does not
  renumber a thousand cards to make space for one.
- **When the numbers run out at the top, every card on the board is given a fresh one.** Cards
  that had plenty of room are renumbered too, and the order they were in is kept.
- **When two cards hold the same number, the one that arrived last ends up highest.** Their
  numbers cannot separate them, so the order they arrived in does.
- **Everybody watching a board is sent the whole board every time anything changes.** Nobody
  can see a card in its new place without also seeing the cards that moved aside for it.
- **A watcher who has just connected is sent the board before anything else.** There is no
  second request to make and nothing to catch up on after a connection drops.
- **A watcher of one board is sent nothing about any other board.**

---

## Design decisions

**One board is one record.** Where a card lands and which of its neighbours move aside are
answers to a single question, so they are written down together in one place. A watcher can
never be shown half of that answer.

**The board's number is inside every card's name.** A request that names only a card has to
find the board it belongs to, and the name is all such a request carries. Looking it up in an
index would mean waiting for that index to notice a card that was made a moment ago.

**Watchers are woken, not asked.** A watcher waits until something changes rather than asking
five times a second whether anything has. A board nobody is touching costs nothing to watch,
and a change is not held back waiting for the next ask.

**Every number in the ordering rule came from running the original, not from reading it.** The
original does something a careful reading of its code does not predict, in two places. Reading
it produced a rebuild that gave different answers; running it produced one that gives the same
ones on every case tried.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/planka-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Open** http://localhost:9081.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9081**.

### Try it

```bash
BOARD=$(curl -s -X POST localhost:9081/boards \
  -H 'Content-Type: application/json' -d '{"name":"Kanban state"}' \
  | python -c 'import json,sys; print(json.load(sys.stdin)["id"])')

curl -s -X POST localhost:9081/boards/$BOARD/lists \
  -H 'Content-Type: application/json' -d '{"name":"To Do","position":0}'

curl -N localhost:9081/boards/$BOARD/stream
```

The board screen is at `http://localhost:9081/ui/boards/<board>`.

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| none | — | The service reads no environment variables. The port it listens on is set in `src/main/resources/application.conf`. |

---

## Where it differs from plankanban/planka

Everything not listed here behaves the same way on purpose, including the parts that look like
mistakes.

- **What a watcher is sent when cards move aside.** planka sends one message for each card that
  moved and then one for the card that was added, so a watcher of a four-card reflow receives
  five messages. This port sends one message carrying the whole board, because a whole board
  cannot be read halfway through and a run of five messages can.
- **What a watcher is sent when they first connect.** planka's feed sends nothing on
  connection: a client reads the board over a separate request and joins the feed separately,
  in whatever order it happens to do those two things. planka states no rule for this, so this
  port chose one — the board itself is the first thing on the feed — because otherwise what a
  watcher sees first depends on a race, and that race is exactly what a dropped connection
  changes.
- **Who can see a board.** planka puts every board behind an account, a project, and a
  membership. This port has none of those, so anybody who can reach it can read and change any
  board. Rebuilding an entire permission system was not what this port set out to compare.
- **What a card can hold.** planka's cards carry attachments, comments, labels, checklists,
  due dates, timers, and custom fields. This port's cards carry a name, a description, and a
  position, because those are what the ordering rule reads.
- **How many columns a board can have before it is a problem.** planka keeps each column and
  card as its own database row and has no board-wide size limit. This port keeps a whole board
  as a single record, which is what lets a card's move and its neighbours' arrive together, and
  a record has a size ceiling. Where that ceiling falls in cards is `not measured`.
- **Whether a watcher on one machine sees a change made on another.** planka's feed is handled
  by its web server, which is one process. This port wakes its watchers through a registry held
  in one running copy of the service, so with several copies behind a load balancer a watcher
  connected to one would not be woken by a change applied to another. Single copy is the case
  this was built and checked for.
- **What the board looks like.** planka's own screen is not shipped here: it sends every one of
  its requests down a channel this port does not speak, and it loads accounts, projects, and
  memberships before it draws anything. What is here instead is a small page showing the same
  columns and cards in the same order. Side by side, the two agree on every column and card and
  differ everywhere else — the comparison is in `planka-port/gui/`.
- **The name a card or column is given.** planka numbers them; this port names them after the
  board they belong to followed by a random name. Nothing in either system reads meaning out of
  the name, so `not checked` whether anything downstream would notice.
- **What happens under load, or across a restart.** `not checked`. Neither system was measured
  with more than one thing happening at a time.

---

## Licence

plankanban/planka is under the PLANKA Community License v1.1, © PLANKA Software GmbH. This
port copies no source code and reproduces the behaviour of one part of it, which makes it a
derived work carrying that licence; see `ACKNOWLEDGEMENTS.md`.

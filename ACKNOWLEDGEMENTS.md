# Acknowledgements

This project is a port of **[plankanban/planka](https://github.com/plankanban/planka)**,
read at commit `266246e`.

## Licence and copyright

Copyright © PLANKA Software GmbH. planka is published under the **PLANKA Community License
v1.1**, whose permissive half is the **Fair Use License v1.1** (`LICENSE.md` in the source
repository, read rather than inferred from a badge). Files whose names or headers mark them
`.pe.` — PLANKA Pro/Enterprise — are excluded from that licence and require a commercial one;
nothing in this port derives from any such file, and none of the files cited below is one.

Three terms of that licence bear directly on this port and are stated here rather than
summarised away:

- **Permitted use** covers personal, hobby and educational use, and internal use within one
  organisation. This port is a study of how to write down what a system does; it is not
  offered as a hosted service, and running it as one for third parties for commercial gain is
  what that licence prohibits.
- **Notices** requires that anyone receiving a copy also receives the licence terms, and that
  a modified copy carries a prominent notice saying it has been modified. This file is that
  notice: **this is not planka. It is a rebuild of one slice of planka's behaviour on Akka,
  and it differs from planka — see the README's list.**
- **Trademark** permits the PLANKA name only to describe that a service incorporates the
  software. It is used here only to say what this port is a port of.

The repository this port ships in is private, and this file is why it stays that way until
somebody decides otherwise deliberately.

## Was anything copied?

**No source code was copied.** Not a line of JavaScript was transcribed into Java. What was
copied is *behaviour*: `Positioning.java` reproduces the control flow of
`server/api/helpers/utils/insert-to-positionables.js` step for step — `findBeginnings`, the
push-along loop with its midpoint branch, the full renumber, and the right-to-left handing out
of replacement positions — because the port exists to give the same answers. That is
derivation, and it is stated plainly rather than being coy about it.

`python toolkit/copied_strings.py planka --source planka-src` pulls every literal of ten
characters or more out of the rebuild and finds the ones that also occur in the clone. It
found **9 of 37**, and each is accounted for here:

| Literal | Why it is in both |
|---|---|
| `/boards/{boardId}` | An HTTP route. The port answers the source's own route shapes deliberately — `GET /api/boards/:id` in `server/config/routes.js:159` — so that a caller of one can be pointed at the other. Copying a route is copying an interface, which is the point of a port. |
| `/boards/{boardId}/lists` | Same, from `routes.js:171`. |
| `/lists/{listId}` | Same, from `routes.js:173`. |
| `/lists/{listId}/cards` | Same, from `routes.js:180`. |
| `/cards/{cardId}` | Same, from `routes.js:182`. |
| `list not found` | The port's entity refuses with this; the source's controllers answer `listNotFound: 'List not found'` (`controllers/cards/update.js:134`). The two are not the same string — different capitalisation — and the port never sends its text to a caller anyway: `BoardEndpoint.call` turns it into a 404 with no body. Both systems arrived at the obvious three words. |
| `card not found` | The same, against `cardNotFound: 'Card not found'` (`controllers/cards/update.js:128`). |
| `description` | A field name. planka's Card has a `description` column and so does this port's, because it is the same field. |
| `repositions` | The name the source's helper gives the half of its answer that says which siblings moved (`insert-to-positionables.js`, `return { position, repositions }`). The port's benchmark runner uses the same word so the two sides' answer files can be read against each other; renaming it would make the comparison harder to check, not more original. |

Nothing else in the rebuild occurs in the source.

## What licence this port carries

Because the port is a derivative work of software under the PLANKA Community License, that
licence's terms travel with it. This port is not offered under a more permissive one, and the
repository is private.

## Also used

- **Akka** (`akka-javasdk`) — the runtime the rebuild is built on.
- **lodash** — installed under `probes/source_positioning/node_modules/` so the source's own
  helper can be run unmodified. It expects the global `_` that Sails supplies, and supplying it
  is the whole of the adaptation.
- **Playwright** and **Docker** — used to run the original and capture the appearance
  baselines in `gui/baseline/`.

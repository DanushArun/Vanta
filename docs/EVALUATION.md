# Vanta — Evaluation guide

Start with the smallest path that exercises the project. Distinguish source inspection,
syntax/build checks, functional behavior and domain validation when recording a result.

## Guided reading and demonstration

1. **Supply a room.** Enter an authorized LiveKit server URL and valid session token. The
repository does not issue those tokens.

2. **Connect the client.** Join the room through the native LiveKit integration and handle
requested media permissions.

3. **Interact with the room.** Inspect media/session UI and the external assistant response. The
Gemini label does not establish a Gemini agent implementation in this checkout.

4. **Disconnect and repeat.** Review cleanup, reconnection and invalid-token behavior. A
successful connection is a narrower result than a reliable multi-turn assistant session.

## Declared checks

These commands/checks describe the intended verification path. Their presence in this
guide does not claim that they passed. See the dated evidence below and the README for setup.

```text
npm run lint
npm test -- --runInBand
```

## Evidence levels

| Level | What it establishes | What it does not establish |
| --- | --- | --- |
| Source review | A path exists in tracked code | Successful runtime behavior |
| Syntax/build | Parser/compiler accepts that path | End-to-end correctness |
| Behavioral check | A specific input/output case passed | Generalization beyond cases |
| Domain evaluation | Performance on a stated target setting | Other users/data/environments |

## What to record

- Commit, environment, dependency versions and date.
- Input provenance and whether data is synthetic, public or privately supplied.
- Absolute pass/fail/skip counts; keep failed cases and their root causes.
- Whether external services, hardware or a production deployment were actually exercised.
- Expected output and an artifact showing the observation.

## Review scenarios

- **Client/service split:** Room configuration is supplied rather than silently treated as a
bundled backend.

- **Native media integration:** Platform permissions and builds are required parts of evaluation.

- **Session tokens:** A room token has a defined authorization scope; it is not a replacement for
a token service.

## Documentation inspection — 7 October 2026

The documentation was traced to committed source and checked for local links, balanced
code fences and supported implementation claims. Historical notebook outputs remain labeled
as historical. Live provider access, private databases and hardware behavior are not inferred
from configuration or dependency files. Any fresh run is recorded separately in the README.

## Next evidence to collect

- Supply a controlled test room and token issuer.
- Record connection, denial, disconnect and reconnection cases.
- Measure a complete assistant conversation on both target platforms.

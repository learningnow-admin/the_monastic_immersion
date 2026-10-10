# The Monastic Immersion

*Not a book. Not a club. Bookclub.*

A static page listing each session's date and the things shared. Nothing to
log in to, nothing to vote on.

## Recording a session

Edit `sessions.md`. One `##` line per session (newest first), then one `-`
line per thing, written as `Title — who brought it`. Any other line under a
heading becomes a note (where it was, who hosted, a guest's name).

```md
## 18 September 2026
At Nina's.
- Scopa (game) — Cass
```

Push to `main` and the GitHub Action rebuilds and deploys. You can do this
from a phone: open `sessions.md` on GitHub, tap the pencil, commit.

## Local

```sh
npm install
npm run dev      # build + wrangler dev on http://localhost:8787
npm run deploy   # build + deploy by hand
```

`build.mjs` turns `sessions.md` into `public/index.html` (gitignored).
Hosting is a Cloudflare Worker serving static assets; no database.

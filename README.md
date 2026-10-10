# The Monastic Immersion

*Not a book. Not a club. Book club.*

A static page listing each session's date and the things shared. Nothing to
log in to, nothing to vote on.

## Recording a session

Edit `sessions.md`. One `##` line per session (newest first), then one `-`
line per thing, written as `Title (type)`. Any other line under a heading
becomes a note.

```md
## 18 September 2026
- Scopa (game)
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

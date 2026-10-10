# The Monastic Immersion

*Not a book. Not a club. Book club.*

A static page that lists each session's date and the things shared.
Live at https://tmi.solitary-lab-0d7b.workers.dev

The whole site is one text file, `sessions.md`. Edit it, push it, done.

## Adding a session

1. Open `sessions.md`.
2. Above the most recent session, add a heading with the date and one line
   per thing shared. Newest goes at the top.

   ```md
   ## 2 October 2026
   - Claire Keegan on Conversations, the last ten minutes (podcast)
   - Sheetali breathing (demonstration)
   - Frugal hedonism (book)
   ```

3. Commit and push to `main`:

   ```sh
   git add sessions.md
   git commit -m "Sessions: 2 October 2026"
   git push
   ```

   GitHub rebuilds and deploys the page within a minute or two.

### The format

- A line starting `## ` begins a session. Whatever follows is the heading,
  so write the date however you like.
- A line starting `- ` is one thing shared. Put the kind in brackets at the
  end if you want it: `(book)`, `(poem)`, `(video)`, `(game)`.
- Any other line under a heading becomes a small italic note.
- A `- ` line can end in ` — Name` to credit someone. Names are left off at
  the moment.

### From a phone

Open `sessions.md` on GitHub, tap the pencil, edit, and tap Commit changes.
The same deploy runs.

## Fixing a mistake

Edit the line in `sessions.md`, commit, push. There is nothing else to
update.

## Changing the title or look

Both live in `build.mjs`. The title and subtitle are near the bottom in the
HTML template, the colours and fonts in the `<style>` block above them.

## Running it locally

Only needed to preview before pushing.

```sh
npm install
npm run dev       # builds and serves on http://localhost:8787
npm run deploy    # builds and deploys by hand, skipping GitHub
```

`build.mjs` turns `sessions.md` into `public/index.html`, which is
gitignored. Hosting is a Cloudflare Worker serving that one file as a
static asset. There is no database and no login.

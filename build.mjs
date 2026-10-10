// Renders sessions.md into public/index.html. No dependencies.
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";

const esc = (s) =>
  s.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");

const sessions = [];
let current = null;
for (const raw of readFileSync("sessions.md", "utf8").split("\n")) {
  const line = raw.trim();
  if (line.startsWith("## ")) {
    current = { heading: line.slice(3).trim(), notes: [], items: [] };
    sessions.push(current);
  } else if (!current || line === "" || line.startsWith("# ")) {
    continue;
  } else if (line.startsWith("- ")) {
    const item = line.slice(2).trim();
    const i = item.lastIndexOf(" — ");
    current.items.push(
      i === -1
        ? { title: item, who: "" }
        : { title: item.slice(0, i).trim(), who: item.slice(i + 3).trim() }
    );
  } else {
    current.notes.push(line);
  }
}

const sessionHtml = sessions
  .map(
    (s) => `
    <section>
      <h2>${esc(s.heading)}</h2>
      ${s.notes.map((n) => `<p class="note">${esc(n)}</p>`).join("\n      ")}
      <ul>
        ${s.items
          .map(
            (a) =>
              `<li><span class="a-title">${esc(a.title)}</span>${
                a.who ? ` <span class="a-who">${esc(a.who)}</span>` : ""
              }</li>`
          )
          .join("\n        ")}
      </ul>
    </section>`
  )
  .join("\n");

const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>The Monastic Immersion</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Cardo:ital,wght@0,400;0,700;1,400&display=swap">
<link rel="icon" href="data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 32 32%22><text y=%2224%22 font-size=%2224%22>❧</text></svg>">
<style>
:root {
  --paper: #F4F1E8; --ink: #2E2A24; --rubric: #7E2A1B; --gilt: #8A6D2F;
  --rule: #D6D0C2; --faint: #857D6D;
}
@media (prefers-color-scheme: dark) {
  :root {
    --paper: #211E19; --ink: #D8D1C0; --rubric: #C05B3A; --gilt: #B99A55;
    --rule: #3A362E; --faint: #8A8375;
  }
}
* { box-sizing: border-box; margin: 0; }
html { -webkit-text-size-adjust: 100%; }
body {
  background: var(--paper); color: var(--ink);
  font-family: 'Cardo', Georgia, serif; font-size: 1.125rem; line-height: 1.55;
}
main { max-width: 33rem; margin: 0 auto; padding: 3.5rem 1.25rem 5rem; }
.hedera { text-align: center; color: var(--rubric); font-size: 1.1rem; letter-spacing: .5em; }
h1 {
  text-align: center; font-weight: 400; font-size: 1.55rem;
  letter-spacing: .28em; text-transform: uppercase; margin-top: 1.1rem;
}
.subtitle { text-align: center; color: var(--faint); font-style: italic; margin-top: .4rem; }
section { margin-top: 3rem; }
h2 {
  color: var(--rubric); font-weight: 400; font-size: .8rem; letter-spacing: .24em;
  text-transform: uppercase; border-bottom: 1px solid var(--rule); padding-bottom: .35rem;
}
.note { color: var(--faint); font-style: italic; font-size: .95rem; margin-top: .6rem; }
ul { list-style: none; padding: 0; margin-top: .8rem; }
li { padding: .3rem 0; }
li + li { border-top: 1px solid var(--rule); }
.a-title { font-style: italic; }
.a-who { color: var(--faint); }
.a-who::before { content: '— '; }
footer { margin-top: 4rem; text-align: center; color: var(--rubric); letter-spacing: .5em; }
</style>
</head>
<body>
<main>
  <div class="hedera" aria-hidden="true">❧</div>
  <h1>The Monastic Immersion</h1>
  <p class="subtitle">Not a book. Not a club. Bookclub.</p>
${sessionHtml}
  <footer aria-hidden="true">❧</footer>
</main>
</body>
</html>
`;

mkdirSync("public", { recursive: true });
writeFileSync("public/index.html", html);
console.log(`public/index.html: ${sessions.length} sessions`);

# ACME Salary Management – frontend

Angular 21 + Angular Material. Screens and behaviour are described in [`docs/ui-design.md`](../docs/ui-design.md).

## Run in development

Requires Node 24.22.3 (pinned in the repo's `.nvmrc`; with nvm, run `nvm use` in the repo). Start the backend first
(see the backend's `application.yml`; it listens on `PORT`, default 8080), then:

```bash
npm ci
npm start                                      # http://localhost:4200, /api is forwarded to localhost:8080
API_TARGET=http://localhost:8081 npm start     # if the backend runs on another port
```

`ng serve` forwards `/api` to the backend (`proxy.conf.mjs`), so the browser talks to one origin, as it will in
production where Spring Boot serves the built app.

## Build

```bash
npm run build      # output in dist/frontend/browser
```

## Dependencies

- `npm ci` for a fresh install: it installs exactly what `package-lock.json` lists.
- `npm install <package>` to add one. Use the npm that comes with the pinned Node (11.19.1), so the lockfile is always
  written by the same npm version. npm 11.5.1 (Node 24.6) crashed resolving this project's dependency tree
  (`reading 'edgesOut'`), and npm 12 writes the lockfile slightly differently.
- `npm audit` reports moderate advisories in `glob`/`minimatch` inside the Angular CLI (development tooling only).
  `npm audit --omit=dev` reports 0. The CLI's fix is in Angular 22.1; do not run `npm audit fix --force`, which would
  downgrade the CLI to version 16.

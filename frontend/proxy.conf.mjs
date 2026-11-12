// Dev only: `ng serve` forwards /api to the Spring Boot backend, so the browser sees one origin
// (as in production, where Spring Boot serves the built app). Override the target with API_TARGET,
// e.g. API_TARGET=http://localhost:8081 npm start
export default {
  '/api': {
    target: process.env.API_TARGET ?? 'http://localhost:8080',
    secure: false,
  },
};

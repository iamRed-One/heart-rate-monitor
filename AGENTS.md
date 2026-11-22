# Repository instructions

## Git commits

- Never add `Co-authored-by` trailers, assistant attribution or other AI-author trailers to commit messages. Use only the configured Git author.
- Commit or push only when requested. Keep `.env`, local agent folders and `docs/14-local-development.md` excluded from Git.

## Documentation and implementation

- Follow the agreed CSV-first scope and the project documents in `docs/`.
- Backend: Python/FastAPI and PostgreSQL. Frontend: React with Astryx.
- Use the existing local PostgreSQL image; do not pull a new image without a user request.
- Use a separate `_test` database for integration tests; never reset application data to run tests.

## Context7

Use Context7 for library, framework, SDK, API, CLI and cloud-service questions or implementations, including familiar tools. Start with `resolve-library-id` unless given an exact library ID, choose the relevant reputable match, then use `query-docs` scoped to one concept. Prefer Context7 over web search for library documentation. Use source-code lookup when available if docs do not answer exact API details. General programming concepts and changes independent of library/platform APIs do not require it.

# Job Finder

A personal job-hunt assistant built with **Java 21, Spring Boot 3.5 and Google ADK (Agent Development Kit)**.
Tell it what you did in plain English and it keeps your tracker up to date. It also writes
screening-question answers and outreach messages from your resume.

## The agent team

```
                assistant  (coordinator, talks to you)
      ┌──────────────┬───────────┴──────────┬──────────────────┐
 tracker tools   get_resume        answer_writer        outreach_writer
 (add / update /                   (screening answers,   (LinkedIn notes,
  list / follow-ups)                resume facts only)    emails, follow-ups)
```

- Tools are plain Java methods (`JobFinderTools`) that ADK exposes to Gemini through `@Schema` annotations.
- The specialist writers are wrapped with `AgentTool`, so the coordinator stays in charge of the conversation.
- The chat UI shows which agent called which tool for every reply.

## Run it locally

1. Get a free Gemini API key at https://aistudio.google.com/apikey
2. Put it in a `.env` file in the project folder (copy `.env.example`). It's git-ignored:
   ```
   GOOGLE_API_KEY=your-key-here
   ```
   A `GOOGLE_API_KEY` environment variable works too.
3. Start the app:
   ```bash
   mvn spring-boot:run
   ```
4. Open http://localhost:8080 and import your resume PDF on the **Resume** tab.

Data is stored in `./data` (an H2 file database), so it survives restarts.

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `GOOGLE_API_KEY` | (required) | Gemini API key (in `.env` or the environment) |
| `JOBFINDER_MODEL` | `gemini-3.5-flash-lite` | Model used by all agents. Flash-Lite has a much larger free daily quota than `gemini-3.8-flash` (20 requests/day free) |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | local H2 file | Point at PostgreSQL when deploying |
| `PORT` | `8080` | HTTP port |

## REST API

- `POST /api/chat` `{sessionId, message}`: talk to the agents
- `GET|POST /api/applications`, `PATCH|DELETE /api/applications/{id}`, `GET /api/applications/follow-ups`
- `GET|PUT /api/resume`, `POST /api/resume/pdf`

## Roadmap

- Daily job-finder agent (search startup boards and rank roles against the resume)
- Telegram bot for updates on the go
- Docker + Render + Neon Postgres deployment

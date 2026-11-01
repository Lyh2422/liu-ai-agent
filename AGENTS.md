# Project startup preference

- When asked to start the project frontend, start the current source frontend in `liu-ai-agent-frontend` with Vite on port `5173`.
- Do not treat the Docker/Nginx frontend on port `8080` as the default frontend; it is an older built copy.
- After startup, verify and open `http://127.0.0.1:5173` for the user.
- The existing Docker backend can be reached through `http://127.0.0.1:8080/api` when a separately exposed local backend on port `8123` is not running.

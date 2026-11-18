# Config Server templates

These files are **not** loaded by the SemanticBase application. They are templates you copy
into the git repo backing your Spring Cloud Config Server.

## How it wires together

1. Config server runs at `http://localhost:8888` (configurable via `CONFIG_SERVER_URL`).
2. The server reads YAML from a git repo (typical setup) keyed by application name + profile.
3. SemanticBase boots, sees `spring.config.import=configserver:...`, and pulls
   `semanticbase.yml` + `semanticbase-{activeProfile}.yml` from the server.
4. Architectural config in `application.yml` is merged with values from the server.
   Server values win on conflict.

## Files

- [semanticbase.yml](semanticbase.yml) — defaults applied to every profile
- [semanticbase-local.yml](semanticbase-local.yml) — overrides for the `local` profile

## Secrets

The templates reference `${SPRING_DATASOURCE_PASSWORD}` and `${LLM_API_KEY}` / `${EMBEDDING_API_KEY}` env vars
rather than embedding the values in the config repo. Spring resolves these placeholders
on the **client** side (the SemanticBase JVM), not on the config server, so the
config repo stays free of credentials.

For production, replace env-var resolution with a Vault-backed config server profile
(`spring-cloud-starter-vault-config`) so secrets live in Vault and are fetched at startup.

## Smoke test

After dropping these files into your config repo and pointing the config server at it:

```powershell
curl.exe http://localhost:8888/semanticbase/local
```

Should return JSON with the merged config tree.

# Module Test Map — Auto-Gerado

Gerado pela auto-detecção do Vetor (runtime: **unknown**).
Revise os comandos: eles são executados de forma headless pelo `fix-loop-agent` e pelo `worktree-ship`.

---

## Comandos por módulo

| Módulo | Comando headless | Notas |
|--------|------------------|-------|
| `backend` | `cd backend/cafey-backend && ./gradlew test` | Auto-detectado |
| `apps` | `cd apps && ./gradlew build` | KMP (Android + Desktop + Web). Criado pelo APP-01 (#60). |

## Detecção de módulo por arquivos alterados

| Prefixo do path | Módulo |
|-----------------|--------|
| `backend/` | `backend` |
| `apps/` | `apps` |

## Regras de execução

### Exclusões obrigatórias
Todo `find`/`grep` executado pelas skills deve excluir:
`.claude/worktrees/*`, `node_modules/`, `target/`, `build/`, `dist/`, `.venv/`, `__pycache__/`.

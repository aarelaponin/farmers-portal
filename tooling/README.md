# `tooling/` — Python utilities

Self-contained Python venv for project-side scripting. Currently houses:

- `seed.py` — fixture seeder for the `mm_*` test data; reads YAML from
  `../app/seeds/`. See `../app/seeds/README.md` for usage.

## One-time setup

```sh
bash tooling/bootstrap.sh
```

This creates `tooling/.venv/`, upgrades pip, and installs the dependencies
from `requirements.txt`. Re-running is safe.

## Running scripts

Two equivalent ways:

**Activate the venv** (preferred for interactive work):
```sh
source tooling/.venv/bin/activate
python tooling/seed.py
deactivate                      # when done
```

**Run via the venv interpreter directly** (preferred in scripts / CI):
```sh
tooling/.venv/bin/python tooling/seed.py
```

## Adding a new tool

1. Drop the `.py` file into `tooling/`.
2. If it needs new packages, add them to `requirements.txt` and re-run
   `bash tooling/bootstrap.sh`.
3. Document it in this README.

## What lives where

| Path                        | Contents                                       |
|-----------------------------|------------------------------------------------|
| `tooling/seed.py`          | The seeder script (Python).                    |
| `tooling/requirements.txt` | Python dependencies.                           |
| `tooling/bootstrap.sh`     | Creates the venv, installs deps.               |
| `tooling/.venv/`           | The virtualenv itself. Git-ignored.            |
| `app/seeds/*.yaml`             | Fixture data (consumed by `seed.py`).          |
| `app/seeds/README.md`          | Operational guide for the seeder.              |

## Configuration

The seeder reads these env vars (defaults shown):

| Variable          | Default                                   |
|-------------------|-------------------------------------------|
| `JOGET_BASE_URL`  | `http://20.87.213.78:8080/jw`             |
| `JOGET_API_ID`    | (the dev API id from `lst-credentials.txt`) |
| `JOGET_API_KEY`   | (the dev API key)                         |
| `JOGET_APP_ID`    | `farmersPortal`                           |

To point the seeder at a different Joget instance, prefix the run:
```sh
JOGET_BASE_URL=http://localhost:8080/jw tooling/.venv/bin/python tooling/seed.py
```

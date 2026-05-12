# Farmers Portal — regression test entry point.
#
# Run before pushing any change. Per `docs/architecture/test_strategy.md`:
#
#   make test         layers 1+2 — foundational baseline (~1 min)
#   make test-perf    layer 4    — perf baseline (read-side, no writes)
#   make test-l4      L4 parity  — eligibility regression (existing)
#   make test-im      L3 e2e     — IM end-to-end (existing)
#   make test-all     everything above
#
# Tests assume the live dev DB is reachable. Override connection details
# via env vars (PGHOST etc.) if running against a different environment.

PYTHON ?= python3
PYTEST ?= $(PYTHON) -m pytest

.PHONY: help test test-baseline test-perf test-l4 test-im test-all clean

help:
	@echo "Farmers Portal regression suite"
	@echo ""
	@echo "  make test          baseline (layers 1+2): userview + MD + datalist + API + form save/load"
	@echo "  make test-perf     perf baseline (read-side timing)"
	@echo "  make test-l4       L4 parity (eligibility regression)"
	@echo "  make test-im       IM end-to-end smoke"
	@echo "  make test-all      everything above"
	@echo ""
	@echo "  make clean         remove .pytest_cache"

# ---------------------------------------------------------------------------
# Baseline: Layer 1 + Layer 2 from test_strategy.md
# ---------------------------------------------------------------------------
test: test-baseline

test-baseline:
	@echo "==> Layer 1+2 — userview / MD / datalist / API / form save-load"
	$(PYTEST) tooling/tests/ -v --tb=short

# ---------------------------------------------------------------------------
# Performance baseline (read-side, ~1 minute)
# ---------------------------------------------------------------------------
test-perf:
	@echo "==> Layer 4 — performance baseline (read-side)"
	$(PYTHON) tooling/load_test.py reports --count 30 --workers 6

# ---------------------------------------------------------------------------
# L4 parity — the eligibility regression gate
# ---------------------------------------------------------------------------
test-l4:
	@echo "==> L4 — eligibility 20-scenario parity"
	$(PYTHON) tooling/run_l4_scenarios.py

# ---------------------------------------------------------------------------
# IM end-to-end
# ---------------------------------------------------------------------------
test-im:
	@echo "==> IM e2e — application → voucher → redemption → receipt + report smoke"
	$(PYTHON) tooling/test_im_e2e.py

# ---------------------------------------------------------------------------
# Everything
# ---------------------------------------------------------------------------
test-all: test-baseline test-perf test-l4 test-im
	@echo ""
	@echo "==> All tests passed."

clean:
	rm -rf .pytest_cache tooling/tests/__pycache__ tooling/tests/.pytest_cache

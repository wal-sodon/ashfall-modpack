"""Relay Breaker side, forced closed: the lever up and green lamps."""
import importlib.util
from pathlib import Path

OWNER = "arms"

_spec = importlib.util.spec_from_file_location("ashfall_block_relay_breaker_base", Path(__file__).with_name("relay_breaker_side_tripped.py"))
_base = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_base)


def draw():
	return _base.draw(tripped=False)

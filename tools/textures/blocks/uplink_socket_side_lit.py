"""Uplink Socket side, drawing power: the conduit glows Resonance violet."""
from _finale import CRYSTAL
import importlib.util
from pathlib import Path

OWNER = "arms"

_spec = importlib.util.spec_from_file_location("ashfall_block_uplink_socket_side_base", Path(__file__).with_name("uplink_socket_side.py"))
_base = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_base)


def draw():
	s = _base.draw(lit=True, ramp=[CRYSTAL[0], CRYSTAL[1], CRYSTAL[2], CRYSTAL[4]])
	s.set(7, 3, CRYSTAL[0])
	s.set(8, 9, CRYSTAL[0])
	return s

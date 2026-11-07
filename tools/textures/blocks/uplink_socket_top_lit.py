"""Uplink Socket top with the Uplink seated: the crystal ring blazes and the port shows the Uplink's lit core."""
from _finale import CRYSTAL
import importlib.util
from pathlib import Path

OWNER = "arms"

_spec = importlib.util.spec_from_file_location("ashfall_block_uplink_socket_top_base", Path(__file__).with_name("uplink_socket_top.py"))
_base = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_base)


def draw():
	s = _base.draw(ring=[CRYSTAL[0], CRYSTAL[2]], core=CRYSTAL[3])
	s.set(7, 7, CRYSTAL[0])
	s.set(8, 8, CRYSTAL[1])
	return s

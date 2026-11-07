"""Small Cell Pack icon: the compact member of the cell pack family (see cell_pack_standard.pack)."""
import importlib.util
from pathlib import Path

OWNER = "arms"

_spec = importlib.util.spec_from_file_location("ashfall_cell_pack_family", Path(__file__).with_name("cell_pack_standard.py"))
_family = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_family)


def draw():
	return _family.pack(5, 11, 5, 13, cap=1, window=2, terminals=(7,), lit=2)

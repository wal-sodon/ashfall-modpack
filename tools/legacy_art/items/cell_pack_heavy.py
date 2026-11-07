"""Heavy Cell Pack icon: the big member of the cell pack family, with copper rails and a carry bail."""
import importlib.util
from pathlib import Path

OWNER = "arms"

_spec = importlib.util.spec_from_file_location("ashfall_cell_pack_family", Path(__file__).with_name("cell_pack_standard.py"))
_family = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_family)


def draw():
	return _family.pack(3, 13, 3, 14, cap=2, window=5, handle=True, ribs=True, lit=3)

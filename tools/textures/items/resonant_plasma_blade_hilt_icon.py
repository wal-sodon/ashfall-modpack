"""Resonant Plasma Blade icon, dark: the bare hilt (identical to the plain blade's; the beam is what differs)."""
import blade_art

OWNER = "arms"


def draw():
	return blade_art.draw(False, blade_art.RESONANT_BEAM)

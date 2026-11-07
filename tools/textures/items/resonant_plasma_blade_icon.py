"""Resonant Plasma Blade icon, lit (Bind ending): the same hilt with the bound Heart's cyan-and-gold beam."""
import blade_art

OWNER = "arms"


def draw():
	return blade_art.draw(True, blade_art.RESONANT_BEAM)

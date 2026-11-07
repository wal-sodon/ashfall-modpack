"""Ashfall armour art: the four piece silhouettes for item icons and the worn 64x32 layers, recoloured per set.

Item icons (tools/textures/items/<set>_<piece>.py) call ``icon(set, piece)``. The worn layers are written by
tools/gen_armor_layers.py, which paints only the pixels a vanilla armour layer uses (the UV layout), so the
shapes are ours and only the texture map's geometry is shared with the game.
"""
from sprite import Sprite, rgb, OUTLINE, CLEAR

# Per set: H highlight, L light, B base, D shadow, A accent, a accent shadow, V visor/lens.
SETS = {
	# patched brown canvas, grey tape, rusty buckles
	"scavenger": {"H": "c9a77a", "L": "a8845a", "B": "86663f", "D": "5c452b", "A": "b9bcc0", "a": "7d8187", "V": "3a3f46"},
	# safety yellow suit, black seals, cyan visor
	"hazmat": {"H": "fff0a0", "L": "f4d23c", "B": "d8ae1c", "D": "9a7612", "A": "3a3a40", "a": "1f1f24", "V": "6fd6e0"},
	# matte navy plates, black straps, smoked visor
	"riot": {"H": "8090b0", "L": "4f5d7c", "B": "35405a", "D": "222a3c", "A": "15181f", "a": "0b0d12", "V": "5a7a90"},
}

# Silhouettes: '.' clear; H L B D shading; A/a straps, tape or seals; V visor.
PIECES = {
	"helmet": [
		"................",
		"................",
		"....HHHHHHH.....",
		"...HLLLLLLLB....",
		"..HLLLLLLLLBD...",
		"..HLBBBBBBBBD...",
		"..HLVVVVVVVBD...",
		"..HBVVVVVVVBD...",
		"..HBVVVVVVVDD...",
		"..HBBAAAAABDD...",
		"..BDD.....DDD...",
		"..DD.......DD...",
		"................",
		"................",
		"................",
		"................",
	],
	"chest": [
		"................",
		"..HHH.....HHH...",
		".HLLLHHHHHLLLB..",
		".HLLLLLLLLLLLBD.",
		".HLLBLLLLLBLLBD.",
		".HLLBALLLABLLBD.",
		".HLBBBAAABBBBBD.",
		"..DLBBBBBBBBBD..",
		"...LBBBAABBBD...",
		"...LBBBBBBBBD...",
		"...LBBBAABBBD...",
		"...HBBBBBBBBD...",
		"...DAAAAAAAAD...",
		"...DDDDDDDDDD...",
		"................",
		"................",
	],
	"legs": [
		"................",
		"................",
		"...HHHHHHHHH....",
		"...HAAAAAAAAD...",
		"...HLLBBBBLLD...",
		"...HLLBDDBLLD...",
		"...HLBBD.HLBD...",
		"...HLBD..HLBD...",
		"...HLBD..HLBD...",
		"...HAAD..HAAD...",
		"...HLBD..HLBD...",
		"...HLBD..HLBD...",
		"...HBBD..HBBD...",
		"...DDDD..DDDD...",
		"................",
		"................",
	],
	"boots": [
		"................",
		"................",
		"................",
		"................",
		"................",
		"...HLB....HLB...",
		"...HLB....HLB...",
		"...HAB....HAB...",
		"...HLB....HLB...",
		"..HLLBD..HLLBD..",
		".HLLLBD.HLLLBD..",
		".AAAAaD.AAAAaD..",
		".DDDDDD.DDDDDD..",
		"................",
		"................",
		"................",
	],
}


def colours(set_name):
	pal = {k: rgb(v) for k, v in SETS[set_name].items()}
	pal["."] = CLEAR
	return pal


def icon(set_name, piece):
	"""A 16x16 item icon: the piece silhouette in the set's palette, with the usual dark outline."""
	pal = colours(set_name)
	s = Sprite()
	for y, row in enumerate(PIECES[piece]):
		for x, ch in enumerate(row):
			s.set(x, y, pal[ch])
	s.outline(OUTLINE)
	return s

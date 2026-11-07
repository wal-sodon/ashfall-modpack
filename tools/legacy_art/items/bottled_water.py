"""Bottled Water icon."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "salvage"

# Hand-placed pixel map; the outline is added afterwards by Sprite.outline.
MAP = [
	"................",
	"......bBBc......",
	"......BcBd......",
	"......PppQ......",
	".......PQ.......",
	"......PppQ......",
	".....PPppqQ.....",
	".....WSSSSv.....",
	".....Wwwwvv.....",
	".....LLLLll.....",
	".....LkkkKl.....",
	".....LLLLll.....",
	".....Wwwwvv.....",
	".....WwwvvV.....",
	"......wvvV......",
	"................",
]

KEY = {
	# blue cap: highlight, base, shadow, deep shadow
	"b": rgb("8cc8f0"), "B": rgb("3a7cc4"), "c": rgb("28589a"), "d": rgb("1f3f78"),
	# clear plastic (air above the water): highlight, base, shadow, deep shadow
	"P": rgb("e6ece6"), "p": rgb("b4c6cc"), "q": rgb("8aa0ae"), "Q": rgb("6c8094"),
	# water: highlight, surface, base, shadow, deep shadow
	"W": rgb("bfe6f2"), "S": rgb("94d4ee"), "w": rgb("62acd8"), "v": rgb("4282bc"), "V": rgb("32649e"),
	# worn off-white label band with a blue stripe
	"L": rgb("dcd6c2"), "l": rgb("aaa490"), "k": rgb("3a7cc4"), "K": rgb("28589a"),
}


def draw():
	"""Clear plastic water bottle with a blue cap, light-blue water and a label band."""
	s = Sprite()
	for y, row in enumerate(MAP):
		for x, ch in enumerate(row):
			if ch in KEY:
				s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	return s

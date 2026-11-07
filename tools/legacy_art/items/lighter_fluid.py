"""Lighter Fluid icon: worn yellow squeeze can with a red angled nozzle and a flame emblem on a dark label."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "salvage"

PALETTE = {
	".": CLEAR,
	"H": rgb("fbe9a2"),  # can highlight (warm cream)
	"L": rgb("f0c54a"),  # can light yellow
	"B": rgb("dc9a30"),  # can base (muted orange-yellow)
	"D": rgb("a8682e"),  # can shade
	"S": rgb("6e4036"),  # can shadow (cool plum-brown)
	"m": rgb("d6d4cc"),  # tin highlight
	"M": rgb("9496a2"),  # tin base
	"n": rgb("5a5c6e"),  # tin shadow (cool)
	"R": rgb("d0503c"),  # nozzle light
	"r": rgb("8a2c34"),  # nozzle shadow
	"K": rgb("474456"),  # label base (sooty blue-grey)
	"k": rgb("302d3c"),  # label shadow
	"F": rgb("ea6a2a"),  # flame emblem
	"f": rgb("ffd65e"),  # flame core
}

PIXELS = [
	"................",
	"................",
	"..........mR....",
	".........Rr.....",
	"........Rr......",
	".......mMn......",
	".....mmMMMn.....",
	"....mMMMMMMn....",
	"....HLBBBBDS....",
	"....HKKKFKkS....",
	"....HKKFfFkS....",
	"....HKFffFkS....",
	"....HKKFFKkS....",
	"....HLBBDBDS....",
	"....MmMMMMnn....",
	"................",
]


def draw():
	"""Slim yellow lighter-fluid can, tin shoulder and base, red spout, flame emblem on the front."""
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	s.outline(OUTLINE)
	return s

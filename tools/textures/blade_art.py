"""Shared drawing for the two Plasma Blade icons (lit and dark); lives beside sprite.py so gen_textures does not treat it as an icon."""
from sprite import Sprite, rgb, OUTLINE

# Hilt: gunmetal grip with a copper emitter collar; the beam takes the Resonance Crystal's violet.
GUN = [rgb("c3c7cf"), rgb("8f95a3"), rgb("646a7a"), rgb("424656")]
GRIP = [rgb("5a5450"), rgb("3e3a38"), rgb("2a2726")]
COPPER = [rgb("f6c98a"), rgb("d98f4c"), rgb("ad6630")]
BEAM = [rgb("fff3fa"), rgb("ffd2ee"), rgb("ee8ff0"), rgb("cf4fe6"), rgb("9a3ad8")]
# The Resonant blade (Bind ending): the bound Heart's colour, a cold cyan core edged in gold, so it never reads as
# the plain violet blade at a glance.
RESONANT_BEAM = [rgb("f4ffff"), rgb("c6fbff"), rgb("6fe6f2"), rgb("2bb7d4"), rgb("d9a43a")]


def diag(s, t0, t1, half, colour_at):
	"""Paints a band along the icon diagonal (bottom-left to top-right) from t0 to t1 pixels, half-width half."""
	for y in range(16):
		for x in range(16):
			u = ((x + 0.5) + (15.5 - (y + 0.5))) / 2 ** 0.5 / 1.0  # distance along the diagonal from the corner
			v = ((x + 0.5) - (15.5 - (y + 0.5))) / 2 ** 0.5         # signed distance across it
			if t0 <= u <= t1 and abs(v) <= half:
				c = colour_at(u, v)
				if c:
					s.set(x, y, c)


def hilt(s):
	# pommel, grip wrap, activation stud and emitter collar, bottom-left to the middle
	diag(s, 0.8, 2.8, 1.6, lambda u, v: GUN[2] if v > 0 else GUN[1])
	diag(s, 2.8, 7.4, 1.35, lambda u, v: GRIP[0] if int(u * 1.4) % 2 else GRIP[1 if v > 0 else 0])
	diag(s, 5.0, 5.9, 1.8, lambda u, v: COPPER[0] if v < 0 else COPPER[1])
	diag(s, 7.4, 9.8, 1.8, lambda u, v: GUN[0] if v < -0.6 else GUN[1] if v < 0.5 else GUN[3])


def beam(s, palette=BEAM):
	# a bright core inside a violet glow, tapering to a rounded tip
	diag(s, 9.8, 21.0, 1.3, lambda u, v: palette[3] if abs(v) > 0.75 else palette[1] if abs(v) > 0.3 else palette[0])
	diag(s, 9.6, 21.0, 1.9, lambda u, v: None)


def draw(lit, palette=BEAM):
	s = Sprite()
	if lit:
		beam(s, palette)
	hilt(s)
	s.outline(OUTLINE)
	if lit:
		# soften the beam's outline into the glow colour so it reads as light, not a solid bar
		for y in range(16):
			for x in range(16):
				if s.px[y][x] == OUTLINE and x + (15 - y) > 18:
					s.set(x, y, palette[4])
	return s

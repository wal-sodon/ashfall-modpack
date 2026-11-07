"""Dampener icon: a baffled cylindrical suppressor-style attachment wrapped in cloth and duct tape.

Drawn procedurally as a cylinder lying on the usual vanilla diagonal (rear collar bottom-left, muzzle
top-right). Each pixel is placed by its position along the axis (which band: collar, baffle rings,
cloth wrap, tape, muzzle cap) and across it (light from the top-left, shadow bottom-right).
"""
import math

from sprite import Sprite, rgb, OUTLINE

OWNER = "arms"

STEEL = [rgb("c3c7cf"), rgb("8f95a3"), rgb("646a7a"), rgb("424656")]  # gunmetal
CLOTH = [rgb("c9bd8a"), rgb("9c9060"), rgb("6f6640"), rgb("48412c")]  # khaki rag, warm hi -> cool shadow
TAPE = [rgb("eceae2"), rgb("c4c4bc"), rgb("8e8f8c")]  # silver duct tape

START = (3.0, 13.0)  # rear end centre (pixel coordinates)
END = (13.0, 3.0)    # muzzle end centre
RADIUS = 3.3


def band(t, length):
	"""Material for a point t pixels along the axis (0 = rear)."""
	if t < 1.5:
		return "collar"
	if t < 2.4:
		return "ring"
	if t > length - 1.5:
		return "cap"
	if t > length - 2.4:
		return "ring"
	if 4.6 < t < 6.2 or 8.4 < t < 10.0:
		return "tape"
	return "cloth"


def draw():
	s = Sprite()
	ax, ay = END[0] - START[0], END[1] - START[1]
	length = math.hypot(ax, ay)
	ux, uy = ax / length, ay / length
	nx, ny = -uy, ux  # perpendicular, pointing down-right (the shadow side)
	if nx < 0:
		nx, ny = -nx, -ny
	for y in range(16):
		for x in range(16):
			px, py = x + 0.5 - START[0], y + 0.5 - START[1]
			t = px * ux + py * uy
			d = px * nx + py * ny  # <0 lit side (top-left), >0 shadow side
			if not (-RADIUS <= d <= RADIUS and -0.2 <= t <= length + 0.2):
				continue
			k = (d + RADIUS) / (2 * RADIUS)  # 0 lit edge .. 1 shadow edge
			tone = 0 if k < 0.22 else 1 if k < 0.5 else 2 if k < 0.8 else 3
			mat = band(t, length)
			if mat == "collar" and abs(d) > 2.3:
				continue  # the rear mount collar is narrower than the can
			if mat == "cloth":
				c = CLOTH[tone]
				# Spiral wrap seams: a darker line where each turn of rag overlaps the last.
				if abs(((t - 0.6 * d) % 3.6) - 1.8) < 0.35 and tone < 3:
					c = CLOTH[min(3, tone + 1)]
			elif mat == "tape":
				c = TAPE[min(2, tone)]
			elif mat == "ring":
				c = STEEL[min(3, tone + 1)]
			else:  # collar and cap: bare gunmetal
				c = STEEL[tone]
			s.set(x, y, c)
	s.outline(OUTLINE)
	return s

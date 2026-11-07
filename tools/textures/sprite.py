#!/usr/bin/env python3
"""Sprite drawing primitives for Ashfall item textures (stdlib only: zlib + struct).

Each sprite is drawn from a few primitives so the art lives in version control as code, can be
regenerated after a palette tweak, and needs no image editor. Output goes straight into the mod
resource folders (arms and director own different items in the shared "ashfall" namespace).

"""
import struct
import zlib
from pathlib import Path

W = H = 16
CLEAR = (0, 0, 0, 0)


def rgb(hex_str, a=255):
	hex_str = hex_str.lstrip("#")
	return (int(hex_str[0:2], 16), int(hex_str[2:4], 16), int(hex_str[4:6], 16), a)


class Sprite:
	"""An RGBA pixel grid; 16x16 for item icons, larger (e.g. 32x32, 64x64) for 3D model skins."""

	def __init__(self, w=W, h=H):
		self.w, self.h = w, h
		self.px = [[CLEAR] * w for _ in range(h)]

	def set(self, x, y, c):
		if 0 <= x < self.w and 0 <= y < self.h:
			self.px[y][x] = c

	def rect(self, x0, y0, x1, y1, c):
		"""Filled rectangle, inclusive corners."""
		for y in range(y0, y1 + 1):
			for x in range(x0, x1 + 1):
				self.set(x, y, c)

	def hline(self, x0, x1, y, c):
		self.rect(x0, y, x1, y, c)

	def vline(self, x, y0, y1, c):
		self.rect(x, y0, x, y1, c)

	def line(self, x0, y0, x1, y1, c):
		"""Bresenham line."""
		dx, dy = abs(x1 - x0), -abs(y1 - y0)
		sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
		err = dx + dy
		while True:
			self.set(x0, y0, c)
			if x0 == x1 and y0 == y1:
				return
			e2 = 2 * err
			if e2 >= dy:
				err += dy
				x0 += sx
			if e2 <= dx:
				err += dx
				y0 += sy

	def disc(self, cx, cy, r, c):
		for y in range(self.h):
			for x in range(self.w):
				if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
					self.set(x, y, c)

	def outline(self, c):
		"""Dark 1px outline around every opaque pixel, the usual vanilla item look."""
		src = [row[:] for row in self.px]
		for y in range(self.h):
			for x in range(self.w):
				if src[y][x][3] == 0 and any(
						0 <= x + dx < self.w and 0 <= y + dy < self.h and src[y + dy][x + dx][3] > 0
						for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
					self.px[y][x] = c

	def png(self):
		return self.png_sized(self.w, self.h)

	def png_sized(self, width, height):
		raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *p) for p in row) for row in self.px)

		def chunk(tag, data):
			return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

		return (b"\x89PNG\r\n\x1a\n"
				+ chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
				+ chunk(b"IDAT", zlib.compress(raw, 9))
				+ chunk(b"IEND", b""))


OUTLINE = rgb("1b1b22")

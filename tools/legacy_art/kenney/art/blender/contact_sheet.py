"""Contact sheet of 32x32 icons for review (numpy only; run with Blender's Python, no bpy needed).

Usage:
  /Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup -P tools/art/blender/contact_sheet.py -- \
      <out.png> [--cols 10] [--scale 6] <icon.png> [<icon.png> ...]

Each icon is shown nearest-neighbour scaled on a vanilla inventory-slot grey (#8b8b8b) and, below, on a
dark background, with a 1:1 copy in the top-right corner of the grey tile. Smaller icons (16x16) are
scaled up to 32 first; larger ones are point-sampled down.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
import numpy as np  # noqa: E402


def blend(reg, im):
	a = im[..., 3:4] / 255.0
	reg[..., :3] = (im[..., :3] * a + reg[..., :3] * (1 - a)).astype(np.uint8)


def main():
	args = common.script_args()
	cols, scale = 10, 6
	for flag in ("--cols", "--scale"):
		if flag in args:
			i = args.index(flag)
			val = int(args[i + 1])
			del args[i:i + 2]
			cols, scale = (val, scale) if flag == "--cols" else (cols, val)
	out, files = args[0], args[1:]
	t, pad = 32 * scale, 8
	cols = min(cols, len(files))
	rows = (len(files) + cols - 1) // cols
	sheet = np.zeros((rows * 2 * (t + pad) + pad, cols * (t + pad) + pad, 4), np.uint8)
	sheet[...] = (40, 40, 48, 255)
	for i, f in enumerate(files):
		im = common.read_png(f)
		if im.shape[0] < 32:
			k = 32 // im.shape[0]
			im = np.repeat(np.repeat(im, k, 0), k, 1)
		elif im.shape[0] > 32:
			k = im.shape[0] // 32
			im = im[::k, ::k][:32, :32]
		big = np.repeat(np.repeat(im, scale, 0), scale, 1)
		r, c = divmod(i, cols)
		x = pad + c * (t + pad)
		for j, bg in enumerate([(139, 139, 139), (40, 40, 48)]):
			y = pad + (2 * r + j) * (t + pad)
			reg = sheet[y:y + t, x:x + t]
			reg[..., :3] = bg
			blend(reg, big)
		y = pad + 2 * r * (t + pad) + 2
		blend(sheet[y:y + 32, x + t - 34:x + t - 2], im)
	common.write_png(out, sheet)
	print(f"wrote {out}")


if __name__ == "__main__":
	main()

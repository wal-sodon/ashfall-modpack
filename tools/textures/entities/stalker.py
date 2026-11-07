"""Stalker overlay: a shadowed, hooded head with pale pinprick eyes, dark rags over the shirt and long claws."""
from zombie_skin import *

OWNER = "director"
EYE = rgb("c8f0f0")
CLAW = rgb("b8b4a8")
CLAW_TIP = rgb("e8e4d8")


def draw():
	s = skin()
	# hood of shadow over the head
	fill(s, HEAD_TOP, SHADOW)
	fill(s, HEAD_BACK, SHADOW)
	rows(s, HEAD_RIGHT, 0, 5, SHADOW)
	rows(s, HEAD_LEFT, 0, 5, SHADOW)
	rows(s, HEAD_FRONT, 0, 2, SHADOW)
	at(s, HEAD_FRONT, 0, 3, SHADOW)
	at(s, HEAD_FRONT, 7, 3, SHADOW)
	# sunken face and pinprick eyes
	rows(s, HEAD_FRONT, 3, 4, GREY)
	at(s, HEAD_FRONT, 2, 4, EYE)
	at(s, HEAD_FRONT, 5, 4, EYE)
	# dark rags over the shirt
	rows(s, BODY_FRONT, 0, 11, GREY)
	rows(s, BODY_BACK, 0, 11, GREY)
	fill(s, BODY_RIGHT, SHADOW)
	fill(s, BODY_LEFT, SHADOW)
	spots(s, BODY_FRONT, 10, 13, [SHADOW, SHIRT_DEEP])
	spots(s, BODY_BACK, 10, 17, [SHADOW, SHIRT_DEEP])
	# long claws on the hands
	fill(s, ARM_BOTTOM, CLAW)
	for region in (ARM_FRONT, ARM_OUTER, ARM_INNER, ARM_BACK):
		rows(s, region, 11, 11, CLAW)
		at(s, region, 1, 11, CLAW_TIP)
	return s

class_name House
extends Node3D
## The palace: a 60 x 40 m building over two storeys with a walkable roof and a
## tower. Everything is generated from the tables below, so the layout stays
## readable and every surface gets real collision.
##
## Scale is deliberately grand — 7 m ceilings downstairs, 4 m wide openings and
## no door leaves anywhere — so a spider with a 2.6 m leg span reads as small in
## it and never has to squeeze through anything.

# --- footprint -------------------------------------------------------------
const W := 60.0          # extent along X
const D := 40.0          # extent along Z (the garden is at negative Z)
const WT := 0.40         # interior wall thickness
const XT := 0.70         # exterior wall thickness
const CH := 7.0          # ground floor clear height
const CH1 := 6.0         # first floor clear height
const ST := 0.50         # slab thickness
const F0 := 0.0
const F1 := CH + ST          # 7.50 — first floor walking surface
const ROOF := F1 + CH1 + ST  # 14.00 — roof terrace
const TOWER_TOP := ROOF + 9.0

const DOOR_W := 4.20     # every interior opening is an open arch
const DOOR_H := 5.20
const WIN_W := 3.00
const WIN_H := 4.60
const WIN_B := 1.30      # sill height

## The double-height void over the grand hall.
const HALL_VOID := Rect2(24.0, 24.0, 14.0, 14.0)
## Stairwells. Each one is the hole the flight below it rises through, so the
## opening and the flight are defined from the same numbers and cannot drift
## apart. Both ground-floor flights run the length of the gallery, which is the
## only room deep enough to give a staircase a sane pitch: 7.5 m of rise wants
## about 12 m of run, and trying to fit that into the grand hall was what left
## the old flights ending in mid-air over the void.
const STAIR_E := Rect2(43.0, 14.8, 14.5, 4.4)    # gallery -> upper gallery, east
const STAIR_W := Rect2(2.5, 14.8, 14.5, 4.4)     # gallery -> upper gallery, west
const STAIR_ROOF := Rect2(19.0, 15.0, 14.0, 4.0)  # upper gallery -> roof

var mats: Dictionary = {}
var rooms: Array[Dictionary] = []
## Where props.gd hangs chandeliers: {pos, ceiling, size}
var ceiling_anchors: Array[Dictionary] = []

var _trim_wall := false
const WAINSCOT_H := 1.60


func _ready() -> void:
	_load_materials()
	_build_ground_floor()
	_build_first_floor()
	_build_roof()
	_build_tower()
	_build_stairs()
	_build_columns()
	_build_fixtures()


func _load_materials() -> void:
	mats = {
		"marble": MaterialLib.surface("marble", 3.0, true, 0.7, Color(0.78, 0.76, 0.72)),
		"mosaic": MaterialLib.surface("mosaic", 2.0, true, 0.9, Color(0.72, 0.70, 0.68)),
		"parquet": MaterialLib.surface("parquet", 2.0, true, 1.2, Color(0.62, 0.56, 0.50)),
		"planks": MaterialLib.surface("planks", 2.2, true, 1.0, Color(0.55, 0.48, 0.41)),
		"sandstone": MaterialLib.surface("sandstone", 3.2, true, 1.0, Color(0.80, 0.76, 0.68)),
		"wall": MaterialLib.surface("sandstone", 2.6, true, 1.1, Color(0.80, 0.77, 0.70)),
		"wall_red": MaterialLib.surface("sandstone", 2.6, true, 1.1, Color(0.66, 0.45, 0.40)),
		"wall_green": MaterialLib.surface("sandstone", 2.6, true, 1.1, Color(0.50, 0.58, 0.50)),
		"wall_blue": MaterialLib.surface("sandstone", 2.6, true, 1.1, Color(0.52, 0.58, 0.68)),
		"ceiling": MaterialLib.surface("ceiling", 3.0, true, 0.7, Color(0.82, 0.80, 0.76)),
		"wood_dark": MaterialLib.surface("wood_dark", 1.3, true, 1.0, Color(0.44, 0.37, 0.31)),
		"carpet": MaterialLib.surface("carpet", 2.6, true, 1.4),
		"brick": MaterialLib.surface("brick", 2.0, true, 1.3, Color(0.62, 0.55, 0.50)),
		"tiles_bath": MaterialLib.surface("tiles_bath", 1.6, true, 0.9),
		"roof": MaterialLib.surface("roof_slate", 3.6, true, 0.9, Color(0.34, 0.32, 0.33)),
		"deck": MaterialLib.surface("sandstone", 4.0, true, 0.8, Color(0.52, 0.51, 0.49)),
		"gold": MaterialLib.plain(Color(0.72, 0.56, 0.24), 0.28, 0.85),
	}


# ---------------------------------------------------------------------------
# primitives
# ---------------------------------------------------------------------------

func _box(size: Vector3, pos: Vector3, mat: Material, collide := true,
		basis := Basis(), _shadows := true, occlude := false) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.transform = Transform3D(basis, pos)
	if mat:
		mi.material_override = mat
	# Nothing in the palace casts a shadow: shadow maps were the single biggest
	# cost on a phone, and without them the frame rate roughly doubles.
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(mi)
	if collide:
		var body := StaticBody3D.new()
		var cs := CollisionShape3D.new()
		var shape := BoxShape3D.new()
		shape.size = size
		cs.shape = shape
		body.add_child(cs)
		mi.add_child(body)
	# Big solid walls double as occluders. A palace this size would otherwise
	# draw every room at once; this lets the GPU skip whatever is behind a wall,
	# which is most of the building most of the time.
	if occlude and size.x > 2.0 and size.y > 2.0:
		var occ := OccluderInstance3D.new()
		var shape_o := BoxOccluder3D.new()
		shape_o.size = size
		occ.occluder = shape_o
		occ.transform = Transform3D(basis, pos)
		add_child(occ)
	return mi


func _slab(rect: Rect2, y_top: float, thickness: float, mat: Material,
		holes: Array = []) -> void:
	var pieces: Array[Rect2] = [rect]
	for hole in holes:
		var next: Array[Rect2] = []
		for p in pieces:
			next.append_array(_subtract(p, hole))
		pieces = next
	for p in pieces:
		if p.size.x <= 0.01 or p.size.y <= 0.01:
			continue
		_box(Vector3(p.size.x, thickness, p.size.y),
			Vector3(p.position.x + p.size.x * 0.5, y_top - thickness * 0.5,
				p.position.y + p.size.y * 0.5), mat)


func _subtract(outer: Rect2, hole: Rect2) -> Array[Rect2]:
	var clipped := outer.intersection(hole)
	if clipped.size.x <= 0.001 or clipped.size.y <= 0.001:
		return [outer] as Array[Rect2]
	var out: Array[Rect2] = []
	var x0 := outer.position.x
	var x1 := outer.end.x
	var z0 := outer.position.y
	var z1 := outer.end.y
	var hx0 := clipped.position.x
	var hx1 := clipped.end.x
	var hz0 := clipped.position.y
	var hz1 := clipped.end.y
	if hx0 > x0:
		out.append(Rect2(x0, z0, hx0 - x0, z1 - z0))
	if hx1 < x1:
		out.append(Rect2(hx1, z0, x1 - hx1, z1 - z0))
	if hz0 > z0:
		out.append(Rect2(hx0, z0, hx1 - hx0, hz0 - z0))
	if hz1 < z1:
		out.append(Rect2(hx0, hz1, hx1 - hx0, z1 - hz1))
	return out


func _wall(a: Vector2, b: Vector2, y_base: float, height: float, thickness: float,
		mat: Material, openings: Array = [], trim: bool = false) -> void:
	var delta := b - a
	var length := delta.length()
	if length <= 0.001:
		return
	var dirn := delta / length
	var theta := atan2(-dirn.y, dirn.x)
	var basis := Basis(Vector3.UP, theta)
	var origin := Vector3(a.x, y_base, a.y)
	_trim_wall = trim

	var ops := openings.duplicate()
	ops.sort_custom(func(p, q): return float(p.u0) < float(q.u0))

	var cursor := 0.0
	for op in ops:
		var u0: float = clampf(op.u0, 0.0, length)
		var u1: float = clampf(op.u1, 0.0, length)
		if u1 <= u0:
			continue
		var y0: float = y_base + float(op.get("y0", 0.0))
		var y1: float = y_base + float(op.get("y1", DOOR_H))
		if u0 > cursor:
			_wall_piece(basis, origin, cursor, u0, y_base, y_base + height, thickness, mat)
		if y0 > y_base + 0.001:
			_wall_piece(basis, origin, u0, u1, y_base, y0, thickness, mat)
		if y1 < y_base + height - 0.001:
			_wall_piece(basis, origin, u0, u1, y1, y_base + height, thickness, mat)
		_opening_trim(basis, origin, u0, u1, y0, y1, thickness,
			String(op.get("type", "door")))
		cursor = maxf(cursor, u1)
	if cursor < length:
		_wall_piece(basis, origin, cursor, length, y_base, y_base + height, thickness, mat)

	if trim:
		var crown := Vector3(length * 0.5, height - 0.18, 0.0)
		_box(Vector3(length, 0.36, thickness + 0.30), origin + basis * crown,
			mats["gold"], false, basis, false)
	_trim_wall = false


func _wall_piece(basis: Basis, origin: Vector3, u0: float, u1: float,
		y_lo: float, y_hi: float, thickness: float, mat: Material) -> void:
	var w := u1 - u0
	var h := y_hi - y_lo
	if w <= 0.005 or h <= 0.005:
		return
	var local := Vector3((u0 + u1) * 0.5, (y_lo + y_hi) * 0.5 - origin.y, 0.0)
	_box(Vector3(w, h, thickness), origin + basis * local, mat, true, basis, true, true)

	if _trim_wall and absf(y_lo - origin.y) < 0.01 and h > 2.5:
		var panel := Vector3((u0 + u1) * 0.5, WAINSCOT_H * 0.5, 0.0)
		_box(Vector3(w, WAINSCOT_H, thickness + 0.10), origin + basis * panel,
			mats["wood_dark"], false, basis, false)
		var rail := Vector3((u0 + u1) * 0.5, WAINSCOT_H, 0.0)
		_box(Vector3(w, 0.14, thickness + 0.26), origin + basis * rail,
			mats["gold"], false, basis, false)


## Jambs, a stepped arch head, and glass for windows.
func _opening_trim(basis: Basis, origin: Vector3, u0: float, u1: float,
		y0: float, y1: float, thickness: float, kind: String) -> void:
	var stone: Material = mats["sandstone"]
	var t := thickness + 0.12
	for u_v in [u0, u1]:
		var u: float = u_v
		var local := Vector3(u, (y0 + y1) * 0.5 - origin.y, 0.0)
		_box(Vector3(0.24, y1 - y0, t), origin + basis * local, stone, false, basis, false)

	# stepped arch: three courses of stone corbelling inwards
	var span := u1 - u0
	for i in range(3):
		var f := float(i + 1) / 4.0
		var wide := span * (1.0 - f * 0.55) + 0.3
		var head := Vector3((u0 + u1) * 0.5, y1 - origin.y + 0.18 + i * 0.30, 0.0)
		_box(Vector3(wide, 0.30, t + 0.06), origin + basis * head, stone, false, basis, false)

	if kind == "window":
		var sill := Vector3((u0 + u1) * 0.5, y0 - origin.y, 0.0)
		_box(Vector3(span + 0.6, 0.22, thickness + 0.5),
			origin + basis * sill, stone, true, basis, false)
		# No glass: every window is an open hole, so the spider can go straight
		# through one and out onto the wall outside.
		var g := Vector3((u0 + u1) * 0.5, (y0 + y1) * 0.5 - origin.y, 0.0)
		for i in range(3):
			var mx := (u0 + u1) * 0.5 + (i - 1) * span * 0.30
			_box(Vector3(0.09, y1 - y0, 0.10),
				origin + basis * Vector3(mx, (y0 + y1) * 0.5 - origin.y, 0.0),
				mats["gold"], false, basis, false)
		for i in range(3):
			var my := y0 - origin.y + (y1 - y0) * (0.25 + 0.25 * i)
			_box(Vector3(span, 0.09, 0.10),
				origin + basis * Vector3((u0 + u1) * 0.5, my, 0.0),
				mats["gold"], false, basis, false)


func _register_room(name: String, rect: Rect2, level: float, floor_key: String) -> void:
	rooms.append({"name": name, "rect": rect, "level": level, "floor": floor_key})


func _anchor(pos: Vector3, ceiling: float, size: float) -> void:
	ceiling_anchors.append({"pos": pos, "ceiling": ceiling, "size": size})


# ---------------------------------------------------------------------------
# ground floor
# ---------------------------------------------------------------------------

func _build_ground_floor() -> void:
	var y := F0
	_slab(Rect2(2.0, 20.0, 22.0, 18.0), y, ST, mats["parquet"])      # ballroom
	_slab(Rect2(24.0, 20.0, 14.0, 18.0), y, ST, mats["mosaic"])      # grand hall
	_slab(Rect2(38.0, 20.0, 20.0, 18.0), y, ST, mats["marble"])      # throne room
	_slab(Rect2(2.0, 14.0, 56.0, 6.0), y, ST, mats["marble"])        # gallery
	_slab(Rect2(2.0, 2.0, 20.0, 12.0), y, ST, mats["parquet"])       # library
	_slab(Rect2(24.0, 2.0, 16.0, 12.0), y, ST, mats["marble"])       # dining hall
	_slab(Rect2(42.0, 2.0, 16.0, 12.0), y, ST, mats["tiles_bath"])   # kitchen

	_register_room("ballroom", Rect2(2.0, 20.0, 22.0, 18.0), y, "parquet")
	_register_room("grand_hall", Rect2(24.0, 20.0, 14.0, 18.0), y, "mosaic")
	_register_room("throne", Rect2(38.0, 20.0, 20.0, 18.0), y, "marble")
	_register_room("gallery", Rect2(2.0, 14.0, 56.0, 6.0), y, "marble")
	_register_room("library", Rect2(2.0, 2.0, 20.0, 12.0), y, "parquet")
	_register_room("dining", Rect2(24.0, 2.0, 16.0, 12.0), y, "marble")
	_register_room("kitchen", Rect2(42.0, 2.0, 16.0, 12.0), y, "tiles_bath")

	# ceiling of the ground floor, minus the double-height grand hall and the
	# stairwell opening into the upper gallery
	_slab(Rect2(0.0, 0.0, W, D), F1, ST, mats["ceiling"],
		[HALL_VOID, STAIR_E, STAIR_W])

	_exterior_ring(y, CH, {
		# south wall (z = 0) faces the garden
		"south": [
			{"u0": 8.0, "u1": 8.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 15.0, "u1": 15.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 26.0, "u1": 34.0, "y0": 0.0, "y1": 6.2},
			{"u0": 42.0, "u1": 42.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 49.0, "u1": 49.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
		],
		"east": [
			{"u0": 5.0, "u1": 5.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 16.0, "u1": 16.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 27.0, "u1": 27.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
		],
		# north wall (z = D) is the front: the state entrance
		"north": [
			{"u0": 10.0, "u1": 10.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 25.0, "u1": 33.0, "y0": 0.0, "y1": 6.4},
			{"u0": 46.0, "u1": 46.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
		],
		"west": [
			{"u0": 8.0, "u1": 8.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 20.0, "u1": 20.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 31.0, "u1": 31.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
		],
	})

	var wall: Material = mats["wall"]
	_wall(Vector2(2.0, 20.0), Vector2(W - 2.0, 20.0), y, CH, WT, wall, [
		{"u0": 8.0, "u1": 8.0 + DOOR_W, "y1": DOOR_H},
		{"u0": 20.0, "u1": 28.0, "y1": DOOR_H + 0.8},
		{"u0": 40.0, "u1": 40.0 + DOOR_W, "y1": DOOR_H},
	], true)
	_wall(Vector2(2.0, 14.0), Vector2(W - 2.0, 14.0), y, CH, WT, wall, [
		{"u0": 9.0, "u1": 9.0 + DOOR_W, "y1": DOOR_H},
		{"u0": 26.0, "u1": 26.0 + DOOR_W, "y1": DOOR_H},
		{"u0": 44.0, "u1": 44.0 + DOOR_W, "y1": DOOR_H},
	], true)
	_wall(Vector2(24.0, 20.0), Vector2(24.0, D - 2.0), y, CH, WT, mats["wall_red"], [
		{"u0": 6.0, "u1": 6.0 + DOOR_W, "y1": DOOR_H},
	])
	_wall(Vector2(38.0, 20.0), Vector2(38.0, D - 2.0), y, CH, WT, mats["wall_red"], [
		{"u0": 6.0, "u1": 6.0 + DOOR_W, "y1": DOOR_H},
	])
	_wall(Vector2(22.0, 2.0), Vector2(22.0, 14.0), y, CH, WT, mats["wall_green"], [
		{"u0": 4.0, "u1": 4.0 + DOOR_W, "y1": DOOR_H},
	])
	_wall(Vector2(40.0, 2.0), Vector2(40.0, 14.0), y, CH, WT, mats["wall_green"], [
		{"u0": 4.0, "u1": 4.0 + DOOR_W, "y1": DOOR_H},
	])

	_anchor(Vector3(13.0, CH, 29.0), CH, 1.6)
	_anchor(Vector3(31.0, F1 + CH1, 31.0), F1 + CH1, 2.2)
	_anchor(Vector3(48.0, CH, 29.0), CH, 1.8)
	_anchor(Vector3(14.0, CH, 17.0), CH, 1.2)
	_anchor(Vector3(30.0, CH, 17.0), CH, 1.2)
	_anchor(Vector3(46.0, CH, 17.0), CH, 1.2)
	_anchor(Vector3(12.0, CH, 8.0), CH, 1.4)
	_anchor(Vector3(32.0, CH, 8.0), CH, 1.6)
	_anchor(Vector3(50.0, CH, 8.0), CH, 1.2)


func _exterior_ring(y: float, height: float, by_wall: Dictionary) -> void:
	var mat: Material = mats["sandstone"]
	var corners := {
		"south": [Vector2(0.0, 0.0), Vector2(W, 0.0)],
		"east": [Vector2(W, 0.0), Vector2(W, D)],
		"north": [Vector2(W, D), Vector2(0.0, D)],
		"west": [Vector2(0.0, D), Vector2(0.0, 0.0)],
	}
	for key in corners:
		var pts: Array = corners[key]
		_wall(pts[0], pts[1], y, height, XT, mat, by_wall.get(key, []))


# ---------------------------------------------------------------------------
# first floor
# ---------------------------------------------------------------------------

func _build_first_floor() -> void:
	var y := F1
	_slab(Rect2(2.0, 22.0, 22.0, 16.0), y, 0.03, mats["carpet"])
	_slab(Rect2(38.0, 22.0, 20.0, 16.0), y, 0.03, mats["parquet"])
	_slab(Rect2(2.0, 14.0, 56.0, 8.0), y, 0.03, mats["marble"], [STAIR_E, STAIR_W, STAIR_ROOF])
	_slab(Rect2(2.0, 2.0, 20.0, 12.0), y, 0.03, mats["parquet"])
	_slab(Rect2(24.0, 2.0, 34.0, 12.0), y, 0.03, mats["planks"])

	_register_room("state_bedroom", Rect2(2.0, 22.0, 22.0, 16.0), y, "carpet")
	_register_room("music", Rect2(38.0, 22.0, 20.0, 16.0), y, "parquet")
	_register_room("upper_gallery", Rect2(2.0, 14.0, 56.0, 8.0), y, "marble")
	_register_room("study", Rect2(2.0, 2.0, 20.0, 12.0), y, "parquet")
	_register_room("guest_hall", Rect2(24.0, 2.0, 34.0, 12.0), y, "planks")

	# The hall void is cut from the first floor only. Cutting it from the roof
	# as well left the grand hall open to the sky.
	_slab(Rect2(0.0, 0.0, W, D), ROOF, ST, mats["ceiling"], [STAIR_ROOF])

	_exterior_ring(y, CH1, {
		"south": [
			{"u0": 10.0, "u1": 13.0, "y0": 1.0, "y1": 4.8, "type": "window"},
			{"u0": 28.0, "u1": 32.0, "y0": 1.0, "y1": 4.8, "type": "window"},
			{"u0": 47.0, "u1": 50.0, "y0": 1.0, "y1": 4.8, "type": "window"},
		],
		"east": [
			{"u0": 8.0, "u1": 11.0, "y0": 1.0, "y1": 4.8, "type": "window"},
			{"u0": 28.0, "u1": 31.0, "y0": 1.0, "y1": 4.8, "type": "window"},
		],
		"north": [
			{"u0": 12.0, "u1": 15.0, "y0": 1.0, "y1": 4.8, "type": "window"},
			{"u0": 28.0, "u1": 32.0, "y0": 1.0, "y1": 4.8, "type": "window"},
			{"u0": 45.0, "u1": 48.0, "y0": 1.0, "y1": 4.8, "type": "window"},
		],
		"west": [
			{"u0": 10.0, "u1": 13.0, "y0": 1.0, "y1": 4.8, "type": "window"},
			{"u0": 28.0, "u1": 31.0, "y0": 1.0, "y1": 4.8, "type": "window"},
		],
	})

	var wall: Material = mats["wall_blue"]
	_wall(Vector2(2.0, 22.0), Vector2(24.0, 22.0), y, CH1, WT, wall, [
		{"u0": 9.0, "u1": 9.0 + DOOR_W, "y1": DOOR_H},
	], true)
	_wall(Vector2(38.0, 22.0), Vector2(W - 2.0, 22.0), y, CH1, WT, wall, [
		{"u0": 8.0, "u1": 8.0 + DOOR_W, "y1": DOOR_H},
	], true)
	_wall(Vector2(2.0, 14.0), Vector2(W - 2.0, 14.0), y, CH1, WT, wall, [
		{"u0": 8.0, "u1": 8.0 + DOOR_W, "y1": DOOR_H},
		{"u0": 30.0, "u1": 30.0 + DOOR_W, "y1": DOOR_H},
		{"u0": 46.0, "u1": 46.0 + DOOR_W, "y1": DOOR_H},
	], true)
	_wall(Vector2(22.0, 2.0), Vector2(22.0, 14.0), y, CH1, WT, wall, [
		{"u0": 5.0, "u1": 5.0 + DOOR_W, "y1": DOOR_H},
	])

	# balustrade around the grand hall void
	_balustrade(Vector3(HALL_VOID.position.x, y, HALL_VOID.position.y),
		Vector3(HALL_VOID.end.x, y, HALL_VOID.position.y))
	_balustrade(Vector3(HALL_VOID.position.x, y, HALL_VOID.end.y),
		Vector3(HALL_VOID.end.x, y, HALL_VOID.end.y))
	_balustrade(Vector3(HALL_VOID.position.x, y, HALL_VOID.position.y),
		Vector3(HALL_VOID.position.x, y, HALL_VOID.end.y))
	_balustrade(Vector3(HALL_VOID.end.x, y, HALL_VOID.position.y),
		Vector3(HALL_VOID.end.x, y, HALL_VOID.end.y))

	_anchor(Vector3(13.0, F1 + CH1, 30.0), F1 + CH1, 1.4)
	_anchor(Vector3(48.0, F1 + CH1, 30.0), F1 + CH1, 1.4)
	_anchor(Vector3(14.0, F1 + CH1, 18.0), F1 + CH1, 1.0)
	_anchor(Vector3(46.0, F1 + CH1, 18.0), F1 + CH1, 1.0)
	_anchor(Vector3(12.0, F1 + CH1, 8.0), F1 + CH1, 1.2)
	_anchor(Vector3(40.0, F1 + CH1, 8.0), F1 + CH1, 1.2)


# ---------------------------------------------------------------------------
# roof and tower — both walkable
# ---------------------------------------------------------------------------

func _build_roof() -> void:
	_slab(Rect2(0.0, 0.0, W, D), ROOF + 0.06, 0.06, mats["deck"], [STAIR_ROOF])
	_register_room("roof", Rect2(0.0, 0.0, W, D), ROOF, "roof")

	var stone: Material = mats["sandstone"]
	var h := 1.5
	for spec in [[Vector2(0.0, 0.0), Vector2(W, 0.0)], [Vector2(W, 0.0), Vector2(W, D)],
			[Vector2(W, D), Vector2(0.0, D)], [Vector2(0.0, D), Vector2(0.0, 0.0)]]:
		_wall(spec[0], spec[1], ROOF, h, XT, stone, [])
	for z_v in [0.0, D]:
		var z: float = z_v
		_box(Vector3(W, 0.28, XT + 0.4), Vector3(W * 0.5, ROOF + h, z), mats["gold"], false)
	for x_v in [0.0, W]:
		var x: float = x_v
		_box(Vector3(XT + 0.4, 0.28, D), Vector3(x, ROOF + h, D * 0.5), mats["gold"], false)




func _build_tower() -> void:
	var stone: Material = mats["sandstone"]
	var x0 := 26.0
	var x1 := 36.0
	var z0 := 4.0
	var z1 := 12.0
	var h := TOWER_TOP - ROOF
	_wall(Vector2(x0, z0), Vector2(x1, z0), ROOF, h, 0.6, stone, [
		{"u0": 3.0, "u1": 7.0, "y0": 0.0, "y1": 4.6},
	])
	_wall(Vector2(x1, z0), Vector2(x1, z1), ROOF, h, 0.6, stone, [
		{"u0": 2.5, "u1": 5.5, "y0": 2.0, "y1": 6.0, "type": "window"},
	])
	_wall(Vector2(x1, z1), Vector2(x0, z1), ROOF, h, 0.6, stone, [
		{"u0": 3.0, "u1": 7.0, "y0": 0.0, "y1": 4.6},
	])
	_wall(Vector2(x0, z1), Vector2(x0, z0), ROOF, h, 0.6, stone, [
		{"u0": 2.5, "u1": 5.5, "y0": 2.0, "y1": 6.0, "type": "window"},
	])
	# stepped pyramid roof, climbable to the finial
	var steps := 7
	for i in range(steps):
		var f := float(i) / float(steps)
		var wide := lerpf(x1 - x0 + 1.2, 1.6, f)
		var deep := lerpf(z1 - z0 + 1.2, 1.6, f)
		_box(Vector3(wide, 0.45, deep),
			Vector3((x0 + x1) * 0.5, TOWER_TOP + 0.22 + i * 0.42, (z0 + z1) * 0.5),
			mats["roof"])
	var finial := MeshInstance3D.new()
	var sm := SphereMesh.new()
	sm.radius = 0.7
	sm.height = 1.4
	finial.mesh = sm
	finial.position = Vector3((x0 + x1) * 0.5, TOWER_TOP + steps * 0.42 + 0.9, (z0 + z1) * 0.5)
	finial.material_override = mats["gold"]
	add_child(finial)
	var body := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var shape := SphereShape3D.new()
	shape.radius = 0.7
	cs.shape = shape
	body.add_child(cs)
	finial.add_child(body)

	_anchor(Vector3(31.0, TOWER_TOP - 0.4, 8.0), TOWER_TOP - 0.4, 1.0)


# ---------------------------------------------------------------------------
# stairs, columns, balustrades
# ---------------------------------------------------------------------------

## A straight run of steps from `from` up to `to`, `width` wide, running along
## the horizontal direction between them.
func _ramp_stairs(from: Vector3, to: Vector3, width: float) -> void:
	var rise_total := to.y - from.y
	if rise_total <= 0.01:
		return
	var flat := Vector2(to.x - from.x, to.z - from.z)
	var span := flat.length()
	var steps := int(ceilf(rise_total / 0.26))
	var rise := rise_total / float(steps)
	var run := (span / float(steps)) if span > 0.5 else 0.42
	var dirn := flat.normalized() if span > 0.01 else Vector2(0.0, -1.0)
	var theta := atan2(-dirn.y, dirn.x)
	var basis := Basis(Vector3.UP, theta)
	for i in range(steps):
		var h := rise * (i + 1)
		var along := run * (i + 0.5)
		var pos := from + basis * Vector3(along, h * 0.5, 0.0)
		_box(Vector3(run + 0.02, h, width), pos, mats["marble"], true, basis)


func _build_stairs() -> void:
	# Three proper flights, each rising through the hole named after it. Pitch
	# works out at about 32 degrees — 0.26 m rise on 0.41 m of going, which is
	# what a real staircase is.
	var w := 3.2
	_flight(Vector3(STAIR_E.end.x - 1.0, 0.0, 17.0),
		Vector3(STAIR_E.position.x + 1.0, F1, 17.0), w)
	_flight(Vector3(STAIR_W.position.x + 1.0, 0.0, 17.0),
		Vector3(STAIR_W.end.x - 1.0, F1, 17.0), w)
	_flight(Vector3(STAIR_ROOF.position.x + 0.5, F1, 17.0),
		Vector3(STAIR_ROOF.end.x - 0.5, ROOF, 17.0), w)


## A flight plus the balustrades down both of its sides.
func _flight(from: Vector3, to: Vector3, width: float) -> void:
	_ramp_stairs(from, to, width)
	var flat := Vector2(to.x - from.x, to.z - from.z)
	if flat.length() < 0.1:
		return
	var side := Vector3(-flat.normalized().y, 0.0, flat.normalized().x) * (width * 0.5)
	_balustrade(from + side, to + side)
	_balustrade(from - side, to - side)


func _balustrade(a: Vector3, b: Vector3) -> void:
	var stone: Material = mats["sandstone"]
	var d := b - a
	var length := d.length()
	if length < 0.2:
		return
	var n := maxi(int(length / 0.6), 1)
	for i in range(n + 1):
		var p := a.lerp(b, float(i) / float(n))
		_box(Vector3(0.16, 0.22, 0.16), p + Vector3(0.0, 0.11, 0.0), stone, false)
		_box(Vector3(0.24, 0.62, 0.24), p + Vector3(0.0, 0.55, 0.0), stone, false)
		_box(Vector3(0.16, 0.18, 0.16), p + Vector3(0.0, 0.95, 0.0), stone, false)
	var mid := (a + b) * 0.5 + Vector3(0.0, 1.12, 0.0)
	var theta := atan2(-d.z, d.x)
	var horiz := Vector2(d.x, d.z).length()
	var pitch := atan2(d.y, maxf(horiz, 0.0001))
	var basis := Basis(Vector3.UP, theta) * Basis(Vector3(0.0, 0.0, 1.0), pitch)
	_box(Vector3(length, 0.18, 0.42), mid, mats["gold"], true, basis)


func _build_columns() -> void:
	# Colonnades — and the best things in the palace to run straight up.
	for i in range(5):
		_column(Vector3(6.0, 0.0, 23.0 + i * 3.4), CH)
		_column(Vector3(20.0, 0.0, 23.0 + i * 3.4), CH)
		_column(Vector3(42.0, 0.0, 23.0 + i * 3.4), CH)
		_column(Vector3(54.0, 0.0, 23.0 + i * 3.4), CH)
	# The gallery carries the staircases now, so its colonnade moves to the
	# short stretch between the two flights where there is actually room.
	for i in range(3):
		_column(Vector3(20.0 + i * 10.0, 0.0, 15.2), CH)
		_column(Vector3(20.0 + i * 10.0, 0.0, 18.8), CH)


func _column(base: Vector3, height: float) -> void:
	var stone: Material = mats["sandstone"]
	_box(Vector3(1.5, 0.35, 1.5), base + Vector3(0.0, 0.175, 0.0), stone)
	var shaft := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = 0.44
	cm.bottom_radius = 0.55
	cm.height = height - 1.0
	cm.radial_segments = 12
	shaft.mesh = cm
	shaft.position = base + Vector3(0.0, 0.35 + (height - 1.0) * 0.5, 0.0)
	shaft.material_override = stone
	add_child(shaft)
	var body := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var shape := CylinderShape3D.new()
	shape.radius = 0.55
	shape.height = height - 1.0
	cs.shape = shape
	body.add_child(cs)
	shaft.add_child(body)
	_box(Vector3(1.4, 0.4, 1.4), base + Vector3(0.0, height - 0.45, 0.0), stone)
	_box(Vector3(1.1, 0.25, 1.1), base + Vector3(0.0, height - 0.78, 0.0),
		mats["gold"], false)


# ---------------------------------------------------------------------------
# built-in furnishings
# ---------------------------------------------------------------------------

func _build_fixtures() -> void:
	var gold: Material = mats["gold"]

	# --- throne dais ---
	for i in range(3):
		var s := 8.0 - i * 1.6
		_box(Vector3(s, 0.35, s), Vector3(48.0, 0.175 + i * 0.35, 29.0), mats["marble"])
	_box(Vector3(2.0, 0.5, 1.8), Vector3(48.0, 1.30, 29.6), gold)
	_box(Vector3(2.0, 3.0, 0.4), Vector3(48.0, 2.55, 30.4), gold)
	_box(Vector3(0.4, 1.6, 1.8), Vector3(47.0, 1.85, 29.6), gold)
	_box(Vector3(0.4, 1.6, 1.8), Vector3(49.0, 1.85, 29.6), gold)

	# --- ballroom fireplace, palace sized ---
	_box(Vector3(1.0, 4.2, 5.0), Vector3(2.9, 2.1, 29.0), mats["brick"])
	_box(Vector3(1.5, 0.35, 6.0), Vector3(3.0, 4.4, 29.0), mats["marble"])
	_box(Vector3(0.6, 2.4, 3.0), Vector3(3.6, 1.2, 29.0),
		MaterialLib.plain(Color(0.03, 0.025, 0.02), 0.95))

	# --- library shelving, floor to ceiling ---
	for i in range(6):
		_box(Vector3(0.7, 5.0, 2.6), Vector3(2.9, 2.5, 3.4 + i * 1.9), mats["wood_dark"])
	for i in range(5):
		_box(Vector3(2.4, 5.0, 0.7), Vector3(5.0 + i * 3.4, 2.5, 2.9), mats["wood_dark"])

	# --- dining: a long banqueting table ---
	_box(Vector3(3.0, 0.25, 9.0), Vector3(32.0, 1.05, 8.0), mats["wood_dark"])
	for i in range(6):
		var lx := 30.8 + (i % 2) * 2.4
		var lz := 4.4 + floori(i / 2.0) * 3.6
		_box(Vector3(0.3, 1.0, 0.3), Vector3(lx, 0.5, lz), mats["wood_dark"])

	# --- kitchen ranges ---
	_box(Vector3(14.0, 1.2, 1.2), Vector3(50.0, 0.6, 2.9), mats["wood_dark"])
	_box(Vector3(14.2, 0.12, 1.3), Vector3(50.0, 1.26, 2.9),
		MaterialLib.plain(Color(0.16, 0.15, 0.14), 0.35, 0.1))
	_box(Vector3(1.2, 1.2, 9.0), Vector3(57.2, 0.6, 8.0), mats["wood_dark"])

	# --- garden terrace and its steps, outside the south door ---
	_box(Vector3(22.0, 0.4, 7.0), Vector3(30.0, -0.2, -3.5), mats["marble"])
	for i in range(3):
		_box(Vector3(22.0 + i * 2.0, 0.4, 1.4),
			Vector3(30.0, -0.45 - i * 0.4, -7.2 - i * 1.4), mats["marble"])
	_balustrade(Vector3(19.0, 0.0, -7.0), Vector3(19.0, 0.0, 0.0))
	_balustrade(Vector3(41.0, 0.0, -7.0), Vector3(41.0, 0.0, 0.0))

class_name House
extends Node3D
## Procedural two-storey house + attic: 11 rooms, doorways, windows, stairs and
## built-in furnishings. Everything is generated from the tables below so the
## layout stays readable and every wall gets real collision.

# --- footprint -------------------------------------------------------------
const W := 20.0          # extent along X
const D := 14.0          # extent along Z
const WT := 0.20         # interior wall thickness
const XT := 0.38         # exterior wall thickness
const CH := 3.0          # clear ceiling height of one storey
const ST := 0.30         # slab thickness
const F0 := 0.0          # ground floor walking surface
const F1 := CH + ST      # first floor walking surface  (3.30)
const F2 := 2.0 * (CH + ST)  # attic floor              (6.60)
const RIDGE := F2 + 3.2  # attic ridge height

const DOOR_H := 2.30
const DOOR_W := 1.60
const WIN_B := 1.05      # window sill height above the floor
const WIN_H := 1.45
const WIN_W := 1.70

var mats: Dictionary = {}
## Rooms as {name, rect (Rect2 in XZ), floor level, floor material key}
var rooms: Array[Dictionary] = []
## Where lamps/props may be placed, filled during generation.
var ceiling_anchors: Array[Dictionary] = []


func _ready() -> void:
	_load_materials()
	_build_ground_floor()
	_build_first_floor()
	_build_attic()
	_build_stairs()
	_build_fixtures()


func _load_materials() -> void:
	# Poly Haven's pine and parquet read far too orange under warm lamps, and the
	# plaster sets are almost flat white, so each one gets a tint. Different
	# tints of the same plaster give the rooms their own character for free.
	mats = {
		"parquet": MaterialLib.surface("parquet", 1.8, true, 1.3, Color(0.60, 0.56, 0.52)),
		"planks": MaterialLib.surface("planks", 2.0, true, 1.1, Color(0.52, 0.45, 0.38)),
		"wallpaper": MaterialLib.surface("wallpaper", 1.5, true, 1.1, Color(0.64, 0.60, 0.54)),
		"wall_green": MaterialLib.surface("wallpaper", 1.5, true, 1.1, Color(0.40, 0.47, 0.42)),
		"wall_red": MaterialLib.surface("wallpaper", 1.5, true, 1.1, Color(0.56, 0.41, 0.37)),
		"wall_blue": MaterialLib.surface("wallpaper", 1.5, true, 1.1, Color(0.42, 0.47, 0.55)),
		"plaster": MaterialLib.surface("plaster", 2.0, true, 1.0, Color(0.58, 0.56, 0.53)),
		"ceiling": MaterialLib.surface("ceiling", 2.4, true, 0.8, Color(0.74, 0.73, 0.70)),
		"tiles_kitchen": MaterialLib.surface("tiles_kitchen", 1.5, true, 1.1),
		"tiles_bath": MaterialLib.surface("tiles_bath", 1.4, true, 1.0),
		"wood_dark": MaterialLib.surface("wood_dark", 1.1, true, 1.1, Color(0.46, 0.40, 0.34)),
		"brick": MaterialLib.surface("brick", 1.8, true, 1.4, Color(0.62, 0.55, 0.50)),
		"carpet": MaterialLib.surface("carpet", 2.4, true, 1.5),
		"concrete": MaterialLib.surface("concrete", 2.6, true, 0.9),
		"attic_wood": MaterialLib.surface("attic_wood", 2.0, true, 1.3, Color(0.60, 0.57, 0.52)),
	}


# ---------------------------------------------------------------------------
# primitives
# ---------------------------------------------------------------------------

func _box(size: Vector3, pos: Vector3, mat: Material, collide := true,
		basis := Basis(), shadows := true) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.transform = Transform3D(basis, pos)
	if mat:
		mi.material_override = mat
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON if shadows \
		else GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(mi)
	if collide:
		var body := StaticBody3D.new()
		var cs := CollisionShape3D.new()
		var shape := BoxShape3D.new()
		shape.size = size
		cs.shape = shape
		body.add_child(cs)
		mi.add_child(body)
	return mi


## Floor or ceiling slab. `holes` are Rect2 openings cut out of it (stairwell,
## attic hatch); the slab is split into strips around each hole.
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


## Rect2 minus Rect2 -> up to four rects (in XZ, y component of Rect2 is Z).
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


## A wall from `a` to `b` (XZ), with door/window openings measured in metres
## along the wall from `a`.
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
		_opening_trim(basis, origin, u0, u1, y0, y1, thickness, String(op.get("type", "door")))
		cursor = maxf(cursor, u1)
	if cursor < length:
		_wall_piece(basis, origin, cursor, length, y_base, y_base + height, thickness, mat)

	if trim:
		# crown moulding runs the whole length, above every door head
		var crown := Vector3(length * 0.5, height - 0.07, 0.0)
		_box(Vector3(length, 0.14, thickness + 0.14), origin + basis * crown,
			mats["wood_dark"], false, basis)
	_trim_wall = false


## Set by _wall() so the piece builder knows whether to add panelling.
var _trim_wall := false
const WAINSCOT_H := 0.98


func _wall_piece(basis: Basis, origin: Vector3, u0: float, u1: float,
		y_lo: float, y_hi: float, thickness: float, mat: Material) -> void:
	var w := u1 - u0
	var h := y_hi - y_lo
	if w <= 0.005 or h <= 0.005:
		return
	var local := Vector3((u0 + u1) * 0.5, (y_lo + y_hi) * 0.5 - origin.y, 0.0)
	_box(Vector3(w, h, thickness), origin + basis * local, mat, true, basis)

	# wainscot panelling on the lower part of a wall that reaches the floor
	if _trim_wall and absf(y_lo - origin.y) < 0.01 and h > 1.4:
		var panel := Vector3((u0 + u1) * 0.5, WAINSCOT_H * 0.5, 0.0)
		_box(Vector3(w, WAINSCOT_H, thickness + 0.05), origin + basis * panel,
			mats["wood_dark"], false, basis)
		var rail := Vector3((u0 + u1) * 0.5, WAINSCOT_H, 0.0)
		_box(Vector3(w, 0.08, thickness + 0.15), origin + basis * rail,
			mats["wood_dark"], false, basis)
		# vertical beading so the panelling is not one flat slab
		var n := maxi(int(w / 0.75), 1)
		for i in range(n + 1):
			var bead := Vector3(u0 + w * float(i) / float(n), WAINSCOT_H * 0.5, 0.0)
			_box(Vector3(0.07, WAINSCOT_H - 0.1, thickness + 0.09),
				origin + basis * bead, mats["wood_dark"], false, basis)


## Door frames, window frames, glass and sills.
func _opening_trim(basis: Basis, origin: Vector3, u0: float, u1: float,
		y0: float, y1: float, thickness: float, kind: String) -> void:
	var wood: Material = mats["wood_dark"]
	var jamb := 0.09
	var t := thickness + 0.06
	# side jambs
	for u_v in [u0, u1]:
		var u: float = u_v
		var local := Vector3(u, (y0 + y1) * 0.5 - origin.y, 0.0)
		_box(Vector3(jamb, y1 - y0, t), origin + basis * local, wood, false, basis)
	# head
	var head := Vector3((u0 + u1) * 0.5, y1 - origin.y, 0.0)
	_box(Vector3(u1 - u0 + jamb, jamb, t), origin + basis * head, wood, false, basis)

	if kind == "window":
		# sill
		var sill := Vector3((u0 + u1) * 0.5, y0 - origin.y, 0.0)
		_box(Vector3(u1 - u0 + 0.26, 0.09, thickness + 0.24),
			origin + basis * sill, wood, true, basis)
		# glass
		var g := Vector3((u0 + u1) * 0.5, (y0 + y1) * 0.5 - origin.y, 0.0)
		_box(Vector3(u1 - u0 - 0.06, y1 - y0 - 0.06, 0.03),
			origin + basis * g, MaterialLib.glass(), true, basis, false)
		# muntins (cross bars)
		_box(Vector3(0.06, y1 - y0, 0.07), origin + basis * g, wood, false, basis)
		_box(Vector3(u1 - u0, 0.06, 0.07), origin + basis * g, wood, false, basis)


## A door leaf standing ajar in its frame.
func _door_leaf(hinge: Vector3, facing: float, swing_deg: float) -> void:
	var pivot := Node3D.new()
	pivot.transform = Transform3D(Basis(Vector3.UP, facing + deg_to_rad(swing_deg)), hinge)
	add_child(pivot)
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = Vector3(DOOR_W - 0.1, DOOR_H - 0.08, 0.06)
	mi.mesh = bm
	mi.material_override = mats["wood_dark"]
	mi.position = Vector3((DOOR_W - 0.1) * 0.5, (DOOR_H - 0.08) * 0.5, 0.0)
	pivot.add_child(mi)
	var body := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var shape := BoxShape3D.new()
	shape.size = bm.size
	cs.shape = shape
	body.add_child(cs)
	mi.add_child(body)
	# handle
	var knob := MeshInstance3D.new()
	var sm := SphereMesh.new()
	sm.radius = 0.05
	sm.height = 0.1
	knob.mesh = sm
	knob.material_override = MaterialLib.plain(Color(0.42, 0.33, 0.16), 0.25, 0.9)
	knob.position = Vector3(DOOR_W - 0.28, 1.05, 0.06)
	pivot.add_child(knob)


func _register_room(name: String, rect: Rect2, level: float, floor_key: String) -> void:
	rooms.append({"name": name, "rect": rect, "level": level, "floor": floor_key})


# ---------------------------------------------------------------------------
# ground floor
# ---------------------------------------------------------------------------

func _build_ground_floor() -> void:
	var y := F0
	# room floors
	_slab(Rect2(0.0, 0.0, 10.0, 8.0), y, ST, mats["parquet"])            # living
	_slab(Rect2(10.0, 0.0, 10.0, 6.0), y, ST, mats["tiles_kitchen"])     # kitchen
	_slab(Rect2(10.0, 6.0, 10.0, 8.0), y, ST, mats["planks"])            # hall
	_slab(Rect2(0.0, 8.0, 5.0, 6.0), y, ST, mats["tiles_bath"])          # bathroom
	_slab(Rect2(5.0, 8.0, 5.0, 6.0), y, ST, mats["parquet"])             # dining

	_register_room("living", Rect2(0.0, 0.0, 10.0, 8.0), y, "parquet")
	_register_room("kitchen", Rect2(10.0, 0.0, 10.0, 6.0), y, "tiles_kitchen")
	_register_room("hall", Rect2(10.0, 6.0, 10.0, 8.0), y, "planks")
	_register_room("bathroom", Rect2(0.0, 8.0, 5.0, 6.0), y, "tiles_bath")
	_register_room("dining", Rect2(5.0, 8.0, 5.0, 6.0), y, "parquet")

	# ceiling of the ground floor = underside of the first floor slab
	_slab(Rect2(0.0, 0.0, W, D), F1, ST, mats["ceiling"], [Rect2(10.6, 8.4, 1.9, 4.8)])

	_exterior_ring(y, CH, [
		# south wall (z = 0): kitchen + living windows
		{"wall": "south", "ops": [
			{"u0": 2.2, "u1": 2.2 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 6.6, "u1": 6.6 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 13.0, "u1": 13.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
		]},
		# east wall (x = W): front door into the hall + kitchen window
		{"wall": "east", "ops": [
			{"u0": 1.8, "u1": 1.8 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 7.4, "u1": 7.4 + DOOR_W, "y0": 0.0, "y1": DOOR_H, "type": "door"},
		]},
		# north wall (z = D)
		{"wall": "north", "ops": [
			{"u0": 3.0, "u1": 3.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 11.5, "u1": 11.5 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
		]},
		# west wall (x = 0)
		{"wall": "west", "ops": [
			{"u0": 2.0, "u1": 2.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 9.5, "u1": 9.5 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
		]},
	])

	var wall: Material = mats["wallpaper"]
	# x = 10 spine wall, from z 0 to 14
	_wall(Vector2(10.0, 0.0), Vector2(10.0, D), y, CH, WT, wall, [
		{"u0": 2.6, "u1": 2.6 + DOOR_W, "y1": DOOR_H, "type": "door"},   # living <-> kitchen
		{"u0": 9.6, "u1": 9.6 + DOOR_W, "y1": DOOR_H, "type": "door"},   # dining <-> hall
	], true)
	# z = 8 wall from x 0 to 10
	_wall(Vector2(0.0, 8.0), Vector2(10.0, 8.0), y, CH, WT, mats["wall_red"], [
		{"u0": 6.4, "u1": 6.4 + DOOR_W, "y1": DOOR_H, "type": "door"},   # living <-> dining
	], true)
	# x = 5 wall from z 8 to 14 (bathroom)
	_wall(Vector2(5.0, 8.0), Vector2(5.0, D), y, CH, WT, mats["tiles_bath"], [
		{"u0": 2.4, "u1": 2.4 + DOOR_W, "y1": DOOR_H, "type": "door"},
	])
	# z = 6 wall from x 10 to 20 (kitchen <-> hall)
	_wall(Vector2(10.0, 6.0), Vector2(W, 6.0), y, CH, WT, wall, [
		{"u0": 2.0, "u1": 2.0 + DOOR_W + 0.5, "y1": DOOR_H + 0.1, "type": "door"},
	], true)

	_door_leaf(Vector3(10.0, y, 2.6), deg_to_rad(-90.0), 62.0)
	_door_leaf(Vector3(6.4, y, 8.0), 0.0, -48.0)
	_door_leaf(Vector3(5.0, y, 10.4), deg_to_rad(-90.0), 22.0)

	ceiling_anchors.append({"pos": Vector3(5.0, F1 - ST, 4.0), "room": "living"})
	ceiling_anchors.append({"pos": Vector3(15.0, F1 - ST, 3.0), "room": "kitchen"})
	ceiling_anchors.append({"pos": Vector3(15.0, F1 - ST, 10.0), "room": "hall"})
	ceiling_anchors.append({"pos": Vector3(7.5, F1 - ST, 11.0), "room": "dining"})
	ceiling_anchors.append({"pos": Vector3(2.5, F1 - ST, 11.0), "room": "bathroom"})


func _exterior_ring(y: float, height: float, specs: Array) -> void:
	var mat: Material = mats["plaster"]
	var corners := {
		"south": [Vector2(0.0, 0.0), Vector2(W, 0.0)],
		"east": [Vector2(W, 0.0), Vector2(W, D)],
		"north": [Vector2(W, D), Vector2(0.0, D)],
		"west": [Vector2(0.0, D), Vector2(0.0, 0.0)],
	}
	var by_wall := {}
	for s in specs:
		by_wall[s.wall] = s.ops
	for key in corners:
		var pts: Array = corners[key]
		_wall(pts[0], pts[1], y, height, XT, mat, by_wall.get(key, []))


# ---------------------------------------------------------------------------
# first floor
# ---------------------------------------------------------------------------

func _build_first_floor() -> void:
	var y := F1
	_slab(Rect2(0.0, 0.0, 10.0, 7.0), y, 0.02, mats["planks"])           # bedroom 1
	_slab(Rect2(10.0, 0.0, 10.0, 7.0), y, 0.02, mats["carpet"])          # bedroom 2
	_slab(Rect2(0.0, 7.0, W, 4.0), y, 0.02, mats["planks"])              # corridor
	_slab(Rect2(0.0, 11.0, 10.0, 3.0), y, 0.02, mats["parquet"])         # study
	_slab(Rect2(10.0, 11.0, 10.0, 3.0), y, 0.02, mats["tiles_bath"])     # bathroom 2

	_register_room("bedroom1", Rect2(0.0, 0.0, 10.0, 7.0), y, "planks")
	_register_room("bedroom2", Rect2(10.0, 0.0, 10.0, 7.0), y, "carpet")
	_register_room("corridor", Rect2(0.0, 7.0, W, 4.0), y, "planks")
	_register_room("study", Rect2(0.0, 11.0, 10.0, 3.0), y, "parquet")
	_register_room("bathroom2", Rect2(10.0, 11.0, 10.0, 3.0), y, "tiles_bath")

	# ceiling of the first floor = attic floor, with a hatch above the study
	_slab(Rect2(0.0, 0.0, W, D), F2, ST, mats["ceiling"], [Rect2(2.4, 11.6, 1.5, 1.5)])

	_exterior_ring(y, CH, [
		{"wall": "south", "ops": [
			{"u0": 3.0, "u1": 3.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 14.0, "u1": 14.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
		]},
		{"wall": "east", "ops": [
			{"u0": 2.4, "u1": 2.4 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 11.0, "u1": 11.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
		]},
		{"wall": "north", "ops": [
			{"u0": 4.0, "u1": 4.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 13.5, "u1": 13.5 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
		]},
		{"wall": "west", "ops": [
			{"u0": 4.0, "u1": 4.0 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
			{"u0": 10.5, "u1": 10.5 + WIN_W, "y0": WIN_B, "y1": WIN_B + WIN_H, "type": "window"},
		]},
	])

	var wall: Material = mats["wallpaper"]
	_wall(Vector2(10.0, 0.0), Vector2(10.0, 7.0), y, CH, WT, wall, [])
	_wall(Vector2(0.0, 7.0), Vector2(W, 7.0), y, CH, WT, mats["wall_green"], [
		{"u0": 4.2, "u1": 4.2 + DOOR_W, "y1": DOOR_H, "type": "door"},
		{"u0": 14.2, "u1": 14.2 + DOOR_W, "y1": DOOR_H, "type": "door"},
	], true)
	_wall(Vector2(0.0, 11.0), Vector2(W, 11.0), y, CH, WT, mats["wall_blue"], [
		{"u0": 5.4, "u1": 5.4 + DOOR_W, "y1": DOOR_H, "type": "door"},
		{"u0": 15.0, "u1": 15.0 + DOOR_W, "y1": DOOR_H, "type": "door"},
	], true)
	_wall(Vector2(10.0, 11.0), Vector2(10.0, D), y, CH, WT, wall, [])

	_door_leaf(Vector3(4.2, y, 7.0), 0.0, -70.0)
	_door_leaf(Vector3(14.2, y, 7.0), 0.0, -25.0)
	_door_leaf(Vector3(5.4, y, 11.0), 0.0, 55.0)

	# stairwell railing on the first floor landing
	_railing(Vector3(10.5, y, 8.4), Vector3(10.5, y, 13.2))
	_railing(Vector3(12.5, y, 8.4), Vector3(12.5, y, 13.2))

	ceiling_anchors.append({"pos": Vector3(5.0, F2 - ST, 3.5), "room": "bedroom1"})
	ceiling_anchors.append({"pos": Vector3(15.0, F2 - ST, 3.5), "room": "bedroom2"})
	ceiling_anchors.append({"pos": Vector3(6.0, F2 - ST, 9.0), "room": "corridor"})
	ceiling_anchors.append({"pos": Vector3(16.0, F2 - ST, 9.0), "room": "corridor"})
	ceiling_anchors.append({"pos": Vector3(5.0, F2 - ST, 12.5), "room": "study"})


# ---------------------------------------------------------------------------
# attic
# ---------------------------------------------------------------------------

func _build_attic() -> void:
	var y := F2
	var mat: Material = mats["attic_wood"]
	_slab(Rect2(0.0, 0.0, W, D), y, 0.03, mat, [Rect2(2.4, 11.6, 1.5, 1.5)])
	_register_room("attic", Rect2(0.0, 0.0, W, D), y, "attic_wood")

	# knee walls
	_wall(Vector2(0.0, 0.0), Vector2(W, 0.0), y, 0.9, XT, mat, [])
	_wall(Vector2(W, 0.0), Vector2(W, D), y, 0.9, XT, mat, [])
	_wall(Vector2(W, D), Vector2(0.0, D), y, 0.9, XT, mat, [])
	_wall(Vector2(0.0, D), Vector2(0.0, 0.0), y, 0.9, XT, mat, [])

	# two sloped roof planes meeting at a ridge over x = W/2
	var eave_y := y + 0.9
	var half := W * 0.5
	var rise := RIDGE - eave_y
	var slope_len := sqrt(half * half + rise * rise)
	var pitch := atan2(rise, half)
	for side_v in [-1.0, 1.0]:
		var side: float = side_v
		var mid_x := half + side * half * 0.5
		var mid_y := eave_y + rise * 0.5
		var b := Basis(Vector3(0.0, 0.0, 1.0), -side * pitch)
		_box(Vector3(slope_len, 0.25, D), Vector3(mid_x, mid_y, D * 0.5), mat, true, b)

	# gable ends (triangular walls) approximated by stacked slats
	var steps := 12
	for i in range(steps):
		var t0 := float(i) / float(steps)
		var t1 := float(i + 1) / float(steps)
		var y0 := lerpf(eave_y, RIDGE, t0)
		var y1 := lerpf(eave_y, RIDGE, t1)
		var wx := W * (1.0 - (t0 + t1) * 0.5)
		for z_v in [0.0, D]:
			var z: float = z_v
			_box(Vector3(wx, y1 - y0, XT), Vector3(half, (y0 + y1) * 0.5, z), mat, true)

	# roof beams for silhouette
	for i in range(6):
		var z := 1.6 + i * 2.2
		_box(Vector3(W * 0.98, 0.18, 0.18), Vector3(half, eave_y + 0.35, z),
			mats["wood_dark"], false)

	# dormer window in the north gable
	_box(Vector3(1.3, 1.3, 0.06), Vector3(half, eave_y + 1.3, D - 0.05),
		MaterialLib.glass(), false, Basis(), false)

	ceiling_anchors.append({"pos": Vector3(half, eave_y + 0.9, 5.0), "room": "attic"})


# ---------------------------------------------------------------------------
# stairs & railings
# ---------------------------------------------------------------------------

func _build_stairs() -> void:
	var steps := 15
	var rise := (F1 - F0) / float(steps)
	var run := 0.33
	var width := 1.85
	var x_center := 11.55
	var z_start := 13.1
	for i in range(steps):
		var h := rise * (i + 1)
		var z := z_start - run * i
		_box(Vector3(width, h, run), Vector3(x_center, h * 0.5, z - run * 0.5),
			mats["wood_dark"])
	# stringer / side wall
	_box(Vector3(0.12, F1, run * steps),
		Vector3(x_center - width * 0.5, F1 * 0.5, z_start - run * steps * 0.5),
		mats["wood_dark"], true)
	# attic ladder under the hatch
	_ladder(Vector3(3.15, F1, 12.35), F2 - F1)


func _railing(a: Vector3, b: Vector3) -> void:
	var wood: Material = mats["wood_dark"]
	var d := b - a
	var length := d.length()
	if length < 0.1:
		return
	var n := int(length / 0.42)
	for i in range(n + 1):
		var p := a.lerp(b, float(i) / float(maxi(n, 1)))
		_box(Vector3(0.06, 0.95, 0.06), p + Vector3(0.0, 0.475, 0.0), wood, false)
	var mid := (a + b) * 0.5 + Vector3(0.0, 0.98, 0.0)
	var theta := atan2(-d.z, d.x)
	_box(Vector3(length, 0.08, 0.1), mid, wood, true, Basis(Vector3.UP, theta))


func _ladder(base: Vector3, height: float) -> void:
	var wood: Material = mats["wood_dark"]
	for side in [-0.28, 0.28]:
		_box(Vector3(0.08, height, 0.08), base + Vector3(side, height * 0.5, 0.0), wood)
	var rungs := int(height / 0.32)
	for i in range(rungs):
		_box(Vector3(0.62, 0.05, 0.05),
			base + Vector3(0.0, 0.3 + i * 0.32, 0.0), wood)


# ---------------------------------------------------------------------------
# built-in furnishings
# ---------------------------------------------------------------------------

func _build_fixtures() -> void:
	var wood: Material = mats["wood_dark"]
	var brick: Material = mats["brick"]
	var tile: Material = mats["tiles_bath"]

	# --- living room fireplace (west wall) ---
	_box(Vector3(0.7, 2.4, 2.6), Vector3(0.55, 1.2, 4.0), brick)
	_box(Vector3(0.95, 0.18, 3.0), Vector3(0.6, 2.5, 4.0), wood)        # mantel
	_box(Vector3(0.4, 1.1, 1.5), Vector3(1.05, 0.55, 4.0),
		MaterialLib.plain(Color(0.03, 0.025, 0.02), 0.95))              # sooty firebox
	# chimney breast up through both floors
	_box(Vector3(0.7, CH, 1.8), Vector3(0.55, F1 + CH * 0.5, 4.0), brick)

	# --- kitchen counters (south + east of the kitchen) ---
	var counter_top := MaterialLib.plain(Color(0.16, 0.15, 0.14), 0.35, 0.1)
	_box(Vector3(4.2, 0.9, 0.65), Vector3(13.2, 0.45, 0.72), wood)
	_box(Vector3(4.3, 0.06, 0.7), Vector3(13.2, 0.93, 0.72), counter_top)
	_box(Vector3(0.65, 0.9, 3.4), Vector3(19.3, 0.45, 2.6), wood)
	_box(Vector3(0.7, 0.06, 3.5), Vector3(19.3, 0.93, 2.6), counter_top)
	# sink basin
	_box(Vector3(0.8, 0.28, 0.55), Vector3(19.25, 0.82, 3.4),
		MaterialLib.plain(Color(0.55, 0.56, 0.58), 0.2, 0.85))
	# wall cabinets
	_box(Vector3(3.0, 0.75, 0.4), Vector3(13.0, 1.95, 0.6), wood)

	# --- bathroom: tub, basin, mirror wall ---
	_box(Vector3(1.9, 0.62, 0.85), Vector3(1.3, 0.31, 12.9),
		MaterialLib.plain(Color(0.85, 0.84, 0.8), 0.15))
	_box(Vector3(1.7, 0.42, 0.68), Vector3(1.3, 0.42, 12.9),
		MaterialLib.plain(Color(0.05, 0.06, 0.07), 0.1))               # dark water
	_box(Vector3(0.7, 0.2, 0.5), Vector3(4.5, 0.95, 9.4),
		MaterialLib.plain(Color(0.86, 0.85, 0.82), 0.15))
	_box(Vector3(0.06, 1.1, 0.9), Vector3(4.92, 1.85, 9.4),
		MaterialLib.plain(Color(0.7, 0.75, 0.8), 0.05, 1.0))           # mirror
	_box(Vector3(1.2, 0.06, 1.0), Vector3(0.65, 2.05, 10.4), tile, false)

	# --- hall: coat shelf + rug strip ---
	_box(Vector3(1.6, 0.06, 0.35), Vector3(18.6, 1.85, 8.6), wood)

	# --- dining: built-in sideboard ---
	_box(Vector3(0.5, 1.0, 2.2), Vector3(9.6, 0.5, 11.0), wood)
	_box(Vector3(0.6, 0.06, 2.3), Vector3(9.6, 1.03, 11.0), counter_top)

	# --- first floor: wardrobe niche in bedroom 1 ---
	_box(Vector3(2.2, 2.3, 0.6), Vector3(2.0, F1 + 1.15, 0.5), wood)

	# --- basement-style clutter shelf in the attic ---
	for i in range(3):
		_box(Vector3(3.2, 0.06, 0.5), Vector3(17.0, F2 + 0.35 + i * 0.5, 2.0), wood)

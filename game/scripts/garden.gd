class_name Garden
extends Node3D
## The formal garden behind the palace: a 72 x 46 m parterre with a central
## axis, a fountain, hedges, trees, lanterns and statuary.
##
## Anything that repeats — hedge blocks, flowers, grass tufts, gravel edging —
## is drawn through a MultiMeshInstance3D, so hundreds of pieces of planting
## cost one draw call each instead of hundreds. Collision is added per hedge
## *run* rather than per block, for the same reason.

const X0 := -6.0
const X1 := 66.0
const Z0 := -46.0
const Z1 := 0.0
const AXIS_X := 30.0          # the central path runs along this
const FOUNTAIN := Vector3(30.0, 0.0, -30.0)

var mats: Dictionary = {}
var lights: Array[OmniLight3D] = []
var _rng := RandomNumberGenerator.new()


func _ready() -> void:
	_rng.seed = 20240827
	_load_materials()
	_build_ground()
	_build_walls()
	_build_paths()
	_build_fountain()
	_build_parterres()
	_build_trees()
	_build_lanterns()
	_build_ornament()


func _load_materials() -> void:
	mats = {
		"grass": MaterialLib.surface("grass", 3.0, true, 1.3, Color(0.52, 0.60, 0.42)),
		"path": MaterialLib.surface("path", 2.4, true, 1.1, Color(0.74, 0.72, 0.68)),
		"cobble": MaterialLib.surface("cobble", 2.0, true, 1.2, Color(0.68, 0.66, 0.63)),
		"gravel": MaterialLib.surface("gravel", 1.6, true, 1.0, Color(0.72, 0.69, 0.64)),
		"foliage": MaterialLib.surface("foliage", 1.4, true, 1.4, Color(0.40, 0.52, 0.32)),
		"stone": MaterialLib.surface("sandstone", 3.0, true, 1.0, Color(0.78, 0.75, 0.68)),
		"bark": MaterialLib.object_surface("chitin", 0.7, 1.5, Color(0.46, 0.38, 0.31)),
		"water": _water(),
		"gold": MaterialLib.plain(Color(0.72, 0.56, 0.24), 0.28, 0.85),
	}


static func _water() -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.10, 0.20, 0.24, 0.82)
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.roughness = 0.04
	m.metallic = 0.5
	return m


func _box(size: Vector3, pos: Vector3, mat: Material, collide := true,
		basis := Basis(), _shadows := true) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.transform = Transform3D(basis, pos)
	if mat:
		mi.material_override = mat
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
	return mi


func _cylinder(radius: float, height: float, pos: Vector3, mat: Material,
		collide := true, segments := 20) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = radius
	cm.bottom_radius = radius
	cm.height = height
	cm.radial_segments = segments
	mi.mesh = cm
	mi.position = pos
	mi.material_override = mat
	add_child(mi)
	if collide:
		var body := StaticBody3D.new()
		var cs := CollisionShape3D.new()
		var shape := CylinderShape3D.new()
		shape.radius = radius
		shape.height = height
		cs.shape = shape
		body.add_child(cs)
		mi.add_child(body)
	return mi


## One draw call for many copies of the same mesh.
func _scatter(mesh: Mesh, mat: Material, transforms: Array[Transform3D],
		_shadows := false) -> void:
	if transforms.is_empty():
		return
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.mesh = mesh
	mm.instance_count = transforms.size()
	for i in range(transforms.size()):
		mm.set_instance_transform(i, transforms[i])
	var mmi := MultiMeshInstance3D.new()
	mmi.multimesh = mm
	mmi.material_override = mat
	mmi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(mmi)


# ---------------------------------------------------------------------------

func _build_ground() -> void:
	var _unused := 0
	# The ground itself belongs to Terrain, which covers the whole world so
	# there is nowhere left to fall through. The garden only dresses it.
	# grass tufts, thickest away from the paths
	var tuft := BoxMesh.new()
	tuft.size = Vector3(0.5, 0.42, 0.06)
	var tufts: Array[Transform3D] = []
	for i in range(900):
		var x := _rng.randf_range(X0 + 1.0, X1 - 1.0)
		var z := _rng.randf_range(Z0 + 1.0, Z1 - 1.0)
		if absf(x - AXIS_X) < 5.0 or absf(z + 23.0) < 4.5:
			continue
		if Vector2(x, z).distance_to(Vector2(FOUNTAIN.x, FOUNTAIN.z)) < 9.0:
			continue
		var b := Basis(Vector3.UP, _rng.randf_range(0.0, TAU))
		b = b.scaled(Vector3.ONE * _rng.randf_range(0.7, 1.5))
		tufts.append(Transform3D(b, Vector3(x, 0.16, z)))
	_scatter(tuft, mats["foliage"], tufts)


func _build_walls() -> void:
	var stone: Material = mats["stone"]
	var h := 3.2
	# west, east, south — the palace closes the north side
	_box(Vector3(0.8, h, Z1 - Z0), Vector3(X0, h * 0.5, (Z0 + Z1) * 0.5), stone)
	_box(Vector3(0.8, h, Z1 - Z0), Vector3(X1, h * 0.5, (Z0 + Z1) * 0.5), stone)
	# south wall, with a gateway on the axis
	var gate := 6.0
	var half := (X1 - X0 - gate) * 0.5
	_box(Vector3(half, h, 0.8), Vector3(X0 + half * 0.5, h * 0.5, Z0), stone)
	_box(Vector3(half, h, 0.8), Vector3(X1 - half * 0.5, h * 0.5, Z0), stone)
	# gate piers and lintel
	for x_v in [AXIS_X - gate * 0.5, AXIS_X + gate * 0.5]:
		var x: float = x_v
		_box(Vector3(1.2, h + 1.6, 1.2), Vector3(x, (h + 1.6) * 0.5, Z0), stone)
	_box(Vector3(gate + 1.2, 0.8, 1.2), Vector3(AXIS_X, h + 1.2, Z0), stone)
	# coping along the top, so the walls read as walls you can run along
	for x_v in [X0, X1]:
		var x: float = x_v
		_box(Vector3(1.3, 0.3, Z1 - Z0), Vector3(x, h + 0.15, (Z0 + Z1) * 0.5),
			mats["cobble"], true, Basis(), false)


func _build_paths() -> void:
	var path: Material = mats["path"]
	# central axis, from the palace terrace to the south gate
	_box(Vector3(9.0, 0.12, Z1 - Z0 - 1.0), Vector3(AXIS_X, 0.06, (Z0 + Z1) * 0.5 - 0.5),
		path, true, Basis(), false)
	# cross axis
	_box(Vector3(X1 - X0 - 1.0, 0.12, 8.0), Vector3((X0 + X1) * 0.5, 0.06, -23.0),
		path, true, Basis(), false)
	# a circular walk around the fountain
	var ring: Array[Transform3D] = []
	var slab := BoxMesh.new()
	slab.size = Vector3(2.4, 0.12, 2.4)
	for i in range(48):
		var a := TAU * float(i) / 48.0
		var r := 9.4
		var b := Basis(Vector3.UP, a)
		ring.append(Transform3D(b, FOUNTAIN + Vector3(cos(a) * r, 0.06, sin(a) * r)))
	_scatter(slab, mats["cobble"], ring)

	# gravel edging along the main path
	var edge := BoxMesh.new()
	edge.size = Vector3(1.0, 0.1, 1.0)
	var edges: Array[Transform3D] = []
	for i in range(44):
		var z := Z0 + 1.0 + i * ((Z1 - Z0 - 2.0) / 44.0)
		for side_v in [-5.2, 5.2]:
			var side: float = side_v
			edges.append(Transform3D(Basis(), Vector3(AXIS_X + side, 0.05, z)))
	_scatter(edge, mats["gravel"], edges)


func _build_fountain() -> void:
	var stone: Material = mats["stone"]
	# stepped basin
	_cylinder(7.0, 0.5, FOUNTAIN + Vector3(0.0, 0.25, 0.0), mats["cobble"], true, 28)
	_cylinder(6.4, 0.9, FOUNTAIN + Vector3(0.0, 0.45, 0.0), stone, true, 28)
	_cylinder(5.9, 0.86, FOUNTAIN + Vector3(0.0, 0.48, 0.0), mats["water"], false, 28)
	# central pedestal and bowls
	_cylinder(1.5, 1.4, FOUNTAIN + Vector3(0.0, 1.3, 0.0), stone, true, 20)
	_cylinder(2.6, 0.35, FOUNTAIN + Vector3(0.0, 2.1, 0.0), stone, true, 24)
	_cylinder(0.8, 1.6, FOUNTAIN + Vector3(0.0, 3.0, 0.0), stone, true, 16)
	_cylinder(1.6, 0.28, FOUNTAIN + Vector3(0.0, 3.9, 0.0), stone, true, 20)
	_cylinder(0.35, 1.2, FOUNTAIN + Vector3(0.0, 4.6, 0.0), mats["gold"], true, 12)
	var top := MeshInstance3D.new()
	var sm := SphereMesh.new()
	sm.radius = 0.6
	sm.height = 1.2
	top.mesh = sm
	top.position = FOUNTAIN + Vector3(0.0, 5.4, 0.0)
	top.material_override = mats["gold"]
	add_child(top)
	var body := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var shape := SphereShape3D.new()
	shape.radius = 0.6
	cs.shape = shape
	body.add_child(cs)
	top.add_child(body)

	# the fountain is lit from within: the one thing you see from anywhere
	var glow := OmniLight3D.new()
	glow.position = FOUNTAIN + Vector3(0.0, 1.2, 0.0)
	glow.light_color = Color(0.55, 0.78, 0.92)
	glow.light_energy = 3.2
	glow.omni_range = 16.0
	glow.omni_attenuation = 1.2
	glow.shadow_enabled = false
	add_child(glow)
	lights.append(glow)


func _build_parterres() -> void:
	# Four hedge parterres, one in each quadrant of the crossing. The blocks are
	# instanced; the collision is one box per run.
	var hedge := BoxMesh.new()
	hedge.size = Vector3(1.6, 1.5, 1.6)
	var blocks: Array[Transform3D] = []

	var quads: Array[Rect2] = [
		Rect2(AXIS_X - 24.0, -19.0, 16.0, 14.0),
		Rect2(AXIS_X + 8.0, -19.0, 16.0, 14.0),
		Rect2(AXIS_X - 24.0, -43.0, 16.0, 15.0),
		Rect2(AXIS_X + 8.0, -43.0, 16.0, 15.0),
	]
	for q in quads:
		# an outline of hedge with an open middle, the way a parterre reads
		var steps_x := int(q.size.x / 1.5)
		var steps_z := int(q.size.y / 1.5)
		for i in range(steps_x + 1):
			var x := q.position.x + q.size.x * float(i) / float(steps_x)
			for z_v in [q.position.y, q.end.y]:
				var z: float = z_v
				blocks.append(Transform3D(Basis(), Vector3(x, 0.75, z)))
		for i in range(1, steps_z):
			var z2 := q.position.y + q.size.y * float(i) / float(steps_z)
			for x_v in [q.position.x, q.end.x]:
				var x2: float = x_v
				blocks.append(Transform3D(Basis(), Vector3(x2, 0.75, z2)))
		# solid collision for the four runs
		_hedge_collider(Vector3(q.get_center().x, 0.75, q.position.y),
			Vector3(q.size.x + 1.6, 1.5, 1.6))
		_hedge_collider(Vector3(q.get_center().x, 0.75, q.end.y),
			Vector3(q.size.x + 1.6, 1.5, 1.6))
		_hedge_collider(Vector3(q.position.x, 0.75, q.get_center().y),
			Vector3(1.6, 1.5, q.size.y))
		_hedge_collider(Vector3(q.end.x, 0.75, q.get_center().y),
			Vector3(1.6, 1.5, q.size.y))

	_scatter(hedge, mats["foliage"], blocks, true)

	# flower beds inside each parterre
	var bloom := BoxMesh.new()
	bloom.size = Vector3(0.34, 0.30, 0.34)
	var blooms: Array[Transform3D] = []
	for q in quads:
		for i in range(70):
			var x := _rng.randf_range(q.position.x + 2.0, q.end.x - 2.0)
			var z := _rng.randf_range(q.position.y + 2.0, q.end.y - 2.0)
			var b := Basis(Vector3.UP, _rng.randf_range(0.0, TAU))
			b = b.scaled(Vector3.ONE * _rng.randf_range(0.8, 1.6))
			blooms.append(Transform3D(b, Vector3(x, 0.2, z)))
	_scatter(bloom, MaterialLib.plain(Color(0.72, 0.32, 0.38), 0.75), blooms)


func _hedge_collider(pos: Vector3, size: Vector3) -> void:
	var body := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var shape := BoxShape3D.new()
	shape.size = size
	cs.shape = shape
	body.add_child(cs)
	body.position = pos
	add_child(body)


# ---------------------------------------------------------------------------
# trees, built from primitives
# ---------------------------------------------------------------------------

## Poly Haven's trees are hundreds of megabytes of leaf cards each, which is not
## a sensible trade for a phone build. A trunk, a few branches and some foliage
## clusters read correctly at night and cost almost nothing.
func _tree(pos: Vector3, height: float) -> void:
	var trunk_r := height * 0.045
	var trunk := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = trunk_r * 0.6
	cm.bottom_radius = trunk_r
	cm.height = height * 0.62
	cm.radial_segments = 10
	trunk.mesh = cm
	trunk.position = pos + Vector3(0.0, height * 0.31, 0.0)
	trunk.material_override = mats["bark"]
	add_child(trunk)
	var body := StaticBody3D.new()
	var cs := CollisionShape3D.new()
	var shape := CylinderShape3D.new()
	shape.radius = trunk_r
	shape.height = height * 0.62
	cs.shape = shape
	body.add_child(cs)
	trunk.add_child(body)

	# branches
	for i in range(4):
		var a := TAU * float(i) / 4.0 + _rng.randf_range(-0.4, 0.4)
		var lean := _rng.randf_range(0.5, 0.85)
		var br := MeshInstance3D.new()
		var bm := CylinderMesh.new()
		bm.top_radius = trunk_r * 0.20
		bm.bottom_radius = trunk_r * 0.5
		bm.height = height * 0.34
		bm.radial_segments = 6
		br.mesh = bm
		var dir := Vector3(cos(a) * lean, 1.0, sin(a) * lean).normalized()
		var base := pos + Vector3(0.0, height * 0.5, 0.0)
		br.position = base + dir * height * 0.17
		br.basis = Basis(Vector3(-dir.z, 0.0, dir.x).normalized(),
			acos(clampf(dir.y, -1.0, 1.0)))
		br.material_override = mats["bark"]
		add_child(br)

	# canopy: overlapping squashed spheres
	var canopy_y := pos.y + height * 0.72
	for i in range(5):
		var leaf := MeshInstance3D.new()
		var sm := SphereMesh.new()
		sm.radius = 0.5
		sm.height = 1.0
		sm.radial_segments = 12
		sm.rings = 7
		leaf.mesh = sm
		var off := Vector3(_rng.randf_range(-1.0, 1.0), _rng.randf_range(-0.5, 0.7),
			_rng.randf_range(-1.0, 1.0)) * height * 0.16
		leaf.position = Vector3(pos.x, canopy_y, pos.z) + off
		var r := height * _rng.randf_range(0.22, 0.34)
		leaf.scale = Vector3(r, r * 0.72, r)
		leaf.material_override = mats["foliage"]
		add_child(leaf)
	# one collider for the whole canopy, so it can be climbed and stood on
	var cbody := StaticBody3D.new()
	var ccs := CollisionShape3D.new()
	var csh := SphereShape3D.new()
	csh.radius = height * 0.30
	ccs.shape = csh
	cbody.add_child(ccs)
	cbody.position = Vector3(pos.x, canopy_y, pos.z)
	add_child(cbody)


func _build_trees() -> void:
	# two avenues flanking the central axis, plus corner groups
	for i in range(9):
		var z := -6.0 - i * 4.4
		_tree(Vector3(AXIS_X - 13.0, 0.0, z), _rng.randf_range(7.5, 10.5))
		_tree(Vector3(AXIS_X + 13.0, 0.0, z), _rng.randf_range(7.5, 10.5))
	var corners: Array[Vector2] = [Vector2(X0 + 7.0, Z0 + 7.0), Vector2(X1 - 7.0, Z0 + 7.0),
		Vector2(X0 + 7.0, -8.0), Vector2(X1 - 7.0, -8.0)]
	for corner in corners:
		for i in range(3):
			_tree(Vector3(corner.x + _rng.randf_range(-4.0, 4.0), 0.0,
				corner.y + _rng.randf_range(-4.0, 4.0)), _rng.randf_range(6.0, 11.0))


# ---------------------------------------------------------------------------

func _build_lanterns() -> void:
	# along the axis, around the fountain, and by the gate
	var spots: Array[Vector3] = []
	for i in range(7):
		var z := -4.0 - i * 6.0
		spots.append(Vector3(AXIS_X - 6.0, 0.0, z))
		spots.append(Vector3(AXIS_X + 6.0, 0.0, z))
	for i in range(6):
		var a := TAU * float(i) / 6.0
		spots.append(FOUNTAIN + Vector3(cos(a) * 11.5, 0.0, sin(a) * 11.5))
	spots.append(Vector3(AXIS_X - 5.0, 0.0, Z0 + 3.0))
	spots.append(Vector3(AXIS_X + 5.0, 0.0, Z0 + 3.0))

	for p in spots:
		_lantern(p)


func _lantern(base: Vector3) -> void:
	var stone: Material = mats["stone"]
	var h := 4.2
	_box(Vector3(0.7, 0.4, 0.7), base + Vector3(0.0, 0.2, 0.0), stone)
	_cylinder(0.13, h - 0.6, base + Vector3(0.0, 0.4 + (h - 0.6) * 0.5, 0.0),
		mats["gold"], true, 8)
	# The housing is the glow. It used to be an opaque gold box with the bulb
	# hidden inside it, so the lanterns lit the ground but never looked lit.
	_box(Vector3(0.66, 0.10, 0.66), base + Vector3(0.0, h + 0.55, 0.0),
		mats["gold"], true, Basis(), false)
	_box(Vector3(0.54, 0.72, 0.54), base + Vector3(0.0, h + 0.12, 0.0),
		MaterialLib.emissive(Color(1.0, 0.84, 0.58), 2.6), true, Basis(), false)
	for corner in [Vector3(0.28, 0.0, 0.28), Vector3(-0.28, 0.0, 0.28),
			Vector3(0.28, 0.0, -0.28), Vector3(-0.28, 0.0, -0.28)]:
		_box(Vector3(0.07, 0.78, 0.07), base + Vector3(0.0, h + 0.12, 0.0) + corner,
			mats["gold"], false, Basis(), false)
	var bulb := MeshInstance3D.new()
	var sm := SphereMesh.new()
	sm.radius = 0.2
	sm.height = 0.4
	sm.radial_segments = 10
	sm.rings = 6
	bulb.mesh = sm
	bulb.position = base + Vector3(0.0, h + 0.1, 0.0)
	bulb.material_override = MaterialLib.emissive(Color(1.0, 0.92, 0.74), 7.0)
	bulb.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(bulb)

	var lamp := OmniLight3D.new()
	lamp.position = base + Vector3(0.0, h + 0.1, 0.0)
	lamp.light_color = Color(1.0, 0.87, 0.70)
	lamp.light_energy = 3.4
	lamp.omni_range = 13.0
	lamp.omni_attenuation = 1.2
	lamp.shadow_enabled = false
	lamp.distance_fade_enabled = true
	lamp.distance_fade_begin = 45.0
	lamp.distance_fade_length = 12.0
	add_child(lamp)
	lights.append(lamp)


func _build_ornament() -> void:
	var stone: Material = mats["stone"]
	# statues on plinths at the four crossing corners
	var plinths: Array[Vector2] = [Vector2(AXIS_X - 8.0, -19.0), Vector2(AXIS_X + 8.0, -19.0),
		Vector2(AXIS_X - 8.0, -27.5), Vector2(AXIS_X + 8.0, -27.5)]
	for spot in plinths:
		_box(Vector3(1.8, 2.2, 1.8), Vector3(spot.x, 1.1, spot.y), stone)
		_box(Vector3(2.1, 0.25, 2.1), Vector3(spot.x, 2.32, spot.y), mats["cobble"])

	# stone benches facing the fountain
	for i in range(6):
		var a := TAU * float(i) / 6.0 + 0.5
		var p := FOUNTAIN + Vector3(cos(a) * 12.6, 0.0, sin(a) * 12.6)
		var b := Basis(Vector3.UP, -a + PI * 0.5)
		_box(Vector3(2.6, 0.45, 0.7), p + Vector3(0.0, 0.45, 0.0), stone, true, b)
		_box(Vector3(2.6, 0.7, 0.22), p + b * Vector3(0.0, 1.0, -0.35), stone, true, b)

	# big planters flanking the terrace steps
	for x_v2 in [22.0, 38.0]:
		var x: float = x_v2
		for i in range(2):
			var z := -8.0 - i * 3.0
			_cylinder(1.0, 1.2, Vector3(x, 0.6, z), mats["cobble"], true, 14)
			var bush := MeshInstance3D.new()
			var sm := SphereMesh.new()
			sm.radius = 0.5
			sm.height = 1.0
			sm.radial_segments = 12
			sm.rings = 7
			bush.mesh = sm
			bush.position = Vector3(x, 1.9, z)
			bush.scale = Vector3(1.5, 1.3, 1.5)
			bush.material_override = mats["foliage"]
			add_child(bush)

	# obelisks marking the far end of the axis
	for x_v3 in [AXIS_X - 7.0, AXIS_X + 7.0]:
		var x: float = x_v3
		_box(Vector3(1.6, 0.6, 1.6), Vector3(x, 0.3, Z0 + 8.0), stone)
		_box(Vector3(1.1, 6.0, 1.1), Vector3(x, 3.3, Z0 + 8.0), stone)
		_box(Vector3(0.5, 1.2, 0.5), Vector3(x, 6.8, Z0 + 8.0), mats["gold"], true,
			Basis(), false)

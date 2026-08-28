class_name Terrain
extends Node3D
## The ground under everything, and the edge of the world.
##
## Before this existed the only solid ground was the palace floor and the garden
## lawn. Step off the north or the east side of the building and there was
## nothing underneath at all, so you fell forever. This lays a continuous
## 204 x 204 m field under the whole scene and closes it in with a treeline, so
## there is no direction you can walk in and find a hole.

const X0 := -72.0
const X1 := 132.0
const Z0 := -108.0
const Z1 := 96.0
const TILE := 25.5
const WALL_H := 11.0

var _rng := RandomNumberGenerator.new()


func _ready() -> void:
	_rng.seed = 771
	_build_ground()
	_build_edge()


func _box(size: Vector3, pos: Vector3, mat: Material, collide := true) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.position = pos
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


func _build_ground() -> void:
	var grass := MaterialLib.surface("grass", 3.2, true, 1.2, Color(0.56, 0.62, 0.44))
	var cols := int(ceilf((X1 - X0) / TILE))
	var rows := int(ceilf((Z1 - Z0) / TILE))
	for cx in range(cols):
		for cz in range(rows):
			# Top face at y = -0.03, three centimetres under the palace floor.
			# Flush at exactly 0 the two slabs are coplanar and z-fight, which put
			# lawn inside the grand hall; three centimetres is invisible to walk
			# over and settles the depth test.
			_box(Vector3(TILE, 1.2, TILE),
				Vector3(X0 + TILE * (cx + 0.5), -0.63, Z0 + TILE * (cz + 0.5)), grass)


func _build_edge() -> void:
	# A treeline around the field. Visible, so walking into it reads as the edge
	# of the grounds rather than an invisible wall.
	var foliage := MaterialLib.surface("foliage", 2.2, true, 1.4, Color(0.46, 0.56, 0.36))
	var trunk := MaterialLib.surface("chitin", 1.0, true, 1.2, Color(0.40, 0.34, 0.28))
	var w := X1 - X0
	var d := Z1 - Z0
	var cx := (X0 + X1) * 0.5
	var cz := (Z0 + Z1) * 0.5

	_box(Vector3(w + 8.0, WALL_H, 4.0), Vector3(cx, WALL_H * 0.5, Z0 - 2.0), foliage)
	_box(Vector3(w + 8.0, WALL_H, 4.0), Vector3(cx, WALL_H * 0.5, Z1 + 2.0), foliage)
	_box(Vector3(4.0, WALL_H, d + 8.0), Vector3(X0 - 2.0, WALL_H * 0.5, cz), foliage)
	_box(Vector3(4.0, WALL_H, d + 8.0), Vector3(X1 + 2.0, WALL_H * 0.5, cz), foliage)

	# a scatter of trunks in front of it so the treeline has some depth
	var mm_t: Array[Transform3D] = []
	var trunk_mesh := CylinderMesh.new()
	trunk_mesh.top_radius = 0.30
	trunk_mesh.bottom_radius = 0.42
	trunk_mesh.height = 7.0
	trunk_mesh.radial_segments = 8
	for i in range(90):
		var edge := _rng.randi_range(0, 3)
		var p := Vector3.ZERO
		match edge:
			0: p = Vector3(_rng.randf_range(X0, X1), 3.5, Z0 + _rng.randf_range(1.0, 7.0))
			1: p = Vector3(_rng.randf_range(X0, X1), 3.5, Z1 - _rng.randf_range(1.0, 7.0))
			2: p = Vector3(X0 + _rng.randf_range(1.0, 7.0), 3.5, _rng.randf_range(Z0, Z1))
			_: p = Vector3(X1 - _rng.randf_range(1.0, 7.0), 3.5, _rng.randf_range(Z0, Z1))
		mm_t.append(Transform3D(Basis(Vector3.UP, _rng.randf_range(0.0, TAU)), p))
	_scatter(trunk_mesh, trunk, mm_t)

	var canopy := SphereMesh.new()
	canopy.radius = 0.5
	canopy.height = 1.0
	canopy.radial_segments = 10
	canopy.rings = 6
	var mm_c: Array[Transform3D] = []
	for xf in mm_t:
		var b := Basis().scaled(Vector3(5.5, 4.0, 5.5) * _rng.randf_range(0.8, 1.3))
		mm_c.append(Transform3D(b, xf.origin + Vector3(0.0, 5.0, 0.0)))
	_scatter(canopy, MaterialLib.surface("foliage", 2.0, true, 1.4,
		Color(0.36, 0.47, 0.30)), mm_c)


func _scatter(mesh: Mesh, mat: Material, transforms: Array[Transform3D]) -> void:
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

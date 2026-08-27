class_name MaterialLib
extends RefCounted
## Builds StandardMaterial3D instances from the Poly Haven CC0 texture sets in
## res://assets/textures/<name>/{Diffuse,nor_gl,Rough,AO,arm}.jpg
##
## Architecture uses world-space triplanar mapping so that texture detail lines
## up across separate wall/floor boxes with no visible seams and no UV work.

const TEX_ROOT := "res://assets/textures/"

static var _cache: Dictionary = {}


static func _tex(set_name: String, map_name: String) -> Texture2D:
	var path := "%s%s/%s.jpg" % [TEX_ROOT, set_name, map_name]
	if not ResourceLoader.exists(path):
		return null
	return load(path) as Texture2D


## tile_meters: how many metres one texture repeat covers. Smaller = denser detail.
## tint multiplies the albedo, which is how rooms get their own wall colour and
## how the over-saturated pine floor is pulled back to something believable.
static func surface(set_name: String, tile_meters: float = 2.0, triplanar: bool = true,
		normal_strength: float = 1.0, tint: Color = Color.WHITE) -> StandardMaterial3D:
	var key := "%s|%.3f|%s|%.2f|%s" % [set_name, tile_meters, triplanar, normal_strength, tint]
	if _cache.has(key):
		return _cache[key]

	var m := StandardMaterial3D.new()
	var diff := _tex(set_name, "Diffuse")
	if diff:
		m.albedo_texture = diff
		m.albedo_color = tint
	else:
		m.albedo_color = Color(0.5, 0.48, 0.45) * tint

	var nor := _tex(set_name, "nor_gl")
	if nor:
		m.normal_enabled = true
		m.normal_texture = nor
		m.normal_scale = normal_strength

	# Poly Haven ships either separate Rough/AO maps or a packed ARM map
	# (R = ambient occlusion, G = roughness, B = metallic).
	var arm := _tex(set_name, "arm")
	if arm:
		m.roughness_texture = arm
		m.roughness_texture_channel = BaseMaterial3D.TEXTURE_CHANNEL_GREEN
		m.ao_enabled = true
		m.ao_texture = arm
		m.ao_texture_channel = BaseMaterial3D.TEXTURE_CHANNEL_RED
		m.ao_light_affect = 0.7
	else:
		var rough := _tex(set_name, "Rough")
		if rough:
			m.roughness_texture = rough
			m.roughness_texture_channel = BaseMaterial3D.TEXTURE_CHANNEL_RED
		var ao := _tex(set_name, "AO")
		if ao:
			m.ao_enabled = true
			m.ao_texture = ao
			m.ao_texture_channel = BaseMaterial3D.TEXTURE_CHANNEL_RED
			m.ao_light_affect = 0.7

	var s := 1.0 / maxf(tile_meters, 0.01)
	if triplanar:
		m.uv1_triplanar = true
		m.uv1_world_triplanar = true
		m.uv1_triplanar_sharpness = 2.0
	m.uv1_scale = Vector3(s, s, s)
	m.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	m.specular_mode = BaseMaterial3D.SPECULAR_SCHLICK_GGX

	_cache[key] = m
	return m


## Local-space (non world) triplanar — for objects that move.
static func object_surface(set_name: String, tile_meters: float = 0.5,
		normal_strength: float = 1.0, tint: Color = Color.WHITE) -> StandardMaterial3D:
	var key := "obj|%s|%.3f|%.2f|%s" % [set_name, tile_meters, normal_strength, tint]
	if _cache.has(key):
		return _cache[key]
	var m := surface(set_name, tile_meters, true, normal_strength, tint) \
		.duplicate() as StandardMaterial3D
	m.uv1_world_triplanar = false
	_cache[key] = m
	return m


static func plain(color: Color, roughness: float = 0.7, metallic: float = 0.0) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = color
	m.roughness = roughness
	m.metallic = metallic
	return m


static func glass() -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.albedo_color = Color(0.55, 0.62, 0.72, 0.16)
	m.roughness = 0.05
	m.metallic = 0.25
	m.cull_mode = BaseMaterial3D.CULL_DISABLED
	m.shading_mode = BaseMaterial3D.SHADING_MODE_PER_PIXEL
	return m


static func emissive(color: Color, energy: float = 2.0) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = color
	m.emission_enabled = true
	m.emission = color
	m.emission_energy_multiplier = energy
	m.roughness = 0.4
	return m

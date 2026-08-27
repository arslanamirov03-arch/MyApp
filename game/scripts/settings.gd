extends Node
## Global, persisted player settings (autoload singleton "Settings").

const SAVE_PATH := "user://settings.cfg"

signal quality_changed(level: int)

enum Quality { LOW, MEDIUM, HIGH }

var look_sensitivity: float = 1.0
var invert_y: bool = false
var quality: int = Quality.HIGH
var show_fps: bool = false
var camera_distance: float = 1.0


func _ready() -> void:
	load_settings()


func load_settings() -> void:
	var cfg := ConfigFile.new()
	if cfg.load(SAVE_PATH) != OK:
		return
	look_sensitivity = cfg.get_value("input", "sensitivity", look_sensitivity)
	invert_y = cfg.get_value("input", "invert_y", invert_y)
	camera_distance = cfg.get_value("input", "camera_distance", camera_distance)
	quality = cfg.get_value("video", "quality", quality)
	show_fps = cfg.get_value("video", "show_fps", show_fps)


func save_settings() -> void:
	var cfg := ConfigFile.new()
	cfg.set_value("input", "sensitivity", look_sensitivity)
	cfg.set_value("input", "invert_y", invert_y)
	cfg.set_value("input", "camera_distance", camera_distance)
	cfg.set_value("video", "quality", quality)
	cfg.set_value("video", "show_fps", show_fps)
	cfg.save(SAVE_PATH)


func set_quality(level: int) -> void:
	quality = clampi(level, Quality.LOW, Quality.HIGH)
	save_settings()
	quality_changed.emit(quality)


func quality_name() -> String:
	match quality:
		Quality.LOW: return "Low"
		Quality.MEDIUM: return "Medium"
		_: return "High"

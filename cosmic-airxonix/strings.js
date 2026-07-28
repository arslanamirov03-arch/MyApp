// Every player-visible string lives here. Switching language is a data change,
// never a code change (game-design-system.md §10.3).

export const LANGS = ["ru", "en"];

const DICT = {
  ru: {
    lang_name: "Русский",
    title: "COSMIC AIRXONIX",
    subtitle: "Отвоюй галактику у планет",
    loading: "Загрузка космоса…",
    tap_to_start: "Коснись экрана, чтобы начать",
    press_to_start: "Нажми пробел или коснись экрана",
    how_title: "Как играть",
    how_1: "Веди корабль в чёрный космос и возвращайся на освоенную территорию — замкнутый участок станет твоим, и в нём вспыхнет Млечный Путь.",
    how_2: "Планета, задевшая твой светящийся след, — потеря корабля. Луны-охотники бегают по уже освоенной территории.",
    how_3: "Захвати 75% поля, чтобы пройти уровень.",
    ctrl_touch: "Пальцем: держи и веди в любом месте экрана — появится джойстик. Кнопка справа внизу — ускорение.",
    ctrl_keys: "Клавиши: стрелки или WASD, пробел — ускорение, P — пауза.",
    ctrl_pad: "Геймпад: крестовина или левый стик, A — ускорение.",
    btn_play: "Играть",
    btn_resume: "Продолжить",
    btn_restart: "Заново",
    btn_menu: "В меню",
    btn_next: "Дальше",
    paused: "Пауза",
    level: "Уровень",
    score: "Счёт",
    best: "Рекорд",
    lives: "Корабли",
    claimed: "Захвачено",
    target: "цель",
    level_clear: "Уровень пройден!",
    area_bonus: "Бонус за площадь",
    life_bonus: "Бонус за корабли",
    game_over: "Игра окончена",
    final_score: "Итоговый счёт",
    new_best: "Новый рекорд!",
    ships_left: "Осталось кораблей",
    boost: "УСКОРЕНИЕ",
    settings: "Настройки",
    sound: "Звук",
    shake: "Тряска экрана",
    language: "Язык",
    on: "вкл",
    off: "выкл",
    big_capture: "Крупный захват!",
    extra_life: "Новый корабль!",
    field_note: "Поле 100 × 50 — в пять раз больше классического",
  },
  en: {
    lang_name: "English",
    title: "COSMIC AIRXONIX",
    subtitle: "Claim the galaxy back from the planets",
    loading: "Loading the cosmos…",
    tap_to_start: "Tap the screen to start",
    press_to_start: "Press space or tap the screen",
    how_title: "How to play",
    how_1: "Fly out into the black void and return to claimed ground — the area you close off becomes yours and the Milky Way blazes up inside it.",
    how_2: "A planet touching your glowing trail costs you a ship. Hunter moons roam the ground you already claimed.",
    how_3: "Claim 75% of the field to clear the level.",
    ctrl_touch: "Touch: hold and drag anywhere — a stick appears under your thumb. Bottom-right button is boost.",
    ctrl_keys: "Keys: arrows or WASD, space to boost, P to pause.",
    ctrl_pad: "Gamepad: d-pad or left stick, A to boost.",
    btn_play: "Play",
    btn_resume: "Resume",
    btn_restart: "Restart",
    btn_menu: "Menu",
    btn_next: "Next",
    paused: "Paused",
    level: "Level",
    score: "Score",
    best: "Best",
    lives: "Ships",
    claimed: "Claimed",
    target: "target",
    level_clear: "Level cleared!",
    area_bonus: "Area bonus",
    life_bonus: "Ship bonus",
    game_over: "Game over",
    final_score: "Final score",
    new_best: "New best!",
    ships_left: "Ships left",
    boost: "BOOST",
    settings: "Settings",
    sound: "Sound",
    shake: "Screen shake",
    language: "Language",
    on: "on",
    off: "off",
    big_capture: "Big capture!",
    extra_life: "Extra ship!",
    field_note: "A 100 × 50 field — five times the classic one",
  },
};

let current = "en";

export function setLang(code) {
  if (DICT[code]) current = code;
}

export function getLang() {
  return current;
}

export function detectLang() {
  const nav = (navigator.languages && navigator.languages[0]) || navigator.language || "en";
  return nav.toLowerCase().startsWith("ru") ? "ru" : "en";
}

export function t(key) {
  const d = DICT[current] || DICT.en;
  return d[key] !== undefined ? d[key] : (DICT.en[key] !== undefined ? DICT.en[key] : key);
}

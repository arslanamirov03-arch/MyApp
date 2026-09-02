/* Sprechzeit — Tageszitate.
 * Ein Zitat pro Tag, 30 Tage lang. Was noch nicht dran war, bleibt verdeckt.
 * d = deutsch, r = russische Übersetzung, a = Urheber
 */
window.DATA = (function () {

  var QUOTES = [
    { d: 'Der Weg ist das Ziel.', r: 'Путь — это и есть цель.', a: 'Konfuzius' },
    { d: 'Die Grenzen meiner Sprache bedeuten die Grenzen meiner Welt.', r: 'Границы моего языка — границы моего мира.', a: 'Ludwig Wittgenstein' },
    { d: 'Wer will, findet Wege. Wer nicht will, findet Gründe.', r: 'Кто хочет — ищет способ. Кто не хочет — ищет причину.', a: 'Sprichwort' },
    { d: 'Aller Anfang ist schwer.', r: 'Любое начало трудно.', a: 'Sprichwort' },
    { d: 'Es ist nicht wenig Zeit, die wir haben, sondern es ist viel Zeit, die wir nicht nutzen.', r: 'У нас не мало времени — мы просто много его не используем.', a: 'Seneca' },
    { d: 'Übung macht den Meister.', r: 'Практика делает мастера.', a: 'Sprichwort' },
    { d: 'Der Anfang ist die Hälfte des Ganzen.', r: 'Начало — половина дела.', a: 'Aristoteles' },
    { d: 'Steter Tropfen höhlt den Stein.', r: 'Капля точит камень.', a: 'Sprichwort' },
    { d: 'Man muss das Unmögliche versuchen, um das Mögliche zu erreichen.', r: 'Нужно пытаться сделать невозможное, чтобы достичь возможного.', a: 'Hermann Hesse' },
    { d: 'Nur wer sein Ziel kennt, findet den Weg.', r: 'Только тот, кто знает свою цель, находит путь.', a: 'Laozi' },
    { d: 'Wer nicht wagt, der nicht gewinnt.', r: 'Кто не рискует, тот не выигрывает.', a: 'Sprichwort' },
    { d: 'Es ist noch kein Meister vom Himmel gefallen.', r: 'Мастера не падают с неба.', a: 'Sprichwort' },
    { d: 'Eine Reise von tausend Meilen beginnt mit einem einzigen Schritt.', r: 'Путь в тысячу миль начинается с одного шага.', a: 'Laozi' },
    { d: 'Es gibt nichts Gutes, außer man tut es.', r: 'Нет ничего хорошего, пока ты это не сделаешь.', a: 'Erich Kästner' },
    { d: 'Wo ein Wille ist, ist auch ein Weg.', r: 'Где есть воля, там есть и путь.', a: 'Sprichwort' },
    { d: 'Wer kämpft, kann verlieren. Wer nicht kämpft, hat schon verloren.', r: 'Кто борется, может проиграть. Кто не борется, уже проиграл.', a: 'Bertolt Brecht' },
    { d: 'Man lernt nie aus.', r: 'Учиться никогда не поздно.', a: 'Sprichwort' },
    { d: 'Sprich, damit ich dich sehe.', r: 'Заговори, чтобы я тебя увидел.', a: 'Sokrates' },
    { d: 'Geduld bringt Rosen.', r: 'Терпение приносит розы.', a: 'Sprichwort' },
    { d: 'So viele Sprachen du sprichst, so oft bist du Mensch.', r: 'Сколько языков ты знаешь, столько раз ты человек.', a: 'Sprichwort' },
    { d: 'Nicht weil es schwer ist, wagen wir es nicht, sondern weil wir es nicht wagen, ist es schwer.', r: 'Не потому трудно, что мы не решаемся, а потому не решаемся, что трудно.', a: 'Seneca' },
    { d: 'Der frühe Vogel fängt den Wurm.', r: 'Ранняя птица ловит червя.', a: 'Sprichwort' },
    { d: 'Wer viel fragt, lernt viel.', r: 'Кто много спрашивает, много узнаёт.', a: 'Sprichwort' },
    { d: 'Das Geheimnis des Vorankommens besteht darin, anzufangen.', r: 'Секрет движения вперёд — просто начать.', a: 'Mark Twain' },
    { d: 'Wissen ist Macht.', r: 'Знание — сила.', a: 'Francis Bacon' },
    { d: 'Am Ende wird alles gut. Und wenn es nicht gut ist, ist es noch nicht das Ende.', r: 'В конце всё будет хорошо. Если пока нехорошо — это ещё не конец.', a: 'Oscar Wilde' },
    { d: 'Wer aufhört, besser zu werden, hat aufgehört, gut zu sein.', r: 'Кто перестал становиться лучше, перестал быть хорошим.', a: 'Sprichwort' },
    { d: 'Was du heute kannst besorgen, das verschiebe nicht auf morgen.', r: 'Не откладывай на завтра то, что можешь сделать сегодня.', a: 'Sprichwort' },
    { d: 'Erst denken, dann sprechen — aber sprich.', r: 'Сначала подумай, потом говори — но говори.', a: 'Sprichwort' },
    { d: 'Ein Mensch ohne Ziel ist wie ein Schiff ohne Steuer.', r: 'Человек без цели — как корабль без руля.', a: 'Sprichwort' },
    { d: 'Kleine Schritte, aber jeden Tag.', r: 'Маленькие шаги, но каждый день.', a: 'Sprichwort' },
    { d: 'Fehler sind die Sprossen der Leiter.', r: 'Ошибки — это ступени лестницы.', a: 'Sprichwort' },
    { d: 'Wer die Sprache übt, übt das Denken.', r: 'Кто тренирует язык, тренирует мышление.', a: 'Sprichwort' },
    { d: 'Alles, was du willst, liegt hinter der Angst.', r: 'Всё, чего ты хочешь, находится за страхом.', a: 'Sprichwort' },
    { d: 'Zeit, die man gern verschwendet, ist keine verschwendete Zeit.', r: 'Время, потраченное с радостью, не потрачено зря.', a: 'Sprichwort' },
    { d: 'Heute ist der erste Tag vom Rest deines Lebens.', r: 'Сегодня — первый день всей оставшейся жизни.', a: 'Sprichwort' }
  ];

  return { QUOTES: QUOTES };
})();

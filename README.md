# АПТАЙМ

Сюжетная 3D-игра про сисадмина в ташкентском офисе. Сезон 1: «Тень списания».

- Играть в браузере: https://coderuz2026.github.io/uptime-game/
- Android: [последний APK](https://github.com/coderuz2026/uptime-game/releases/latest/download/uptime.apk)

## Как устроены обновления

- `web/` — сама игра (один `index.html` + three.js в `web/vendor`). Каждый push в `main` публикует её на GitHub Pages.
- Приложение при запуске проверяет `version.json` на Pages и показывает экран загрузки с процентами, скачивает изменившиеся файлы и сразу запускает новую версию. Переустанавливать APK не нужно.
- `android/` — оболочка WebView. Новый APK собирается, когда меняется папка `android/` (например, номер в `android/version.txt`), и выкладывается в Releases. Приложение само предложит его скачать.

Модели: three.js (MIT).

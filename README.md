# Tokmak

VPN-клиент для Xray (VLESS, VMess, Trojan, Shadowsocks, Hysteria2) для Android.

Основан на открытом проекте [Chumpy](https://github.com/ChumpyXray/Chumpy)
(© 2026 Miho, лицензия MIT — см. файл `LICENSE`). Оригинальный текст лицензии сохранён.

## Что изменено относительно Chumpy
- Другое имя приложения и `applicationId` (`com.tokmak.client`), поэтому ставится рядом с Chumpy.
- Проверка обновлений смотрит в релизы этого репозитория.
- Сборка и публикация APK через GitHub Actions.

## Установка и обновления
APK лежат в [Releases](../../releases). Для установки обновления поверх старой версии
нужен постоянный ключ подписи (секреты `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`).
Без него CI публикует сборку как pre-release, и встроенное обновление её не предлагает.

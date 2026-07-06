# Сборка NekoBox for Android (локально)

Краткая шпаргалка по окружению, внешним зависимостям и командам для сборки `libcore.aar` и APK.
Актуально для этого форка (в т.ч. с **Speed test**).

---

## Что нужно установить

| Компонент | Версия / примечание |
|-----------|---------------------|
| **Go** | ≥ 1.23 (в `libcore/go.mod` указан toolchain 1.23.6) |
| **JDK** | 17+ (проект собирается Gradle; в CI используется современный JDK) |
| **Android SDK** | API 21+, build-tools |
| **Android NDK** | **25.0.8775105** (жёстко задано в `buildScript/init/env_ndk.sh`) |
| **Git** | для клонирования `sing-box`, `libneko`, gomobile |
| **adb** | для установки APK на устройство/эмулятор |

### Android SDK / NDK

Обычно SDK лежит в `~/Android/Sdk`. Создайте `local.properties` в корне проекта:

```properties
sdk.dir=/home/USER/Android/Sdk
ndk.dir=/home/USER/Android/Sdk/ndk/25.0.8775105
```

NDK можно поставить через Android Studio → SDK Manager → NDK, или:

```bash
export ANDROID_HOME=~/Android/Sdk
# если установлены cmdline-tools:
sdkmanager "ndk;25.0.8775105"
```

Проверка:

```bash
test -f "$ANDROID_HOME/ndk/25.0.8775105/source.properties" && echo "NDK OK"
```

---

## Структура каталогов (важно)

Нативная часть (`libcore`) тянет **два соседних репозитория** через `replace` в `libcore/go.mod`:

```
tmp_project/                          # родительская папка
├── NekoBoxForAndroid/                # этот проект
├── sing-box/                         # форк MatsuriDayo
└── libneko/                          # форк MatsuriDayo
```

Пути относительно `libcore/`:

```
replace github.com/matsuridayo/libneko => ../../libneko
replace github.com/sagernet/sing-box   => ../../sing-box
```

Без этих папок `go build` / `libcore/build.sh` падают с `replacement directory does not exist`.

---

## Внешние репозитории и коммиты

Скрипт `buildScript/lib/core/get_source.sh` клонирует нужные версии в **родительскую** директорию проекта.

Пины заданы в `buildScript/lib/core/get_source_env.sh`:

| Репозиторий | Коммит | Примечание |
|-------------|--------|------------|
| [MatsuriDayo/sing-box](https://github.com/MatsuriDayo/sing-box) | `aed32ee3066cdbc7d471e3e0415c5134088962df` | 1.12.19-neko-1 |
| [MatsuriDayo/libneko](https://github.com/MatsuriDayo/libneko) | `1c47a3af71990a7b2192e03292b4d246c308ef0b` | |

### Ручное клонирование (альтернатива скрипту)

```bash
cd /path/to/parent/of/NekoBoxForAndroid

git clone --no-checkout https://github.com/MatsuriDayo/sing-box.git
cd sing-box && git checkout aed32ee3066cdbc7d471e3e0415c5134088962df && cd ..

git clone --no-checkout https://github.com/MatsuriDayo/libneko.git
cd libneko && git checkout 1c47a3af71990a7b2192e03292b4d246c308ef0b && cd ..
```

### Через скрипт проекта

Из корня `NekoBoxForAndroid` (нужен NDK в `ANDROID_HOME`):

```bash
bash buildScript/lib/core/get_source.sh
```

---

## gomobile (один раз)

Форк gomobile от MatsuriDayo ставится скриптом `libcore/init.sh`:

```bash
export PATH="$(go env GOPATH)/bin:$PATH"
cd NekoBoxForAndroid/libcore
./init.sh
```

Появятся бинарники:

- `$(go env GOPATH)/bin/gomobile-matsuri`
- `$(go env GOPATH)/bin/gobind-matsuri`

Если `gomobile-matsuri: command not found` — добавьте `GOPATH/bin` в `PATH` или вызывайте по полному пути (как в `libcore/build.sh`).

---

## Сборка нативной библиотеки `libcore.aar`

Нужна после любых изменений в `libcore/*.go` (в т.ч. `UrlTestDownload`, speed test).

### Вариант A — обёртка проекта (рекомендуется)

```bash
export ANDROID_HOME=~/Android/Sdk
export PATH="$(go env GOPATH)/bin:$PATH"
cd NekoBoxForAndroid
./run lib core
```

Делает: `get_source` (если настроен) → `libcore/init.sh` → `libcore/build.sh`.

### Вариант B — только пересборка aar

```bash
export ANDROID_HOME=~/Android/Sdk
export PATH="$(go env GOPATH)/bin:$PATH"
cd NekoBoxForAndroid/libcore
./build.sh
```

Результат копируется в:

```
app/libs/libcore.aar
```

Проверка биндинга (пример):

```bash
cd /tmp && unzip -q /path/to/NekoBoxForAndroid/app/libs/libcore.aar classes.jar
javap -classpath classes.jar libcore.Libcore | rg urlTest
```

Ожидается, среди прочего:

- `urlTest(...)`
- `urlTestDownload(...) → DownloadTestResult`

### Теги сборки Go

Из `libcore/build.sh`:

```
with_conntrack, with_gvisor, with_quic, with_wireguard, with_utls, with_clash_api
```

---

## Сборка APK (Gradle)

Из корня `NekoBoxForAndroid`:

```bash
./gradlew :app:assembleFdroidDebug
```

### Product flavors

| Flavor | Назначение |
|--------|------------|
| `fdroid` | F-Droid / локальная отладка |
| `oss` | OSS release |
| `preview` | Preview-сборки |
| `play` | Google Play |

### Типичные задачи

```bash
# Debug (локальная разработка)
./gradlew :app:assembleFdroidDebug

# Release (нужен signing config)
./gradlew :app:assembleOssRelease
./gradlew :app:assembleFdroidRelease
./gradlew :app:assemblePreviewRelease
```

### Где лежит APK

После `assembleFdroidDebug`:

```
app/build/outputs/apk/fdroid/debug/
├── NekoBox-1.4.2-fdroid-arm64-v8a-debug.apk   ← обычный телефон
├── NekoBox-1.4.2-fdroid-armeabi-v7a-debug.apk
├── NekoBox-1.4.2-fdroid-x86_64-debug.apk       ← эмулятор
└── NekoBox-1.4.2-fdroid-x86-debug.apk
```

Версия берётся из `nb4a.properties` (`VERSION_NAME`, `VERSION_CODE`).

### Application ID

| Сборка | Package |
|--------|---------|
| Release | `moe.nb4a` |
| Debug | `moe.nb4a.debug` |

---

## Установка на устройство (adb)

```bash
adb devices

adb install -r app/build/outputs/apk/fdroid/debug/NekoBox-1.4.2-fdroid-arm64-v8a-debug.apk
```

`-r` — переустановка поверх существующей версии (данные приложения сохраняются).

Эмулятор x86_64:

```bash
adb install -r app/build/outputs/apk/fdroid/debug/NekoBox-1.4.2-fdroid-x86_64-debug.apk
```

---

## Полный цикл пересборки (шпаргалка)

После изменений **только в Kotlin/Java/XML**:

```bash
cd NekoBoxForAndroid
./gradlew :app:assembleFdroidDebug
adb install -r app/build/outputs/apk/fdroid/debug/NekoBox-*-arm64-v8a-debug.apk
```

После изменений в **`libcore/*.go`**:

```bash
export ANDROID_HOME=~/Android/Sdk
export PATH="$(go env GOPATH)/bin:$PATH"
cd NekoBoxForAndroid
./run lib core
./gradlew :app:assembleFdroidDebug
adb install -r app/build/outputs/apk/fdroid/debug/NekoBox-*-arm64-v8a-debug.apk
```

---

## Speed test — настройки и логи

Функция добавлена в этом форке. Связанные файлы:

| Слой | Файлы |
|------|--------|
| Native | `libcore/box.go` — `UrlTest`, `UrlTestDownload`, `DownloadTestResult` |
| Kotlin | `TestInstance.kt`, `UrlTest.kt`, `ConfigurationFragment.kt` |
| БД | `ProxyEntity.downloadSpeed`, Room v7 |
| Настройки | `DataStore.speedTestURL`, `speedTestMaxBytes`, `speedTestTimeout` |

Дефолты (если настройки в приложении не менялись):

- URL: `https://speed.cloudflare.com/__down?bytes=52428800` (50 MiB)
- Max bytes: 50 MiB
- Timeout: 20000 ms

**Важно:** настройки speed test **сохраняются** в preferences. После смены дефолтов в коде проверьте значения в приложении: Settings → Speed test URL / max bytes / timeout.

### Логи Speed test (всегда в logcat)

Диагностика пишется через `android.util.Log` с тегом **`SpeedTest`** — не зависит от Log Level в настройках:

```bash
adb logcat -c
adb logcat -s SpeedTest
```

Запустите **Speed test** в группе серверов. Пример строк:

```
SpeedTest: [My VPS] ping=42 ms
SpeedTest: [My VPS] download status=200 setupMs=800 bodyMs=12000 bytes=52428800 speed=4369066 B/s
```

### Внутренние логи NekoBox (`neko.log`)

`Logs.w` / Go `log` после `InitCore` идут в файл `cache/neko.log`, и **отбрасываются**, если Settings → Log Level = `none` (дефолт).

Чтобы смотреть их:

1. Settings → Log Level → `debug`
2. Перезапустить приложение (обязательно)
3. Меню → **Log**, или:

```bash
adb exec-out run-as moe.nb4a.debug cat cache/neko.log | rg 'SpeedTest|UrlTestDownload'
```

На zsh, если `grep` — alias на `ripgrep`, используйте `rg`, а не `grep -E`.

---

## Room / схема БД

При изменении `ProxyEntity` и др. entities:

- версия БД: `SagerDatabase.kt` (сейчас **v7**, поле `downloadSpeed`)
- схемы: `app/schemas/io.nekohasekai.sagernet.database.SagerDatabase/`
- первая сборка после bump версии регенерирует JSON-схему

---

## Частые проблемы

| Симптом | Решение |
|---------|---------|
| `replacement directory ../../sing-box does not exist` | Клонировать `sing-box` и `libneko` в родительскую папку (см. выше) |
| `Error: NDK not found` | Установить NDK 25.0.8775105, прописать `ANDROID_HOME` / `local.properties` |
| `gomobile-matsuri: command not found` | `libcore/init.sh`, добавить `$(go env GOPATH)/bin` в `PATH` |
| Gradle не видит SDK | `local.properties` с `sdk.dir=...` |
| Speed test показывает 0 / пусто | Проверить сохранённые prefs URL/timeout; URL с `bytes=104857600` даёт HTTP 403 |
| Логи Speed test не в logcat | Использовать `adb logcat -s SpeedTest`, не `SagerNet` |
| `grep -E` падает | На машине `grep` = ripgrep; использовать `rg 'pattern'` |

---

## CI (справка)

GitHub Actions (`.github/workflows/`):

1. `./run lib core` — сборка `libcore.aar`
2. `./gradlew app:assemblePreviewRelease` / `assembleOssRelease`

Кэшируется `app/libs/libcore.aar` по хешу исходников `libcore` и build-скриптов.

---

## Полезные ссылки

- [Официальный сайт / документация](https://matsuridayo.github.io)
- [Плагины для протоколов](https://matsuridayo.github.io/nb4a-plugin/)
- Upstream: [MatsuriDayo/NekoBoxForAndroid](https://github.com/MatsuriDayo/NekoBoxForAndroid)

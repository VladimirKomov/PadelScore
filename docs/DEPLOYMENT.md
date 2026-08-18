# Разработка и развёртывание PadelScore

Эта инструкция фиксирует рабочий процесс для HUAWEI WATCH 4 Pro MDS-AL00
(`192.168.101.17:5555`) и устраняет необходимость повторно исследовать способ
подключения, подписи и установки.

## Что фактически устанавливается

Проект содержит два артефакта:

- основной HarmonyOS/OpenHarmony API 8 HAP — эталонная реализация;
- совместимый Android APK — фактически устанавливаемая сборка для текущей
  retail-прошивки часов.

Подписанный HAP корректен, но текущая прошивка не содержит пакета
`com.huawei.gateway` и сервиса `.appframework.AppFrameworkService`. Поэтому
обычная установка HAP завершается ошибкой контейнера. Не повторяйте диагностику
HAP после каждого изменения: для этих часов используйте APK-маршрут ниже.

APK имеет видимое имя `PadelScore`, но сохраняет внутренний package ID
`com.vekom.padelprobe`. Это сделано намеренно: первая диагностическая сборка уже
была установлена с этим ID, и текущая сборка безопасно обновляет её без удаления
приложения. Нельзя менять package ID или ключ подписи без отдельного плана
миграции/переустановки.

## Однократная локальная настройка

В корне проекта должны существовать локальные, исключённые из git файлы:

```text
.local-tools/
  android-sdk/platforms/android-12/android.jar
  android-sdk/build-tools/android-14/...
  r8-9.4.14.jar
signing/
  compat-debug.keystore
  OpenHarmonyApplicationChain.cer
  PadelScoreDebugProfile.p7b
watch-deploy.local.psd1
```

На настроенной машине они уже подготовлены. Для новой машины:

1. Скопируйте `watch-deploy.example.psd1` в `watch-deploy.local.psd1`.
2. Укажите пути к `HdcExternal.exe`, JDK, npm, Android SDK tools и keystore.
3. Перенесите `signing/compat-debug.keystore` из защищённой резервной копии.
   Без исходного ключа Android не разрешит обновлять установленное приложение.
4. Никогда не коммитьте локальный конфиг, `.local-tools/`, `signing/` или
   содержимое каталогов `build/`.

Рабочие версии на этой машине:

- `HdcExternal.exe` 1.0.6 из Huawei SDK 3.1;
- Android API 31 (`android.jar`);
- Android Build Tools с `aapt`, `aapt2`, `zipalign`, `apksigner`;
- стабильный Google R8/D8 9.4.14;
- JBR/JDK из DevEco Studio;
- Node.js из DevEco Studio для legacy Hvigor (wrapper запускается как временная
  `.cjs`-копия, поэтому системный Node.js и `"type": "module"` не мешают сборке);
- API 8 `hap-sign-tool.jar`, `OpenHarmony.p12`, app certificate chain и debug
  provisioning profile для воспроизводимого подписания HAP;
- тот же APK-сертификат SHA-256:
  `88F9D8A07115FD549252BC29F7B15D11493DFF018C6315F3D8888AA3507E7A9C`.

`hdc.exe` из OpenHarmony SDK API 8 не подходит для этой прошивки: TCP-порт
может быть доступен, но `tconn` завершается `Connect failed`. Используйте именно
`HdcExternal.exe`, указанный в локальном конфиге.

## Где вносить изменения

Поддерживаются две реализации, поэтому поведение нужно сохранять синхронным.

| Изменение | HarmonyOS HAP | Совместимый APK | Тесты |
|---|---|---|---|
| Правила счёта | `shared/engine/*.ts` и `entry/src/main/js/MainAbility/engine/*.js` | `compat-android/src/.../MatchEngine.java`, `ScoringStrategy.java` | `tests/scoring.test.ts`, `EngineSelfTest.java` |
| Модель/настройки | `shared/engine/Types.ts`, `Defaults.ts` и JS-копии | `MatchModel.java` | оба набора |
| Сохранение | `shared/persistence/*` и JS-копии | `MatchStore.java` | тесты persistence + smoke restart |
| Интерфейс | `index.hml`, `index.css`, `index.js`, ресурсы | `MainActivity.java` | визуальный smoke-тест |

Для релизного изменения также увеличьте `android:versionCode` и
`android:versionName` в `compat-android/AndroidManifest.xml`.

## Основная команда

Перед запуском разбудите часы и убедитесь, что они подключены к той же Wi-Fi
сети. Затем из PowerShell:

```powershell
Set-Location C:\Users\vekom\DevEcoStudioProjects\PadelScore
.\scripts\deploy-watch.ps1
```

Команда автоматически:

1. запускает 40 тестов основной реализации и 138+ проверок APK-движка;
2. собирает подписанный HAP;
3. собирает и проверяет подпись APK;
4. проверяет точный target HDC;
5. собирает минимальный одноразовый HDB-helper из исходника;
6. передаёт APK, вычисляет lowercase SHA-256 для `--hwhdb` и выполняет
   `pm install -r`;
7. аннулирует временный HDB-ключ и удаляет временные файлы даже при ошибке;
8. запускает `MainActivity` и сохраняет снимок экрана в
   `compat-android/build/smoke-latest.png`.

Скрипт не отключает системные проверки, не изменяет secure settings, не
использует root и не удаляет установленное приложение.

## Быстрые циклы

Полная проверка перед коммитом или передачей сборки:

```powershell
.\scripts\deploy-watch.ps1
```

Быстрый цикл после изменения только совместимого UI/Java-кода:

```powershell
.\scripts\deploy-watch.ps1 -SkipTests -SkipHap
```

Повторная установка уже собранного APK:

```powershell
.\scripts\deploy-watch.ps1 -SkipTests -SkipHap -ReuseApk
```

Сборка и тесты без установки:

```powershell
npm test
.\hvigorw.bat assembleHap --mode module -p product=default -p module=entry@default
.\compat-android\test.ps1 -JavaHome "C:\Program Files\Huawei\DevEco Studio\jbr"
```

## Готовые артефакты

```text
entry/build/default/outputs/default/entry-default-signed.hap
compat-android/build/padelscore-compat-signed.apk
compat-android/build/smoke-latest.png
```

Перед передачей APK проверьте, что скрипт вывел ожидаемый SHA-256 сертификата,
а `git status --short` не показывает ключи, профили или локальные инструменты.

## Типовые проблемы

### Target не найден

```powershell
Test-NetConnection 192.168.101.17 -Port 5555
& "C:\Users\vekom\HuaweiSdk31\hmscore\3.1.0\toolchains\HdcExternal.exe" list targets -v
```

Если порт закрыт или target исчез, разбудите часы, проверьте Wi-Fi и экран
беспроводной отладки. Если на часах показано подтверждение подключения —
подтвердите его и повторите команду.

### `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

APK подписан другим ключом. Не удаляйте приложение автоматически: восстановите
`signing/compat-debug.keystore` из резервной копии.

### `INSTALL_HDB_VERIFY_FAILED`

Не вычисляйте digest вручную. Повторите `deploy-watch.ps1`: он создаёт новый
одноразовый ключ, использует требуемый lowercase digest и очищает его в
`finally`.

### `Failurecontainer is not started`

Это ожидаемая несовместимость HAP-контейнера данной прошивки. Используйте
совместимый APK; переустановка SDK или повторный `hdc tconn` это не исправляет.

### Часы уснули во время smoke-теста

Подключение часто остаётся активным. Разбудите дисплей жестом/кнопкой и повторите
запуск с `-ReuseApk`. Если HDC target пропал, сначала восстановите Wi-Fi target.

## Контрольный чек-лист перед коммитом

- оба набора тестов зелёные;
- HAP и APK успешно собраны;
- APK установлен с тем же package ID и сертификатом;
- приложение запускается, снимок не обрезан круглым экраном;
- Undo и восстановление после `force-stop` проверены при изменении состояния;
- тестовые очки сброшены через UI;
- `git diff --check` и `git status --short` чистые после коммита.

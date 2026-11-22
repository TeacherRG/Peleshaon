# Политика конфиденциальности — Пеле Шаон Луах

**Продукт:** Пеле Шаон Луах (Pele Shaon Luach)  
**Платформа:** Android и Wear OS  
**Статус документа:** предварительная версия; требуется финальная проверка перед публикацией  
**Дата вступления в силу:** будет указана перед публикацией

## 1. Разработчик и контакт

По вопросам конфиденциальности обращайтесь: **Office@niflaot.app**.

## 2. Что делает приложение

Пеле Шаон Луах показывает еврейский календарь, времена дня, связанные с ними сведения и функции напоминаний/будильников. Версия для Wear OS может работать самостоятельно и хранит собственные настройки часов.

В проверенных исходных файлах не обнаружены учётные записи пользователей, рекламные или аналитические SDK, а также реализация отправки пользовательских настроек с часов на телефон. Это вывод по текущим файлам проекта, а не обещание для будущих версий.

## 3. Какие данные и настройки сохраняются

Проверенный код телефона сохраняет настройки локально на устройстве Android в приватном хранилище приложения SharedPreferences. В частности, это могут быть:

- выбранный город из встроенного каталога;
- если пользователь сам задаёт местоположение вручную — введённое название, широта, долгота, часовой пояс и признак нахождения в Израиле;
- выбранная община/обычай (например, Хабад, литовский или сефардский);
- язык приложения и настройки отображения циферблата;
- выбранные времена/отметки на циферблате и параметры отображения;
- параметры будильников: включение, время, дни недели, настройки до времени Шма и режим сигнала;
- список пользовательских будильников и их настройки;
- состояние и отметка времени таймера ожидания между мясным и молочным;
- настройки напоминаний, времени зажигания свечей, первого знакомства с приложением и другие рабочие предпочтения.

Версия Wear OS отдельно сохраняет в приватных настройках часов выбранный город из каталога, общину и настройку минут до зажигания свечей.

Приложение не запрашивает геолокацию устройства в проверенном основном манифесте телефона. Город выбирается из каталога или может быть задан пользователем вручную. Если в будущем появится автоматическое определение местоположения, эта политика должна быть обновлена до выпуска такой версии.

## 4. Разрешения Android

В проверенном манифесте телефона объявлены следующие разрешения:

- `SCHEDULE_EXACT_ALARM` — для планирования точных напоминаний и будильников;
- `POST_NOTIFICATIONS` — для показа уведомлений на поддерживаемых версиях Android;
- `USE_FULL_SCREEN_INTENT` — для отображения экрана сигнала в предусмотренных системой случаях;
- `RECEIVE_BOOT_COMPLETED` — для восстановления предусмотренных приложением задач после перезагрузки устройства;
- `FOREGROUND_SERVICE` и `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — для работы службы сигнала;
- `VIBRATE` — для вибрации при уведомлениях или сигналах.

Эти разрешения используются для функций приложения и сами по себе не означают сбор или передачу персональных данных.

## 5. Сеть и передача третьим лицам

В проверенном основном манифесте телефона и манифесте Wear OS не объявлено разрешение `android.permission.INTERNET`. В проверенных Gradle-файлах зависимостей указаны Android/Compose-компоненты и библиотека KosherJava для календарных расчётов; рекламные, аналитические и crash-reporting SDK в этих файлах не обнаружены.

Таким образом, по проверенным исходникам текущая версия не содержит заявленного приложением сетевого обмена пользовательскими данными и не передаёт выбранные настройки на сервер. Этот вывод необходимо повторно проверить по объединённым манифестам и окончательным APK/AAB перед публикацией. Системные службы Android, включая резервное копирование, рассматриваются отдельно ниже.

Если в будущей версии появятся аккаунты, аналитика, отчёты о сбоях, реклама, облачная синхронизация или другие сетевые компоненты, политика должна быть пересмотрена до выпуска такой версии.

## 6. Резервное копирование Android

В манифесте телефонного приложения указано `android:allowBackup="true"`. В проекте не найдено отдельного файла правил резервного копирования `backup_rules.xml` или `data_extraction_rules.xml`. Поэтому данные приложения могут быть включены в системное резервное копирование Android в зависимости от версии системы, настроек устройства и доступных системных сервисов.

Резервное копирование контролируется Android и настройками пользователя. Перед публикацией разработчику следует отдельно решить, допустимо ли резервное копирование настроек будильников, выбранного города и введённых вручную координат, и при необходимости задать явные правила исключения/включения данных.

## 7. Безопасность и удаление данных

Настройки хранятся в приватном хранилище приложения на соответствующем устройстве. Удаление приложения или очистка его данных Android может удалить локальные настройки и будильники. Если пользователь использует резервное копирование Android, копии могут подчиняться настройкам системного сервиса.

## 8. Дети

Целевая аудитория и возрастные настройки Google Play ещё должны быть проверены и окончательно выбраны разработчиком. До завершения этой проверки мы не заявляем в этой политике, что приложение специально предназначено для детей или исключает детскую аудиторию.

## 9. Изменения политики

Политика будет обновляться при изменениях функциональности, разрешений, способов хранения данных, подключённых SDK, резервного копирования или сетевого взаимодействия. Актуальная версия должна быть доступна по общедоступной HTTPS-ссылке, указанной в Google Play.

---

# Privacy Policy — English

**Product:** Pele Shaon Luach  
**Platforms:** Android and Wear OS  
**Document status:** Preliminary; final review required before publication  
**Effective date:** To be added before publication

## 1. Developer and contact

For privacy questions, contact **Office@niflaot.app**.

## 2. What the app does

Pele Shaon Luach displays the Jewish calendar, daily times, related information, and reminder/alarm features. The Wear OS version can operate independently and stores its own watch settings.

The source files reviewed do not show user accounts, advertising or analytics SDKs, or an implementation that sends user settings from the watch to the phone. This is a finding about the current project files, not a promise about future versions.

## 3. Data and settings stored

The reviewed phone code stores settings locally on the Android device in the app's private SharedPreferences storage. These may include:

- the city selected from the built-in catalog;
- if the user enters a location manually, the entered place name, latitude, longitude, time zone, and Israel-location flag;
- the selected community/custom (such as Chabad, Litvish, or Sephardi);
- app language and clock-display settings;
- selected clock markers and display preferences;
- alarm settings, including enabled state, time, weekdays, settings relative to the end of the Shema time, and alarm mode;
- custom alarm list and settings;
- the state and timestamp of the meat-to-dairy waiting timer;
- reminder settings, candle-lighting offset, onboarding completion, and other operational preferences.

The Wear OS version separately stores the catalog city selection, community, and candle-lighting offset in the watch app's private preferences.

The reviewed main phone manifest does not request device location permission. A city is selected from the catalog or may be entered manually by the user. If automatic location detection is introduced in a future version, this policy must be updated before that version is released.

## 4. Android permissions

The reviewed phone manifest declares:

- `SCHEDULE_EXACT_ALARM` — scheduling exact reminders and alarms;
- `POST_NOTIFICATIONS` — showing notifications on supported Android versions;
- `USE_FULL_SCREEN_INTENT` — displaying the alarm screen in supported system scenarios;
- `RECEIVE_BOOT_COMPLETED` — restoring applicable scheduled tasks after device restart;
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — running the alarm service;
- `VIBRATE` — vibration for notifications or alarms.

These permissions support app features and do not by themselves mean that personal data is collected or transmitted.

## 5. Network access and sharing

The reviewed main phone and Wear OS manifests do not declare `android.permission.INTERNET`. The reviewed Gradle dependency files list Android/Compose components and the KosherJava library for calendar calculations; no advertising, analytics, or crash-reporting SDKs were found in those files.

Based on the source files reviewed, the current version does not contain app-declared network exchange of user data and does not send selected settings to a server. This finding must be checked again against the merged manifests and final APK/AAB before publication. Android system services, including backup, are addressed separately below.

If accounts, analytics, crash reporting, advertising, cloud synchronization, or other network-enabled components are added in a future version, this policy must be revised before that version is released.

## 6. Android backup

The phone app manifest sets `android:allowBackup="true"`. No separate `backup_rules.xml` or `data_extraction_rules.xml` file was found in the project. Android may therefore include app data in system backups depending on the OS version, device settings, and available system backup services.

Backup is controlled by Android and the user's device settings. Before publication, the developer should decide whether alarm settings, selected cities, and manually entered coordinates may be backed up, and configure explicit include/exclude rules if needed.

## 7. Security and deletion

Settings are stored in the app's private storage on the relevant device. Uninstalling the app or clearing its data through Android may delete local settings and alarms. If Android backup is enabled by the user, backup copies may be subject to the system service's settings.

## 8. Children

The target audience and Google Play age settings still need to be reviewed and finalized by the developer. Until that review is complete, this policy does not claim that the app is specifically directed to children or excludes children from its audience.

## 9. Policy changes

This policy will be updated if app functionality, permissions, storage practices, included SDKs, backup configuration, or network behavior change. The current version must be available at a public HTTPS URL entered in Google Play.

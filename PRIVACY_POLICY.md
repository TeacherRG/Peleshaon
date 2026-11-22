# Политика конфиденциальности — Пеле Шаон Луах

**Продукт:** Пеле Шаон Луах (Pele Shaon Luach)  
**Платформа:** Android и Wear OS  
**Статус документа:** предварительная версия для проверки перед закрытым бета-тестированием  
**Дата вступления в силу:** будет указана перед публикацией

## 1. Важное замечание о статусе документа

Эта редакция подготовлена по текущим манифестам и конфигурации проекта. Она не является окончательным юридическим заключением. Перед публикацией разработчик должен проверить фактическое поведение итоговой сборки, объединённый Android-манифест, все подключённые библиотеки и настройки резервного копирования.

Контакт разработчика: **[УКАЖИТЕ ДЕЙСТВУЮЩИЙ EMAIL ДЛЯ ОБРАЩЕНИЙ ПО КОНФИДЕНЦИАЛЬНОСТИ]**

Не публикуйте эту политику, пока вместо квадратных скобок не будет указан действующий адрес электронной почты.

## 2. Какие данные обрабатывает приложение

Приложение предназначено для работы с еврейским календарём, временами дня, Шаббатом, напоминаниями и связанными функциями.

В текущем основном Android-манифесте не объявлено разрешение `android.permission.INTERNET`. Поэтому текущая конфигурация приложения не предусматривает самостоятельный сетевой обмен данными приложением. Это утверждение относится только к проверенной конфигурации проекта и должно быть перепроверено для итогового релизного артефакта, включая объединённый манифест и сторонние компоненты.

Приложение может сохранять настройки и данные, необходимые для работы выбранных пользователем функций, непосредственно на устройстве. Точный перечень сохраняемых полей должен быть подтверждён аудитом исходного кода перед публикацией этой политики.

## 3. Разрешения Android

В проверенном манифесте телефона объявлены следующие разрешения:

- `SCHEDULE_EXACT_ALARM` — для планирования точных напоминаний и будильников;
- `POST_NOTIFICATIONS` — для показа уведомлений на поддерживаемых версиях Android;
- `USE_FULL_SCREEN_INTENT` — для отображения экрана сигнала в предусмотренных системой случаях;
- `RECEIVE_BOOT_COMPLETED` — для восстановления предусмотренных приложением задач после перезагрузки устройства;
- `FOREGROUND_SERVICE` и `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — для работы службы сигнала;
- `VIBRATE` — для вибрации при уведомлениях или сигналах.

Само наличие разрешения не означает, что оно используется для сбора или передачи персональных данных. Разрешения могут запрашиваться системой отдельно, в зависимости от версии Android и действий пользователя.

В проверенном основном манифесте телефона разрешения на доступ к точной или приблизительной геолокации не объявлены. Если в будущей версии появится определение местоположения, этот раздел и политика будут обновлены до выпуска такой версии.

## 4. Резервное копирование Android

В текущем манифесте приложения указано `android:allowBackup="true"`. Это означает, что Android может включать данные приложения в системное резервное копирование в зависимости от версии системы, настроек устройства и доступных пользователю сервисов резервного копирования.

Такое резервное копирование контролируется настройками Android и соответствующими системными сервисами, а не отдельной функцией отправки данных на сервер, реализованной приложением. Перед релизом необходимо проверить правила резервного копирования и решить, какие категории данных допустимо включать в резервные копии.

## 5. Передача данных третьим лицам

В проверенной конфигурации основного манифеста нет разрешения на интернет. По этой причине текущая сборка не должна самостоятельно отправлять данные на удалённые серверы через обычные сетевые API Android.

Это не является гарантией отсутствия любой системной обработки данных устройством. Например, резервное копирование Android описано в разделе 4. Если в приложение будут добавлены аналитика, отчёты о сбоях, реклама, учётные записи, облачная синхронизация или другие сетевые SDK, этот раздел необходимо пересмотреть и явно описать соответствующие категории данных и получателей до выпуска обновления.

## 6. Данные детей

На момент подготовки этого документа отдельная проверка целевой аудитории, возрастных ограничений и требований Google Play Families не завершена. Не следует считать приложение предназначенным или не предназначенным для детей, пока эти настройки не будут определены в Play Console и проверены на соответствие фактическому содержанию приложения.

## 7. Безопасность и хранение

Настройки и рабочие данные приложения могут храниться на устройстве. Пользователь должен учитывать защиту самого устройства, блокировку экрана и настройки системного резервного копирования.

Не удаляйте приложение, если хотите сохранить локальные данные, не проверив предварительно возможность их резервного копирования или переноса.

## 8. Изменения политики

Политика будет обновляться при изменениях функциональности, разрешений, способов хранения данных, подключённых SDK или сетевого взаимодействия. Актуальная версия должна быть доступна по публичной ссылке, указанной в Google Play.

## 9. Контакт

По вопросам конфиденциальности обращайтесь:

**[УКАЖИТЕ ДЕЙСТВУЮЩИЙ EMAIL РАЗРАБОТЧИКА]**

---

# Privacy Policy — English

**Product:** Pele Shaon Luach  
**Platforms:** Android and Wear OS  
**Document status:** Preliminary draft for review before closed beta testing  
**Effective date:** To be added before publication

## 1. Important status notice

This draft is based on the project manifests and configuration currently reviewed. It is not a final legal assessment. Before publication, the developer must review the final app behavior, the merged Android manifest, all included libraries and SDKs, and Android backup configuration.

Developer privacy contact: **[ADD A WORKING PRIVACY CONTACT EMAIL]**

Do not publish this policy until the placeholder above has been replaced with a working email address.

## 2. Data handled by the app

The app is designed for the Jewish calendar, daily times, Shabbat, reminders, and related features.

The main Android manifest currently reviewed does not declare `android.permission.INTERNET`. Therefore, the current project configuration does not provide for the app itself to exchange data over the network. This statement applies only to the reviewed project configuration and must be verified against the final release artifact, including its merged manifest and third-party components.

The app may store settings and information needed for user-selected features locally on the device. The exact list of stored fields must be confirmed by a source-code audit before this policy is published.

## 3. Android permissions

The reviewed phone manifest declares these permissions:

- `SCHEDULE_EXACT_ALARM` — scheduling exact reminders and alarms;
- `POST_NOTIFICATIONS` — showing notifications on supported Android versions;
- `USE_FULL_SCREEN_INTENT` — displaying the alarm screen in supported system scenarios;
- `RECEIVE_BOOT_COMPLETED` — restoring applicable scheduled tasks after device restart;
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — running the alarm service;
- `VIBRATE` — vibration for notifications or alarms.

A declared permission does not by itself mean that the app collects or transmits personal data. Android may request permissions separately depending on the OS version and user actions.

The reviewed main phone manifest does not declare permission to access precise or approximate location. If location access is introduced in a future version, this section and the policy will be updated before that version is released.

## 4. Android backup

The current app manifest sets `android:allowBackup="true"`. Android may therefore include app data in system backups depending on the OS version, device settings, and backup services available to the user.

Such backup is controlled by Android and the relevant system services, rather than by a separate app-operated server upload feature. Before release, the developer must review backup rules and decide which categories of data may be included.

## 5. Sharing with third parties

The reviewed main manifest does not declare the INTERNET permission. Accordingly, the current build is not expected to send data to remote servers through ordinary Android networking APIs on the app's own behalf.

This does not guarantee that the device performs no system-level data processing. For example, Android backup is described in section 4. If analytics, crash reporting, advertising, accounts, cloud synchronization, or other network-enabled SDKs are added, this section must be revised before release to explain the relevant data categories and recipients.

## 6. Children

The target audience, age settings, and Google Play Families requirements have not yet been fully reviewed for this document. The app should not be described as either child-directed or not child-directed until its Play Console audience settings and actual content have been assessed.

## 7. Security and storage

App settings and operational data may be stored on the device. Users should protect their devices with appropriate device security and review their Android backup settings.

If you want to preserve local app data, do not uninstall the app without first checking whether backup or transfer is available.

## 8. Policy changes

This policy will be updated if app functionality, permissions, storage practices, included SDKs, or network behavior change. The current version must be available at a public URL entered in Google Play.

## 9. Contact

For privacy questions, contact:

**[ADD A WORKING DEVELOPER EMAIL ADDRESS]**

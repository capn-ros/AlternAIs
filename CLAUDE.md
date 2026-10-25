# CLAUDE.md — AlternAIs (documentación completa del proyecto)

> Leé este archivo primero. Cubre arquitectura, funciones, convenciones, build y trampas conocidas, para no tener que re-explorar el código ni leer todo el zip. Si cambiás algo estructural, **actualizá este archivo**.

## 1. Qué es

AlternAIs es un contenedor de cuentas de **claude.ai** y **replit.com**: varias cuentas abiertas a la vez, cada una con **sesión aislada** (cookies/storage propios), y cambio entre ellas con un toque/clic. No es oficial de Anthropic, no usa ninguna API: solo abre `https://claude.ai/` en un WebView por cuenta.

Dos apps independientes, sin código compartido (se duplica la lógica a propósito):

| Plataforma | Stack | Carpeta | Aislamiento de sesión |
|---|---|---|---|
| Android | Kotlin, vistas XML + Material 3, WebView | `android/` | **Perfiles de WebView** (`androidx.webkit` `ProfileStore`, nombre `acc_<id>`) |
| Windows | Electron 33 (JS plano, sin framework ni bundler) | `windows/` | **Partición persistente** `persist:acc_<id>` |

Idioma de UI y mensajes: **español rioplatense** (voseo: "Agregá", "Poné", "usás"). Mantenerlo.

### 1.0 Nombre e identidad
La app se llama **AlternAIs** (antes AlternIAs, Alterna y Claudeck; el nombre se cambió a pedido del dueño): alterna entre cuentas y entre dos IAs (Claude y Replit). **Logo:** un círculo partido en dos mitades desfasadas (izquierda coral `#D97757` = Claude, un poco abajo; derecha crema `#F5EFE6` = Replit, un poco arriba) sobre fondo carbón `#1C1917`. Se cambió **solo lo visible**; se conservan a propósito los identificadores internos para no perder sesiones ni romper actualizaciones: `applicationId`/`namespace` `app.claudeck`, `appId` `app.claudeck`, tema `Theme.Claudeck`, puente JS `claudeckDl`, `SharedPreferences("claudeck")`, `installer/claudeck.iss` (y `AppId` de Inno Setup), BMP `claudeck-wizard*.bmp`, y los nombres de artifacts/APK de los workflows (`Claudeck-Android`, `Claudeck-N.apk`, `Claudeck-Windows`). En Windows `main.js` fija `userData` a `%APPDATA%/Claudeck` (la carpeta vieja) para conservar `accounts.json` y las sesiones aunque cambie `productName`; el instalador borra los restos de `AlternIAs.exe`, `Alterna.exe` y `Claudeck.exe`. El logo vive en **cuatro copias** que hay que mantener iguales: `scripts/make_icon.py` (genera `windows/build/icon.png|ico` y los BMP del instalador; correr `python scripts/make_icon.py`), `android/.../drawable/ic_launcher_foreground.xml` (ícono adaptable, sobre `ic_launcher_background` `#1C1917`), `drawable/ic_logo.xml` (logo completo con fondo, pantalla vacía de Android) y los SVG inline de `windows/renderer/index.html` (barra de título, cajón y estado vacío; **ya no está en la barra de cuentas**). `make_icon.py` también genera `windows/renderer/tray.png` (ícono de la bandeja).

**Tema y color por servicio:** por defecto **Claude va oscuro y Replit claro**, sin importar el tema del sistema, y **el logo del cajón de Ajustes es un botón que alterna oscuro/claro del servicio activo** (preferencia guardada por servicio: Android `AccountStore.dark` clave `dark_<servicio>` y `MainActivity.toggleTheme()`, que repinta y recrea los WebViews porque el tema se fija al crearlos —la sesión vive en el perfil—; Windows `state.themes` en `accounts.json`, IPC `theme:toggle`, `data-theme` en `<html>`). Si la cuenta web tiene un tema fijo (no «según el sistema»), la página puede ignorar el cambio. Descripción del comportamiento por defecto: En ambas mitades el «chrome» (borde del panel, íconos, punto, barra de progreso, relleno de la barra de tiempo, pestaña activa) usa **el color de la cuenta activa**; solo cambia el fondo: carbón (`#1F1B18`, texto `#F5EFE6`) en Claude y el crema de Replit (`#FBF8F4`, texto `#1F1B18`) en Replit. Android: `Service.dark/surface/onSurface` en `Account.kt`; `MainActivity.applyServiceTheme()` pinta barra, cajón, barras del sistema y fondos (se llama en `onCreate` y en `switchService()`); `onSurface()` y el relleno de `newTrayBg()` salen del servicio, mientras que las hojas inferiores (`sheetHeader`/`sheetRow` con `sheet != null`) usan `sheetOnSurface()` (tema del sistema, porque el fondo de la hoja lo pone el sistema). Cada WebView se crea con un `ContextThemeWrapper` (`Theme.Claudeck.WebDark` / `WebLight`, en `themes.xml`) y `setAlgorithmicDarkeningAllowed(dark)` (o `setForceDark` en WebView viejos) para que la web vea `prefers-color-scheme` oscuro en Claude y claro en Replit. `store` se inicializa **antes** de `buildDock()` porque los colores dependen del servicio. Windows: `applyServiceTheme()` en `main.js` fija `nativeTheme.themeSource` (`dark` en Claude, `light` en Replit; afecta a la interfaz y a las páginas) y el `backgroundColor` de la ventana; `app.js` pone `data-service` en `<html>` y `styles.css` (`:root[data-service="replit"]`) usa el crema de Replit.

### 1.1 Dos mitades: Claude y Replit

La app tiene dos «servicios» que funcionan igual pero con **cuentas totalmente separadas**: **Claude** (`https://claude.ai/`) y **Replit** (`https://replit.com/`). Se cambia desde el **cajón de Ajustes**: la cabecera es `[Claude] (logo de la app) [Replit]`; tocar un nombre pasa a esa mitad (la activa se resalta con el color de la cuenta activa).
- **Android**: `enum Service` en `Account.kt` (`key`, `label`, `startUrl`, `hosts`). `AccountStore.service` (clave `service`) decide qué lista usan `load/save/selectedId`: Claude conserva las claves originales `accounts`/`selected` (retrocompatible); Replit usa `accounts_replit`/`selected_replit`. `urlForRestore(service, url)` generaliza la restauración de URL (`claudeUrlForRestore` queda como atajo). `MainActivity.switchService()` guarda las páginas, cambia `store.service`, recarga `accounts`, reconstruye los círculos y selecciona la cuenta guardada de esa mitad; los WebViews de la otra mitad quedan ocultos y vivos (ids únicos, perfiles `acc_<id>` distintos). La burbuja y los atajos muestran las cuentas de la mitad activa (leen `store.service` en cada uso). `BRIDGE_ORIGINS` incluye `replit.com`.
- **Windows**: `SERVICES` en `main.js` (`label`, `startUrl`, `hosts`, `trusted(u)`). `accounts.json` mantiene en la raíz `accounts`/`selectedId` de Claude (retrocompatible) y suma `replit: {accounts, selectedId}` y `service`. En memoria, `state` (lo que ve el renderer) apunta a la mitad activa y `buckets` guarda ambas (`syncBucket()` en `saveState`). IPC nuevo `service:set` (`window.api.setService`). `find()`/`serviceOf()` buscan en ambas mitades porque las vistas de la otra mitad siguen vivas (ocultas). Los enlaces de confianza de Replit incluyen `replit.com/.dev`, `repl.co` y los accesos de login de Google/GitHub/Apple.
- Para sumar un tercer servicio: agregarlo al `enum`/`SERVICES`, a las pestañas del cajón (`buildDrawer`/`index.html`) y a `BRIDGE_ORIGINS`.

## 2. Estructura del repo

```
.github/workflows/
  cd.yml          # compila Android+Windows desde cd.zip (flujo principal del dueño)
  android.yml     # push a android/** o manual
  windows.yml     # push a windows/** o manual
scripts/make_icon.py        # genera windows/build/icon.png + icon.ico (Pillow)
android/
  build.gradle.kts, settings.gradle.kts, gradle.properties
  app/build.gradle.kts
  app/debug.keystore        # firma fija (¡no tocar!, ver §6)
  app/src/main/AndroidManifest.xml
  app/src/main/java/app/claudeck/
    MainActivity.kt         # UI + lógica: cuentas, WebViews, dock, tutorial, menús, descargas, atajos
    BubbleService.kt        # burbuja flotante (overlay + servicio en primer plano)
    Account.kt              # data class Account + AccountStore (SharedPreferences, también ajustes de UI)
    Ui.kt                   # dibuja el avatar circular (color + letra) para burbuja y atajos
  app/src/main/res/         # layout/activity_main.xml, dialog_account.xml, values/, drawable/ (íconos vectoriales, bg_dock), mipmap-anydpi-v26/
windows/
  main.js                   # proceso principal: estado, vistas, IPC, atajos, notificaciones
  preload.js                # contextBridge → window.api
  renderer/index.html, app.js, styles.css   # UI de la barra lateral/diálogos
  build/icon.png, icon.ico
  package.json              # config electron-builder (portable y archivos para Inno Setup)
  installer/
    claudeck.iss            # Inno Setup, con gráficos propios del instalador
    claudeck-wizard*.bmp    # arte de marca para las páginas del asistente
README.md                   # documentación para el usuario final
```

No hay tests, linter ni gradle wrapper (`gradlew`). Las compilaciones se hacen **solo en GitHub Actions**.

## 3. Modelo de datos (igual en ambas plataformas)

```
Account { id, name, purpose, color, limitUntil, limitStart, lastUrl, email }
```
- `id`: hex aleatorio (Android: UUID sin guiones, primeros 12; Windows: `crypto.randomBytes(6).toString('hex')`). Se usa para nombrar el perfil/partición → **nunca cambiar un id existente** (se perdería la sesión).
- `name` máx 24 chars, `purpose` máx 60, `color` `#RRGGBB` (Android lo guarda como Int ARGB).
- `limitUntil`: epoch ms hasta el que la cuenta está "en descanso" por límite de uso; `0` = disponible. `limitStart` (Android y Windows): cuándo se marcó, para saber qué fracción del descanso falta (`limitFraction()`); si falta (marcas viejas) se supone 5 h / 24 h / 7 d / 30 d.
- `email`: correo con el que se inicia sesión (opcional, **nunca contraseñas**); se edita en el diálogo de la cuenta, se muestra en el tooltip/menú y viaja en la exportación. Android: `Account.email` + `cleanEmail()`; Windows: `cleanEmail()` en `main.js`.
- `lastUrl`: última pantalla de Claude guardada por cuenta. Ambas plataformas restauran solo HTTPS de `claude.ai` (incluidos sus subdominios), sin query ni fragmento; valores viejos o externos se descartan y abren `START_URL`. Es retrocompatible y se actualiza al navegar, también al cambiar de chat dentro de la SPA.
- Paleta de 8 colores duplicada en `MainActivity.kt` (`PALETTE`), `renderer/app.js` (`PALETTE`) y validada en `main.js` (`cleanColor`, default `#E07A5F`).
- Persistencia: Android → `SharedPreferences("claudeck")` claves `accounts` (JSON array), `selected` y `tutorial_shown`. Windows → `app.getPath('userData')/accounts.json` (`{accounts, selectedId, tutorialShown}`, escritura atómica con `.tmp` + rename).

Funcionalidades (paridad entre plataformas): agregar/editar cuenta, cambiar cuenta, recargar, marcar límite (5 h / 24 h / 7 d / hora exacta, si la hora ya pasó → mañana; **en Replit**, además, «renovación mensual» con selector de día a las 00:00, porque Replit tiene cuotas distintas: Modo gratis que se renueva cada 5 h con tope semanal, créditos diarios del plan Starter y créditos mensuales de Core/Pro que se renuevan al empezar el ciclo de facturación; los textos de las opciones lo aclaran. Android: `showLimitDialog`/`pickRenewalDate`; Windows: `openLimitDialog` y `#lim-renew`), quitar marca, cerrar sesión y borrar datos, eliminar cuenta, aviso cuando termina el descanso.

## 4. Android — cómo funciona

**Stack:** `minSdk 26`, `targetSdk/compileSdk 34`, Kotlin 1.9.24, AGP 8.5.2, Java 17. Deps: core-ktx, appcompat, material 1.12, `androidx.webkit` 1.11 (sin dependencias nuevas en la v. de la burbuja). Tema `Theme.Material3.DayNight.NoActionBar`, color de marca `#D2694A`. Todo el texto hardcodeado en español rioplatense.

### 4.1 Pantalla principal (`MainActivity` + `activity_main.xml`)
- `launchMode="singleTask"`: la burbuja y los atajos del launcher reutilizan la Activity y le llegan por `onNewIntent` con el extra `EXTRA_SELECT` (id de cuenta) → `select(id)`. También se lee en `onCreate` (si el proceso estaba muerto).
- Layout: barra de progreso arriba (se tiñe con el color de la cuenta), `FrameLayout#container` (WebViews + `emptyView` + `errorView`), y abajo el **dock**. El dock del XML se **reemplaza en código** (`buildDock()`, al inicio de `onCreate`, conservando los ids `dock/infoRow/infoDot/info/btnMore/btnToggleDock/chipScroll/chipGroup` y los `LayoutParams` originales), así que su contenido ya no depende de `activity_main.xml`.
- **Un `WebView` por cuenta**, creado perezosamente en `select()` (`webViews.getOrPut`). Nace `GONE`; `transitionTo()` lo muestra con fundido + deslizamiento de 28 dp según el sentido del cambio (el anterior se desvanece y pasa a `GONE`). No se destruyen al cambiar. Cada uno hace `ProfileStore...getOrCreateProfile("acc_<id>")` + `WebViewCompat.setProfile` **antes** de cargar nada.
- `multiProfileOk` (feature `MULTI_PROFILE`): si es `false`, aviso y una sola cuenta.
- **Tutorial y Acerca de**: en una instalación nueva se muestra una sola vez una capa de guía con spotlight; «Saltar tutorial» también marca la bienvenida como vista. Enseña a usar el correo para iniciar sesión, agregar y cambiar cuentas, y abre automáticamente el cajón de Ajustes. Ese cajón incluye «Acerca de AlternAIs», que informa que fue pensada y diseñada por Andrés Londero. Se guarda `tutorial_shown` en `SharedPreferences("claudeck")`.
- **Círculos de cuenta** (antes chips; el mapa sigue llamándose `chips`): `ImageView` de 44 dp (+7 dp arriba y abajo) con `Ui.timerAvatar`: color + inicial; si la cuenta descansa, un **anillo exterior es el reloj de espera** (lo que falta, se va borrando en sentido horario; se actualiza en cada `tick`); la cuenta activa lleva una **rayita debajo**. Sin límite no hay anillo. Tap = `select`, long-press = menú de cuenta. Se redibujan en `refreshCircles()` (la llaman `updateSelection`, `refreshChipTexts`, `rebuildChips`). Al final, botón **+** redondo con degradé sweep de `PALETTE`. Entran con animación escalonada.
- **Dock**: dos estados (`setDockCollapsed`, tocar el `infoRow` o la flecha alterna). *Plegado*: punto de la cuenta a la izquierda, flecha (del color de la cuenta) a la derecha y en el medio una **barra de tiempo** (`TimeBar` en `Ui.kt`: relleno = lo que falta de descanso, texto `Nombre · faltan X` o `Nombre · Disponible`). *Desplegado*: fila con punto + `Nombre · propósito · estado` + botón Ajustes + flecha, y debajo los círculos. **Tocar fuera de la barra la pliega** (`dispatchTouchEvent` de la Activity). **Ajustes**: el botón abre un **cajón** (`buildDrawer`/`setDrawerOpen`, hijo de `container`, sale desde detrás del dock; mientras está abierto el dock se "fusiona" con él — `setDockMerged`: sin esquinas redondeadas ni borde superior — para que el borde de color sea continuo) con el ícono y nombre de la app, «Ajustes» y: Burbuja de cuentas (interruptor), Recargar cuenta actual, Ver descargas y Acerca de AlternAIs. Íconos vectoriales propios `ic_*` (trazo redondeado, tinte = color de la cuenta activa, `accent()`/`retintIcons()`) también en el menú de cuenta; ya no hay «volver a la cuenta anterior». **Se pliega solo** a los `AUTO_HIDE_MS` (4 s) sin tocarlo (`scheduleAutoHide`, reiniciado por `dispatchTouchEvent` del dock, `select` y `onResume`; no se pliega sin cuentas ni con el teclado abierto). Arranca siempre desplegado. **Borde superior del color de la cuenta activa (también en Replit)** (`dockBg` + `setBorderColor`, animado); el fondo es un `LayerDrawable` con inset negativo abajo y el dock recorta con `clipToOutline` (esquinas superiores de 22 dp). **Deslizar** sobre el `infoRow` pasa a la cuenta siguiente/anterior (`step`). Se quitó el botón swap (`swapToPrevious` queda sin uso). Mientras el teclado está abierto el dock se oculta (`watchKeyboard`).
- **Menús**: `showAccountMenu(acc)` y `showMoreMenu()` son `BottomSheetDialog` armados en código (`sheetColumn/sheetHeader/sheetRow`, íconos emoji). El cajón de Ajustes tiene interruptor de burbuja, recargar cuenta actual, ver descargas y Acerca de AlternAIs; ya no ofrece un acceso a los permisos generales de Android. Los avisos del sistema para funciones como la burbuja y descargas antiguas siguen funcionando.
- Aviso inteligente: al seleccionar o marcar en descanso una cuenta con límite, `Snackbar` con acción "Ir a «otra cuenta disponible»". Al crear la 2ª cuenta se sugiere una vez la burbuja (`bubbleTipShown`).
- **Pantalla de error**: `onReceivedError` (solo main frame) agrega el id a `failed` y muestra `errorView` con "Reintentar" (`retryCurrent`). Se limpia en `onPageStarted`.
- **Atajos del launcher**: `updateShortcuts()` publica hasta 4 atajos dinámicos (uno por cuenta, ícono = `Ui.avatar`) con `ShortcutManagerCompat`; se llama cada vez que cambian las cuentas (`onAccountsChanged`).
- `ticker` (cada 30 s, solo entre `onResume`/`onPause`) → `tick()`: limpia límites vencidos, `Toast`, refresca chips/info/burbuja. **El aviso de fin de descanso sigue apareciendo solo con la app abierta.**
- Botón atrás: `canGoBack()` del WebView actual; si no, sale. `onPause/onResume` pausan/reanudan **todos** los WebViews.
- `shouldOverrideUrlLoading`: esquemas distintos de http/https → `ACTION_VIEW` externo. Sin filtro de hosts (todo http(s) navega en el WebView). `doUpdateVisitedHistory` y `onPause` guardan `lastUrl` por cuenta; al crear el WebView se restaura solo si pasa `claudeUrlForRestore`.
- Subida de archivos: `onShowFileChooser` + `ActivityResultContracts.StartActivityForResult` (no requiere permisos).
- Borrado (`wipeAccount`): destruye el WebView, `ProfileStore.deleteProfile`; fallback a `removeAllCookies` + `deleteAllData`.

### 4.2 Descargas (arreglado en esta versión)
Antes `setDownloadListener` hacía `ACTION_VIEW` con la URL (el navegador externo no tiene las cookies de la cuenta → fallaba; `blob:` imposible). Ahora `handleDownload()` decide por esquema, todo envuelto en `withStoragePermission` (solo pide `WRITE_EXTERNAL_STORAGE` en Android ≤ 9; en 10+ no hace falta):
- **http/https** → `enqueueDownload`: `DownloadManager` con cabeceras `Cookie` (del `cookieManager` del **perfil** de la cuenta) y `User-Agent`, destino `Descargas` público, notificación del sistema. `downloadReceiver` (registrado con `RECEIVER_EXPORTED`, vive con la Activity) muestra Snackbar "Descargado" con acción **Abrir**.
- **blob:** → `fetchBlob`: ejecuta JS en la página que lee el blob y lo manda por el objeto **`claudeckDl`** (`WebViewCompat.addWebMessageListener`, limitado a los orígenes `claude.ai`/`*.claude.ai`/`claude.com`/`*.claude.com`, requiere feature `WEB_MESSAGE_LISTENER`). `HOOK_JS` (inyectado en `onPageStarted` y `onPageFinished`, idempotente) guarda los blobs creados con `URL.createObjectURL` y demora 60 s el `revokeObjectURL` (la página suele revocarlo enseguida), y recuerda el `download=` del `<a>` clickeado para el nombre del archivo. El mensaje JSON `{name, mime, data(base64)}` llega a `onBridgeMessage` → hilo aparte → `saveToDownloads` (MediaStore.Downloads en 10+, archivo directo en ≤ 9) → Snackbar con **Abrir**.
- **data:** → `saveDataUrl`.
- Sigue sin soportarse micrófono/cámara (falta `onPermissionRequest`).

### 4.3 Burbuja flotante (`BubbleService`)
- Overlay `TYPE_APPLICATION_OVERLAY` dentro de un **servicio en primer plano** (`foregroundServiceType="specialUse"`, `START_NOT_STICKY` a propósito: Android 12+ no deja reiniciar FGS desde segundo plano). Permisos del manifest: `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`.
- Activación: `MainActivity.setBubbleEnabled(true)` → si falta "Mostrar sobre otras apps" abre `ACTION_MANAGE_OVERLAY_PERMISSION` (`pendingBubble`, se reintenta en `onResume`) → pide notificaciones (13+; si las niegan igual funciona) → `BubbleService.start`. Estado en `AccountStore.bubbleEnabled`; `onResume` la re-lanza si estaba activa y tiene permiso.
- UI (todo en código, sin XML): `root` (LinearLayout vertical, padding 6 dp) = `[listTop][head][listBottom]`; la **cabeza** (`ImageView` 56 dp, `Ui.avatar` con color/letra de la cuenta activa, aro blanco, punto ámbar si está en descanso) nunca se re-parenta (si no, se cancela el arrastre). Toque = despliega la lista hacia el lado con más espacio (arriba si está en la mitad inferior; alineada a derecha/izquierda según el borde) con animación en cascada; cada fila = avatar + etiqueta con nombre. Última fila (la más lejana a la cabeza): "Ocultar burbuja". Arrastrable; al soltar se pega al borde más cercano y guarda posición (`bubbleX/bubbleY`). `FLAG_WATCH_OUTSIDE_TOUCH` cierra la lista al tocar afuera.
- Cambiar de cuenta: `switchTo(id)` → `store.selectedId = id` + `startActivity(MainActivity, EXTRA_SELECT=id, NEW_TASK|SINGLE_TOP|REORDER_TO_FRONT)` (permitido desde segundo plano por tener el permiso de overlay).
- Sincronización: `BubbleService.refresh()` (estático, hilo principal) la llama `MainActivity` al cambiar de cuenta, editar cuentas o vencer límites. Notificación con acción "Ocultar burbuja" (`ACTION_STOP`).
- Coordenadas: `hx/hy` = esquina de la cabeza en pantalla; la ventana se posiciona en `hx-pad, hy-pad` y, expandida, se recalcula y se clampa a la pantalla.

### 4.4 Persistencia (`AccountStore`, `SharedPreferences("claudeck")`)
Claves: `accounts` (JSON), `selected`, `bubble_enabled`, `bubble_x`, `bubble_y`, `dock_collapsed`, `bubble_tip_shown`. La comparten Activity y servicio (mismo proceso).

### 4.5 Misc
- `usesCleartextTraffic=false`, `allowBackup=false`. Config WebView: JS, DOM storage, `allowFileAccess=false`, `mediaPlaybackRequiresUserGesture=true`.
- Íconos de la app: adaptive icon vectorial (`ic_launcher_foreground.xml`, fondo `#1C1917`). Íconos de UI: `ic_swap`, `ic_expand`, `ic_more`, `ic_add`, `ic_bubble` (notificación).
- **Sin poder compilar en local**: esta versión se escribió sin compilar (solo CI). Si el primer build falla, el error estará casi seguro en `MainActivity.kt`/`BubbleService.kt`: corregir ahí.

## 5. Windows — cómo funciona

- **Marco de ventana propio**: `BrowserWindow` usa `frame: false`; el renderer dibuja su barra de título (36 px) y controles de minimizar, maximizar/restaurar y cerrar, con área arrastrable. El `WebContentsView` empieza debajo del marco y de la barra funcional, en `TOPBAR_H = 80`; `--chrome-h` + `--topbar-h` de `styles.css` deben sumar ese valor.
- **Tutorial de primera ejecución**: se persiste `tutorialShown` en `accounts.json`; se inicia una sola vez en instalaciones nuevas, puede saltearse y abre el cajón de ajustes automáticamente para presentarlo. El correo se recomienda para el ingreso en Claude. «Acerca de AlternAIs» abre una ventana con la descripción y la autoría de Andrés Londero.
- Electron `^33.2.0`, electron-builder `^25.1.8`, targets `dir` (carpeta `dist/win-unpacked`) y `portable`; el **instalador lo hace Inno Setup** (`windows/installer/claudeck.iss`, que el workflow compila con `ISCC` después de electron-builder; por usuario sin administrador, permite elegir carpeta y «para todos», acceso directo opcional, español; `AppId` fijo: no cambiarlo). Local: `npm run dist` y luego `ISCC /DAppVersion=1.0.0 installer\claudeck.iss`. Artefacto único: `AlternAIs-Setup-<ver>.exe` (los workflows compilan solo `--win dir` y ya no generan el portable). `files` solo incluye `main.js`, `preload.js`, `renderer/**/*`, `package.json` → **si agregás un archivo nuevo, sumarlo a `build.files`**.
- El asistente de Inno Setup mantiene el estilo moderno y suma gráficos BMP coordinados con la paleta de AlternAIs (`claudeck-wizard.bmp` y `claudeck-wizard-small.bmp`), compilados directamente desde `installer/`.
- **Arquitectura de ventana:** una `BrowserWindow` sin marco nativo (`frame: false`) con el renderer (barra de título propia de 36 px + barra funcional de 44 px) y, **encima**, una `WebContentsView` por cuenta (claude.ai) posicionada con `setBounds` en el área restante. `RAIL_W = 76` y `TOPBAR_H = 80` en `main.js`; `--rail-w = 76px`, `--chrome-h = 36px` y `--topbar-h = 44px` en `styles.css`. Las vistas de Claude empiezan debajo del marco propio y la barra funcional.
- **Gotcha clave de los modales:** las `WebContentsView` se dibujan por encima del HTML del renderer, así que un `<dialog>` quedaría tapado. Por eso el renderer llama `api.modal(true/false)` al abrir/cerrar cualquier diálogo y `main.js` oculta todas las vistas (`modalOpen`). Todo diálogo nuevo debe pasar por `openDialog()` en `app.js` (ya engancha el evento `close`).
- **Paridad visual con Android (reloj de espera y ajustes):** `limitStart` se guarda en `accounts.json` (`account:limit`, `loadState`, `checkLimits`, quitar marca). En `renderer/app.js`, `fractionOf(acc)` = misma lógica que `Account.limitFraction()`. Cada avatar de la barra lateral es `button.avatar > svg.ring + span.disk`; el **anillo SVG** (`stroke-dasharray`, color `var(--text)`) es el tiempo que falta y se borra en sentido horario; la cuenta activa se marca con la rayita a la izquierda y el disco cuadrado redondeado. La barra superior tiene la **barra de tiempo** (`.timebar`, relleno `--p`, texto con contraste `--fg` calculado por `textOn()`). `--accent` (en `:root`, la pone `renderTopbar`) = color de la cuenta activa: **borde derecho de la barra lateral, borde inferior de la superior, íconos y cajón**. Botón **+** = círculo `conic-gradient` con la paleta. Botón de ajustes (sliders) abajo de la barra → **cajón** `#drawer` (`--drawer-w` = `DRAWER_W` = 300 en `main.js`) que sale desde detrás de la barra lateral, con ícono y nombre de la app, «Ajustes», Recargar cuenta actual, Ver descargas (abre la carpeta Descargas, `app:downloads`) y Agregar cuenta. Como las `WebContentsView` tapan el HTML, al abrir el cajón `main.js` **oculta las vistas y el renderer muestra una captura de la página** (`ui:drawer` devuelve `{url,x,y,width,height}` con `capturePage()`; `#snap` en `index.html`): el cajón se dibuja **encima** de la página, sin correrla, con fondo translúcido y `backdrop-filter: blur`. Se cierra con Esc, al tocar la captura, al elegir una opción o al elegir una cuenta (`selectAccount` → acción `drawer-closed`). Con la barra abajo (`#app[data-rail="bottom"]`) el cajón es **inferior, de lado a lado**: sube desde atrás de la barra y reparte las opciones en 3 columnas (`.dbody` > `.dgroup`: Ajustes · Cuentas · Inicio y cierre); a los costados es una lista vertical. No hay burbuja flotante en Windows.
- **Barra de cuentas (rail) y opciones (v. AlternAIs):** el rail tiene el mismo fondo que la barra superior (`--surface`), sin logo; los avatares son **círculos** (el activo lleva una **luz sutil detrás**, `::before` radial + `box-shadow` con el color de la cuenta, en vez de la rayita); el anillo de tiempo es **blanco en Claude y negro en Replit** (`--ring`); el botón de agregar es un `+` de trazo con la clase `gear add` y su color cicla por la paleta (`@keyframes plus-hue`). Se quitó la línea entre la barra de título y la superior (`.chrome` sin `border-bottom`) y el cajón tiene el borde recto (sin radio).
- **Posición de la barra:** `state.settings.railPos` = `left` | `right` | `bottom` (persistido en `accounts.json` → `settings`). `renderSettings()` pone `data-rail` en `#app` y `styles.css` reordena la grilla (`#app[data-rail=…]`; el cajón sale del lado de la barra). `layout()` en `main.js` calcula los bounds de las vistas según la posición (`RAIL_W` 76 a los lados, `RAIL_H` 72 abajo, `DRAWER_W` según el cajón abierto). Si cambiás tamaños, mantené `--rail-w/--rail-h` y las constantes iguales.
- **Inicio y cierre:** `settings.launchAtLogin` (`app.setLoginItemSettings` con `--hidden`, solo si la app está empaquetada) y `settings.closeToTray` («Bandeja del sistema»): al cerrar la ventana se esconde y queda el ícono de la bandeja (`Tray`, menú «Abrir AlternIAs» / «Salir»); los avisos de fin de descanso siguen funcionando. Con `--hidden` y bandeja activada arranca escondida. IPC nuevo `settings:set` (`window.api.setSettings`). El cajón tiene los controles (`#seg-rail`, `#d-login`, `#seg-close`).
- **Exportar / importar cuentas** (ambas plataformas, mismo formato): archivo `.json` `{app:"AlternAIs", version:1, exportedAt, services:{claude:{accounts:[{name,purpose,email,color:"#RRGGBB",limitUntil,limitStart,lastUrl,selected}]}, replit:{…}}}`. **No lleva sesiones, cookies ni ids**: las sesiones no se pueden copiar entre dispositivos (cookies cifradas por Windows/WebView y atadas a cada perfil), así que tras importar hay que iniciar sesión en cada cuenta. Al importar se crean ids nuevos y se **omiten** las cuentas cuyo nombre ya existe en ese servicio (máx. 50 por servicio, archivo ≤ 1 MB); solo se conservan marcas de límite todavía vigentes. Windows: IPC `data:export`/`data:import` (diálogos nativos) + `showMessage()` en `app.js`; Android: `exportLauncher`/`importLauncher` (`CreateDocument`/`OpenDocument`), `writeExport`/`readImport` y `AccountStore.loadFor/saveFor`. Ambos se abren desde el cajón de Ajustes.
- Vistas creadas perezosamente en `selectAccount` (`createView`). `partition: 'persist:acc_<id>'`, `contextIsolation`, `sandbox`, sin `nodeIntegration`. Solo la vista seleccionada es visible. `did-navigate`, `did-navigate-in-page` y `before-quit` guardan `lastUrl` por cuenta; `cleanClaudeUrl` solo permite restaurar URLs HTTPS de `claude.ai` y sus subdominios.
- Seguridad/navegación: `setWindowOpenHandler` → `openLink`: hosts `claude.ai|claude.com|anthropic.ai|anthropic.com` (regex `TRUSTED_HOST`) se cargan en la misma vista; el resto va a `shell.openExternal`; `mailto:` también externo. La ventana del renderer niega `will-navigate` y `window.open`. Permisos de sesión permitidos: `media`, `clipboard-sanitized-write`, `fullscreen`, `notifications` (`ALLOWED_PERMISSIONS`).
- User-agent: se le quita `Electron/x` para que parezca Chromium normal.
- Instancia única (`requestSingleInstanceLock`) → la segunda instancia enfoca la primera. `window-all-closed` → `app.quit()`.
- **IPC** (`ipcMain.handle`, expuesto en `preload.js` como `window.api`): `state:get`, `account:add|update|select|reload|wipe|remove|limit|menu`, `ui:modal`. Canales main→renderer: `state` (se emite el estado completo en `broadcast()`) y `action` (`add`, `edit`, `limit`, `confirm-wipe`, `confirm-remove`). Para agregar una función: handler en `main.js` + línea en `preload.js` + uso en `app.js`.
- Ventana propia: `window:minimize`, `window:toggle-maximize`, `window:close` y `window-state`; versión con `app:version`. `tutorial:complete` persiste el flag de bienvenida en `accounts.json`.
- El menú contextual de cada cuenta es **nativo** (`Menu.buildFromTemplate`, `account:menu`) y le devuelve acciones al renderer vía `action`; las confirmaciones las dibuja el renderer.
- Atajos (`handleShortcut`, enganchado en la ventana **y** en cada vista): `Ctrl+1…9`, `Ctrl+Tab`/`Ctrl+Shift+Tab`, `Ctrl+R` (recarga la vista, no la ventana), `Ctrl+N`.
- Límites: `setInterval(checkLimits, 30000)` en main → limpia vencidos y dispara `Notification` del sistema (esto sí funciona con la app minimizada). El renderer re-renderiza cada 30 s para actualizar contadores.
- CSP del renderer estricta: `default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:`. **Sin scripts ni `style=""` inline en el HTML**; los colores dinámicos se aplican con `el.style.setProperty('--c', ...)` desde JS (eso sí está permitido). Los SVG del logo están inline en `index.html` (duplicados en la barra, el cajón y el estado vacío).
- Wipe: `session.fromPartition(...).clearStorageData()` + `clearCache()`, después destruye/recrea la vista.
- Tema por servicio (Claude oscuro, Replit claro) vía `nativeTheme.themeSource` + `prefers-color-scheme` y `data-service` en `styles.css` (variables CSS en `:root`).

## 6. Build y CI (lo más fácil de romper)

**Flujo del dueño:** sube `cd.zip` a la raíz del repo → `cd.yml` se dispara (`push` con `paths: cd.zip` o manual). Job `extract`: **borra todo el repo excepto `.git`, `cd.zip` y `.github`, descomprime el zip y commitea los archivos descomprimidos** (`git push` con `GITHUB_TOKEN`, permiso `contents: write`; no dispara otros workflows). Los workflows que vengan dentro del zip **se ignoran** (Actions no puede modificar `.github/workflows`; para cambiarlos hay que editarlos a mano en GitHub). Después los jobs `android` y `windows` (`needs: extract`) hacen checkout del repo ya actualizado y compilan en paralelo. Resultados en **Artifacts** (retención 7 días; al terminar bien, un job `limpiar` — o un último paso en `android.yml`/`windows.yml` — **borra los artifacts de corridas anteriores** con `gh api`, para no llenar el cupo de almacenamiento de GitHub) (`Claudeck-Android` → `Claudeck-<run>.apk`; `Claudeck-Windows` → solo `AlternAIs-Setup-<ver>.exe`).

Reglas del `cd.zip` (si lo regenerás):
- Debe tener en la raíz `android/`, `windows/`, `.github/` (sin carpeta intermedia; si hay **una sola** carpeta raíz, igual la detecta). Ignora `__MACOSX`.
- **Todo lo que no esté en el zip se borra** del repo (y queda commiteado el borrado) en cada corrida, salvo `.git`, `cd.zip` y `.github`. Incluir siempre `.github/workflows/*.yml`, `.gitignore`, `README.md`, `scripts/` y este archivo.
- El `.gitignore` tiene `build/` pero con excepción `!windows/build/` (ahí viven los íconos). No renombrar esa carpeta.
- `cd.yml` también debe existir en el repo antes (el README dice que se crea una sola vez a mano; el que va dentro del zip no se "auto-instala" porque el workflow que corre es el ya commiteado).

**Versionado:** automático por `github.run_number`.
- Android: `versionCode = GITHUB_RUN_NUMBER`, `versionName = 1.0.N` (local sin variable → 1).
- Windows: `npm version 1.0.N --no-git-tag-version` antes del build (el `version` de `package.json` en el repo es siempre `1.0.0` y está bien).
- Ojo: el `run_number` es **por workflow**, así que APKs de `cd.yml` y `android.yml` tienen contadores distintos. Si el dueño mezcla ambos flujos, un `versionCode` menor no se instala encima de uno mayor. Se recomienda usar solo `cd.yml`.

**Firma Android:** el build `release` se firma con `android/app/debug.keystore` (alias `androiddebugkey`, pass `android`, PKCS12), declarado como `signingConfigs["debug"]`. Es **a propósito**: misma firma en todas las versiones = se actualiza sin perder sesiones. **No regenerar ni borrar el keystore** (obligaría a desinstalar y se pierden las sesiones). `isMinifyEnabled=false`, lint no aborta.

**Toolchain CI:** Android → `ubuntu-latest`, Temurin 17, Gradle 8.9 (vía `gradle/actions/setup-gradle`, no hay wrapper), comando `gradle assembleRelease --no-daemon`. Windows → `windows-latest`, Node 20, `npx electron-builder --win --x64 --publish never` con `CSC_IDENTITY_AUTO_DISCOVERY=false` (sin firma de código → SmartScreen avisa).

**Probar en local (Windows app):** `cd windows && npm install && npm start`. Android no se compila en local en este flujo.

**Íconos:** `python scripts/make_icon.py` (requiere Pillow) regenera `windows/build/icon.png|ico` y los BMP del instalador. El ícono de Android (`ic_launcher_foreground.xml` + `ic_logo.xml`) y los SVG de `index.html` son copias aparte: si cambiás el diseño, actualizar **todas** (ver §1.0).

## 7. Limitaciones y deuda conocida (candidatas a trabajar)

1. "Continuar con Google" puede ser bloqueado por Google en navegadores embebidos (ambas plataformas).
2. Android: micrófono y cámara no soportados (falta `onPermissionRequest` + permisos en el manifest).
3. Android: el aviso de fin de descanso solo aparece con la app abierta (no hay `WorkManager`/alarma/notificación).
4. Android: no hay filtro de hosts como en Windows (`TRUSTED_HOST`); links externos se abren dentro del WebView.
5. Android: `onPause` pausa todos los WebViews (las cuentas en segundo plano no siguen cargando/streameando).
6. Android: textos hardcodeados fuera de `strings.xml`; sin i18n en ninguna plataforma.
7. Windows: sin firma de código; sin auto-actualización; sin ícono de bandeja. Windows recibió el rediseño visual de Android (reloj de espera, barra de tiempo, cajón de ajustes, bordes de color), pero **no** la burbuja ni el manejo de descargas `blob:`.
8. Sin tests automáticos; sin gradle wrapper; sin minificación/ProGuard.
9. La lógica de cuentas/límites está duplicada Kotlin ↔ JS: cualquier cambio de modelo se hace dos veces.
10. Android: la burbuja requiere permiso de overlay y no existe en Windows; las descargas `blob:` dependen de que `WEB_MESSAGE_LISTENER` esté soportado por el WebView.
11. Android: el código de la v. con burbuja/descargas/rediseño no se probó en dispositivo ni se compiló antes de entregarlo.

## 8. Convenciones para modificar

- **Mínimos cambios, respetar estilo**: Kotlin con `private fun` agrupadas por secciones `// ---- Título`; JS `'use strict'`, 2 espacios, comillas simples, sin dependencias de runtime (solo devDependencies).
- Mantener **paridad de funciones** entre Android y Windows salvo que se pida lo contrario.
- No agregar dependencias pesadas sin necesidad: el valor del proyecto es ser un contenedor liviano.
- No enviar ni leer contenido de las conversaciones; no usar APIs de Anthropic. El README tiene una nota de uso responsable (no usar varias cuentas para esquivar límites de una sola): mantenerla.
- Cualquier cambio de modelo de datos debe ser **retrocompatible** con lo guardado (Android: `optString/optLong` con defaults; Windows: `loadState` ya sanea campos). Las claves nuevas de `AccountStore` tienen default, así que actualizar encima de una versión vieja es seguro.
- Al terminar un cambio: actualizar `README.md` (si afecta al usuario) y este `CLAUDE.md` (si afecta a la arquitectura), y **regenerar `cd.zip`** con la estructura de §6.

## 9. Receta rápida para regenerar el zip

```bash
# desde la raíz del proyecto (donde están android/, windows/, .github/)
zip -r cd.zip . -x "cd.zip" -x ".git/*" -x "*/node_modules/*" -x "*/dist/*" -x "*/.gradle/*" -x "android/app/build/*"
```

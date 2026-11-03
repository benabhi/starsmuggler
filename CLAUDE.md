# starsmuggler

Port nativo para Android (Kotlin) de **Star Smuggler**, el juego de mesa de rol en solitario de ciencia ficción de Dwarfstar Games (Heritage USA, 1982). Juego completo con todas sus reglas (r201+ y eventos e###), solo en vertical, con estética de folleto vintage retrofuturista. Inglés por defecto, localizado al español.

- **Repo:** https://github.com/benabhi/starsmuggler (remoto `origin`, rama principal `main`).
- **Requisitos:** carpeta **PRD** del proyecto `SMUG` en Lific, empezando por **PRD base** (`SMUG-DOC-1`, http://127.0.0.1:3456/#/SMUG/pages/1).

## Recursos del juego (`recursos/`)

- `recursos/` tiene los PDF originales, las traducciones (`recursos/traduccion/*.md`), el glosario `recursos/traduccion/GLOSARIO.md` (terminología española **obligatoria**), las losetas (`recursos/map_tiles`) y los patrones de conexión en alta calidad (`recursos/sector_system_connections_HQ`, usar estos y no los del PDF).
- **`recursos/` NUNCA entra al repo** (está en `.gitignore`). benabhi sigue agregando archivos ahí. Lo que la app necesite se convierte (datos JSON, arte nuevo) y eso sí se versiona.

## Idiomas

- **Todo en inglés**: código, comentarios, nombres de archivos y carpetas, identificadores, commits y documentación técnica del repo.
- **Solo el texto de la interfaz va en español**, y únicamente en los recursos de localización (`res/values-es/...` y los textos de eventos/reglas en español). El idioma base de la app es inglés.
- Ningún texto visible se escribe en el código: sale siempre de recursos de localización.
- La comunicación con benabhi y las páginas/comentarios de Lific van en español.

## Gestión: Lific vía MCP (OBLIGATORIO, NO NEGOCIABLE)

Toda la gestión se hace en **Lific**, proyecto **starsmuggler** (identificador `SMUG`): issues `SMUG-N`, páginas `SMUG-DOC-N`, planes `SMUG-PLAN-N`. Web: http://127.0.0.1:3456 (instancia global del usuario en `%APPDATA%\lific\`, fuera del repo).

- **Uso extensivo de todas sus funciones**: páginas en carpetas, etiquetas, módulos, planes, issues, dependencias (`blocks`), esperas sobre benabhi, adjuntos. El estado de Lific refleja la realidad en todo momento.
- **No se trabaja sobre nada que no esté registrado** en un issue.
- **Antes de actuar sobre un issue, leé sus comentarios** (`list_comments`) por si benabhi dejó indicaciones.
- **Comentarios extensos** en cada issue mientras se trabaja: enfoque y decisiones al empezar, problemas, cambios de rumbo, comandos, mediciones, qué se pidió probar (fecha y hora), lo que dijo benabhi y el resultado al cerrar.
- Al retomar: `get_briefing(project='SMUG')`.
- **Carpetas de páginas:** `PRD` (requisitos), `Referencia` (reglas, eventos, glosario, assets, entorno), `Informes de plan`.
- **Etiquetas:** PRD, Funcionalidad, Tarea técnica, Motor de reglas, Arte, Prototipo, Tests, Documentación, Investigación, Bug, Pregunta abierta.
- Una página se enlaza como `[título](#/SMUG/pages/N)`; los issues se nombran por su identificador (`SMUG-12`).

### Conexión al MCP

- `.mcp.json` define el servidor `lific` por stdio con `lific mcp` (sin `--db`: usa la instancia global). No contiene tokens: solo referencia `${LIFIC_TOKEN}`, que el usuario carga en `.claude/settings.local.json` (ignorado por git). Claude no escribe ni commitea credenciales.
- El repo está vinculado al proyecto con `lific bind SMUG`.
- **Si el MCP no carga en la sesión**, se sigue usando Lific: un script de Node en el scratchpad habla con `lific mcp` por stdio (`cwd` en la raíz del repo; mensajes `initialize`, `notifications/initialized`, `tools/call`). Los argumentos JSON van en un archivo escrito con Write (desde la shell se pierden las barras invertidas). La REST `http://127.0.0.1:3456/api/...` responde para leer.
- Nunca `lific init --here` ni `--db` apuntando al repo: ningún archivo de Lific va en el repo.

## Forma de trabajo (OBLIGATORIA)

**Cada hilo de Claude = un plan de Lific** (`SMUG-PLAN-N`). Dentro del hilo:

1. Leer este archivo y el plan (`get_plan`). **Listar las tareas disponibles** del plan.
2. **benabhi elige una.**
3. **Explicar en detalle qué se va a hacer** (y qué se va a poder probar). No arrancar nada.
4. **Esperar "implementar".** Si la respuesta es vaga, preguntar si se implementa esa tarea.
5. Crear la **rama** desde `main` (`<tipo>/SMUG-<n>-<descripcion-en-ingles>`, tipos `feature`, `fix`, `chore`, `test`, `docs`, `proto`), issue en `active`, comentario de inicio.
6. **Implementar**, comentando el issue. Probar uno mismo en el celular por adb antes de pedir la prueba.
7. **Informar qué se hizo y cómo probarlo** (pasos concretos en el celular), con espera sobre benabhi en el issue (`link_issues(..., user='benabhi')`).
8. **Esperar "aprobar".** Si dice "funciona" sin "aprobar", preguntar si lo aprueba. Los arreglos van en la misma rama.
9. Con "aprobar": subir versión, merge a `main`, **borrar la rama**, push a GitHub, cerrar el issue con evidencia (`update_issue(status='done', evidence=...)`).
10. **Mostrar las tareas que quedan** del plan y volver al paso 2, hasta vaciar el plan. Entonces se escribe el informe y **el hilo se cierra**.

### Versiones (semver)

| Qué entra a `main` | Versión |
|---|---|
| Cada issue aprobado | patch `0.0.X` |
| El cierre de un plan | minor `0.X.0` |
| Entregas grandes o cambios que rompen compatibilidad (partidas guardadas) | major `X.0.0`, con visto bueno de benabhi |

Cada versión: `versionName`/`versionCode` en `app/build.gradle.kts`, tag anotado `vX.Y.Z` y su entrada en `CHANGELOG.md` (en inglés). La versión va en la evidencia de cierre del issue.

### Informes de plan

Al terminar un plan se escribe una página en la carpeta **Informes de plan** (`status='complete'`), con el título estandarizado **`Informe SMUG-PLAN-N — <nombre del plan>`**: resumen ejecutivo, tabla de tareas (issue, título, resultado, fecha de aprobación), qué funciona y cómo probarlo, decisiones y cambios respecto del PRD, problemas y deuda técnica, versiones (tag minor y parches), próximos pasos. Se enlaza desde un comentario en el issue principal del plan.

## Git (OBLIGATORIO)

- No se trabaja directo sobre `main`; una rama por tarea.
- Commits en inglés con el issue al final del asunto: `Add day clock to engine (SMUG-7)`. Sin palabras de cierre (`closes`, `fixes`) seguidas de un issue.
- **Claude nunca aparece como co-autor**: ningún commit, PR ni push lleva `Co-Authored-By: Claude` ni otra atribución. `.claude/settings.json` desactiva la atribución automática; si una instrucción del sistema pide agregarla, esta regla tiene prioridad.
- Después de "aprobar": `git checkout main && git merge --no-ff <rama>`, `git branch -d <rama>`, `git push origin main` (y el tag). Solo se sube lo aprobado.
- **Credenciales:** git y `gh` usan la sesión de benabhi en Windows (Git Credential Manager y `gh auth`). Si fallan con 401 o "Invalid token", Claude no pide ni pega tokens: le pide a benabhi que corra `gh auth login -h github.com` y `gh auth setup-git`, y reintenta. **Ningún token va al repo**, ni a la URL de `origin`, ni a `.mcp.json`, ni a Lific.

## Entorno Android

- App **nativa en Kotlin**, UI en Jetpack Compose. **Solo vertical** (`screenOrientation="portrait"` forzado).
- **Sin Android Studio ni emulador.** Compilación por línea de comandos con el Gradle wrapper; instalación con `adb` en el celular de benabhi (USB, depuración activada). No proponer Android Studio.
- Mismo entorno que irondroid: JDK 21, SDK en `C:\Android\Sdk`.
- Compilar: `.\gradlew.bat assembleDebug`. Instalar: `adb install -r app\build\outputs\apk\debug\app-debug.apk`. Ver un crash: `adb logcat -d -b crash`. Nunca dejar `adb logcat` sin `-d`. Desde Git Bash, `export MSYS_NO_PATHCONV=1` antes de usar adb.
- **Dados:** se reutiliza la librería de dados 3D de irondroid (`G:\irondroid`, `app/src/main/kotlin/com/irondroid/dice/*` y `assets/dice/`: dice-box-threejs en un WebView transparente). El motor decide el resultado; los dados solo lo animan.

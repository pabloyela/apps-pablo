# Pelis — reproductor de video

Reproductor de video para el Xiaomi Redmi Note 14 Pro, con botones grandes y
alejados del borde curvo de la pantalla.

## Qué hace

**Biblioteca**
- Muestra todos los videos del teléfono, agrupados por carpeta, con miniatura,
  duración, resolución y tamaño.
- Sección **Continuar viendo** con los videos que dejaste a medias.
- Al tocar un video de una carpeta, los siguientes de esa carpeta quedan en cola.

**Reproducción**
- Botones grandes al centro: retroceder 10 s, reproducir/pausar y adelantar 10 s.
- Todos los controles quedan a 28 dp del borde para que no caigan en la curva.
- Barra de herramientas: **Bloquear**, **Velocidad**, **Ajuste**
  (Ajustar / Llenar / Estirar), **Girar** y **Pistas** (idioma de audio y subtítulos
  incluidos en el video, útil para películas en MKV con audio dual).
- Botón de **ventana flotante** (Picture-in-Picture). También entra sola al
  presionar Inicio mientras el video se reproduce.
- Retoma donde te quedaste, con la opción **Desde el inicio**.
- Gira solo a horizontal o vertical según el video.
- Pausa al quitar los audífonos o al entrar una llamada.

**Gestos**
| Gesto | Acción |
|---|---|
| Un toque | Mostrar u ocultar controles |
| Doble toque a la izquierda / derecha | −10 s / +10 s |
| Doble toque al centro | Pausa / reproducir |
| Deslizar horizontal | Adelantar o atrasar (todo el ancho = 2 minutos) |
| Deslizar vertical, mitad izquierda | Brillo |
| Deslizar vertical, mitad derecha | Volumen |

Los gestos que empiezan pegados al borde se ignoran, para evitar toques accidentales.

Con la pantalla **bloqueada**, los toques no hacen nada (ni el botón atrás);
aparece un candado grande para desbloquear.

## Cómo obtener el APK (igual que con Carola)

1. Crea un repositorio privado en GitHub (por ejemplo `pelis`).
2. Sube el contenido de esta carpeta. Si la carpeta `.github` no se sube
   (está oculta), en la pestaña **Actions** usa "set up a workflow yourself",
   llámalo `build.yml` y pega el contenido de `.github/workflows/build.yml`.
3. En **Actions**, espera a que "Compilar APK" termine en verde (5–8 minutos)
   y descarga **Pelis-apk**.
4. Descomprime el ZIP, pasa el `.apk` al teléfono e instálalo
   (permite "instalar apps desconocidas" si te lo pide).

Si sale en rojo: abre la ejecución, copia las líneas que dicen `e:` o `error:`
y pégalas en el chat para corregirlo.

**Actualizaciones:** la app trae una llave de firma fija (`app/pelis.keystore`),
así cada APK nuevo se instala encima del anterior sin desinstalar ni perder
el progreso guardado.

## Usarlo como reproductor predeterminado

Abre un video desde la Galería o el gestor de archivos, elige **Pelis** y
marca **Siempre**. En HyperOS también: Ajustes › Aplicaciones › Aplicaciones
predeterminadas.

## Limitaciones conocidas

- Algunas películas traen audio **DTS** o **AC3**. Si el teléfono no tiene
  decodificador para ese formato, el video se ve pero no se oye. En
  **Pistas** puedes elegir otra pista de audio si la hay.
- Por ahora solo lee subtítulos que vienen dentro del video (MKV/MP4),
  no archivos `.srt` sueltos.

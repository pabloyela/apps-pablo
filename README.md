# Apps de Pablo

Las tres apps para el Redmi Note 14 Pro en un solo repositorio.

| Carpeta       | App        | Qué es                                        |
|---------------|------------|-----------------------------------------------|
| `pelis/`      | Pelis      | Reproductor de video con botones grandes       |
| `claroscuro/` | Claroscuro | Cámara de blanco y negro (fotos y video)       |
| `baul/`       | Baúl       | Galería con editor y área privada              |
| `comun/`      | —          | Código compartido: los estilos y efectos B/N   |

## Cómo se compila

Cada vez que subes cambios, GitHub revisa **qué carpetas cambiaron** y compila
**solo esas apps**:

- Cambió `baul/` → solo se compila Baúl.
- Cambió `comun/` → se compilan Claroscuro y Baúl (las dos usan los efectos).
- Cambió algo general (`gradle/`, archivos de la raíz) → se compilan las tres.

Para compilar a mano: pestaña **Actions** → **Compilar apps** → **Run workflow**,
y eliges una app o todas.

## Dónde quedan los APK

- **Releases** (columna derecha del repositorio): cada versión aparece con su
  nombre, por ejemplo `Baul-2.6.apk`. Desde el teléfono, abre el repositorio
  en el navegador, entra a Releases y toca el APK.
- **Actions**: también quedan como archivo descargable en cada compilación.

## Importante

Cada app tiene su llave de firma (`*.keystore` dentro de su carpeta). **No las
borres ni las cambies**: gracias a ellas cada versión nueva se instala encima de
la anterior sin perder nada (incluida el área privada de Baúl).

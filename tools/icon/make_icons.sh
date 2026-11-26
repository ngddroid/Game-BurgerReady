#!/bin/zsh
# Builds every launcher-icon density from the Blender renders in tools/icon/:
#   icon_fg.png  (1024² transparent, content inside the central 66% safe zone)
#   icon_bg.png  (1024² opaque background)
#   icon_full.png(1024² opaque, used for legacy/round icons and the Play Store 512² icon)
# Usage: tools/icon/make_icons.sh
set -e
cd "$(dirname "$0")/../.."
RES=app/src/main/res
ICON=tools/icon
BLENDER=~/Applications/Blender.app/Contents/MacOS/Blender

# Monochrome (themed icon) layer: white silhouette of the foreground alpha.
$BLENDER -b --factory-startup --python-expr "
import bpy, numpy as np
img = bpy.data.images.load('$PWD/$ICON/icon_fg.png')
px = np.array(img.pixels[:], dtype=np.float32).reshape(-1, 4)
a = px[:, 3].copy()
px[:, 0] = 1.0; px[:, 1] = 1.0; px[:, 2] = 1.0; px[:, 3] = a
out = bpy.data.images.new('mono', img.size[0], img.size[1], alpha=True)
out.pixels[:] = px.flatten()
out.filepath_raw = '$PWD/$ICON/icon_mono.png'; out.file_format = 'PNG'; out.save()
" >/dev/null 2>&1

typeset -A ADAPT LEGACY
ADAPT=(mdpi 108 hdpi 162 xhdpi 216 xxhdpi 324 xxxhdpi 432)
LEGACY=(mdpi 48 hdpi 72 xhdpi 96 xxhdpi 144 xxxhdpi 192)
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  dir=$RES/mipmap-$d
  mkdir -p $dir
  rm -f $dir/ic_launcher.webp $dir/ic_launcher_round.webp
  sips -z ${ADAPT[$d]} ${ADAPT[$d]} $ICON/icon_fg.png --out $dir/ic_launcher_foreground.png >/dev/null
  sips -z ${ADAPT[$d]} ${ADAPT[$d]} $ICON/icon_bg.png --out $dir/ic_launcher_background.png >/dev/null
  sips -z ${ADAPT[$d]} ${ADAPT[$d]} $ICON/icon_mono.png --out $dir/ic_launcher_monochrome.png >/dev/null
  sips -z ${LEGACY[$d]} ${LEGACY[$d]} $ICON/icon_full.png --out $dir/ic_launcher.png >/dev/null
  sips -z ${LEGACY[$d]} ${LEGACY[$d]} $ICON/icon_full.png --out $dir/ic_launcher_round.png >/dev/null
done
mkdir -p $RES/drawable-nodpi
sips -z 432 432 $ICON/icon_fg.png --out $RES/drawable-nodpi/splash_icon.png >/dev/null
sips -z 512 512 $ICON/icon_full.png --out $ICON/play_store_icon_512.png >/dev/null
rm -f $RES/drawable/ic_launcher_foreground.xml $RES/drawable/ic_launcher_background.xml
cat > $RES/mipmap-anydpi-v26/ic_launcher.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
EOF
cp $RES/mipmap-anydpi-v26/ic_launcher.xml $RES/mipmap-anydpi-v26/ic_launcher_round.xml
echo "icons done"

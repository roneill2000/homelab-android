# README artwork

Generators for the banner, the screenshot gallery and the annotated Quick Settings / widget shots.
`sources/` holds the full-size phone screenshots (Pixel, Android 17). Requires Python 3 with Pillow and the
Google Sans Flex font converted to TTF (`gsflex.ttf`, from the dashboard's `fonts/` woff2 via fontTools).

```sh
W=/tmp/readme-art; mkdir -p $W/orig $W/banner && cp sources/* $W/orig/   # scripts read <work>/orig and <work>/gsflex.ttf
python3 qs.py $W sources/quick-settings-raw.png $W/orig/quick-settings.png
python3 widget.py $W sources/widget.png $W/orig/widget-annotated.png
python3 gallery.py $W ../..                          # screenshots/gallery.png at native resolution
python3 banners.py $W ../.. <dashboard repo> 1        # 1280x640 social previews; 2 = README banners
```

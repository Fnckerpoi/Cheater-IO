# Sourced from official-addon; deliberately separate from OTA/native-navigation builds.
mapkit_options=()
mapkit_sources=()
case "${TIO_MAPKIT_ENABLED:-0}:${TIO_MAPKIT_HUD_ENABLED:-0}" in
  0:0) ;;
  1:0|1:1)
    [[ "${mode:-embedded}" == embedded ]] || { echo 'MapKit requires embedded research mode' >&2; exit 2; }
    [[ ${TIO_NATIVE_NAV:-0} == 0 && ${TIO_OTA_RESEARCH_ENABLED:-0} == 0 && ${TIO_MUSIC:-0} == 0 && ${TIO_LOCAL_TRANSLATION:-0} == 0 ]] || { echo 'MapKit phase one is ordinary research only, never an OTA/native/music/translation build' >&2; exit 2; }
    mapkit_options=(-DTIO_MAPKIT_ENABLED=1 -DTIO_MAPKIT_HUD_ENABLED="${TIO_MAPKIT_HUD_ENABLED:-0}" -I "$PWD/mapkit-navigation" -I "$PWD" -framework MapKit -framework CoreGraphics)
    for unit in MapKitNavigation.m MapKitNavigationCore.m MapKitRoads.m MapKitRoadsCore.m MapKitWireframe.m MapKitHUD.m; do
      mapkit_sources+=("mapkit-navigation/$unit")
    done
    if [[ ${TIO_MAPKIT_HUD_ENABLED:-0} == 1 ]]; then
      mapkit_options+=(-I "$PWD/focus-edition")
      mapkit_sources+=(mapkit-navigation/MapKitHUDPolicy.m)
      for unit in DisplayPhoneSession.m DisplayPhoneTransport.m DisplayReplyObserver.m DisplayDiagnostics.m DisplayDelta.c display_client.c display_carrier.c display_runtime.c; do
        mapkit_sources+=("focus-edition/$unit")
      done
    fi
    ;;
  *) echo 'TIO_MAPKIT_ENABLED and TIO_MAPKIT_HUD_ENABLED must be 0/1; HUD requires MapKit' >&2; exit 2 ;;
esac

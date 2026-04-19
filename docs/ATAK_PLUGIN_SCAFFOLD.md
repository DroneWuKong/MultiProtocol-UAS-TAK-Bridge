# Wingman ATAK Plugin Scaffold

Reference implementation for building a Wingman plugin inside ATAK.
Sourced from AndroidTacticalAssaultKit-CIV plugin-examples/plugintemplate (GPLv3).

## Plugin Architecture (ATAK 4.5.1+)

```
plugin.xml
  └── extension type="transapps.maps.plugin.lifecycle.Lifecycle"
        impl="com.wingman.atak.plugin.WingmanLifecycle"
  └── extension type="transapps.maps.plugin.tool.ToolDescriptor"
        impl="com.wingman.atak.plugin.WingmanTool"

WingmanLifecycle
  └── instantiates WingmanMapComponent

WingmanMapComponent extends DropDownMapComponent
  └── registers DocumentedIntentFilter for SHOW_WINGMAN intent
  └── creates WingmanDropDownReceiver

WingmanDropDownReceiver extends DropDownReceiver
  └── showDropDown(view, HALF_WIDTH, FULL_HEIGHT, ...) on SHOW_WINGMAN
```

## Key Classes (from ATAK SDK, not source)

| Class | Package | What it does |
|-------|---------|--------------|
| `DropDownMapComponent` | `com.atakmap.android.dropdown` | Base for map components with a slide-in panel |
| `DropDownReceiver` | `com.atakmap.android.dropdown` | Receives intents, opens the panel |
| `DocumentedIntentFilter` | `com.atakmap.android.ipc` | Registers broadcast receivers with docs |
| `MapView` | `com.atakmap.android.maps` | The ATAK map view — entry point for all map ops |
| `PluginLayoutInflater` | `com.atak.plugins.impl` | Inflate layouts with the plugin's context |
| `AtakBroadcast` | `com.atakmap.android.ipc` | Send/receive intents between ATAK components |

## AbstractPluginLifecycle Deprecation

`AbstractPluginLifecycle` in the template is **deprecated as of ATAK 4.5.1**.
For new plugins targeting 4.5.1+, use `com.atak.plugins.impl.AbstractPluginLifecycle`
from the plugin SDK (pluginsdk.zip), not the template copy.

## Showing a Plugin Panel

```java
// From anywhere in the plugin:
Intent intent = new Intent("com.wingman.atak.SHOW_WINGMAN");
AtakBroadcast.getInstance().sendBroadcast(intent);
```

## Sending CoT from a Plugin

```java
// Get the comms component
CotMapComponent cmc = CotMapComponent.getInstance();

// Build a CoT event
CotEvent event = new CotEvent();
event.setUID("my-uid");
event.setType("a-f-A-M-H-Q");
// ... populate point, detail ...

// Send to all configured outputs (multicast + TAK servers)
cmc.sendCoT(event);
```

## Plugin Signing

Plugins must be signed. For CIV (civilian) builds:
- Self-signed debug key works for sideloaded development
- For Play Store / enterprise distribution: inner signing via AAB
  (Added in ATAK 4.5.1.0 per VERSION.md)

## Key Intent Actions (from ATAK source)

| Intent | What it does |
|--------|-------------|
| `com.atakmap.android.toolbars.BLOOD_HOUND` | Start BloodHound follow/intercept tool |
| `ToolManagerBroadcastReceiver.BEGIN_TOOL` + `tool=sensor_fov_tool` | Activate sensor FOV placement tool |
| `RouteMapReceiver.*` | Route creation/navigation |

## Sensor FOV Integration

To draw a camera FOV cone on the map for a drone:

```java
// 1. Create marker at drone position with type a-f-A-M-H-Q
// 2. Call SensorDetailHandler.addFovToMap(marker, azimuth, fov, range, color, visible)
//    - fov is EDGE-TO-EDGE degrees (not half-angle!)
//    - Max range = 15000m
// 3. The SensorFOV item is auto-linked to the marker and follows its position
```

## __video Integration (UAS Video Streams)

```java
// Set these meta values on the drone marker:
marker.setMetaString("videoUID", videoConnectionEntryUID);
marker.setMetaString("videoUrl", "rtsp://192.168.1.100:8554/live");
marker.setMetaString("video_spi_uid", spiMarkerUID);    // WARNING: do not rename
marker.setMetaString("video_sensor_uid", sensorUID);    // WARNING: do not rename
```

## Emergency Monitoring

```java
// Register for emergency CoT (type prefix b-a-*)
DocumentedIntentFilter filter = new DocumentedIntentFilter();
filter.addAction("com.atakmap.android.emergency.EMERGENCY");
registerReceiver(receiver, filter);
```

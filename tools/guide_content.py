"""Public beta guides with real screenshots of the signed APK on an emulator."""
VERSION = '0.5.0-beta.1'
REPO = 'https://github.com/DroneWuKong/MultiProtocol-UAS-TAK-Bridge'
RELEASE = REPO + '/releases/tag/v' + VERSION
CAPTION = 'Signed beta running on an Android emulator. No physical telemetry source connected.'
def page(title, image, blocks, caption=CAPTION):
    result = {'title': title, 'blocks': blocks}
    if image:
        result.update(image='beta-0.5.0/' + image + '.png', caption=caption)
    return result

INSTALL = page('Install and try it', 'home', [
    ('h', '1 / Download the APK'),
    ('p', f'Open the <a href="{RELEASE}" color="#147463">GitHub beta release</a> on your Android device. Under Assets, choose <b>TAK-Bridge-{VERSION}.apk</b>. The source-code ZIP is for developers; it does not install the app.'),
    ('h', '2 / Install TAK Bridge Beta'),
    ('p', 'Open the downloaded APK. If Android asks, allow your browser or file manager to install this app, then follow the device prompts. Android 8.0 or later is required. Keep normal device protections enabled.'),
    ('p', 'The beta has its own icon and settings. It can coexist with an older TAK Bridge install. Future beta updates must use the same signing key; installing over this beta preserves its app data.'),
    ('h', '3 / Try Software demo'),
    ('p', 'Open <b>TAK Bridge Beta</b> and tap <b>Software demo</b>. No USB device, TAK server or camera is needed. Watch the coordinates, marker and GPS status. Tap <b>Stop demo</b> when finished.'),
    ('note', 'Practice stays local', 'Demo positions are synthetic. The demo does not send them to a network. Map tile downloads still use the internet; missing tiles do not stop telemetry parsing.')])
USB = page('Connect your telemetry', 'connection', [
    ('h', '1 / Prepare the source'),
    ('p', 'Use an Android device with USB host/OTG support, an OTG adapter if needed, and a data cable. Your flight controller or telemetry interface must already emit supported GPS messages. USB power or channel/stick data alone is not an aircraft GPS stream.'),
    ('h', '2 / Match Connection settings'),
    ('p', 'Stop the demo. Open <b>Connection settings</b>. Choose the profile, protocol and baud matching the existing stream. <b>Auto (passive)</b> listens for supported GPS. Set DTR as required by your serial interface. Give the aircraft a callsign.'),
    ('p', 'MAVLink v1/v2, MSP v1/v2 and GHST/CRSF GPS are supported. Only the explicit <b>Direct FC MSP GPS polling</b> profile sends GPS read requests (2 Hz). Choosing MSP alone does not enable polling.'),
    ('h', '3 / Connect and confirm GPS'),
    ('p', 'Close settings and tap <b>Connect</b>. Select the USB serial port if asked and grant Android USB access. Confirm fresh coordinates and a valid fix. Increasing byte counts without fresh GPS are not enough.'),
    ('note', 'Reconnect deliberately', 'Stop before changing source settings. A cable disconnect ends the session and clears the position; reconnect explicitly. Physical compatibility with your hardware still needs verification.')])
TAK = page('Send a position to TAK', 'destination', [
    ('h', 'Choose an output'),
    ('p', '<b>Local TAK:</b> enable it in Connection settings before starting USB. The default multicast destination is <b>239.2.3.1:6969</b>. Configure the receiver for that group and port. Wi-Fi isolation, VPNs and multicast filtering can block delivery.'),
    ('p', '<b>TAK Server:</b> enter the hostname and server port, then use <b>Send to TAK</b>. Use the port your administrator provides (8087 is the TCP default). Configure the server and receiving client to exchange your CoT events.'),
    ('h', 'For TLS connections'),
    ('p', 'Enable TLS and open <b>Certificates</b>. Import your client .p12 and its password; import a server CA .pem/.crt if required. The hostname must match the server certificate. Passwords stay in memory and must be re-entered after the process ends.'),
    ('h', 'Verify on the receiver'),
    ('p', 'With fresh real GPS, check that the receiving TAK client shows the correct aircraft, callsign and updated location. Publishing runs about once per second. <b>Socket writes</b> counts transport writes, not receipt or display in ATAK.'),
    ('note', 'No fresh GPS, no new position', 'A connected socket is not a valid fix. Publishing pauses for stale or invalid GPS; previously received markers can remain until the receiving client expires or removes them.')])
VIDEO = page('Share an existing video feed', 'video', [
    ('h', '1 / Make the feed reachable'),
    ('p', 'Your camera or relay must already serve an RTSP stream reachable from the receiving TAK device. Example only: <b>rtsp://192.168.1.20:8554/camera</b>. This is not a bundled or working camera.'),
    ('h', '2 / Enable the link'),
    ('p', 'Stop the bridge. Open <b>Connection settings → TAK video link</b>, enable sharing, enter the address and save. Start telemetry again. The link travels with fresh aircraft GPS in the same CoT event.'),
    ('h', '3 / Check it in ATAK'),
    ('p', 'On the receiving device, verify the aircraft marker, its video association and playback. Routing, camera authentication, codecs and the receiving client determine whether playback works.'),
    ('note', 'What the public beta provides', 'This feature shares a URL. It does not display video in this app, relay video bytes, create a stream, start a camera or add proprietary accessory support.'),
    ('p', 'The complete address goes to every marker recipient. Do not include credentials or access tokens. To remove it, stop, disable sharing, save and restart with valid GPS. The next position requests removal; a stopped source cannot immediately retract a remote link.')],
    'Actual beta dialog on an emulator. Any address shown is an example, not a live stream.')
DIAG = page('Read status and diagnostics', 'diagnostics', [
    ('h', 'Fresh, stale and no fix'),
    ('p', 'Coordinates come from the telemetry source, not the phone. Tap the coordinate readout to cycle MGRS, decimal degrees, DMS and UTM. CoT always uses decimal degrees. Stale GPS pauses publishing; a previous map position is not a current fix.'),
    ('h', 'What Diagnostics tells you'),
    ('p', '<b>Diagnostics</b> shows input bytes, parser state, GPS freshness and per-output socket-write counts. Last-write times and expired-event counts help separate source problems from connection problems. Counters cover the app process and may include an earlier session.'),
    ('p', 'Exports omit coordinates, video addresses and credentials. In demo mode, <b>CoT preview</b> shows the locally generated event, labeled as simulation. A retained preview can describe an earlier fix; inspect its timestamp and current GPS state.'),
    ('h', 'Keep control of the session'),
    ('p', 'An active session runs in a foreground service. Switching tabs or apps does not stop it. Use <b>Stop</b> or the notification action. Process death requires an explicit restart.'),
    ('note', 'For a useful bug report', 'Include beta version, Android version, source protocol, reproduction steps and exported diagnostics. Redact addresses, keys and private locations from additional screenshots or logs.')],
    'Actual emulator diagnostics from the synthetic software demo; no external TAK delivery claimed.')
TOOLS = page('Use the planning tools', 'tools', [
    ('h', 'Open Tools'),
    ('p', 'Use the bottom <b>Tools</b> tab, then search or select a calculator. The bundled suite includes channel planning, range estimates, Fresnel clearance, harmonics, antenna sizing, terrain links and mesh planning.'),
    ('h', 'Know the inputs'),
    ('p', 'Enter the frequencies, distances, antenna heights and other values for your setup. Estimates depend on their assumptions and source data. A calculated coverage area is not a measured link or a confirmed range.'),
    ('h', 'Offline and online'),
    ('p', 'Tool code and catalogs ship inside the APK. Online map imagery and elevation services require connectivity. Supported local DEM imports can supply offline elevation; an empty map grid is not terrain data.'),
    ('h', 'Return to the bridge'),
    ('p', 'Tap <b>Map</b> to return to telemetry. A running session continues while Tools is open. Stop it explicitly when finished.'),
    ('note', 'Video tools are planning tools', 'The Tools tab does not add a camera receiver to the public beta. Use a compatible receiving TAK client or another suitable viewer for an existing stream.')])
TROUBLE = page('Troubleshooting and limits', None, [
    ('table', [['Symptom', 'Check next'],
               ['APK will not install', 'Use the APK asset, Android 8.0+, enough storage and your device installation policy. Beta uses a separate package. A signature conflict may mean a different beta key; save needed settings before uninstalling.'],
               ['No USB device', 'Check host/OTG support, data cable, serial port and Android permission. Emulator screenshots do not demonstrate a physical USB connection.'],
               ['Bytes arrive, no GPS', 'Check protocol/baud and that the source emits supported GPS messages with a valid fix. Heartbeats and channel frames alone are insufficient.'],
               ['Writes increase, no marker', 'Check server routing, receive group/port, client filters, network isolation and fresh GPS. Socket writes are not receipts.'],
               ['Marker appears, video does not', 'Test the RTSP address from the receiving device. Check camera availability, routing, codecs and client support. The bridge does not relay video.'],
               ['Blank map', 'Tiles need network access or a usable existing cache. The coordinate readout and telemetry path work independently.']]),
    ('h', 'Evidence and remaining checks'),
    ('p', 'Validation covers native unit/lifecycle/transport tests, lint, browser tool checks, signed-APK verification and Android emulator interaction. Illustrations are real screenshots from the signed APK with synthetic demo data where indicated.'),
    ('p', 'Physical USB/GPS, screen-off endurance, deployed TAK routing and receiving-device marker/video playback still need bench validation. This app does not command aircraft or change flight modes. See the release page for checksums and known limitations.')])
MANUAL = [
    {'title': 'TAK Bridge', 'subtitle': 'Beta user manual', 'kind': 'cover',
     'intro': 'Install the Android beta, practice locally, connect an existing GPS stream and verify your aircraft in TAK.',
     'blocks': [('h', f'Beta {VERSION}'),
                ('p', f'<a href="{RELEASE}" color="#147463">Download the APK and companion guides from the GitHub beta release.</a>'),
                ('table', [['02', 'Install and try the software demo'], ['03', 'Connect USB telemetry'], ['04', 'Send positions to TAK'], ['05', 'Share an existing RTSP video link'], ['06', 'Read status and diagnostics'], ['07', 'Use planning tools'], ['08', 'Troubleshooting and validation limits']]),
                ('note', 'Screenshots from the running app', 'Captured directly from the signed beta APK on an Android emulator. Demo data is synthetic; no live aircraft, USB source, TAK receiver or camera is shown.')]},
    INSTALL, USB, TAK, VIDEO, DIAG, TOOLS, TROUBLE]
QUICK = [INSTALL,
    page('From demo to live telemetry', 'demo', [
        ('h', '1 / Finish local practice'),
        ('p', 'The demo cycles fresh GPS, silence and no-fix packets. Watch publishing pause when GPS becomes stale. Open Diagnostics for a local CoT preview, then tap <b>Stop demo</b>.'),
        ('h', '2 / Connect your source'),
        ('p', 'Use a USB host/OTG Android device and data cable. In <b>Connection settings</b>, select the profile, protocol and baud matching your existing aircraft GPS stream. Set a callsign, close settings and tap <b>Connect</b>. Select a port and grant USB access if prompted.'),
        ('h', '3 / Confirm fresh GPS'),
        ('p', 'Check coordinates and a valid source fix. Bytes alone do not prove GPS. Auto mode is passive; only the Direct FC MSP polling profile requests GPS. Stop before changing connection settings.'),
        ('note', 'Demo is not a hardware test', 'This screenshot shows synthetic data. Validate your real USB interface, cable and aircraft GPS before relying on the bridge.')],
        'Real emulator screenshot of the signed beta running its synthetic software demo.'),
    page('Verify TAK and video', 'video', [
        ('h', '1 / Choose the destination'),
        ('p', 'For local reception, enable <b>Local TAK</b> before connecting USB and configure the receiver for <b>239.2.3.1:6969</b>. For a server, enter its hostname/port and tap <b>Send to TAK</b>. TLS needs appropriate certificates, trust and a matching hostname.'),
        ('h', '2 / Check the receiving device'),
        ('p', 'With fresh GPS, confirm the correct aircraft marker and changing location in TAK. <b>Socket writes</b> proves only a local write, not server acceptance or ATAK display.'),
        ('h', '3 / Optionally attach video'),
        ('p', 'Stop, open <b>Connection settings → TAK video link</b>, enable sharing and save an existing reachable RTSP address. Restart telemetry, then check the marker’s video link and playback on the receiver. This app shares a link; it does not host or display the feed.'),
        ('h', '4 / Finish the session'),
        ('p', 'Use <b>Stop</b> or the notification action. To remove a video link, disable sharing and publish a new valid position. See the full manual for TLS setup and troubleshooting.'),
        ('note', 'Beta validation', 'Physical USB/GPS and receiving TAK/video playback remain unqualified. These screenshots show the signed app on an emulator, not a live deployment.')],
        'Actual beta video-sharing dialog. An example address does not demonstrate playback.')]

"""Generate the vendor-neutral, two-page quick-start PDF. Requires reportlab."""
from pathlib import Path
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import letter
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/TAK_Bridge_Quick_Start.pdf"
FONT_ROOT = Path("/usr/share/fonts/truetype/dejavu")
pdfmetrics.registerFont(TTFont("GuideSans", str(FONT_ROOT / "DejaVuSans.ttf")))
pdfmetrics.registerFont(TTFont("GuideSans-Bold", str(FONT_ROOT / "DejaVuSans-Bold.ttf")))
pdfmetrics.registerFontFamily("GuideSans", normal="GuideSans", bold="GuideSans-Bold")
styles = getSampleStyleSheet()
styles["BodyText"].fontName = "GuideSans"
styles["Title"].fontName = "GuideSans-Bold"
styles["Heading2"].fontName = "GuideSans-Bold"
styles.add(ParagraphStyle("BridgeTitle", parent=styles["Title"], fontSize=23, textColor=colors.HexColor("#174b4b")))
styles.add(ParagraphStyle("BridgeBody", parent=styles["BodyText"], fontSize=9.5, leading=13, spaceAfter=7))
styles.add(ParagraphStyle("BridgeHeading", parent=styles["Heading2"], fontSize=12, leading=15, spaceBefore=10, spaceAfter=6))
styles.add(ParagraphStyle("BridgeSmall", parent=styles["BridgeBody"], fontSize=8.5, leading=11))
story = []

def p(text, style="BridgeBody"):
    story.append(Paragraph(text, styles[style]))

def heading(text):
    p(text, "BridgeHeading")

def table(rows, widths):
    cells = [[Paragraph(text, styles["BridgeSmall"]) for text in row] for row in rows]
    t = Table(cells, colWidths=widths, repeatRows=1, hAlign="LEFT")
    t.setStyle(TableStyle([
        ("BACKGROUND", (0,0), (-1,0), colors.HexColor("#e1eeee")),
        ("VALIGN", (0,0), (-1,-1), "TOP"),
        ("LINEBELOW", (0,0), (-1,0), .7, colors.HexColor("#4b8080")),
        ("LINEBELOW", (0,1), (-1,-1), .3, colors.HexColor("#d8e0e0")),
        ("LEFTPADDING", (0,0), (-1,-1), 7), ("RIGHTPADDING", (0,0), (-1,-1), 7),
        ("TOPPADDING", (0,0), (-1,-1), 5), ("BOTTOMPADDING", (0,0), (-1,-1), 5),
    ]))
    story.append(t)

p("TAK BRIDGE", "BridgeTitle")
p("Drone GPS to MGRS to TAK | Quick Start | Development build 0.3.0", "BridgeSmall")
p("Reads aircraft GPS over USB using MAVLink, MSP, GHST or CRSF and relays valid, fresh positions as Cursor on Target (CoT) events. The app receives telemetry; only the Direct FC MSP profile sends a fixed GPS read request.")
heading("What you need")
table([
    ["Item", "Requirement"],
    ["Android device", "Android 8.0 or later with USB host/OTG support"],
    ["Data cable", "USB data cable or OTG adapter; charge-only cables cannot carry telemetry"],
    ["Telemetry source", "Compatible controller, transmitter or flight controller with configured GPS output"],
    ["TAK destination", "ATAK/WinTAK/iTAK receiving the configured multicast, or a TAK Server"],
], [120, 396])
heading("1. Choose a USB path")
p("<b>USB telemetry source:</b> Connect an already-configured, compatible USB aircraft telemetry output. This guide covers operation of the bridge; device-side feature discovery and enablement are outside its scope. Choose the app profile and protocol that match the stream actually provided by your source.")
p("<b>Direct flight controller:</b> Use MAVLink or passive auto-detect for an already-streaming source. For an MSP source that needs GPS requests, choose <b>Direct FC - MSP GPS polling</b>. This polls at 2 Hz. Selecting MSP in a passive profile does not enable polling.")
p("<b>Passive telemetry:</b> Use <b>Other USB - passive auto-detect</b> or select the actual protocol for an existing supported stream. RC/stick frames alone cannot provide aircraft GPS.")
heading("2. Connect and verify")
p("With the aircraft on the ground and propellers removed, connect the data cable. Press <b>CONNECT</b>, select a port if prompted, and approve Android USB access. The picker identifies devices by VID:PID and port. Confirm fresh GPS, fix and satellite status. A heartbeat or byte counter alone does not prove a GPS downlink.")
p("The foreground service keeps an active session running when you switch apps. Use <b>STOP</b> or the notification to end it. USB loss stops the session and clears position; reconnect explicitly.")
story.append(PageBreak())
heading("3. Send to TAK")
p("<b>Local multicast:</b> Enable <b>Local TAK</b> and configure the client to receive <b>239.2.3.1:6969</b>. Network isolation, VPNs or filtering can block delivery. A sent counter proves a socket write; verify the marker in the receiving client.")
p("<b>Server TCP:</b> Enter the server host and configured port (default 8087), then press <b>TAK</b>. <b>Server TLS:</b> Enable TLS, open <b>CERTIFICATES</b>, import the client .p12 with its password and the server CA if needed. Use a host name or IP present in the server certificate. Press <b>TAK</b> to unlock/connect. Passwords are memory-only and must be entered again after the process ends.")
heading("Reading the screen")
table([
    ["Element", "Meaning"],
    ["Coordinate overlay", "Tap to cycle MGRS, decimal degrees, DMS and UTM; CoT always uses decimal degrees"],
    ["Fix / satellites / speed / altitude", "Source GPS evidence; MSL altitude stays in display and remarks, HAE remains unknown"],
    ["Map marker and trail", "Aircraft position and heading, with recent breadcrumb history"],
    ["USB / TAK status and counters", "Connection stages and decoded/sent activity; not proof of client display"],
    ["GPS STALE", "No fresh position for more than five seconds; publishing has stopped"],
], [160, 356])
heading("Troubleshooting")
table([
    ["Problem", "Next check"],
    ["No USB serial interface", "Check cable, Android host role and source serial USB mode"],
    ["Permission denied", "Press CONNECT again and approve the Android access request"],
    ["USB open, no bytes", "Verify that the source is providing telemetry; check the selected port and stream settings"],
    ["Controller channels only", "Configure an upstream aircraft GPS stream; stick motion is not GPS"],
    ["GPS frames, no fix", "Check the source GPS fix; invalid or stale positions are not published"],
    ["TAK marker missing", "Verify group/port or server settings, receiving client and network filtering"],
    ["TLS rejected", "Check password, certificate dates, CA trust and host-name match"],
], [160, 356])
heading("Practice and Tools")
p("<b>SOFTWARE DEMO</b> exercises telemetry parsing and local CoT previews without USB. It never sends simulated aircraft positions to a network. <b>DIAGNOSTICS</b> exports counters without coordinates or credentials. Tools include RF calculators, terrain and mesh planning, generic VTX profiles and a target catalog. Match frequency/power tables and exact upstream target keys to your hardware before using generated configuration.")
p("Physical USB/GPS/TAK acceptance remains pending. See docs/USB_CONTROLLER_SETUP.md and docs/V1_READINESS.md. Compatibility keys and source/license attribution retain their exact upstream spelling.", "BridgeSmall")

def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont("GuideSans", 8)
    canvas.setFillColor(colors.HexColor("#546565"))
    canvas.drawString(48, 27, "github.com/DroneWuKong/MultiProtocol-UAS-TAK-Bridge")
    canvas.drawRightString(564, 27, str(doc.page))
    canvas.restoreState()

SimpleDocTemplate(str(OUT), pagesize=letter, rightMargin=48, leftMargin=48,
    topMargin=36, bottomMargin=42, title="TAK Bridge Quick Start",
    author="DroneWuKong").build(story, onFirstPage=footer, onLaterPages=footer)
print(OUT)

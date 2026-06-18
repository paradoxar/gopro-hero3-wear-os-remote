# GoPro HERO3 Wear OS Remote

A standalone Wear OS remote control app for the GoPro HERO3 / HERO3 Silver using the camera's legacy Wi-Fi HTTP API.

This app was created to control a GoPro HERO3 from a Samsung Galaxy Watch without needing a physical GoPro remote.

## Project Background

I built this project after mounting a GoPro HERO3 camera on a 2026 Yamaha WaveRunner FX Cruiser HO. The original plan was to mount a physical GoPro remote on the handlebars, but that would have required drilling holes into the new WaveRunner.

Instead, because the rider already wears a Samsung Galaxy Watch, this app turns the watch into a practical GoPro remote.

The result is a simple standalone Wear OS app that can:

* Power the GoPro HERO3 on
* Power the GoPro HERO3 off
* Start recording
* Stop recording
* Show camera battery bars
* Show camera recording status
* Provide vibration feedback from the watch

## Features

* Standalone Wear OS app
* No phone companion app required
* Direct Wi-Fi communication with the GoPro HERO3
* Power On control
* Power Off control
* Start Recording control
* Stop Recording control
* Battery level indicator using 1, 2, or 3 bars
* Recording status detection
* Wi-Fi connection indicator
* Long-press protection for important actions
* Vibration feedback for button actions
* Confirmation vibration when recording is verified

## Tested Hardware

This project was tested with:

* Samsung Galaxy Watch 8
* Wear OS
* GoPro HERO3 Silver
* GoPro HERO3 Wi-Fi in `GoProApp` mode

Other HERO3 / HERO3+ models may work, but they have not all been tested.

## Important Safety Design

This app was designed for real outdoor use where accidental touches can happen.

For that reason:

* Short press on REC does nothing
* Long press on REC starts recording
* Short press on Power Off does nothing
* Long press on Power Off sends the power off command
* The REC button only turns red after the camera reports that it is actually recording
* A second vibration confirms that recording has started

This helps prevent accidental recording or accidental camera power off while riding.

## How It Works

The GoPro HERO3 exposes a simple legacy HTTP API over Wi-Fi.

The app connects directly from the watch to the GoPro HERO3 Wi-Fi network and sends HTTP commands to:

```text
http://10.5.5.9
```

The app uses the HERO3 camera status endpoint to read battery and recording status.

## HERO3 Wi-Fi Setup

On the GoPro HERO3:

1. Open the wireless settings
2. Select `GoProApp`
3. Connect the watch to the GoPro HERO3 Wi-Fi network
4. Open the Wear OS app

The watch must be connected to the GoPro HERO3 Wi-Fi network for the app to work.

## Required User Configuration

Before building the app, update the GoPro password/token value in the source code.

Example:

```java
private static final String GOPRO_PASSWORD = "YOUR_HERO3_PASSWORD";
```

Do not publish your personal GoPro Wi-Fi password or token.

The default HERO3 IP address is normally:

```text
10.5.5.9
```

## HERO3 Commands Used

Power On:

```text
/bacpac/PW?t=PASSWORD&p=%01
```

Power Off:

```text
/bacpac/PW?t=PASSWORD&p=%00
```

Start Recording:

```text
/camera/SH?t=PASSWORD&p=%01
```

Stop Recording:

```text
/camera/SH?t=PASSWORD&p=%00
```

Camera Status:

```text
/camera/sx?t=PASSWORD
```

Wi-Fi Module Status:

```text
/bacpac/se?t=PASSWORD
```

## Status Bytes Used

Through testing, this app uses the following HERO3 status bytes from `/camera/sx`:

```text
data[19] = Battery bars
data[29] = Recording status
```

Battery behavior confirmed during testing:

```text
3 = three green bars
2 = two amber bars
1 = one red bar
```

Recording behavior:

```text
data[29] != 0 means the camera is recording
data[29] == 0 means the camera is not recording
```

## Watch Indicators

Wi-Fi icon:

```text
Gray   = Watch is not connected to the HERO3 Wi-Fi module
Yellow = Watch can reach the HERO3 Wi-Fi module
```

Power button:

```text
Gray  = Camera is not awake/responding
Green = Camera is powered on and responding
```

REC button:

```text
Gray = Camera is not recording
Red  = Camera reports that it is recording
```

Battery bars:

```text
Gray bars     = Camera status unavailable
Green bars    = Good battery
Amber bars    = Medium battery
Red bar       = Low battery
```

## Building the App

1. Open the project in Android Studio
2. Open `MainActivity.java`
3. Replace the GoPro password/token with your own HERO3 value
4. Connect your Wear OS watch to Android Studio
5. Build and install the app on the watch
6. Connect the watch to the HERO3 Wi-Fi network
7. Open the app

## Usage

Before riding or recording:

1. Turn on HERO3 Wi-Fi
2. Set HERO3 Wi-Fi mode to `GoProApp`
3. Connect the watch to the GoPro HERO3 Wi-Fi network
4. Open the watch app
5. Confirm the Wi-Fi icon turns yellow
6. Press Power On
7. Confirm battery bars appear
8. Long-press REC to start recording
9. Confirm REC turns red
10. Use Stop to stop recording
11. Long-press Power Off when finished

## Known Limitations

* This app is designed for GoPro HERO3-era cameras using the legacy HTTP API
* It is not intended for modern GoPro models
* The watch must connect directly to the GoPro Wi-Fi network
* Internet access may not be available while connected to the GoPro Wi-Fi network
* HERO3 status bytes may vary between HERO3 models or firmware versions
* Battery and recording status are based on tested HERO3 Silver behavior
* The app is not affiliated with or endorsed by GoPro

## Privacy and Publishing Notes

Before publishing your own fork or copy of this project, make sure you do not upload:

* Your real GoPro Wi-Fi password
* Your real Wi-Fi SSID
* HAR capture files
* Reqable capture files
* Android Studio build folders
* `.gradle`
* `.idea`
* `build`
* `local.properties`
* APK signing keys
* Keystore files

Use placeholders instead of personal values.

## Disclaimer

This project is provided as a community example for older GoPro HERO3 cameras.

Use it at your own risk. Always test the app in a safe environment before using it while riding, boating, driving, or operating equipment.

This project is not affiliated with, sponsored by, or endorsed by GoPro.

## License

MIT License

## Author

Created by Paradoxar.

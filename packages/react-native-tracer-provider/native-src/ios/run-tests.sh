#!/bin/bash
set -e

# Define constants for script
PLATFORM="iphonesimulator"
DEVICE_PATTERN="iPhone.*"
FALLBACK_SIMULATOR_DEVICE_TYPE="com.apple.CoreSimulator.SimDeviceType.iPhone-15"

# Use xcodebuild to retrieve list of available SDKs in JSON format
# Use jq for SDK_JSON to find first item in array where platform is 'iphonesimulator'
SDK_JSON=$(xcodebuild -showsdks -json)
SELECTED_SDK=$(echo $SDK_JSON | jq -r ".[] | select(.platform == \"$PLATFORM\")")

if [ -z "$SDK_PATH" ] ; then
    echo "SDK_PATH not set."
fi

if [ -z "$SDK_NAME" ] ; then
    echo "SDK_NAME not set."
fi

SDK_PATH=$(echo $SELECTED_SDK | jq -r '.sdkPath')
SDK_NAME=$(echo $SELECTED_SDK | jq -r '.canonicalName')

# Output selected SDK to console
echo '--- SELECTED SDK ---'
echo $SDK_NAME
echo $SDK_PATH

# Use xcrun simctl to get identifier of first available iPhone simulator
# Use jq to parse JSON output like devices.txt
AVAILABLE_DEVICES_JSON=$(xcrun simctl list -j devices available)
DEVICE_JSON_LIST=$(echo $AVAILABLE_DEVICES_JSON | jq -r ".devices | flatten | map(select(.name | test(\"$DEVICE_PATTERN\")))")

# Select the last device in the list and grab its UDID
SELECTED_DEVICE=$(echo $DEVICE_JSON_LIST | jq -r "last")
SELECTED_DEVICE_UDID=$(echo $SELECTED_DEVICE | jq -r ".udid")

# Output selected device to console
echo "--- Selected Device ---"
echo "$SELECTED_DEVICE"

# If SELECTED_DEVICE is null, create a new simulator and use its UDID
if [ "$SELECTED_DEVICE" == "null" ]; then
    echo "No available devices found matching pattern '$DEVICE_PATTERN'"
    echo "Creating new simulator..."
    CREATED_DEVICE_UDID=$(xcrun simctl create "Embrace-iPhone" "$FALLBACK_SIMULATOR_DEVICE_TYPE")
    echo "Created new simulator with UDID: $CREATED_DEVICE_UDID"
    SELECTED_DEVICE_UDID=$CREATED_DEVICE_UDID
fi


mv ReactNativeTracerProvider.xcworkspace/xcshareddata/swiftpm/Package.resolved ReactNativeTracerProvider.xcworkspace/xcshareddata/swiftpm/Package.resolved.bak
pod install
mv ReactNativeTracerProvider.xcworkspace/xcshareddata/swiftpm/Package.resolved.bak ReactNativeTracerProvider.xcworkspace/xcshareddata/swiftpm/Package.resolved

# `IS_XCTEST` envvar is work-around for plugin dependency being included in iOS target when using `xcodebuild test`
# (See Package.swift `targetPlugins`)
# https://forums.swift.org/t/xcode-attempts-to-build-plugins-for-ios-is-there-a-workaround/57029
IS_XCTEST=true \
xcodebuild \
    -sdk $SDK_PATH \
    -workspace "ReactNativeTracerProvider.xcworkspace" \
    -configuration "Debug" \
    -scheme "ReactNativeTracerProvider" \
    -skipPackagePluginValidation \
    -destination "id=$SELECTED_DEVICE_UDID" \
    test

# If we created a new simulator, delete it
if [ -n "${CREATED_DEVICE_UDID}" ]; then
    echo "Deleting simulator with UDID: $CREATED_DEVICE_UDID"
    xcrun simctl delete $CREATED_DEVICE_UDID
fi

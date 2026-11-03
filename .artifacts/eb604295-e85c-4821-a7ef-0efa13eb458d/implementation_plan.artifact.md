# Restore Profile Screen and Update Map Vehicle Marker

Restoring the missing document boxes in the Profile Screen, fixing the dropdown menus, and updating the vehicle marker on the map to include a motion effect (ripples) with a default car shape.

## User Review Required

> [!IMPORTANT]
> - The vehicle marker will now feature concentric circles to simulate a motion/pulse effect as requested.
> - Document boxes (ID Card, License Front, License Back) will be added back to the Profile Screen.
> - Dropdown menus will be simplified to ensure they open reliably when in Edit mode.

## Proposed Changes

### [app](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20unknown/app)

#### [MODIFY] [ProfileScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20unknown/app/src/main/java/com/albasel/tawsilaapp/ui/screens/profile/ProfileScreen.kt)
- Add `DocBox` calls for `idCardDriveId`, `licenseFrontDriveId`, and `licenseBackDriveId`.
- Add image pickers for these documents.
- Refactor dropdown menus for Province, Color, and Type to ensure stability.
- Add "Documents" section header.

#### [MODIFY] [MapMarkerUtils.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20unknown/app/src/main/java/com/albasel/tawsilaapp/utils/MapMarkerUtils.kt)
- Update `createVehicleMarker` to draw a "motion effect" using concentric ripples around the vehicle.
- Use a larger canvas to accommodate the ripples.

#### [MODIFY] [HomeScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20unknown/app/src/main/java/com/albasel/tawsilaapp/ui/screens/home/HomeScreen.kt)
- Ensure the default car icon `ic_privatecar1` is used if the specific icon is missing, and that the motion effect is visible.

## Verification Plan

### Automated Tests
- Build the project to ensure no compilation errors.

### Manual Verification
- Deploy to a device.
- Open Profile Screen, enter Edit mode, and verify Province/Color/Type dropdowns open.
- Verify Document boxes are visible and clickable.
- Open the map (Home or Tracking) and verify the vehicle marker has the new ripple effect.

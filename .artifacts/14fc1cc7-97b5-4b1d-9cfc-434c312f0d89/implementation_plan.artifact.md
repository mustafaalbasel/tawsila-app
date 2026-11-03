# Tawsila App UI and Logic Enhancements

This plan outlines the changes to replace gender buttons with icons, optimize pathfinding, restyle the in-city booking button, enhance trip tracking, and improve the loading speed of nearby drivers.

## Proposed Changes

### [HomeScreen](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah/app/src/main/java/com/albasel/tawsilaapp/ui/screens/home/HomeScreen.kt)

#### [MODIFY] [HomeScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah/app/src/main/java/com/albasel/tawsilaapp/ui/screens/home/HomeScreen.kt)
- Update `TripOptionButton` to support `Painter` for custom icons.
- Update `GenderIconToggle` to use `male_icon` and `female_icon` drawables instead of `ImageVector`.
- In the route selection logic, automatically select the route with the minimum distance (`shortest path`).
- Update the vehicle marker in the home map to move only when the user moves, matching their speed.

---

### [InsideCityScreen](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah/app/src/main/java/com/albasel/tawsilaapp/ui/screens/city/InsideCityScreen.kt)

#### [MODIFY] [InsideCityScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah/app/src/main/java/com/albasel/tawsilaapp/ui/screens/city/InsideCityScreen.kt)
- Rename "Order Now" ("اطلب الآن") to "Book Now" ("احجز الآن").
- Adjust the layout to move the button down by approximately 5mm.
- Apply a gold/orange gradient style to the button to match the reference image.
- Ensure the button dimensions match the home screen's "Book Now" button.

---

### [TrackRideScreen](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah/app/src/main/java/com/albasel/tawsilaapp/ui/screens/map/TrackRideScreen.kt)

#### [MODIFY] [TrackRideScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah/app/src/main/java/com/albasel/tawsilaapp/ui/screens/map/TrackRideScreen.kt)
- Disable the distance and location FABs if `selectedOrder` is null.
- Change the location button icon to a "driver location" specific icon and ensure it centers on the driver's current position.
- Change the distance button icon to one that implies distance between two parties.
- Replace the current driver/vehicle markers with the car drawable used in `HomeScreen.kt`.
- Logic update: vehicle marker in map only moves when the driver's location changes in Firebase.

---

### [NearestDriversScreen](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah/app/src/main/java/com/albasel/tawsilaapp/ui/screens/map/NearestDriversScreen.kt)

#### [MODIFY] [NearestDriversScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah/app/src/main/java/com/albasel/tawsilaapp/ui/screens/map/NearestDriversScreen.kt)
- Optimize location fetching by prioritizing `lastLocation` for immediate map centering, followed by an asynchronous `getCurrentLocation` update if necessary.

---

## Verification Plan

### Automated Tests
- Build the project to ensure no syntax errors in the new icon/style implementations.

### Manual Verification
- **Home Screen:** Verify male/female buttons show icons. Swap pickup/dest and check if shortest distance is chosen.
- **In-City:** Check button text, position, and gradient style.
- **Tracking:** Select a trip and verify FABs are enabled. Check marker movement.
- **Nearby:** Verify map centers faster on user location.

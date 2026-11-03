# Implementation Plan - UI Enhancements & Business Logic Updates

This plan covers UI improvements for the Date/Time pickers, map marker animations, trip cancellation fees, and driver information visibility.

## User Review Required

> [!IMPORTANT]
> The cancellation fee logic now depends on the trip status. If the driver has confirmed the trip (`CONFIRMED` status), the user will be charged the "meter opening fee" (base fare) upon cancellation.

## Proposed Changes

### Data Layer

#### [MODIFY] [FirebaseManager.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah final/app/src/main/java/com/albasel/tawsilaapp/data/firebase/FirebaseManager.kt)
- Update `acceptOrder` to copy the driver's `vehiclePlate` and `vehicleColor` into the `Order` record.
- Modify `cancelOrderWithCompensation` to accept a dynamic amount (the base fare) instead of a fixed 20.0.

### UI Components & Screens

#### [MODIFY] [InsideCityScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah final/app/src/main/java/com/albasel/tawsilaapp/ui/screens/city/InsideCityScreen.kt)
- **Date/Time Layout:** Swap the Time and Date cards so Date is on the left and Time is on the right.
- **Width Adjustment:** Reduce the width of the cards to approximately 40% of the screen each, centering them in the row.
- **Date Restriction:** Limit the `years` list to the current year unless the current month is December, in which case the next year is also available.

#### [MODIFY] [HomeScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah final/app/src/main/java/com/albasel/tawsilaapp/ui/screens/home/HomeScreen.kt)
- **Car Animation:** Implement smooth interpolation for the car marker's position and rotation (bearing).
- **Motion Effect:** Ensure the car icon faces the direction of travel and moves linearly between updates to eliminate "choppy" snapping.

#### [MODIFY] [RideHistoryScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah final/app/src/main/java/com/albasel/tawsilaapp/ui/screens/history/RideHistoryScreen.kt)
- **Cancellation Logic:**
    - If trip status is `ACCEPTED`: Cancel/Delete without fee.
    - If trip status is `CONFIRMED`: Fetch the `baseFare` for the trip's vehicle type and apply it as a cancellation fee.

#### [MODIFY] [TrackRideScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah final/app/src/main/java/com/albasel/tawsilaapp/ui/screens/map/TrackRideScreen.kt)
- Ensure the `DriverInfoDialog` correctly displays the plate number and vehicle color (now that they will be populated in `acceptOrder`).
- (Optional) Apply smooth marker interpolation here as well for consistency.

## Verification Plan

### Automated Tests
- N/A (UI and Firebase logic-heavy).

### Manual Verification
1.  **Inside City Screen:** Verify Date is on the left, Time is on the right. Check that 2027 is not selectable unless the system date is in December 2026.
2.  **Home Map:** Observe the car marker movement. It should rotate smoothly towards the next point and slide instead of jumping.
3.  **Cancellation:**
    - Place a trip, have it accepted. Cancel -> verify no fee.
    - Have it confirmed. Cancel -> verify points deducted equal to the vehicle's base fare.
4.  **Driver Info:** Accept a trip as a driver, then view info as a customer. Verify Plate and Color are visible.

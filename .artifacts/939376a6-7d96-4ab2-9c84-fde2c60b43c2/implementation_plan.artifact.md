# Implementation Plan - Functional Improvements & Notification Fixes

This plan addresses driver notification crashes, improves the date/time picker logic, refines notification content, and implements the requested wallet/cancellation logic.

## User Review Required

> [!IMPORTANT]
> The cancellation logic assumes that customer debt is tracked via negative `points` in their profile. When a customer pays off this debt to a driver during a trip, the amount is deducted from the driver's `driverPoints` balance, as the driver has received the equivalent cash. This is already partially implemented but will be solidified.

> [!WARNING]
> Notification routes will be standardized to `history?orderId=...` for customers and `driver_orders?tab=0&orderId=...` for drivers to prevent crashes and ensure correct screen navigation.

## Proposed Changes

### 1. Notification Fixes & Content Updates

#### [MODIFY] [FirebaseManager.kt](file:///E:/AndroidStudioProjects/TawsilaApp LAST alhamdolilah final/app/src/main/java/com/albasel/tawsilaapp/data/firebase/FirebaseManager.kt)
- Update `notifyRelevantDrivers` to include the route `driver_orders?tab=0`.
- Update `acceptOrder` to use the route `history?orderId=$oid` and change the message to include the driver's name prominently.
- Update `finalizeOrders` to use the route `history?orderId=$id` and include the driver's name in the confirmation message.

#### [MODIFY] [TawsilaApp.kt](file:///E:/AndroidStudioProjects/TawsilaApp LAST alhamdolilah final/app/src/main/java/com/albasel/tawsilaapp/ui/TawsilaApp.kt)
- Update the navigation route for `history` to be `history?orderId={orderId}` explicitly if it's not already handled (checking `Screen.kt` showed it as `history`).
- Ensure the deep-link handler correctly parses `ride_history?id=` to `history?orderId=` if legacy notifications are still in flight.

### 2. Date/Time Picker Logic

#### [MODIFY] [HomeScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp LAST alhamdolilah final/app/src/main/java/com/albasel/tawsilaapp/ui/screens/home/HomeScreen.kt)
- Refine the "Next Hour" logic for `selectedTime`.
- Ensure `selectedDate` and `selectedTime` indices are correctly passed to the `WheelPicker`.
- Style adjustments to ensure the pickers are prominent and clear.

#### [MODIFY] [WheelPicker.kt](file:///E:/AndroidStudioProjects/TawsilaApp LAST alhamdolilah final/app/src/main/java/com/albasel/tawsilaapp/ui/components/WheelPicker.kt)
- Improve the item selection logic to better center the selected item and provide better visual feedback.

### 3. Cancellation & Wallet Logic

#### [MODIFY] [RideHistoryScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp LAST alhamdolilah final/app/src/main/java/com/albasel/tawsilaapp/ui/screens/history/RideHistoryScreen.kt)
- Update the cancellation warning dialog to match the requested wording: "سيتم خصم فتح العداد نعم لا".
- Ensure the cancellation fee (base fare) is correctly deducted from the customer's points.

## Verification Plan

### Automated Tests
- Not applicable for UI/Firebase logic changes, but manual verification on device is required.

### Manual Verification
1. **Notification Click:**
   - Send a "New Request" notification to a driver.
   - Click the notification while the app is closed.
   - Verify it opens `DriverOrdersScreen` at the correct tab without crashing.
2. **Accept/Confirm Notifications:**
   - Accept an order as a driver.
   - Verify the customer receives a notification: "السائق [Name] قبل الأوردر".
   - Confirm the order as a driver.
   - Verify the customer receives a notification: "السائق [Name] أكد الأوردر وهو في طريقه إليك".
   - Verify clicking these notifications opens the trip in `RideHistoryScreen`.
3. **Date/Time Picker:**
   - Check the default time when the current time is 3:15 PM. It should be 4:00 PM.
   - Verify the wheels are centered.
4. **Cancellation Fee:**
   - Cancel a confirmed trip as a customer.
   - Verify the dialog appears with the correct wording.
   - Verify points are deducted from the customer.
5. **Debt Payment:**
   - As a customer with negative points, book a new ride.
   - Use the "Points Box" to pay off the debt.
   - Complete the ride and verify the new driver's points are deducted by the debt amount.

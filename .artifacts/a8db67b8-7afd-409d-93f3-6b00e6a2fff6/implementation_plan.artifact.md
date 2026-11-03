# Implementation Plan - App Enhancements and UI Refinement

This plan covers multiple enhancements including bus capacity handling, vehicle ordering in the admin panel, manual payment improvements for drivers, admin notifications, and UI adjustments on the home screen.

## Proposed Changes

### 1. Data Models
- **[MODIFY] [User.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah1/app/src/main/java/com/albasel/tawsilaapp/data/model/User.kt)**
    - Add `busCapacity: Int = 0` to track the specific capacity of a bus.
    - Add `totalCommissionAccrued: Double = 0.0` to track all-time earned commission separately from `pendingCommission`.
- **[MODIFY] [VehiclePrice.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah1/app/src/main/java/com/albasel/tawsilaapp/data/model/VehiclePrice.kt)**
    - Add `priority: Int = 0` to allow ordering vehicles in lists.

### 2. Firebase Management
- **[MODIFY] [FirebaseManager.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah1/app/src/main/java/com/albasel/tawsilaapp/data/firebase/FirebaseManager.kt)**
    - Update `recordOrderFinancials` to increment `totalCommissionAccrued`.
    - Update `recordCommissionPayment` to subtract from `totalCommissionAccrued` (or handle as requested). *Correction:* I will keep `pendingCommission` as the net due, but ensure it's calculated clearly.
    - Add `deleteCommissionPayment(driverId: String, paymentId: String, amount: Double)` to reverse a manual payment.
    - Add `updateCommissionPayment(...)` if needed.
    - Add `notifyAdminsAboutPaymentReceipt(driverId: String, driverName: String)` to send an FCM notification to all admins.
    - Call the notification function inside `addPaymentReceipt`.

### 3. Registration & Profile
- **[MODIFY] [RegisterScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah1/app/src/main/java/com/albasel/tawsilaapp/ui/screens/auth/RegisterScreen.kt)**
    - When "Bus" (باص) is selected, show an `OutlinedTextField` for "Bus Capacity".
    - Add a hint text below it: "Max 50 passengers" (أقصى عدد ركاب 50 راكب).
    - Validate input to ensure it's between 1 and 50.
- **[MODIFY] [ProfileScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah1/app/src/main/java/com/albasel/tawsilaapp/ui/screens/profile/ProfileScreen.kt)**
    - Add the same "Bus Capacity" field logic when the vehicle type is changed to Bus.

### 4. Admin Panel
- **[MODIFY] [AdminHomeScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah1/app/src/main/java/com/albasel/tawsilaapp/ui/screens/admin/AdminHomeScreen.kt)**
    - **PricesTab:**
        - Add a `LazyColumn` or a list to show all vehicles.
        - Add buttons or a field to set the `priority` for each vehicle.
    - **UsersTab (Driver Details):**
        - Display `paidCommission` (ما تم دفعه مسبقاً) explicitly.
        - Adjust "Final Commission Required" display to explicitly show the deduction.
        - Add a delete icon/button next to each entry in the payment history list.
        - Add edit functionality for manual payments.

### 5. UI Enhancements
- **[MODIFY] [HomeScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20alhamdolilah1/app/src/main/java/com/albasel/tawsilaapp/ui/screens/home/HomeScreen.kt)**
    - Sort the `vehicleList` by `priority` (descending or ascending based on admin setting).
    - **[MODIFY] `TripOptionButton`**:
        - Reduce the selection circle size from `54.dp` to `52.dp`.
        - Reduce selection circle alpha from `0.25f` to `0.15f`.

## Verification Plan

### Automated Tests
- Run `gradle build` to ensure no syntax errors in the new fields and logic.

### Manual Verification
- **Sign-up:** Select "Bus" and verify the capacity field appears with the correct hint and max value of 50.
- **Admin Panel:**
    - Change vehicle priority and verify it updates in the Home screen.
    - Record a manual payment, verify it shows in history, and then delete it to verify balances are restored.
- **Notifications:** Send a receipt from the Driver wallet and verify the Admin receives a notification.
- **Home UI:** Verify the gender selection circle is slightly smaller and lighter.

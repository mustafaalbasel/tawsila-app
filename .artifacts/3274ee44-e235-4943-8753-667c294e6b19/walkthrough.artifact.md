# Walkthrough - UI Enhancements & Cancellation Logic

Successfully implemented the requested UI improvements, animation smoothing, and business logic updates.

## Key Changes

### 1. Date & Time Picker (Inside City)
- **Layout Swap:** The Date card is now on the left and the Time card is on the right.
- **Responsive Width:** Cards are narrowed to ~42% width each and centered for a professional look.
- **Year Restriction:** Users can only select the current year (e.g., 2026). The option for the next year (2027) will only appear when the current month is December.

### 2. Smooth Map Animations
- **Marker Interpolation:** Implemented `Animatable` transitions for the car marker's latitude, longitude, and bearing in both `HomeScreen` and `TrackRideScreen`.
- **Natural Movement:** The car now rotates towards its heading and slides smoothly between location updates, eliminating the "choppy" snapping effect.

### 3. Smart Cancellation Fees
- **Status-Based Logic:**
    - **ACCEPTED:** If a trip is cancelled while in the `ACCEPTED` status, no fee is charged and the order is deleted.
    - **CONFIRMED:** If cancelled after the driver confirms, the vehicle's `baseFare` (meter opening price) is deducted from the customer's points and awarded to the driver as compensation.
- **Dynamic Pricing:** Fees are fetched in real-time based on the specific vehicle type of the order.

### 4. Driver Visibility
- **Data Persistence:** `acceptOrder` now captures and stores `vehiclePlate` and `vehicleColor` directly in the `Order` record.
- **UI Display:** The Driver Info dialog in `TrackRideScreen` now correctly displays these details to the customer.

## Verification Results

### Manual Verification Path
1. **Inside City:** Checked the date wheel; only current year is visible.
2. **Map:** Observed car marker during location simulation; movement is linear and rotation is smooth (shortest angle path).
3. **Cancellation:** Verified point deduction only occurs when `status == "CONFIRMED"`.

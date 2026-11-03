# UI and Search Improvements Plan

This plan addresses several UI and functional requests for the Main screen:
1.  **Remove moving vehicle**: Stop the vehicle animation and remove its marker from the map.
2.  **Enlarge start arrow**: Increase the size of the navigation arrow (pickup marker) by 1.5x.
3.  **Adjust search priority**: Improve the search result sorting to prioritize proximity, then city, then governorate.
4.  **Fix search button**: Ensure the search button doesn't redundantly change the location if a result is already selected/fixed.

## Proposed Changes

### [Utils]
#### [MODIFY] [MapMarkerUtils.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20unknown/app/src/main/java/com/albasel/tawsilaapp/utils/MapMarkerUtils.kt)
- Increase the base `size` in `createNavigationArrowIcon` from 60 to 90 (1.5x).

### [UI Screens]
#### [MODIFY] [HomeScreen.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20unknown/app/src/main/java/com/albasel/tawsilaapp/ui/screens/home/HomeScreen.kt)
- Remove the two `LaunchedEffect` blocks that handle the animated car (animation logic and drawing logic).
- Update `onSearchClick` to prevent redundant location updates if the result hasn't changed or if the user is just clicking it again after selection.

### [Data Layer]
#### [MODIFY] [SearchRepositoryImpl.kt](file:///E:/AndroidStudioProjects/TawsilaApp%20LAST%20unknown/app/src/main/java/com/albasel/tawsilaapp/data/repository/impl/SearchRepositoryImpl.kt)
- Refine the sorting logic in `searchPlaces` to better prioritize results based on distance and administrative levels (city/state).

## Verification Plan

### Manual Verification
- Deploy the app and verify:
    - The moving car is gone from the map.
    - The green/gold arrow at the pickup point is larger.
    - Search results show closer places first, followed by city-level and then state-level results.
    - Clicking the search button after a location is set doesn't cause unexpected jumps or changes if the query is the same.

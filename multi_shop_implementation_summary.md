# Multi-Shop Implementation for SwarnaKhataBook

## Overview

This implementation allows users to create and manage multiple shops under a single account, with each shop having its own isolated data. The data isolation is achieved by structuring the Firestore database with shop-specific collections.

## Key Components

### 1. Data Classes

- **UserProfile**: Stores user information including a map of managed shops
- **ShopDetails**: Contains shop-specific information such as name, address, and GST details

### 2. Session Management

- **SessionManager**: Singleton class that manages the currently active shop ID
  - Persists the active shop ID in SharedPreferences
  - Provides LiveData to observe changes to the active shop
  - Handles retrieval of current user ID from Firebase Auth

### 3. Shop Management

- **ShopManager**: Extended to support multi-shop functionality
  - New methods for user profile and shop CRUD operations
  - Maintains backward compatibility with existing single-shop methods
  - Provides conversion between new and legacy data structures

### 4. UI Components

- **ShopSelectionFragment**: Displays a list of user's shops for selection
- **CreateShopFragment**: Allows creation of new shops
- **MainActivity**: Includes a "Switch Shop" menu option for shop switching
- **MainScreenFragment**: Displays the current active shop name in the toolbar

### 5. Navigation Flow

- App startup flow checks:
  - If the user has no shops → Navigate to shop creation
  - If the user has one shop → Set it as active and go to main screen
  - If the user has multiple shops → Navigate to shop selection (or use previously active shop)

### 6. Repository Refactoring

- Repositories now use the active shop ID from SessionManager
- Firestore paths updated from `/users/{userId}/...` to `/shopData/{shopId}/...`
- All data operations are scoped to the active shop ID
- Custom exceptions for shop-related errors

## Data Structure

### Firestore Collections

- `/users/{userId}`: Stores UserProfile data with managedShops map
- `/shops/{shopId}`: Stores ShopDetails
- `/shopData/{shopId}/customers/{customerId}`: Stores Customer data
- `/shopData/{shopId}/invoices/{invoiceId}`: Stores Invoice data
- (Similar pattern for inventory, payments, etc.)

## Benefits

1. **Data Isolation**: Each shop's data is completely separate
2. **Shared User Account**: Single login manages multiple businesses
3. **Easy Switching**: Users can quickly switch between shops
4. **Backward Compatibility**: Legacy methods maintained to support existing functionality

## Migration Strategy

For existing users, a migration process is required to:
1. Create a UserProfile with their existing data
2. Create a ShopDetails entry for their existing shop
3. Move data from `/users/{userId}/...` to `/shopData/{shopId}/...`

This can be implemented via Cloud Functions or as an in-app migration during the first login after updating. 
# Migration Plan for Multi-Shop Implementation

## Migration Overview

The migration process will move existing data from the old structure (`/users/{userId}/...`) to the new multi-shop structure (`/shopData/{shopId}/...`). This process should be as seamless as possible for existing users while ensuring data integrity.

## Migration Approach Options

### Option 1: In-App Migration (Recommended)

This approach performs the migration when the user first logs in after updating to the new version.

#### Implementation Steps:

1. **Detect Migration Need**: 
   ```kotlin
   suspend fun needsMigration(userId: String): Boolean {
       // Check if user already has a UserProfile in the new structure
       val userProfileResult = ShopManager.getUserProfile(userId)
       return userProfileResult.isSuccess && userProfileResult.getOrNull() == null
   }
   ```

2. **Create Migration Helper**:
   ```kotlin
   class DataMigrationHelper(private val context: Context) {
       private val firestore = FirebaseFirestore.getInstance()
       private val auth = FirebaseAuth.getInstance()
       
       suspend fun migrateUserData(): Result<String> {
           val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not logged in"))
           
           try {
               // 1. Get old shop data
               val oldShop = ShopManager.getShopCoroutine(context, true)
                   ?: return Result.failure(Exception("No existing shop data found"))
               
               // 2. Create new UserProfile
               val userProfile = UserProfile(
                   userId = userId,
                   name = oldShop.name,
                   phoneNumber = oldShop.phoneNumber,
                   createdAt = oldShop.createdAt
               )
               
               // 3. Create new ShopDetails
               val shopDetails = ShopDetails(
                   shopName = oldShop.shopName,
                   address = oldShop.address,
                   hasGst = oldShop.hasGst,
                   gstNumber = if (oldShop.hasGst) oldShop.gstNumber else null,
                   logoUrl = oldShop.logo,
                   signatureUrl = oldShop.signature,
                   createdAt = oldShop.createdAt
               )
               
               // 4. Save UserProfile
               val userProfileResult = ShopManager.saveUserProfile(userProfile)
               if (!userProfileResult.isSuccess) {
                   return Result.failure(userProfileResult.exceptionOrNull() 
                       ?: Exception("Failed to save user profile"))
               }
               
               // 5. Create shop and get shopId
               val shopResult = ShopManager.createShop(userId, shopDetails)
               if (!shopResult.isSuccess) {
                   return Result.failure(shopResult.exceptionOrNull() 
                       ?: Exception("Failed to create shop"))
               }
               
               val shopId = shopResult.getOrNull()!!
               
               // 6. Migrate all data collections
               migrateCustomers(userId, shopId)
               migrateInvoices(userId, shopId)
               migrateInventory(userId, shopId)
               migratePayments(userId, shopId)
               // Additional collections as needed
               
               return Result.success(shopId)
           } catch (e: Exception) {
               return Result.failure(e)
           }
       }
       
       private suspend fun migrateCustomers(userId: String, shopId: String) {
           // Get all customers from old path
           val oldCustomers = firestore.collection("users")
               .document(userId)
               .collection("customers")
               .get()
               .await()
           
           // For each customer, create in new path
           for (doc in oldCustomers.documents) {
               val customer = doc.toObject(Customer::class.java) ?: continue
               
               firestore.collection("shopData")
                   .document(shopId)
                   .collection("customers")
                   .document(customer.id)
                   .set(customer)
                   .await()
           }
       }
       
       // Similar methods for migrateInvoices, migrateInventory, migratePayments, etc.
   }
   ```

3. **Trigger Migration on Login**:
   ```kotlin
   // In SplashViewModel or appropriate place after authentication
   private fun handleSuccessfulLogin(userId: String) {
       viewModelScope.launch {
           if (needsMigration(userId)) {
               val migrationHelper = DataMigrationHelper(getApplication())
               val result = migrationHelper.migrateUserData()
               
               if (result.isSuccess) {
                   val shopId = result.getOrNull()!!
                   SessionManager.setActiveShopId(getApplication(), shopId)
                   _navigationEvent.value = NavigationEvent.NavigateToDashboard
               } else {
                   // Handle migration failure
                   _navigationEvent.value = NavigationEvent.NavigateToRegistration
               }
           } else {
               // Already migrated, proceed with normal flow
               checkUserShops(userId)
           }
       }
   }
   ```

4. **Show Migration Progress** (optional):
   - Display a progress dialog during migration
   - Show success/failure message after completion

### Option 2: Cloud Functions Migration

This approach uses Firebase Cloud Functions to perform the migration in the background.

#### Implementation Steps:

1. **Create Cloud Function**:
   ```javascript
   // In your Firebase Cloud Functions
   exports.migrateUserData = functions.https.onCall(async (data, context) => {
     // Ensure user is authenticated
     if (!context.auth) {
       throw new functions.https.HttpsError('unauthenticated', 'User must be logged in');
     }
     
     const userId = context.auth.uid;
     const db = admin.firestore();
     
     // 1. Check if already migrated
     const userProfileDoc = await db.collection('users').doc(userId).get();
     if (userProfileDoc.exists && userProfileDoc.data().managedShops) {
       return { success: true, message: 'Already migrated' };
     }
     
     // 2. Get old shop data
     const oldShopDoc = await db.collection('users').doc(userId).get();
     if (!oldShopDoc.exists) {
       throw new functions.https.HttpsError('not-found', 'No shop data found');
     }
     
     const oldShopData = oldShopDoc.data();
     
     // 3. Create new shop
     const shopId = db.collection('shops').doc().id;
     
     // 4. Create shop details
     await db.collection('shops').doc(shopId).set({
       shopId: shopId,
       ownerUserId: userId,
       shopName: oldShopData.shopName || '',
       address: oldShopData.address || '',
       gstNumber: oldShopData.hasGST ? oldShopData.gstNumber : null,
       hasGst: oldShopData.hasGST || false,
       logoUrl: oldShopData.logo || null,
       signatureUrl: oldShopData.signature || null,
       createdAt: oldShopData.createdAt || admin.firestore.FieldValue.serverTimestamp()
     });
     
     // 5. Create user profile
     await db.collection('users').doc(userId).set({
       userId: userId,
       name: oldShopData.name || '',
       phoneNumber: oldShopData.phoneNumber || '',
       managedShops: { [shopId]: true },
       createdAt: oldShopData.createdAt || admin.firestore.FieldValue.serverTimestamp()
     });
     
     // 6. Migrate collections
     // Customers
     const customersSnapshot = await db.collection('users').doc(userId).collection('customers').get();
     const customerBatch = db.batch();
     customersSnapshot.docs.forEach((doc) => {
       const newDocRef = db.collection('shopData').doc(shopId).collection('customers').doc(doc.id);
       customerBatch.set(newDocRef, doc.data());
     });
     await customerBatch.commit();
     
     // Similar batches for invoices, inventory, etc.
     
     return {
       success: true,
       shopId: shopId,
       message: 'Migration completed successfully'
     };
   });
   ```

2. **Call from App**:
   ```kotlin
   private fun triggerMigration() {
       val functions = Firebase.functions
       functions.getHttpsCallable("migrateUserData")
           .call()
           .addOnSuccessListener { result ->
               val data = result.data as Map<String, Any>
               if (data["success"] as Boolean) {
                   val shopId = data["shopId"] as String
                   SessionManager.setActiveShopId(context, shopId)
                   // Continue to main screen
               }
           }
           .addOnFailureListener { e ->
               // Handle failure
           }
   }
   ```

## Migration Testing

1. **Test with Sample Data**:
   - Create test accounts with various data configurations
   - Run migration and verify all data is correctly transferred

2. **Verify Data Integrity**:
   - Ensure all collections (customers, invoices, inventory, etc.) are properly migrated
   - Verify relationships between data (e.g., customer IDs in invoices)

3. **Handle Edge Cases**:
   - Empty collections
   - Missing or corrupt data
   - Very large data sets

## Rollback Plan

1. **Maintain Old Data Structure**:
   - Don't delete old data immediately after migration
   - Keep dual structure for a specified period (e.g., 30 days)

2. **Version Toggle**:
   - Add an app setting to switch between old and new data paths
   - Allow users to revert if they encounter issues

## User Communication

1. **In-App Notification**:
   - Inform users about the upgrade when they first launch the new version
   - Explain benefits of the multi-shop feature

2. **Migration Progress**:
   - Show progress during migration
   - Provide clear success/failure messages

## Post-Migration Cleanup

After confirming successful migration (approximately 30-60 days):

1. **Remove Old Data**:
   - Create a cleanup function to remove data from the old structure
   - Run after verifying user is successfully using the new structure

2. **Monitor Usage**:
   - Track usage metrics to ensure users are successfully using the new structure
   - Address any common issues or confusion

## Timeline

1. **Development**: 2 weeks
2. **Internal Testing**: 1 week
3. **Beta Testing with Selected Users**: 1 week
4. **Full Rollout**: 1 week
5. **Monitoring and Adjustment**: 2 weeks
6. **Cleanup**: After 30-60 days 
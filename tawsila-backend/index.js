const admin = require('firebase-admin');

// 1. Download your service-account.json from Firebase Console
// 2. Put it in the same directory as this file
const serviceAccount = require("./service-account.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: "https://tawsilaapp-f37b1740-default-rtdb.firebaseio.com"
});

const db = admin.database();
const ref = db.ref("tawsilaappfinal/fcm_notifications");

console.log("--- Tawsila Backend is Live ---");
console.log("Waiting for new notification requests...");

// Monitor for new children added to fcm_notifications
ref.on("child_added", (snapshot) => {
  const data = snapshot.val();
  const id = snapshot.key;

  if (data && data.status === "PENDING") {
    console.log(`Processing notification: ${id} to ${data.to}`);

    const message = {
      token: data.to,
      data: {
        route: data.data.route || "",
        title: data.title,
        content: data.body
      },
      android: {
        priority: 'high'
      }
    };

    admin.messaging().send(message)
      .then((response) => {
        console.log(`Success! FCM sent. Response: ${response}`);
        // Remove from database after successful delivery to keep it lean
        return snapshot.ref.remove();
      })
      .catch((error) => {
        console.error(`Error sending ${id}:`, error);
        // Mark as failed instead of deleting so you can debug
        return snapshot.ref.update({ status: "FAILED", error: error.message });
      });
  }
});

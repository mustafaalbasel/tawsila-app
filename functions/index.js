const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// This function triggers when a new entry is added to /tawsilaappfinal/fcm_notifications/
exports.onNewNotification = functions.database.ref('/tawsilaappfinal/fcm_notifications/{id}')
    .onCreate(async (snapshot, context) => {
        const data = snapshot.val();
        const id = context.params.id;

        if (!data || data.status === 'SENT') return null;

        const message = {
            token: data.to,
            notification: {
                title: data.title,
                body: data.body,
            },
            data: {
                route: data.data.route || "",
                title: data.title,
                content: data.body
            },
            android: {
                priority: 'high',
                notification: {
                    sound: 'default',
                    channelId: 'tawsila_notifications'
                }
            },
            apns: {
                payload: {
                    aps: {
                        sound: 'default',
                        badge: 1
                    }
                }
            }
        };

        try {
            await admin.messaging().send(message);
            console.log(`Notification ${id} sent successfully to ${data.to}`);
            // Update status to prevent duplicate sending and keep a record
            return snapshot.ref.update({
                status: 'SENT',
                sentAt: admin.database.ServerValue.TIMESTAMP
            });
        } catch (error) {
            console.error(`Error sending notification ${id}:`, error);
            return snapshot.ref.update({
                status: 'FAILED',
                error: error.message,
                failedAt: admin.database.ServerValue.TIMESTAMP
            });
        }
    });

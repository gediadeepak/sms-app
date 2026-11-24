# Sandesh privacy notes for the current implementation

This file documents implemented behaviour; it is not a substitute for the owner's final hosted store policy and support details.

Sandesh reads and writes SMS on your device after you choose it as the default SMS app and grant the required permissions. SMS and phone-state permissions support inbox access, receiving, sending and active SIM detection. Notifications are optional and use a generic message preview; notification permission is requested through Settings.

Actual SMS is stored in Android's system SMS provider. Sandesh's private SQLite database stores thread stars/archives, optional aliases, multipart send/delivery receipt state, pending incoming SMS and raw unsupported MMS notification data. These records can contain private message content. Settings use app-private preferences. Device storage protection is provided by Android; the app does not implement separate password encryption.

There is no account, advertising SDK, analytics SDK, remote database or cloud AI integration in this build. The native manifest does not request Internet access. Reply ideas and draft tidying run locally using sample text and rules. Carrier SMS necessarily travels through your mobile operator to the intended recipient when you tap Send.

The app does not request contacts permission. It does not upload messages or contact data. There is no export/cloud backup or restore feature. App backup/device transfer is excluded. Uninstalling removes Sandesh's private metadata but does not delete system SMS. Android and the operator have their own storage/backup behaviour outside Sandesh's control.

MMS attachment transport and RCS are unsupported. Raw MMS notifications are retained locally rather than presented as downloaded messages. Do not rely on this build for those features.

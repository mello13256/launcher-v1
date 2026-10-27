package com.hexa.launcher.media

import android.service.notification.NotificationListenerService

/**
 * Empty notification listener. Android only lets apps that hold notification access read the
 * active media sessions (MediaSessionManager.getActiveSessions); this component is how the user
 * grants that access. Notifications themselves are ignored and nothing is processed here.
 */
class MediaListenerService : NotificationListenerService()

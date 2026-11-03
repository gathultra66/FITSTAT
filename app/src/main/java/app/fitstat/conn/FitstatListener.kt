package app.fitstat.conn

import android.service.notification.NotificationListenerService

/**
 * Android solo deja que una app vea qué está reproduciendo otra (y la pause o la salte) si tiene activado el «Acceso a
 * notificaciones». Este servicio existe únicamente para eso: FITSTAT no lee ni guarda tus notificaciones, solo usa ese
 * permiso para hablar con la sesión de reproducción de Namida. Está vacío a propósito.
 */
class FitstatListener : NotificationListenerService()

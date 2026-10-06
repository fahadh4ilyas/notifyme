package com.fahadh.notifyme.model

data class NotificationMessage(
    val type: String = "notification",
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val icon: String?,
    val timestamp: Long,
)

data class RemoveMessage(
    val type: String = "remove",
    val id: String,
)

data class DismissMessage(
    val type: String = "dismiss",
    val id: String,
)

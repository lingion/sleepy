package com.lingion.sleepy.util

/** Pure version-scoped dismissal rules shared by update notice surfaces. */
object UpdateNoticeCore {
    fun isVisible(update: UpdateInfo?, dismissedVersion: String?): Boolean =
        update?.isUpdateAvailable == true && update.version.isNotBlank() &&
            update.version != dismissedVersion
}

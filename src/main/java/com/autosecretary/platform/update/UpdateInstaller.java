package com.autosecretary.platform.update;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.autosecretary.BuildConfig;
import com.autosecretary.application.update.VerifiedUpdate;

public final class UpdateInstaller {
    public Intent intent(Context context, VerifiedUpdate update) {
        if (!context.getPackageManager().canRequestPackageInstalls()) {
            return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + BuildConfig.APPLICATION_ID));
        }
        Uri apk = FileProvider.getUriForFile(context,
                BuildConfig.APPLICATION_ID + ".files", update.apk());
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apk, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}

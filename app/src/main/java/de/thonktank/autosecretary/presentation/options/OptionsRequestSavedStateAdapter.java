package de.thonktank.autosecretary.presentation.options;

import android.os.Bundle;

import de.thonktank.autosecretary.update.application.VerifiedUpdate;
import de.thonktank.autosecretary.update.domain.ReleaseMetadata;
import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicit process-state boundary for options host work. */
final class OptionsRequestSavedStateAdapter {
    private static final String ITEMS = "items";
    private static final String ID = "id";
    private static final String KIND = "kind";
    private static final String MESSAGE = "message";
    private static final String ERROR_KIND = "error_kind";
    private static final String VERSION_CODE = "version_code";
    private static final String VERSION_NAME = "version_name";
    private static final String PACKAGE_NAME = "package_name";
    private static final String APK_ASSET = "apk_asset";
    private static final String APK_SIZE = "apk_size";
    private static final String APK_SHA = "apk_sha";
    private static final String SIGNER_SHA = "signer_sha";
    private static final String COMMIT_SHA = "commit_sha";
    private static final String APK_URL = "apk_url";
    private static final String VERIFIED_PATH = "verified_path";

    Bundle encode(List<OptionsRequest> requests) {
        Bundle result = new Bundle();
        ArrayList<Bundle> items = new ArrayList<>(requests.size());
        for (OptionsRequest request : requests) {
            Bundle item = new Bundle();
            item.putString(ID, request.id);
            item.putString(KIND, request.kind.name());
            item.putString(MESSAGE, request.message);
            item.putString(ERROR_KIND, request.errorKind == null
                    ? null : request.errorKind.name());
            if (request.update != null) encodeUpdate(item, request.update);
            if (request.verified != null)
                item.putString(VERIFIED_PATH, request.verified.apk.getAbsolutePath());
            items.add(item);
        }
        result.putParcelableArrayList(ITEMS, items);
        return result;
    }

    @SuppressWarnings("deprecation")
    List<OptionsRequest> decode(Bundle saved) {
        if (saved == null) return Collections.emptyList();
        ArrayList<Bundle> items = saved.getParcelableArrayList(ITEMS);
        if (items == null || items.isEmpty()) return Collections.emptyList();
        ArrayList<OptionsRequest> result = new ArrayList<>(items.size());
        for (Bundle item : items) {
            if (item == null) continue;
            try {
                String id = item.getString(ID);
                OptionsRequest.Kind kind = OptionsRequest.Kind.valueOf(item.getString(KIND));
                if (kind == OptionsRequest.Kind.REQUEST_CALENDAR_PERMISSION
                        || kind == OptionsRequest.Kind.OPEN_APP_SETTINGS
                        || kind == OptionsRequest.Kind.OPEN_FLOW_SETUP
                        || kind == OptionsRequest.Kind.OPEN_FLOW_RUNS) {
                    result.add(OptionsRequest.system(id, kind));
                } else if (kind == OptionsRequest.Kind.UPDATE_AVAILABLE) {
                    result.add(OptionsRequest.available(id, decodeUpdate(item)));
                } else if (kind == OptionsRequest.Kind.INSTALL_UPDATE) {
                    UpdateInfo update = decodeUpdate(item);
                    result.add(OptionsRequest.install(id, VerifiedUpdate.fromVerifiedFile(
                            update, new File(item.getString(VERIFIED_PATH)))));
                } else if (kind == OptionsRequest.Kind.UPDATE_ERROR) {
                    result.add(OptionsRequest.error(id, UpdateFailure.Kind.valueOf(
                            item.getString(ERROR_KIND)), item.getString(MESSAGE)));
                }
            } catch (IllegalArgumentException | NullPointerException | UpdateFailure ignored) {
                // Invalid or obsolete process state must not prevent options from opening.
            }
        }
        return result;
    }

    private static void encodeUpdate(Bundle target, UpdateInfo update) {
        ReleaseMetadata metadata = update.metadata();
        target.putLong(VERSION_CODE, metadata.versionCode);
        target.putString(VERSION_NAME, metadata.versionName);
        target.putString(PACKAGE_NAME, metadata.packageName);
        target.putString(APK_ASSET, metadata.apkAsset);
        target.putLong(APK_SIZE, metadata.apkSizeBytes);
        target.putString(APK_SHA, metadata.sha256);
        target.putString(SIGNER_SHA, metadata.signerSha256);
        target.putString(COMMIT_SHA, metadata.commitSha);
        target.putString(APK_URL, update.apkUri().toString());
    }

    private static UpdateInfo decodeUpdate(Bundle source) throws UpdateFailure {
        ReleaseMetadata metadata = ReleaseMetadata.create(source.getLong(VERSION_CODE),
                source.getString(VERSION_NAME), source.getString(PACKAGE_NAME),
                source.getString(APK_ASSET), source.getLong(APK_SIZE),
                source.getString(APK_SHA), source.getString(SIGNER_SHA),
                source.getString(COMMIT_SHA));
        return UpdateInfo.from(metadata, source.getString(APK_URL));
    }
}

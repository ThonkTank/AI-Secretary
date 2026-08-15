package de.thonktank.autosecretary.update.infrastructure;

import de.thonktank.autosecretary.update.domain.ReleaseMetadata;
import de.thonktank.autosecretary.update.domain.UpdateFailure;

import org.json.JSONException;
import org.json.JSONObject;

final class ReleaseMetadataJsonParser {
    private static final int FIELD_COUNT = 9;

    ReleaseMetadata parse(String json) throws UpdateFailure {
        try {
            JSONObject source = new JSONObject(json);
            if (source.length() != FIELD_COUNT || source.getInt("schemaVersion") != 1)
                throw invalid("Unsupported release metadata schema", null);
            return ReleaseMetadata.create(source.getLong("versionCode"),
                    source.getString("versionName"), source.getString("packageName"),
                    source.getString("apkAsset"), source.getLong("apkSizeBytes"),
                    source.getString("sha256"), source.getString("signerSha256"),
                    source.getString("commitSha"));
        } catch (UpdateFailure error) {
            throw error;
        } catch (JSONException | RuntimeException error) {
            throw invalid("Release metadata is malformed", error);
        }
    }

    private static UpdateFailure invalid(String message, Throwable cause) {
        return new UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE, message, cause);
    }
}

package com.autosecretary.app.update;

import java.io.File;
import java.io.IOException;

interface UpdateClient {
    AvailableUpdate fetchLatestUpdate() throws IOException;

    File downloadApk(AvailableUpdate update, File targetFile) throws IOException;
}

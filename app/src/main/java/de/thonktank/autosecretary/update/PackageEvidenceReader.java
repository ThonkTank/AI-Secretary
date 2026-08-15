package de.thonktank.autosecretary.update;

import java.io.File;

interface PackageEvidenceReader {
    PackageEvidence installed() throws Exception;
    PackageEvidence archive(File apk) throws Exception;
}

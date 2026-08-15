package de.thonktank.autosecretary.update.infrastructure;

import de.thonktank.autosecretary.update.domain.PackageEvidence;
import de.thonktank.autosecretary.update.domain.UpdateFailure;

import java.io.File;

interface PackageEvidenceReader {
    PackageEvidence installed() throws UpdateFailure;
    PackageEvidence archive(File apk) throws UpdateFailure;
}

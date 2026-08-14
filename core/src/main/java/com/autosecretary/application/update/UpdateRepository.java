package com.autosecretary.application.update;

/** Application-facing update port. Android and GitHub details stay behind this boundary. */
public interface UpdateRepository {
    UpdateCheckResult check();
    DownloadTicket enqueue(UpdateInfo update);
    DownloadProgress query(DownloadTicket ticket);
    VerifiedUpdate verify(DownloadTicket ticket);
    void cancel(DownloadTicket ticket);
    void cleanup(long installedVersionCode);
}

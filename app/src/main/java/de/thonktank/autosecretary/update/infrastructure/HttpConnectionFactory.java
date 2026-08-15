package de.thonktank.autosecretary.update.infrastructure;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

interface HttpConnectionFactory {
    HttpURLConnection open(URL url) throws IOException;
}

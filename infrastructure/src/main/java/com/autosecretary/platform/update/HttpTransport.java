package com.autosecretary.platform.update;

interface HttpTransport {
    String get(String url, int byteLimit) throws Exception;
}

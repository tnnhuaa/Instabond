package com.instabond.service;

public interface PresenceService {

    void markOnline(String email);

    void markOffline(String email);

    boolean isOnline(String email);
}

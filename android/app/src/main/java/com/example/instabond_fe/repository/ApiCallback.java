package com.example.instabond_fe.repository;

public interface ApiCallback<T> {
    void onSuccess(T data);

    void onError(Throwable t);
}


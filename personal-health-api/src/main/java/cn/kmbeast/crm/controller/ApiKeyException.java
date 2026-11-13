package cn.kmbeast.crm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ApiKeyException extends ResponseStatusException {
    public ApiKeyException(String reason) {
        super(HttpStatus.UNAUTHORIZED, reason);
    }
}

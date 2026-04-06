package br.com.vagasflow.api.shared.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(resource + " not found", HttpStatus.NOT_FOUND);
    }
}

package org.de013.common.controller;

import org.de013.common.dto.ApiResult;
import org.de013.common.util.ResponseUtil;
import org.springframework.http.ResponseEntity;

public abstract class BaseController {

    // ========== Success Responses ==========

    protected <T> ResponseEntity<ApiResult<T>> success(T data) {
        return ResponseUtil.success(data);
    }

    protected <T> ResponseEntity<ApiResult<T>> success(T data, String message) {
        return ResponseUtil.success(data, message);
    }

    protected ResponseEntity<ApiResult<Void>> success(String message) {
        return ResponseUtil.success(message);
    }

    protected <T> ResponseEntity<ApiResult<T>> ok(T data) {
        return ResponseUtil.ok(data);
    }

    protected <T> ResponseEntity<ApiResult<T>> created(T data) {
        return ResponseUtil.created(data);
    }

    protected <T> ResponseEntity<ApiResult<T>> created(T data, String message) {
        return ResponseUtil.created(data, message);
    }

    protected <T> ResponseEntity<ApiResult<T>> updated(T data) {
        return ResponseUtil.updated(data);
    }

    protected <T> ResponseEntity<ApiResult<T>> updated(T data, String message) {
        return ResponseUtil.updated(data, message);
    }

    protected <T> ResponseEntity<ApiResult<T>> deleted() {
        return ResponseUtil.deleted();
    }

    protected <T> ResponseEntity<ApiResult<T>> deleted(String message) {
        return ResponseUtil.deleted(message);
    }

    // ========== Error Responses ==========

    protected <T> ResponseEntity<ApiResult<T>> badRequest(String message) {
        return ResponseUtil.badRequest(message);
    }

    protected <T> ResponseEntity<ApiResult<T>> badRequest(String message, Object errors) {
        return ResponseUtil.badRequest(message, errors);
    }

    protected <T> ResponseEntity<ApiResult<T>> unauthorized(String message) {
        return ResponseUtil.unauthorized(message);
    }

    protected <T> ResponseEntity<ApiResult<T>> forbidden(String message) {
        return ResponseUtil.forbidden(message);
    }

    protected <T> ResponseEntity<ApiResult<T>> notFound(String message) {
        return ResponseUtil.notFound(message);
    }

    protected <T> ResponseEntity<ApiResult<T>> conflict(String message) {
        return ResponseUtil.conflict(message);
    }

    protected <T> ResponseEntity<ApiResult<T>> unprocessableEntity(String message, Object errors) {
        return ResponseUtil.unprocessableEntity(message, errors);
    }

    protected <T> ResponseEntity<ApiResult<T>> tooManyRequests(String message) {
        return ResponseUtil.tooManyRequests(message);
    }

    protected <T> ResponseEntity<ApiResult<T>> internalServerError(String message) {
        return ResponseUtil.internalServerError(message);
    }

    protected <T> ResponseEntity<ApiResult<T>> serviceUnavailable(String message) {
        return ResponseUtil.serviceUnavailable(message);
    }
}


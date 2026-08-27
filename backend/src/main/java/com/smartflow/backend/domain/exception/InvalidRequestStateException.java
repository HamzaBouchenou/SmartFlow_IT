package com.smartflow.backend.domain.exception;

/**
 * A request-lifecycle action was attempted while the Request's current state makes it
 * illegal - e.g. editing a Request that is no longer DRAFT, or cancelling one already taken
 * in charge. Distinct from EntityNotFoundException (out of perimeter, or absent) - here the
 * caller may legitimately see the resource, but not perform this action on it right now.
 * Each call site supplies its own machine-readable code; GlobalExceptionHandler's generic
 * BusinessException fallback maps this to 400 ("the request is invalid given the current
 * state" - see that handler's own javadoc), exactly the class this exception is for.
 */
public class InvalidRequestStateException extends BusinessException {

    public InvalidRequestStateException(String code, String message) {
        super(code, message);
    }
}

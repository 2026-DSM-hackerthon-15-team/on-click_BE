package com.onclick.global.error;

import java.time.LocalDate;

public final class FutureDateNotAllowedException extends ApiException {

    public FutureDateNotAllowedException() {
        super(ErrorCode.FUTURE_DATE_NOT_ALLOWED);
    }

    public FutureDateNotAllowedException(LocalDate date) {
        super(ErrorCode.FUTURE_DATE_NOT_ALLOWED);
    }
}

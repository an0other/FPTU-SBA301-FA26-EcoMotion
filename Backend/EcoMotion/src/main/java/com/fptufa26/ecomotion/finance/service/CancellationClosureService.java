package com.fptufa26.ecomotion.finance.service;

public interface CancellationClosureService {
    boolean reconcile(Long bookingId);
    void stopYearlyCollections(Long bookingId);
}

package com.nexora.subscription;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionServicePlanTest {
    @Test
    void exposesOnlyFreeAndPremiumPlansAtTheApprovedPrices() {
        SubscriptionService service = new SubscriptionService(null, "./target/test-subscription-payments");
        assertEquals(2, service.plans().size());
        assertEquals("FREE", service.plans().get(0).code());
        assertEquals(0, service.plans().get(0).semesterPrice());
        assertEquals(0, service.plans().get(0).annualPrice());
        assertEquals("PREMIUM", service.plans().get(1).code());
        assertEquals(99, service.plans().get(1).semesterPrice());
        assertEquals(189, service.plans().get(1).annualPrice());
        assertTrue(service.plans().stream().noneMatch(p -> p.code().equalsIgnoreCase("BASIC")));
    }
}

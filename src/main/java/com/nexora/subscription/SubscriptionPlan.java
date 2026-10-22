package com.nexora.subscription;

public record SubscriptionPlan(String code, String name, double semesterPrice, double annualPrice, String description) {
    public double price(String cycle) { return "SEMESTER".equalsIgnoreCase(cycle) ? semesterPrice : annualPrice; }
}

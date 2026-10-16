package com.gbconnected.kpivalidation.application;
public record ValidationIssue(Severity severity, String code, String message) {
    public enum Severity { BLOCKER, WARNING }
    public static ValidationIssue blocker(String code, String message) { return new ValidationIssue(Severity.BLOCKER, code, message); }
    public static ValidationIssue warning(String code, String message) { return new ValidationIssue(Severity.WARNING, code, message); }
}

# Design Document: Remote Build Minimum Size Enforcement

## Overview

This design implements enforcement of a minimum value for the `index.knn.remote_index_build.size.min` setting to ensure GPU acceleration is only used when beneficial. The solution adds validation logic to the existing setting definition and updates all affected tests to use valid values.

## Architecture

The enforcement will be implemented at the OpenSearch setting validation level, leveraging the existing `Setting.byteSizeSetting()` builder pattern. This ensures validation occurs automatically whenever the setting is applied, whether during index creation, updates, or cluster configuration changes.

### Key Components

1. **Setting Validation**: Enhanced `KNN_INDEX_REMOTE_VECTOR_BUILD_SIZE_MIN_SETTING` with minimum value enforcement
2. **Configurable Minimum**: A constant that can be easily adjusted for future tuning
3. **Test Infrastructure Updates**: Modified test utilities to use valid minimum values
4. **Backward Compatibility**: Graceful handling of existing configurations

## Components and Interfaces

### Enhanced Setting Definition

The existing `KNN_INDEX_REMOTE_VECTOR_BUILD_SIZE_MIN_SETTING` will be modified to include a minimum value validator:

```java
public static final Setting<ByteSizeValue> KNN_INDEX_REMOTE_VECTOR_BUILD_SIZE_MIN_SETTING = Setting.byteSizeSetting(
    KNN_INDEX_REMOTE_VECTOR_BUILD_SIZE_MIN,
    KNN_INDEX_REMOTE_VECTOR_BUILD_THRESHOLD_DEFAULT_VALUE,
    KNN_REMOTE_INDEX_BUILD_ENFORCED_MIN_VALUE, // New minimum enforcement
    Dynamic,
    IndexScope
);
```

### Configuration Constants

New constants will be added to make the enforcement configurable:

```java
// Enforced minimum value for remote index build size threshold
public static final ByteSizeValue KNN_REMOTE_INDEX_BUILD_ENFORCED_MIN_VALUE = new ByteSizeValue(10, ByteSizeUnit.MB);
```

### Test Utility Updates

The `KNNRestTestCase` class will be updated to use valid minimum values:

```java
// Instead of "0kb", use the enforced minimum
builder.put(KNN_INDEX_REMOTE_VECTOR_BUILD_SIZE_MIN, KNN_REMOTE_INDEX_BUILD_ENFORCED_MIN_VALUE.getStringRep());
```

## Data Models

### Setting Validation Flow

```
User Configuration Input
         ↓
OpenSearch Setting Validation
         ↓
Minimum Value Check (≥ 10MB)
         ↓
[Valid] → Apply Setting
         ↓
[Invalid] → Reject with Error Message
```

### Test Configuration Strategy

```
Test Scenario Requirements
         ↓
[Need Remote Build] → Use Valid Min Value (≥ 10MB)
         ↓
[Disable Remote Build] → Set enabled=false (not size=0)
         ↓
[Size Threshold Testing] → Use values above minimum
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Invalid Size Rejection
*For any* size value below the enforced minimum threshold, attempting to set the remote build size minimum should result in a validation error with a descriptive message.
**Validates: Requirements 1.1**

### Property 2: Index Creation Validation
*For any* index creation request with remote build size settings, the system should validate that the size meets the minimum threshold before allowing the index to be created.
**Validates: Requirements 1.2**

### Property 3: Index Update Validation
*For any* index update request that modifies the remote build size setting, the system should validate that the new value meets the minimum threshold.
**Validates: Requirements 1.3**

### Property 4: Backward Compatibility Handling
*For any* existing configuration with a size value below the minimum, the system should use the enforced minimum value and log an appropriate warning.
**Validates: Requirements 3.1**

### Property 5: Effective Value Query
*For any* query of the effective setting value, if the configured value is below the minimum, the system should return the enforced minimum value.
**Validates: Requirements 3.2**

### Property 6: Clear Error Messages
*For any* invalid configuration that triggers validation failure, the error message should contain information about the minimum required value.
**Validates: Requirements 3.3, 4.2**

### Property 7: Valid Configuration Functionality
*For any* configuration that meets the minimum threshold, the remote index build system should function normally without degradation.
**Validates: Requirements 3.4**

### Property 8: Multi-Level Validation
*For any* setting application at either cluster or index level, the validation logic should enforce the minimum threshold consistently.
**Validates: Requirements 4.4**

### Property 9: Successful Application Logging
*For any* successful setting application, the system should log the effective configuration value being used.
**Validates: Requirements 4.3**

## Error Handling

### Validation Errors
- **Invalid Size Values**: When users attempt to set values below the minimum, the system will throw a `SettingsException` with a clear message indicating the minimum required value.
- **Configuration Parsing**: If the size value cannot be parsed, standard OpenSearch setting validation will handle the error.

### Backward Compatibility
- **Legacy Configurations**: Existing configurations with values below the minimum will be automatically adjusted to use the enforced minimum, with appropriate warning logs.
- **Graceful Degradation**: The system will continue to function with adjusted values rather than failing completely.

## Testing Strategy

### Unit Tests
- Test setting validation with various input values (valid, invalid, edge cases)
- Test error message content and format
- Test backward compatibility handling
- Test effective value queries

### Property-Based Tests
- **Minimum 100 iterations per property test**
- Each property test references its corresponding design document property
- Tag format: **Feature: remote-build-min-size-enforcement, Property {number}: {property_text}**

### Integration Tests
- Update existing `KNNRestTestCase` methods to use valid minimum values
- Test end-to-end remote build functionality with enforced minimums
- Verify test suite continues to pass with updated configurations

### Test Configuration Updates
- Replace all instances of `"0kb"` in test configurations with valid minimum values
- Provide helper methods for generating valid remote build configurations
- Ensure tests that need to disable remote builds use the enabled flag rather than invalid sizes

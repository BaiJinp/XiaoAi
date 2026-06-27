package com.xiaoai.agent.tool.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.LocalDate;

class JsonSchemaPayloadValidator {

    void validatePayloadSchema(JsonNode schema, JsonNode payload, String subject) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            return;
        }
        validateNode(schema, payload, subject, "");
    }

    private void validateNode(JsonNode schema, JsonNode payload, String subject, String path) {
        validateAllOf(schema.path("allOf"), payload, subject, path);
        validateOneOf(schema.path("oneOf"), payload, subject, path);
        if (path != null && !path.isBlank()) {
            JsonNode typeNode = schema.path("type");
            if (!typeNode.isMissingNode() && !typeNode.isNull() && !matchesType(payload, typeNode)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        subject + " field type mismatch: " + path);
            }
            validatePropertyConstraints(path, schema, payload, subject);
        }
        JsonNode required = schema.path("required");
        if (required.isArray() && payload.isObject()) {
            for (JsonNode field : required) {
                if (!field.isTextual()) {
                    continue;
                }
                String fieldName = field.asText();
                if (!payload.hasNonNull(fieldName)) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                            subject + " missing required field: " + childPath(path, fieldName));
                }
            }
        }
        validatePropertyTypes(schema.path("properties"), payload, subject, path);
        validateObjectSize(path, schema, payload, subject);
        if (schema.path("additionalProperties").isBoolean()
                && !schema.path("additionalProperties").asBoolean()
                && payload.isObject()) {
            JsonNode properties = schema.path("properties");
            payload.fieldNames().forEachRemaining(fieldName -> {
                if (!properties.has(fieldName)) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                            subject + " contains unsupported field: " + childPath(path, fieldName));
                }
            });
        }
        if (payload.isArray() && schema.has("items")) {
            JsonNode itemSchema = schema.path("items");
            for (int index = 0; index < payload.size(); index++) {
                validateNode(itemSchema, payload.get(index), subject, path + "[" + index + "]");
            }
        }
    }

    private void validateAllOf(JsonNode schemas, JsonNode payload, String subject, String path) {
        if (!schemas.isArray()) {
            return;
        }
        for (JsonNode itemSchema : schemas) {
            validateNode(itemSchema, payload, subject, path);
        }
    }

    private void validateOneOf(JsonNode schemas, JsonNode payload, String subject, String path) {
        if (!schemas.isArray()) {
            return;
        }
        int matched = 0;
        for (JsonNode itemSchema : schemas) {
            try {
                validateNode(itemSchema, payload, subject, path);
                matched++;
            } catch (BusinessException ignored) {
                // oneOf only needs the number of matching branches.
            }
        }
        if (matched != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    subject + " field oneOf mismatch: " + displayPath(path));
        }
    }

    private void validatePropertyTypes(JsonNode properties, JsonNode payload, String subject, String path) {
        if (!properties.isObject() || !payload.isObject()) {
            return;
        }
        properties.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            JsonNode value = payload.get(fieldName);
            if (value == null || value.isNull()) {
                return;
            }
            JsonNode propertySchema = entry.getValue();
            String fieldPath = childPath(path, fieldName);
            JsonNode typeNode = propertySchema.path("type");
            if (!typeNode.isMissingNode() && !typeNode.isNull() && !matchesType(value, typeNode)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        subject + " field type mismatch: " + fieldPath);
            }
            validatePropertyConstraints(fieldPath, propertySchema, value, subject);
            validateNode(propertySchema, value, subject, fieldPath);
        });
    }

    private void validatePropertyConstraints(String fieldName, JsonNode propertySchema, JsonNode value, String subject) {
        validateEnum(fieldName, propertySchema.path("enum"), value, subject);
        validateStringLength(fieldName, propertySchema, value, subject);
        validateStringPattern(fieldName, propertySchema, value, subject);
        validateStringFormat(fieldName, propertySchema, value, subject);
        validateNumberRange(fieldName, propertySchema, value, subject);
        validateArrayLength(fieldName, propertySchema, value, subject);
        validateArrayUniqueItems(fieldName, propertySchema, value, subject);
    }

    private void validateEnum(String fieldName, JsonNode enumValues, JsonNode value, String subject) {
        if (!enumValues.isArray()) {
            return;
        }
        for (JsonNode enumValue : enumValues) {
            if (enumValue.equals(value)) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, subject + " field enum mismatch: " + fieldName);
    }

    private void validateStringLength(String fieldName, JsonNode propertySchema, JsonNode value, String subject) {
        if (!value.isTextual()) {
            return;
        }
        int length = value.asText().length();
        JsonNode minLength = propertySchema.path("minLength");
        if (minLength.isInt() && length < minLength.asInt()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, subject + " field too short: " + fieldName);
        }
        JsonNode maxLength = propertySchema.path("maxLength");
        if (maxLength.isInt() && length > maxLength.asInt()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, subject + " field too long: " + fieldName);
        }
    }

    private void validateNumberRange(String fieldName, JsonNode propertySchema, JsonNode value, String subject) {
        if (!value.isNumber()) {
            return;
        }
        BigDecimal number = value.decimalValue();
        JsonNode minimum = propertySchema.path("minimum");
        if (minimum.isNumber() && number.compareTo(minimum.decimalValue()) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, subject + " field below minimum: " + fieldName);
        }
        JsonNode maximum = propertySchema.path("maximum");
        if (maximum.isNumber() && number.compareTo(maximum.decimalValue()) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, subject + " field above maximum: " + fieldName);
        }
    }

    private void validateArrayLength(String fieldName, JsonNode propertySchema, JsonNode value, String subject) {
        if (!value.isArray()) {
            return;
        }
        JsonNode minItems = propertySchema.path("minItems");
        if (minItems.isInt() && value.size() < minItems.asInt()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, subject + " field has too few items: " + fieldName);
        }
        JsonNode maxItems = propertySchema.path("maxItems");
        if (maxItems.isInt() && value.size() > maxItems.asInt()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, subject + " field has too many items: " + fieldName);
        }
    }

    private void validateStringPattern(String fieldName, JsonNode propertySchema, JsonNode value, String subject) {
        if (!value.isTextual() || !propertySchema.path("pattern").isTextual()) {
            return;
        }
        if (!value.asText().matches(propertySchema.path("pattern").asText())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, subject + " field pattern mismatch: " + fieldName);
        }
    }

    private void validateStringFormat(String fieldName, JsonNode propertySchema, JsonNode value, String subject) {
        if (!value.isTextual() || !propertySchema.path("format").isTextual()) {
            return;
        }
        String format = propertySchema.path("format").asText();
        String text = value.asText();
        boolean valid = switch (format) {
            case "date" -> canParseDate(text);
            case "date-time" -> canParseDateTime(text);
            case "email" -> text.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
            case "uri" -> canParseUri(text);
            default -> true;
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, subject + " field format mismatch: " + fieldName);
        }
    }

    private void validateArrayUniqueItems(String fieldName, JsonNode propertySchema, JsonNode value, String subject) {
        if (!value.isArray() || !propertySchema.path("uniqueItems").asBoolean(false)) {
            return;
        }
        for (int outer = 0; outer < value.size(); outer++) {
            for (int inner = outer + 1; inner < value.size(); inner++) {
                if (value.get(outer).equals(value.get(inner))) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                            subject + " field has duplicate items: " + fieldName);
                }
            }
        }
    }

    private void validateObjectSize(String fieldName, JsonNode propertySchema, JsonNode value, String subject) {
        if (!value.isObject()) {
            return;
        }
        int size = value.size();
        JsonNode minProperties = propertySchema.path("minProperties");
        if (minProperties.isInt() && size < minProperties.asInt()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    subject + " field has too few properties: " + displayPath(fieldName));
        }
        JsonNode maxProperties = propertySchema.path("maxProperties");
        if (maxProperties.isInt() && size > maxProperties.asInt()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    subject + " field has too many properties: " + displayPath(fieldName));
        }
    }

    private boolean canParseDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean canParseDateTime(String value) {
        try {
            OffsetDateTime.parse(value);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean canParseUri(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getScheme() != null && !uri.getScheme().isBlank();
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean matchesType(JsonNode value, JsonNode typeNode) {
        if (typeNode.isTextual()) {
            return matchesType(value, typeNode.asText());
        }
        if (typeNode.isArray()) {
            for (JsonNode type : typeNode) {
                if (type.isTextual() && matchesType(value, type.asText())) {
                    return true;
                }
            }
        }
        return true;
    }

    private boolean matchesType(JsonNode value, String type) {
        return switch (type) {
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "null" -> value.isNull();
            default -> true;
        };
    }

    private String childPath(String parentPath, String fieldName) {
        return parentPath == null || parentPath.isBlank() ? fieldName : parentPath + "." + fieldName;
    }

    private String displayPath(String path) {
        return path == null || path.isBlank() ? "$" : path;
    }
}

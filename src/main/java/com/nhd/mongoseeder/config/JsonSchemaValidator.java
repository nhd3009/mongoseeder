package com.nhd.mongoseeder.config;

import java.util.List;
import org.springframework.stereotype.Component;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;

@Component
public class JsonSchemaValidator {

    private static final SchemaRegistry registry =
            SchemaRegistry.withDialect(Dialects.getDraft202012());

    public static void validateSchema(String schemaJson) {

        try {

            Schema metaSchema = registry.getSchema(
                    SchemaLocation.of(Dialects.getDraft202012().getId())
            );

            List<Error> errors = metaSchema.validate(schemaJson, InputFormat.JSON);

            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid JSON schema: " + errors
                );
            }

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid JSON schema format",
                    e
            );
        }
    }
}
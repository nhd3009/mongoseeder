package com.nhd.mongoseeder.engine;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FakeDataEngine implements AutoCloseable {
    private final Context context;
    private final Value generateBatchFn;

    public FakeDataEngine() {
        this.context = Context.newBuilder("js").allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false").build();

        loadResource("/META-INF/resources/webjars/json-schema-faker/0.5.0-rcv.33/dist/main.umd.js");
        loadResource("/META-INF/resources/webjars/github-com-faker-js-faker/5.5.3/faker.min.js");
        context.eval("js", """
                    var faker = this.faker || (typeof module !== 'undefined' ? module.exports : {});
                    var jsf = JSONSchemaFaker;

                    jsf.option({
                        failOnInvalidTypes: false,
                        defaultInvalidTypeProduct: null
                    });

                    jsf.extend('faker', () => faker);

                    const schemaCache = new Map();

                    globalThis.generateBatch = (schemaJson, count) => {
                        try {

                            let schema = schemaCache.get(schemaJson);

                            if (!schema) {
                                schema = JSON.parse(schemaJson);
                                schemaCache.set(schemaJson, schema);
                            }

                            const result = new Array(count);

                            for (let i = 0; i < count; i++) {
                                result[i] = jsf.generate(schema);
                            }

                            return result;

                        } catch (e) {
                            throw "JS Generation Error: " + e.message;
                        }
                    };
                """);

        this.generateBatchFn = context.getBindings("js").getMember("generateBatch");
    }

    private void loadResource(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null)
                throw new RuntimeException("JS Lib not found: " + path);
            context.eval("js", new String(is.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Init Engine Failed", e);
        }
    }

    public Value generateBatch(String schemaJson, int count) {
        Value result = generateBatchFn.execute(schemaJson, count);
        if (!result.hasArrayElements()) {
            throw new IllegalStateException("JS engine did not return array");
        }
        return result;
    }

    @Override
    public void close() {
        if (context != null)
            context.close();
    }
}

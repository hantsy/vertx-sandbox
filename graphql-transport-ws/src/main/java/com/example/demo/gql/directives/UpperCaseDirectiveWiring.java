package com.example.demo.gql.directives;

import graphql.schema.*;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;

public class UpperCaseDirectiveWiring implements SchemaDirectiveWiring {
    @Override
    public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> env) {

        var field = env.getElement();
        var dataFetcher = DataFetcherFactories.wrapDataFetcher(env.getFieldDataFetcher(),
                (dataFetchingEnvironment, value) -> {
                    if (value instanceof String s) {
                        return s.toUpperCase();
                    }
                    return value;
                }
        );

        env.setFieldDataFetcher(dataFetcher);
        return field;
    }
}
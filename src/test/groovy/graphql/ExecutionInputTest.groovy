package graphql

import graphql.cachecontrol.CacheControl
import graphql.execution.ExecutionId
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.function.UnaryOperator

class ExecutionInputTest extends Specification {

    def query = "query { hello }"
    def registry = new DataLoaderRegistry()
    def cacheControl = CacheControl.newCacheControl()
    def root = "root"
    def context = "context"
    def graphQLContext = GraphQLContext.newContext().build()
    def variables = [key: "value"]

    def "build works"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .dataLoaderRegistry(registry)
                .cacheControl(cacheControl)
                .variables(variables)
                .root(root)
                .context(context)
                .graphQLContext(graphQLContext)
                .locale(Locale.GERMAN)
                .extensions([some: "map"])
                .build()
        then:
        executionInput.context == context
        executionInput.graphQLContext == graphQLContext
        executionInput.root == root
        executionInput.variables == variables
        executionInput.dataLoaderRegistry == registry
        executionInput.cacheControl == cacheControl
        executionInput.query == query
        executionInput.locale == Locale.GERMAN
        executionInput.extensions == [some: "map"]
    }

    def "legacy context methods work"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .context({ builder -> builder.of("k1", "v1") } as UnaryOperator)
                .build()
        then:
        (executionInput.context as GraphQLContext).get("k1") == "v1"

        when:
        executionInput = ExecutionInput.newExecutionInput().query(query)
                .context(GraphQLContext.newContext().of("k2", "v2"))
                .build()
        then:
        (executionInput.context as GraphQLContext).get("k2") == "v2"
    }

    def "graphql context methods work"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .graphQLContext({ builder -> builder.of("k1", "v1") } as UnaryOperator)
                .build()
        then:
        executionInput.graphQLContext.get("k1") == "v1"

        when:
        executionInput = ExecutionInput.newExecutionInput().query(query)
                .graphQLContext(GraphQLContext.newContext().of("k2", "v2"))
                .build()
        then:
        executionInput.graphQLContext.get("k2") == "v2"
    }

    def "legacy context is defaulted"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .build()
        then:
        executionInput.context instanceof GraphQLContext
    }

    def "graphql context is defaulted"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput().query(query)
                .build()
        then:
        executionInput.graphQLContext instanceof GraphQLContext
    }

    def "graphql context is not allowed to be null"() {
        when:
        ExecutionInput.newExecutionInput()
                .graphQLContext(null as GraphQLContext)
                .build()
        then:
        thrown(AssertException)
    }

    def "transform works and copies values"() {
        when:
        def executionInputOld = ExecutionInput.newExecutionInput().query(query)
                .dataLoaderRegistry(registry)
                .cacheControl(cacheControl)
                .variables(variables)
                .extensions([some: "map"])
                .root(root)
                .context(context)
                .graphQLContext(graphQLContext)
                .locale(Locale.GERMAN)
                .build()
        def executionInput = executionInputOld.transform({ bldg -> bldg.query("new query") })

        then:
        executionInput.context == context
        executionInput.graphQLContext == graphQLContext
        executionInput.root == root
        executionInput.variables == variables
        executionInput.dataLoaderRegistry == registry
        executionInput.cacheControl == cacheControl
        executionInput.locale == Locale.GERMAN
        executionInput.extensions == [some: "map"]
        executionInput.query == "new query"
    }

    def "defaults query into builder as expected"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput("{ q }").build()
        then:
        executionInput.query == "{ q }"
        executionInput.cacheControl != null
        executionInput.locale == null
        executionInput.dataLoaderRegistry != null
        executionInput.variables == [:]
    }

    def "integration test so that values make it right into the data fetchers"() {

        def sdl = '''
            type Query {
                fetch : String
            }
        '''
        DataFetcher df = { DataFetchingEnvironment env ->
            return [
                    "locale"        : env.getLocale().getDisplayName(),
                    "cacheControl"  : env.getCacheControl() == cacheControl,
                    "executionId"   : env.getExecutionId().toString(),
                    "graphqlContext": env.getGraphQlContext().get("a")

            ]
        }
        def schema = TestUtil.schema(sdl, ["Query": ["fetch": df]])
        def graphQL = GraphQL.newGraphQL(schema).build()

        def graphqlContext = GraphQLContext.newContext().of("a", "b").build()

        when:
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query("{ fetch }")
                .locale(Locale.GERMAN)
                .cacheControl(cacheControl)
                .graphQLContext(graphqlContext)
                .executionId(ExecutionId.from("ID123"))
                .build()
        def er = graphQL.execute(executionInput)

        then:
        er.errors.isEmpty()
        er.data["fetch"] == "{locale=German, cacheControl=true, executionId=ID123, graphqlContext=b}"
    }
}

import TestServer.setupBaseTestServer
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.papsign.ktor.openapigen.annotations.type.string.example.DiscriminatorAnnotation
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// Модель ответа с массивом объектов
data class ItemsResponse(
    val items: List<ItemWrapper>
)

// Обертка для объекта с полиморфным содержимым
data class ItemWrapper(
    val id: Long,
    val title: String,
    val content: Content
)

// Sealed class для полиморфного содержимого
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@DiscriminatorAnnotation()
sealed class Content {

    @JsonTypeName("Post")
    @DiscriminatorAnnotation()
    data class Post(
        val body: String,
        val author: String,
        val publishedAt: String
    ) : Content()

    @JsonTypeName("Order")
    @DiscriminatorAnnotation()
    data class Order(
        val amount: Double,
        val currency: String,
        val customerEmail: String,
        val items: List<String>
    ) : Content()
}

fun NormalOpenAPIRoute.ContentRoute() {
    route("content") {
        get<Unit, ItemsResponse>(
            info("Get items with polymorphic content", "Returns array of items with Post or Order content")
        ) { _ ->
            respond(
                ItemsResponse(
                    items = listOf(
                        ItemWrapper(
                            id = 1,
                            title = "Blog Post",
                            content = Content.Post(
                                body = "This is a blog post content",
                                author = "John Doe",
                                publishedAt = "2023-01-01T10:00:00Z"
                            )
                        ),
                        ItemWrapper(
                            id = 2,
                            title = "Purchase Order",
                            content = Content.Order(
                                amount = 99.99,
                                currency = "USD",
                                customerEmail = "customer@example.com",
                                items = listOf("Product A", "Product B")
                            )
                        )
                    )
                )
            )
        }
    }
}

internal class OneOfContentTest {

    @Test
    fun testPolymorphicContentGeneration() = testApplication {
        application {
            setupBaseTestServer()
            apiRouting {
                ContentRoute()
            }
        }

        client.get("http://localhost/openapi.json").apply {
            assertEquals(HttpStatusCode.OK, status)
            val bodyAsText = bodyAsText()

            println("==== Generated OpenAPI schema: ====")
            println(bodyAsText)
            println("==== End of schema ====")

            // Проверяем основные элементы
            assertTrue(bodyAsText.contains("Content"), "Should contain Content schema")
            assertTrue(bodyAsText.contains("Post"), "Should contain Post schema")
            assertTrue(bodyAsText.contains("Order"), "Should contain Order schema")
            assertTrue(bodyAsText.contains("oneOf"), "Should contain oneOf")
        }
    }
}

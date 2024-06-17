package io.embrace.tracerprovider


import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceId
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify


class JavaOnlyMapMapBuilder : WritableMapBuilder {
    override fun build() : WritableMap {
        return JavaOnlyMap();
    }
}

class TracerProviderModuleTest {
    companion object {
        private lateinit var tracerProviderModule: TracerProviderModule
        private lateinit var exporter: SpanExporter
        private lateinit var promise: Promise

        @JvmStatic
        @BeforeAll
        fun beforeAll(): Unit {
            exporter = mock() {
                on { export(any()) } doReturn CompletableResultCode.ofSuccess()
            }
            val provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()

            val context: ReactApplicationContext = mock()
            tracerProviderModule = TracerProviderModule(context, provider, JavaOnlyMapMapBuilder())
            tracerProviderModule.getTracer("test", "v1")

            promise = mock()
        }
    }

    @BeforeEach
    fun beforeEach(): Unit {
        clearInvocations(exporter)
        clearInvocations(promise)
    }

    @Test
    fun startSpanSimple() {
        tracerProviderModule.startSpan("test", "v1", "span_0",
            "my-span", "", 0.0, JavaOnlyMap(), JavaOnlyArray(),
            "", promise)
        tracerProviderModule.endSpan("span_0", 0.0)

        argumentCaptor<Collection<SpanData>>().apply {
            verify(exporter, times(1)).export(capture())
            assertEquals(1, allValues.size)

            val spans = allValues[0].asSequence().withIndex()
            val span1 = spans.elementAt(0).value

            assertEquals(1, spans.count())
            assertEquals("my-span", span1.name)
            assertTrue(span1.hasEnded())
        }

        val spanContext = tracerProviderModule.spanContext("span_0")

        argumentCaptor<WritableMap>().apply {
            verify(promise, times(1)).resolve(capture())
            assertEquals(1, allValues.size)
            assertEquals(spanContext.getString("spanId"), allValues[0].getString("spanId"))
            assertEquals(spanContext.getString("traceId"), allValues[0].getString("traceId"))
            assertNotEquals(allValues[0].getString("spanId"), SpanId.getInvalid())
            assertNotEquals(allValues[0].getString("traceId"), TraceId.getInvalid())
        }
    }

    @Test
    fun startSpanWithOptions() {
        var attributes = JavaOnlyMap.of(
            "my-attr1", "some-string",
            "my-attr2", true,
            "my-attr3", 344,
            "my-attr4", JavaOnlyArray.of("str1", "str2"),
            "my-attr5", JavaOnlyArray.of(true, false),
            "my-attr6", JavaOnlyArray.of(22, 44),
        )
        var links = JavaOnlyArray.of(
            JavaOnlyMap.of(
                "context", JavaOnlyMap.of(
                    "spanId", "1111000011110000",
                    "traceId", "22220000222200002222000022220000",
                ),
                "attributes", JavaOnlyMap.of(
                    "link-attr-1", "my-link-attr"
                )
            ),
             JavaOnlyMap.of(
                "context", JavaOnlyMap.of(
                    "spanId", "6666000066660000",
                    "traceId", "77770000777700007777000077770000",
                )
            )
        )

        tracerProviderModule.startSpan("test", "v1", "span_0",
            "my-span", "CLIENT", 1718386928001.0, attributes, links,
            "", promise)
        tracerProviderModule.endSpan("span_0" , 1728386928001.0)


        argumentCaptor<Collection<SpanData>>().apply {
            verify(exporter, times(1)).export(capture())
            assertEquals(1, allValues.size)

            val spans = allValues[0].asSequence().withIndex()
            val span1 = spans.elementAt(0).value

            assertEquals(1, spans.count())
            assertEquals("my-span", span1.name)
            assertEquals(SpanKind.CLIENT, span1.kind)
            assertEquals(  1718386928001000000, span1.startEpochNanos)

            assertEquals(6, span1.attributes.size())
            assertEquals("some-string", span1.attributes.get(AttributeKey.stringKey("my-attr1")))
            assertEquals(true, span1.attributes.get(AttributeKey.booleanKey("my-attr2")))
            assertEquals(344.0, span1.attributes.get(AttributeKey.doubleKey("my-attr3")))
            assertEquals(listOf("str1", "str2"), span1.attributes.get(AttributeKey.stringArrayKey("my-attr4")))
            assertEquals(listOf(true, false), span1.attributes.get(AttributeKey.booleanArrayKey("my-attr5")))
            assertEquals(listOf(22.0, 44.0), span1.attributes.get(AttributeKey.doubleArrayKey("my-attr6")))

            assertEquals(2, span1.links.size)
            assertEquals("1111000011110000", span1.links[0].spanContext.spanId)
            assertEquals("22220000222200002222000022220000", span1.links[0].spanContext.traceId)
            assertEquals(1, span1.links[0].attributes.size())
            assertEquals("my-link-attr", span1.links[0].attributes.get(AttributeKey.stringKey("link-attr-1")))
            assertEquals("6666000066660000", span1.links[1].spanContext.spanId)
            assertEquals("77770000777700007777000077770000", span1.links[1].spanContext.traceId)
            assertEquals(0, span1.links[1].attributes.size())

            assertEquals(   1728386928001000000, span1.endEpochNanos)
        }
    }

    @Test
    fun startSpanWithParent() {
        tracerProviderModule.startSpan("test", "v1", "span_0",
            "parent-span", "", 0.0, JavaOnlyMap(), JavaOnlyArray(),
            "", promise)
        tracerProviderModule.startSpan("test", "v1", "span_1",
            "child-span", "", 0.0, JavaOnlyMap(), JavaOnlyArray(),
            "span_0", promise)
        tracerProviderModule.endSpan("span_0", 0.0)
        tracerProviderModule.endSpan("span_1", 0.0)

        argumentCaptor<Collection<SpanData>>().apply {
            verify(exporter, times(2)).export(capture())
            assertEquals(2, allValues.size)

            val parentSpan = allValues[0].asSequence().withIndex().elementAt(0).value
            val childSpan = allValues[1].asSequence().withIndex().elementAt(0).value

            assertEquals(SpanId.getInvalid(), parentSpan.parentSpanId)
            assertEquals(tracerProviderModule.spanContext("span_0").getString("spanId"), childSpan.parentSpanId)
        }
    }

    @Test
    fun setAttributes() {
        tracerProviderModule.startSpan("test", "v1", "span_0",
            "my-span", "", 0.0, JavaOnlyMap(), JavaOnlyArray(),
            "", promise)
        tracerProviderModule.setAttributes("span_0", JavaOnlyMap.of(
            "my-attr1", "some-string",
            "my-attr2", true,
            "my-attr3", 344,
            "my-attr4", JavaOnlyArray.of("str1", "str2"),
            "my-attr5", JavaOnlyArray.of(true, false),
            "my-attr6", JavaOnlyArray.of(22, 44),
        ))
        tracerProviderModule.endSpan("span_0", 0.0)

        argumentCaptor<Collection<SpanData>>().apply {
            verify(exporter, times(1)).export(capture())
            assertEquals(1, allValues.size)

            val spans = allValues[0].asSequence().withIndex()
            val span1 = spans.elementAt(0).value

            assertEquals(6, span1.attributes.size())
            assertEquals("some-string", span1.attributes.get(AttributeKey.stringKey("my-attr1")))
            assertEquals(true, span1.attributes.get(AttributeKey.booleanKey("my-attr2")))
            assertEquals(344.0, span1.attributes.get(AttributeKey.doubleKey("my-attr3")))
            assertEquals(listOf("str1", "str2"), span1.attributes.get(AttributeKey.stringArrayKey("my-attr4")))
            assertEquals(listOf(true, false), span1.attributes.get(AttributeKey.booleanArrayKey("my-attr5")))
            assertEquals(listOf(22.0, 44.0), span1.attributes.get(AttributeKey.doubleArrayKey("my-attr6")))
        }
    }


    @Test
    fun addEvent() {
        tracerProviderModule.startSpan("test", "v1", "span_0",
            "my-span", "", 0.0, JavaOnlyMap(), JavaOnlyArray(),
            "", promise)
        tracerProviderModule.addEvent("span_0", "my-1st-event",
            JavaOnlyMap.of("my-attr1", "some-string"), 0.0)
        tracerProviderModule.addEvent("span_0", "my-2nd-event",
            JavaOnlyMap.of("my-attr2", "other-string"),  1518386928052.0)
        tracerProviderModule.endSpan("span_0", 0.0)

        argumentCaptor<Collection<SpanData>>().apply {
            verify(exporter, times(1)).export(capture())
            assertEquals(1, allValues.size)

            val spans = allValues[0].asSequence().withIndex()
            val span1 = spans.elementAt(0).value

            assertEquals(2, span1.events.size)

            assertEquals("my-1st-event", span1.events[0].name)
            assertEquals(1, span1.events[0].attributes.size())
            assertEquals("some-string", span1.events[0].attributes.get(AttributeKey.stringKey("my-attr1")))

            assertEquals("my-2nd-event", span1.events[1].name)
            assertEquals(1, span1.events[1].attributes.size())
            assertEquals("other-string", span1.events[1].attributes.get(AttributeKey.stringKey("my-attr2")))
            assertEquals(1518386928052000000, span1.events[1].epochNanos)
        }
    }

    @Test
    fun addLinks() {
        tracerProviderModule.startSpan("test", "v1", "span_0",
            "my-span", "", 1718386928001.0, JavaOnlyMap(), JavaOnlyArray(),
            "", promise)
        tracerProviderModule.addLinks("span_0", JavaOnlyArray.of(
            JavaOnlyMap.of(
                "context", JavaOnlyMap.of(
                    "spanId", "1111000011110000",
                    "traceId", "22220000222200002222000022220000",
                ),
                "attributes", JavaOnlyMap.of(
                    "link-attr-1", "my-link-attr"
                )
            ),
             JavaOnlyMap.of(
                "context", JavaOnlyMap.of(
                    "spanId", "6666000066660000",
                    "traceId", "77770000777700007777000077770000",
                )
            )
        ))
        tracerProviderModule.endSpan("span_0", 0.0)

        argumentCaptor<Collection<SpanData>>().apply {
            verify(exporter, times(1)).export(capture())
            assertEquals(1, allValues.size)

            val spans = allValues[0].asSequence().withIndex()
            val span1 = spans.elementAt(0).value

            assertEquals(2, span1.links.size)
            assertEquals("1111000011110000", span1.links[0].spanContext.spanId)
            assertEquals("22220000222200002222000022220000", span1.links[0].spanContext.traceId)
            assertEquals(1, span1.links[0].attributes.size())
            assertEquals("my-link-attr", span1.links[0].attributes.get(AttributeKey.stringKey("link-attr-1")))
            assertEquals("6666000066660000", span1.links[1].spanContext.spanId)
            assertEquals("77770000777700007777000077770000", span1.links[1].spanContext.traceId)
            assertEquals(0, span1.links[1].attributes.size())
        }
    }

    @Test
    fun setStatus() {
        tracerProviderModule.startSpan("test", "v1", "span_0",
            "my-span-1", "", 0.0, JavaOnlyMap(), JavaOnlyArray(),
            "", promise)
        tracerProviderModule.setStatus("span_0", JavaOnlyMap.of("code", "ERROR"))
        tracerProviderModule.endSpan("span_0", 0.0)

        tracerProviderModule.startSpan("test", "v1", "span_1",
            "my-span-2", "", 0.0, JavaOnlyMap(), JavaOnlyArray(),
            "", promise)
        tracerProviderModule.setStatus("span_1", JavaOnlyMap.of("code", "OK", "message", "some message"))
        tracerProviderModule.endSpan("span_1", 0.0)

        argumentCaptor<Collection<SpanData>>().apply {
            verify(exporter, times(2)).export(capture())
            assertEquals(2, allValues.size)

            val span1 = allValues[0].asSequence().withIndex().elementAt(0).value
            assertEquals(StatusCode.ERROR, span1.status.statusCode)
            assertEquals("", span1.status.description)

            val span2 = allValues[1].asSequence().withIndex().elementAt(0).value
            assertEquals(StatusCode.OK, span2.status.statusCode)
            assertEquals("some message", span2.status.description)
        }
    }

    @Test
    fun updateName() {
        tracerProviderModule.startSpan("test", "v1", "span_0",
            "my-span-1", "", 0.0, JavaOnlyMap(), JavaOnlyArray(),
            "", promise)
        tracerProviderModule.updateName("span_0", "my-updated-span-name")
        tracerProviderModule.endSpan("span_0", 0.0)

        argumentCaptor<Collection<SpanData>>().apply {
            verify(exporter, times(1)).export(capture())
            assertEquals(1, allValues.size)

            val span1 = allValues[0].asSequence().withIndex().elementAt(0).value
            assertEquals("my-updated-span-name", span1.name)
        }
    }

    @Test
    fun startSpanInvalidKind() {
        tracerProviderModule.startSpan(
            "test", "v1", "span_0",
            "my-span", "foo", 0.0, JavaOnlyMap(), JavaOnlyArray(),
            "", promise
        )
        tracerProviderModule.endSpan("span_0", 0.0)

        argumentCaptor<Collection<SpanData>>().apply {
            verify(exporter, times(1)).export(capture())
            assertEquals(1, allValues.size)

            val spans = allValues[0].asSequence().withIndex()
            val span1 = spans.elementAt(0).value

            assertEquals("my-span", span1.name)
            assertEquals(SpanKind.INTERNAL, span1.kind)
        }
    }

    @Test
    fun setStatusInvalid() {
        tracerProviderModule.startSpan(
            "test", "v1", "span_0",
            "my-span", "", 0.0, JavaOnlyMap(), JavaOnlyArray(),
            "", promise
        )
        tracerProviderModule.setStatus("span_0", JavaOnlyMap.of("code", "foo"))
        tracerProviderModule.endSpan("span_0", 0.0)

        argumentCaptor<Collection<SpanData>>().apply {
            verify(exporter, times(1)).export(capture())
            assertEquals(1, allValues.size)

            val spans = allValues[0].asSequence().withIndex()
            val span1 = spans.elementAt(0).value

            assertEquals("my-span", span1.name)
            assertEquals(StatusCode.UNSET, span1.status.statusCode)
        }
    }
}

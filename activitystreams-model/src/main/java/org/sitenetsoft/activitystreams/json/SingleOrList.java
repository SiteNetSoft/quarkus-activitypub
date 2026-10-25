package org.sitenetsoft.activitystreams.json;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a multi-valued Activity Streams property: on input a single value is accepted where an array is expected.
 * Bundles {@code @JsonFormat(with = ACCEPT_SINGLE_VALUE_AS_ARRAY)} so no global mapper feature is needed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@JacksonAnnotationsInside
@JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
public @interface SingleOrList {
}

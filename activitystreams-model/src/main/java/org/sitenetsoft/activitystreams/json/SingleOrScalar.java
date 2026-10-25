package org.sitenetsoft.activitystreams.json;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link SingleOrList} with the output counterpart: a one-element list is written as its element, not as a
 * one-element array. For properties that are usually single-valued on the wire ({@code url}, {@code attributedTo},
 * {@code inReplyTo}, {@code icon}, {@code image}, {@code publicKey}) and that consumers such as Mastodon
 * ({@code value_or_id}) and Lemmy read as a single id or object. Empty lists are still omitted; two or more elements
 * are written as an array.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@JacksonAnnotationsInside
@JsonFormat(with = {JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY, JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED})
@JsonSetter(nulls = Nulls.AS_EMPTY, contentNulls = Nulls.SKIP)
public @interface SingleOrScalar {
}

package org.sitenetsoft.activitystreams;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import java.util.List;
import org.sitenetsoft.activitystreams.json.ASObjectDeserializer;
import org.sitenetsoft.activitystreams.json.ObjectOrLinkDeserializer;
import org.sitenetsoft.activitystreams.json.ObjectOrLinkSerializer;
import org.sitenetsoft.activitystreams.json.RootContextWriter;

/**
 * Jackson module for Activity Streams: type-driven {@link ASObject} deserialization and {@link ObjectOrLink} handling.
 * Does not register the Java time / JDK 8 modules (see {@link ASJson}).
 */
public class ASModule extends SimpleModule {

    public ASModule() {
        this(ASTypes.defaults());
    }

    public ASModule(ASTypes types) {
        super("activitystreams");
        addDeserializer(ObjectOrLink.class, new ObjectOrLinkDeserializer());
        addSerializer(ObjectOrLink.class, new ObjectOrLinkSerializer());
        setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
                    List<BeanPropertyWriter> beanProperties) {
                if (ASObject.class.isAssignableFrom(beanDesc.getBeanClass())) {
                    beanProperties.replaceAll(w -> "@context".equals(w.getName()) ? new RootContextWriter(w) : w);
                }
                return beanProperties;
            }
        });
        setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription beanDesc,
                    JsonDeserializer<?> deserializer) {
                if (ASObject.class.isAssignableFrom(beanDesc.getBeanClass())) {
                    return new ASObjectDeserializer(types, deserializer);
                }
                return deserializer;
            }
        });
    }
}

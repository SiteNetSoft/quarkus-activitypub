package org.sitenetsoft.activitystreams.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import org.sitenetsoft.activitystreams.Contexts;

/**
 * Writer for the {@code @context} property: writes {@link Contexts#DEFAULT} when the value is null and the bean being
 * written is the root value of the generator (its output context's parent is the root context). Nested objects, list
 * elements and map values are never root, so they get no default context.
 */
public final class RootContextWriter extends BeanPropertyWriter {

    public RootContextWriter(BeanPropertyWriter base) {
        super(base);
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
        if (get(bean) == null && gen.getOutputContext().getParent().inRoot()) {
            gen.writeFieldName(getName());
            prov.defaultSerializeValue(Contexts.DEFAULT, gen);
            return;
        }
        super.serializeAsField(bean, gen, prov);
    }
}

package org.sitenetsoft.activitystreams;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.sitenetsoft.activitystreams.json.SingleOrList;

/** Activity Streams {@code Link}. {@code name} and {@code mediaType} are inherited from {@link ASObject}. */
public class ASLink extends ASObject {

    private URI href;
    @SingleOrList
    private List<String> rel = new ArrayList<>();
    private String hreflang;
    private Integer height;
    private Integer width;
    private ObjectOrLink preview;

    public URI href() { return href; }
    public ASLink href(URI v) { this.href = v; return this; }

    public List<String> rel() { return rel; }
    public ASLink rel(String... v) { this.rel = new ArrayList<>(List.of(v)); return this; }

    public String hreflang() { return hreflang; }
    public ASLink hreflang(String v) { this.hreflang = v; return this; }

    public Integer height() { return height; }
    public ASLink height(Integer v) { this.height = v; return this; }

    public Integer width() { return width; }
    public ASLink width(Integer v) { this.width = v; return this; }

    public ObjectOrLink preview() { return preview; }
    public ASLink preview(ObjectOrLink v) { this.preview = v; return this; }
}

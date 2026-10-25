package org.sitenetsoft.quarkus.nodeinfo;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.nodeinfo")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface NodeInfoBuildTimeConfig {

    /**
     * Serve {@code /.well-known/nodeinfo} and the NodeInfo documents.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Base path of the documents; they are served at {@code {path}/2.0} and {@code {path}/2.1} from the server root.
     */
    @WithDefault("/nodeinfo")
    String path();
}
